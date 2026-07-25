import json
import re
from datetime import datetime
from decimal import Decimal
from typing import Any, cast
from uuid import uuid4

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from app.agent.graph import run_response_guard_graph
from app.agent.routing import build_rule_based_plan
from app.agent.tools.executor import tool_executor
from app.agent.tools.registry import (
    GetOrderDetailArgs,
    ListMyOrdersArgs,
    RequestOrderCancellationArgs,
    RequestRefundArgs,
)
from app.core.security import AuthenticatedUser
from app.db.models import (
    AgentActionRequest,
    AgentRetrievalTrace,
    AgentRun,
    AgentStep,
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
        effective_question = await self._resolve_conversation_context(session, conversation_id, question)
        plan = build_rule_based_plan(effective_question)
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
            self._compact(effective_question),
            f"intent={plan.intent}, risk={plan.risk_level}",
            "COMPLETED",
        )
        answer_response = await self._answer_with_tools(
            session,
            user,
            conversation_id,
            run_id,
            plan,
            effective_question,
        )
        self._record_step(
            session,
            run_id,
            "tool_executor",
            f"intent={plan.intent}",
            f"confidence={answer_response.confidenceLevel}, need_human={answer_response.needHuman}",
            "COMPLETED",
        )
        answer = answer_response.answer
        state = run_response_guard_graph(
            {
                "run_id": run_id,
                "conversation_id": conversation_id,
                "authenticated_user_id": user.user_id,
                "user_role": user.role,
                "user_goal": effective_question,
                "intent": plan.intent,
                "risk_level": plan.risk_level,
                "final_answer": answer,
            }
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

    async def _resolve_conversation_context(self, session: AsyncSession, conversation_id: int, question: str) -> str:
        if re.search(r"ORD[0-9A-Z]{8,}", question, flags=re.IGNORECASE):
            return question
        index = self._short_choice_index(question)
        previous_messages = list((
            await session.execute(
                select(ChatMessage)
                .where(ChatMessage.conversation_id == conversation_id, ChatMessage.role == "ASSISTANT")
                .order_by(ChatMessage.created_at.desc())
                .limit(6)
            )
        ).scalars().all())
        if not previous_messages:
            return question

        if index is not None:
            return self._resolve_order_choice_by_index(previous_messages, question, index)

        clean = question.strip()
        if self._order_context_follow_up(clean):
            order_no = self._latest_order_no(previous_messages)
            if order_no:
                return f"订单 {order_no} {clean}"
        if self._product_context_follow_up(clean):
            product_name = self._latest_product_name(previous_messages)
            if product_name:
                return f"{product_name} {clean}"
        return question

    def _resolve_order_choice_by_index(self, messages: list[ChatMessage], question: str, index: int) -> str:
        for message in messages:
            content = str(message.content)
            if "多笔订单" not in content:
                continue
            order_nos = re.findall(r"订单\s+(ORD[0-9A-Z]+)", content, flags=re.IGNORECASE)
            if index < len(order_nos):
                return f"订单 {order_nos[index].upper()} 物流到哪里了"
        return question

    def _latest_order_no(self, messages: list[ChatMessage]) -> str | None:
        for message in messages:
            content = str(message.content)
            exact_match = re.search(r"我查到订单\s+(ORD[0-9A-Z]+)", content, flags=re.IGNORECASE)
            if exact_match:
                return str(exact_match.group(1)).upper()
        for message in messages:
            content = str(message.content)
            order_nos = re.findall(r"订单\s+(ORD[0-9A-Z]+)", content, flags=re.IGNORECASE)
            if len(order_nos) == 1:
                return str(order_nos[0]).upper()
        return None

    def _latest_product_name(self, messages: list[ChatMessage]) -> str | None:
        for message in messages:
            content = str(message.content)
            match = re.search(r"「([^」]+)」", content)
            if match:
                return str(match.group(1))
        return None

    def _order_context_follow_up(self, question: str) -> bool:
        if not 0 < len(question) <= 24:
            return False
        terms = [
            "物流",
            "快递",
            "发货",
            "发货规则",
            "发货时效",
            "出库规则",
            "多久发货",
            "什么时候发货",
            "退货",
            "退款",
            "退钱",
            "退换货",
            "售后",
            "包装破损",
            "破损",
            "损坏",
            "换货",
            "拆封",
        ]
        return any(term in question for term in terms)

    def _short_choice_index(self, question: str) -> int | None:
        clean = question.strip()
        if clean.isdigit():
            value = int(clean)
            return value - 1 if value > 0 else None
        mapping = {"一": 0, "二": 1, "三": 2, "四": 3, "五": 4}
        return mapping.get(clean)

    def _after_sale_follow_up(self, question: str) -> bool:
        clean = question.strip()
        if not 0 < len(clean) <= 12:
            return False
        terms = ["退货", "退款", "退钱", "退换货", "售后", "包装破损", "破损", "损坏", "换货", "拆封"]
        return any(term in clean for term in terms)

    def _shipping_rule_follow_up(self, question: str) -> bool:
        clean = question.strip()
        if not 0 < len(clean) <= 16:
            return False
        return self._is_shipping_rule_question(clean)

    def _product_context_follow_up(self, question: str) -> bool:
        if not 0 < len(question) <= 18:
            return False
        terms = ["库存", "价格", "多少钱", "在售", "还有货", "发货规则", "发货时效", "售后规则"]
        return any(term in question for term in terms)

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
            order = cast(CustomerOrder | None, await tool_executor.execute(
                session,
                run_id,
                user,
                "get_order_detail",
                self._order_tool_args(plan),
                lambda args: self._resolve_order_by_args(session, user, args),
                lambda resolved: "resolved order" if resolved else "not found",
            ))
            if order is None:
                return self._plain_response(
                    conversation_id,
                    "我还没定位到要处理的订单。请补充订单号，或说明是最近订单/第几个订单。",
                )
            resolved_order = order
            action_type = "ORDER_CANCELLATION" if plan.intent == "CANCEL_ORDER" else "REFUND"
            action_tool = "request_order_cancellation" if action_type == "ORDER_CANCELLATION" else "request_refund"
            await tool_executor.execute(
                session,
                run_id,
                user,
                action_tool,
                {"order_no": resolved_order.order_no, "reason": question},
                lambda args: self._create_action_request(session, user, run_id, resolved_order, action_type, args),
                lambda request: f"created pending approval request {request.id or 'new'}",
            )
            action_label = "取消订单" if action_type == "ORDER_CANCELLATION" else "退款"
            return self._plain_response(
                conversation_id,
                f"我已生成{action_label}申请计划，订单是 {resolved_order.order_no}。"
                "这类操作不会由模型直接改数据库，需要您确认后进入管理员审批；审批通过后才会执行。",
            )
        if plan.intent in {"SHIPPING_QUERY", "ORDER_QUERY"}:
            if plan.order_reference and plan.order_reference.list_all:
                orders = cast(list[CustomerOrder], await tool_executor.execute(
                    session,
                    run_id,
                    user,
                    "list_my_orders",
                    {"limit": 20},
                    lambda args: self._resolve_orders_by_args(session, user, args),
                    lambda resolved: f"resolved {len(resolved)} orders",
                ))
                if not orders:
                    return self._plain_response(conversation_id, "我这边暂时没有查到您的已下单商品。")
                return self._plain_response(conversation_id, self._order_list_answer(orders))
            if plan.order_reference and plan.order_reference.product_keyword and not plan.order_reference.latest:
                matched_orders = await self._resolve_orders_by_args(
                    session,
                    user,
                    ListMyOrdersArgs(product_keyword=plan.order_reference.product_keyword, limit=20),
                )
                if len(matched_orders) > 1:
                    return self._plain_response(conversation_id, self._multiple_order_answer(matched_orders))
            order = cast(CustomerOrder | None, await tool_executor.execute(
                session,
                run_id,
                user,
                "get_order_detail",
                self._order_tool_args(plan),
                lambda args: self._resolve_order_by_args(session, user, args),
                lambda resolved: "resolved order" if resolved else "not found",
            ))
            if order is None:
                return self._plain_response(
                    conversation_id,
                    "我没有定位到对应订单。您可以说“最近订单”“第二个订单”，或直接提供订单号。",
                )
            return self._plain_response(conversation_id, self._order_answer(order, question))
        if plan.intent == "PRODUCT_QUERY" and plan.product_reference:
            product = cast(ProductCatalog | None, await tool_executor.execute(
                session,
                run_id,
                user,
                "get_product_information",
                {"product_keyword": plan.product_reference},
                lambda args: self._resolve_product(session, args.product_keyword),
                lambda resolved: "resolved product" if resolved else "not found",
            ))
            if product is not None:
                return self._plain_response(
                    conversation_id,
                    f"「{product.product_name}」当前状态为{product.sale_status}，库存 {product.stock_quantity} 件，"
                    f"售价 {product.price} 元。发货规则：{self._clean_sentence(product.dispatch_rule)}。"
                    f"售后说明：{self._clean_sentence(product.after_sale_rule)}。",
                )
        retrieval_context = await self._build_retrieval_context(session, user, plan)
        candidates = cast(list[RetrievalCandidate], await tool_executor.execute(
            session,
            run_id,
            user,
            "search_knowledge_base",
            {"query": question, "limit": 5},
            lambda args: knowledge_service.retrieve(
                session,
                args.query,
                limit=args.limit,
                context=retrieval_context,
            ),
            lambda resolved: f"resolved {len(resolved)} retrieval candidates",
        ))
        self._record_retrieval_trace(session, run_id, candidates, knowledge_service.last_diagnostics)
        sources = self._source_references(candidates)
        if candidates:
            best = candidates[0]
            order_for_answer = await self._resolve_order(session, user, plan) if plan.order_reference else None
            return ChatResponse(
                conversationId=conversation_id,
                answer=self._knowledge_answer(candidates, order_for_answer, question),
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
        return await self._resolve_order_by_args(
            session,
            user,
            GetOrderDetailArgs.model_validate(self._order_tool_args(plan)),
        )

    async def _resolve_order_by_args(
        self, session: AsyncSession, user: AuthenticatedUser, args: GetOrderDetailArgs
    ) -> CustomerOrder | None:
        query = (
            select(CustomerOrder)
            .options(selectinload(CustomerOrder.product))
            .where(CustomerOrder.user_id == user.user_id)
        )
        if args.order_no:
            return (await session.execute(query.where(CustomerOrder.order_no == args.order_no))).scalar_one_or_none()
        query = query.order_by(CustomerOrder.created_at.desc())
        orders = (await session.execute(query.limit(20))).scalars().all()
        if not orders:
            return None
        if args.ordinal_index is not None:
            return orders[args.ordinal_index] if args.ordinal_index < len(orders) else None
        if args.product_keyword:
            for order in orders:
                if args.product_keyword in order.product.product_name:
                    return order
        return orders[0]

    async def _resolve_orders(
        self, session: AsyncSession, user: AuthenticatedUser, limit: int = 20
    ) -> list[CustomerOrder]:
        return await self._resolve_orders_by_args(session, user, ListMyOrdersArgs(limit=limit))

    async def _resolve_orders_by_args(
        self, session: AsyncSession, user: AuthenticatedUser, args: ListMyOrdersArgs
    ) -> list[CustomerOrder]:
        query = (
            select(CustomerOrder)
            .options(selectinload(CustomerOrder.product))
            .where(CustomerOrder.user_id == user.user_id)
        )
        if args.status:
            query = query.where(CustomerOrder.status == args.status)
        query = query.order_by(CustomerOrder.created_at.desc()).limit(args.limit)
        orders = list((await session.execute(query)).scalars().all())
        if args.product_keyword:
            return [order for order in orders if args.product_keyword in order.product.product_name]
        return orders

    def _order_tool_args(self, plan: AgentPlan) -> dict[str, object]:
        ref = plan.order_reference
        if ref is None:
            return {"latest": True}
        return {
            "order_no": ref.order_no,
            "ordinal_index": ref.ordinal_index,
            "product_keyword": ref.product_keyword,
            "latest": ref.latest or not any([ref.order_no, ref.ordinal_index is not None, ref.product_keyword]),
        }

    async def _create_action_request(
        self,
        session: AsyncSession,
        user: AuthenticatedUser,
        run_id: str,
        order: CustomerOrder,
        action_type: str,
        args: RequestOrderCancellationArgs | RequestRefundArgs,
    ) -> AgentActionRequest:
        request = AgentActionRequest(
            run_id=run_id,
            action_type=action_type,
            target_order_id=order.id,
            action_payload_json=json.dumps({"reason": args.reason}, ensure_ascii=False),
            risk_level="HIGH",
            status="PENDING",
            idempotency_key=f"{action_type}:{order.id}:{user.user_id}:{run_id}",
            created_by=user.user_id,
            created_at=datetime.now(),
        )
        session.add(request)
        await session.flush()
        return request

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
        if self._is_shipping_rule_question(question):
            return (
                f"{lead}这个商品的发货规则是：{order.product.dispatch_rule}"
                f"这单当前状态：{self._order_status_label(order.status)}，"
                f"预计发货时间：{self._format_time(order.expected_ship_at)}。"
            )
        if order.status in {"PAID", "WAITING_SHIPMENT"}:
            if any(word in question for word in ["物流", "快递", "到哪", "到哪里"]):
                return f"{lead}这单还没有进入物流运输，预计发货时间是 {self._format_time(order.expected_ship_at)}。"
            return f"{lead}当前还未发货，预计发货时间是 {self._format_time(order.expected_ship_at)}。"
        if order.status in {"SHIPPED", "IN_TRANSIT"}:
            return f"{lead}这单已发货，当前状态是 {order.status}。"
        if order.status == "SIGNED":
            return f"{lead}这单已签收。如需售后，可以继续描述商品问题。"
        return f"{lead}当前状态是 {self._order_status_label(order.status)}。"

    def _is_shipping_rule_question(self, question: str) -> bool:
        return any(term in question for term in ["发货规则", "发货时效", "出库规则", "多久发货", "什么时候发货"])

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

    def _multiple_order_answer(self, orders: list[CustomerOrder]) -> str:
        lines = ["我查到这个商品有多笔订单，先不替您默认选某一单："]
        for index, order in enumerate(orders, start=1):
            latest_event = "暂无物流"
            lines.append(
                f"{index}. 订单 {order.order_no}，商品「{order.product.product_name}」，"
                f"状态：{self._order_status_label(order.status)}，预计发货：{self._format_time(order.expected_ship_at)}，"
                f"{latest_event}。"
            )
        lines.append("请直接发订单号，或说“第几个订单”，我再按那一单查询。")
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
            if candidate.source_type == "structured_rule":
                sources.append(
                    SourceReference(
                        documentId=0,
                        fileName=f"售后规则：{candidate.metadata.get('rule_title', '结构化规则')}",
                        snippet=candidate.content[:260],
                        score=candidate.rerank_score or candidate.fused_score or candidate.original_score,
                    )
                )
                continue
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

    def _knowledge_answer(
        self,
        candidates: list[RetrievalCandidate],
        order: CustomerOrder | None = None,
        question: str = "",
    ) -> str:
        structured = [candidate for candidate in candidates if candidate.source_type == "structured_rule"]
        if order is not None and self._is_after_sale_rule_question(question):
            product_rule = (
                f"我查到这单商品是「{order.product.product_name}」。"
                f"该商品售后规则：{self._clean_sentence(order.product.after_sale_rule)}。"
            )
            if structured:
                return f"{product_rule}\n{structured[0].content}"
            return product_rule
        if structured:
            rule = structured[0]
            return rule.content
        return candidates[0].content[:320]

    def _is_after_sale_rule_question(self, question: str) -> bool:
        terms = ["退货", "退款", "退钱", "售后", "破损", "损坏", "包装", "拆封", "换货", "能不能退", "怎么退"]
        return any(term in question for term in terms)

    def _clean_sentence(self, value: str) -> str:
        return value.strip().rstrip("。.!！")

    def _record_retrieval_trace(
        self,
        session: AsyncSession,
        run_id: str,
        candidates: list[RetrievalCandidate],
        diagnostics: list[Any] | None = None,
    ) -> None:
        for diagnostic in diagnostics or []:
            payload = (
                diagnostic.model_dump(mode="json")
                if hasattr(diagnostic, "model_dump")
                else {"value": str(diagnostic)}
            )
            status = str(payload.get("status", "UNKNOWN"))
            if status == "OK":
                continue
            channel = str(payload.get("channel", "unknown"))
            session.add(
                AgentRetrievalTrace(
                    run_id=run_id,
                    candidate_id=f"diagnostic:{channel}",
                    source_type=channel,
                    document_id=None,
                    chunk_id=None,
                    rule_id=None,
                    original_score=Decimal("0"),
                    fused_score=None,
                    rerank_score=None,
                    selected=False,
                    decision_reason=f"retrieval channel {status.lower()}: {payload.get('error_type') or 'unknown'}",
                    metadata_json=json.dumps(payload, ensure_ascii=False),
                    created_at=datetime.now(),
                )
            )
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


def _shipment_status(order_status: str) -> str:
    if order_status in {"SHIPPED", "IN_TRANSIT", "SIGNED"}:
        return "SHIPPED"
    return "UNSHIPPED"
