import json
from datetime import datetime
from decimal import Decimal
from uuid import uuid4

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.agent.graph import run_fallback_graph
from app.agent.routing import build_rule_based_plan
from app.core.security import AuthenticatedUser
from app.db.models import (
    AgentActionRequest,
    AgentRetrievalTrace,
    AgentRun,
    AgentStep,
    AgentToolCall,
    ChatMessage,
    CustomerOrder,
    ProductCatalog,
)
from app.schemas.agent import AgentPlan
from app.schemas.chat import ChatResponse, SourceReference
from app.schemas.retrieval import RetrievalCandidate, RetrievalQueryContext
from app.services.knowledge_service import knowledge_service


class AgentService:
    async def chat(
        self,
        session: AsyncSession,
        user: AuthenticatedUser,
        conversation_id: int,
        question: str,
    ) -> ChatResponse:
        now = datetime.now()
        run_id = "run_" + uuid4().hex
        plan = build_rule_based_plan(question)
        session.add(ChatMessage(conversation_id=conversation_id, role="USER", content=question, created_at=now))
        session.add(
            AgentRun(
                run_id=run_id,
                thread_id=f"conversation-{conversation_id}",
                conversation_id=conversation_id,
                user_id=user.user_id,
                status="RUNNING",
                intent=plan.intent,
                risk_level=plan.risk_level,
                started_at=now,
                request_id=uuid4().hex,
            )
        )
        await session.flush()
        self._record_step(
            session,
            run_id,
            "intent_router",
            self._compact(question),
            f"intent={plan.intent}, risk={plan.risk_level}",
            "COMPLETED",
        )
        answer_response = await self._answer_with_tools(session, user, conversation_id, run_id, plan, question)
        self._record_step(
            session,
            run_id,
            "tool_executor",
            f"intent={plan.intent}",
            f"confidence={answer_response.confidenceLevel}, need_human={answer_response.needHuman}",
            "COMPLETED",
        )
        answer = answer_response.answer
        state = run_fallback_graph(
            {
                "run_id": run_id,
                "conversation_id": conversation_id,
                "authenticated_user_id": user.user_id,
                "user_role": user.role,
                "user_goal": question,
                "intent": plan.intent,
                "risk_level": plan.risk_level,
                "final_answer": answer,
            },
            lambda s: s,
        )
        final_answer = state.get("final_answer") or answer
        self._record_step(
            session,
            run_id,
            "response_guardrail",
            self._compact(answer),
            self._compact(final_answer),
            "COMPLETED",
        )
        session.add(
            ChatMessage(
                conversation_id=conversation_id,
                role="ASSISTANT",
                content=final_answer,
                sources_json=answer_response.model_dump_json(include={"sources"}),
                retrieval_score=answer_response.retrievalScore,
                confidence_level=answer_response.confidenceLevel,
                need_human=answer_response.needHuman,
                created_at=datetime.now(),
            )
        )
        await session.execute(
            update(AgentRun)
            .where(AgentRun.run_id == run_id)
            .values(status="COMPLETED", completed_at=datetime.now(), final_answer=final_answer)
        )
        await session.commit()
        return ChatResponse(
            conversationId=conversation_id,
            answer=final_answer,
            sources=answer_response.sources,
            retrievalScore=answer_response.retrievalScore,
            confidenceLevel=answer_response.confidenceLevel,
            needHuman=answer_response.needHuman,
        )

    async def _answer_with_tools(
        self,
        session: AsyncSession,
        user: AuthenticatedUser,
        conversation_id: int,
        run_id: str,
        plan: AgentPlan,
        question: str,
    ) -> ChatResponse:
        if plan.intent in {"CANCEL_ORDER", "REFUND_REQUEST"}:
            order = await self._resolve_order(session, user, plan)
            if order is None:
                return self._plain_response(
                    conversation_id,
                    "我还没定位到要处理的订单。请补充订单号，或说明是最近订单/第几个订单。",
                )
            action_type = "ORDER_CANCELLATION" if plan.intent == "CANCEL_ORDER" else "REFUND"
            request = AgentActionRequest(
                run_id=run_id,
                action_type=action_type,
                target_order_id=order.id,
                action_payload_json=json.dumps({"reason": question}, ensure_ascii=False),
                risk_level="HIGH",
                status="PENDING",
                idempotency_key=f"{action_type}:{order.id}:{user.user_id}:{run_id}",
                created_by=user.user_id,
                created_at=datetime.now(),
            )
            session.add(request)
            await self._record_tool(
                session,
                run_id,
                "request_order_cancellation" if action_type == "ORDER_CANCELLATION" else "request_refund",
                {"order_no": order.order_no},
                "created pending approval",
                True,
            )
            action_label = "取消订单" if action_type == "ORDER_CANCELLATION" else "退款"
            return self._plain_response(
                conversation_id,
                f"我已生成{action_label}申请计划，订单是 {order.order_no}。"
                "这类操作不会由模型直接改数据库，需要您确认后进入管理员审批；审批通过后才会执行。",
            )
        if plan.intent in {"SHIPPING_QUERY", "ORDER_QUERY"}:
            if plan.order_reference and plan.order_reference.list_all:
                orders = await self._resolve_orders(session, user, limit=20)
                await self._record_tool(
                    session,
                    run_id,
                    "list_user_orders",
                    {"limit": 20},
                    f"resolved {len(orders)} orders",
                    bool(orders),
                )
                if not orders:
                    return self._plain_response(conversation_id, "我这边暂时没有查到您的已下单商品。")
                return self._plain_response(conversation_id, self._order_list_answer(orders))
            order = await self._resolve_order(session, user, plan)
            await self._record_tool(
                session,
                run_id,
                "get_order_detail",
                plan.model_dump(mode="json"),
                "resolved order" if order else "not found",
                order is not None,
            )
            if order is None:
                return self._plain_response(
                    conversation_id,
                    "我没有定位到对应订单。您可以说“最近订单”“第二个订单”，或直接提供订单号。",
                )
            return self._plain_response(conversation_id, self._order_answer(order, question))
        if plan.intent == "PRODUCT_QUERY" and plan.product_reference:
            product = await self._resolve_product(session, plan.product_reference)
            await self._record_tool(
                session,
                run_id,
                "get_product_information",
                {"keyword": plan.product_reference},
                "resolved product" if product else "not found",
                product is not None,
            )
            if product is not None:
                return self._plain_response(
                    conversation_id,
                    f"「{product.product_name}」当前状态为{product.sale_status}，库存 {product.stock_quantity} 件，"
                    f"售价 {product.price} 元。发货规则：{product.dispatch_rule}。售后说明：{product.after_sale_rule}",
                )
        retrieval_context = await self._build_retrieval_context(session, user, plan)
        candidates = await knowledge_service.retrieve(session, question, context=retrieval_context)
        self._record_retrieval_trace(session, run_id, candidates)
        sources = self._source_references(candidates)
        await self._record_tool(
            session,
            run_id,
            "hybrid_retrieve_knowledge_base",
            {"query": question, "channels": ["keyword", "dense_vector", "structured_rule"], "limit": len(candidates)},
            "resolved knowledge" if candidates else "not found",
            bool(candidates),
        )
        if candidates:
            best = candidates[0]
            return ChatResponse(
                conversationId=conversation_id,
                answer=self._knowledge_answer(candidates),
                sources=sources,
                retrievalScore=best.rerank_score or best.fused_score or best.original_score,
                confidenceLevel="MEDIUM" if (best.rerank_score or 0) >= 0.5 else "LOW",
                needHuman=False,
            )
        return self._plain_response(
            conversation_id,
            "这个问题我暂时没有足够依据直接回答。为了避免编造规则，建议转人工客服处理。",
            confidence_level="LOW",
            need_human=True,
        )

    async def _resolve_order(
        self, session: AsyncSession, user: AuthenticatedUser, plan: AgentPlan
    ) -> CustomerOrder | None:
        ref = plan.order_reference
        query = (
            select(CustomerOrder)
            .options(selectinload(CustomerOrder.product))
            .where(CustomerOrder.user_id == user.user_id)
        )
        if ref and ref.order_no:
            return (await session.execute(query.where(CustomerOrder.order_no == ref.order_no))).scalar_one_or_none()
        query = query.order_by(CustomerOrder.created_at.desc())
        orders = (await session.execute(query.limit(20))).scalars().all()
        if not orders:
            return None
        if ref and ref.ordinal_index is not None:
            return orders[ref.ordinal_index] if ref.ordinal_index < len(orders) else None
        if ref and ref.product_keyword:
            for order in orders:
                if ref.product_keyword in order.product.product_name:
                    return order
        return orders[0]

    async def _resolve_orders(
        self, session: AsyncSession, user: AuthenticatedUser, limit: int = 20
    ) -> list[CustomerOrder]:
        return list(
            (
                await session.execute(
                    select(CustomerOrder)
                    .options(selectinload(CustomerOrder.product))
                    .where(CustomerOrder.user_id == user.user_id)
                    .order_by(CustomerOrder.created_at.desc())
                    .limit(limit)
                )
            )
            .scalars()
            .all()
        )

    async def _resolve_product(self, session: AsyncSession, keyword: str) -> ProductCatalog | None:
        return (
            await session.execute(
                select(ProductCatalog)
                .where(
                    ProductCatalog.product_name.like(f"%{keyword}%") | ProductCatalog.product_code.like(f"%{keyword}%")
                )
                .order_by(ProductCatalog.id.asc())
                .limit(1)
            )
        ).scalar_one_or_none()

    async def _build_retrieval_context(
        self, session: AsyncSession, user: AuthenticatedUser, plan: AgentPlan
    ) -> RetrievalQueryContext:
        context = RetrievalQueryContext()
        if plan.order_reference is None:
            return context
        order = await self._resolve_order(session, user, plan)
        if order is None:
            return context
        signed_days = (datetime.now() - order.signed_at).days if order.signed_at else None
        return RetrievalQueryContext(
            product_category=order.product.category,
            order_status=order.status,
            payment_status="PAID" if order.paid_at else "UNPAID",
            shipment_status=_shipment_status(order.status),
            signed_days=signed_days,
            has_specific_order=True,
        )

    def _order_answer(self, order: CustomerOrder, question: str) -> str:
        lead = f"我查到订单 {order.order_no} 是「{order.product.product_name}」。"
        if order.status in {"PAID", "WAITING_SHIPMENT"}:
            if any(word in question for word in ["物流", "快递", "到哪", "到哪里"]):
                return f"{lead}这单还没有进入物流运输，预计发货时间是 {self._format_time(order.expected_ship_at)}。"
            return f"{lead}当前还未发货，预计发货时间是 {self._format_time(order.expected_ship_at)}。"
        if order.status in {"SHIPPED", "IN_TRANSIT"}:
            return f"{lead}这单已发货，当前状态是 {order.status}。"
        if order.status == "SIGNED":
            return f"{lead}这单已签收。如需售后，可以继续描述商品问题。"
        return f"{lead}当前状态是 {order.status}。"

    def _order_list_answer(self, orders: list[CustomerOrder]) -> str:
        lines = ["我查到您已下单的商品如下，按下单时间从近到远排列："]
        for index, order in enumerate(orders, start=1):
            lines.append(
                f"{index}. 「{order.product.product_name}」x {order.quantity}，"
                f"订单号 {order.order_no}，状态：{self._order_status_label(order.status)}，"
                f"预计发货：{self._format_time(order.expected_ship_at)}。"
            )
        lines.append("您可以继续问“第几个订单物流到哪里了”，也可以直接按商品名或订单号查询。")
        return "\n".join(lines)

    def _order_status_label(self, status: str) -> str:
        return {
            "PENDING_PAYMENT": "待付款",
            "PAID": "已付款",
            "WAITING_SHIPMENT": "待发货",
            "SHIPPED": "已发货",
            "IN_TRANSIT": "运输中",
            "SIGNED": "已签收",
            "REFUNDING": "退款中",
            "REFUNDED": "已退款",
            "CANCELLED": "已取消",
        }.get(status, status)

    def _source_references(self, candidates: list[RetrievalCandidate]) -> list[SourceReference]:
        sources: list[SourceReference] = []
        for candidate in candidates:
            if candidate.document_id is None:
                continue
            sources.append(
                SourceReference(
                    documentId=int(candidate.document_id),
                    fileName=str(candidate.metadata.get("file_name", "knowledge")),
                    snippet=candidate.content[:260],
                    score=candidate.rerank_score or candidate.fused_score or candidate.original_score,
                )
            )
        return sources

    def _knowledge_answer(self, candidates: list[RetrievalCandidate]) -> str:
        structured = [candidate for candidate in candidates if candidate.source_type == "structured_rule"]
        if structured:
            rule = structured[0]
            title = str(rule.metadata.get("rule_title", "售后规则"))
            return f"{rule.content}\n\n依据：{title}。如果订单情况和描述不一致，建议转人工复核。"
        return candidates[0].content[:320]

    def _record_retrieval_trace(
        self, session: AsyncSession, run_id: str, candidates: list[RetrievalCandidate]
    ) -> None:
        selected_ids = {candidate.candidate_id for candidate in candidates[:3]}
        for candidate in candidates:
            session.add(
                AgentRetrievalTrace(
                    run_id=run_id,
                    candidate_id=candidate.candidate_id,
                    source_type=candidate.source_type,
                    document_id=candidate.document_id,
                    chunk_id=candidate.chunk_id,
                    rule_id=candidate.rule_id,
                    original_score=Decimal(str(candidate.original_score)),
                    fused_score=Decimal(str(candidate.fused_score)) if candidate.fused_score is not None else None,
                    rerank_score=Decimal(str(candidate.rerank_score)) if candidate.rerank_score is not None else None,
                    selected=candidate.candidate_id in selected_ids,
                    decision_reason=candidate.decision_reason,
                    metadata_json=json.dumps(candidate.metadata, ensure_ascii=False),
                    created_at=datetime.now(),
                )
            )

    def _format_time(self, value: datetime | None) -> str:
        return value.strftime("%Y-%m-%d %H:%M") if value else "暂未同步"

    def _plain_response(
        self,
        conversation_id: int,
        answer: str,
        confidence_level: str = "HIGH",
        need_human: bool = False,
    ) -> ChatResponse:
        return ChatResponse(
            conversationId=conversation_id,
            answer=answer,
            sources=[],
            retrievalScore=0,
            confidenceLevel=confidence_level,
            needHuman=need_human,
        )

    def _record_step(
        self,
        session: AsyncSession,
        run_id: str,
        node_name: str,
        input_summary: str | None,
        output_summary: str | None,
        status: str,
    ) -> None:
        session.add(
            AgentStep(
                run_id=run_id,
                node_name=node_name,
                input_summary=input_summary,
                output_summary=output_summary,
                status=status,
                created_at=datetime.now(),
            )
        )

    def _compact(self, value: str, limit: int = 240) -> str:
        normalized = " ".join(value.split())
        return normalized if len(normalized) <= limit else normalized[: limit - 3] + "..."

    async def _record_tool(
        self,
        session: AsyncSession,
        run_id: str,
        tool_name: str,
        arguments: dict[str, object],
        result_summary: str,
        success: bool,
    ) -> None:
        session.add(
            AgentToolCall(
                run_id=run_id,
                tool_name=tool_name,
                redacted_arguments_json=json.dumps(arguments, ensure_ascii=False),
                result_summary=result_summary,
                success=success,
                created_at=datetime.now(),
            )
        )


def _shipment_status(order_status: str) -> str:
    if order_status in {"SHIPPED", "IN_TRANSIT", "SIGNED"}:
        return "SHIPPED"
    return "UNSHIPPED"
