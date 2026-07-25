import json
import logging
import re
from collections.abc import Iterable
from datetime import datetime

from sqlalchemy import or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.models import AfterSaleRule, AfterSaleRuleCondition, KbChunk, KbDocument
from app.embeddings.mock_embedding import MockEmbeddingClient
from app.repositories.qdrant_store import VectorSearchHit, qdrant_store
from app.schemas.chat import SourceReference
from app.schemas.retrieval import RetrievalCandidate, RetrievalChannelDiagnostic, RetrievalQueryContext
from app.services.redis_runtime_service import redis_runtime_service

RRF_K = 60
logger = logging.getLogger(__name__)


class KnowledgeService:
    def __init__(self) -> None:
        self.last_diagnostics: list[RetrievalChannelDiagnostic] = []

    async def search(self, session: AsyncSession, query: str, limit: int | None = None) -> list[SourceReference]:
        candidates = await self.retrieve(session, query, limit=limit)
        return [
            SourceReference(
                documentId=int(candidate.document_id or 0),
                fileName=str(candidate.metadata.get("file_name", "structured-rule")),
                snippet=_snippet(candidate.content),
                score=candidate.rerank_score or candidate.fused_score or candidate.original_score,
            )
            for candidate in candidates
            if candidate.document_id is not None
        ]

    async def retrieve(
        self,
        session: AsyncSession,
        query: str,
        limit: int | None = None,
        context: RetrievalQueryContext | None = None,
    ) -> list[RetrievalCandidate]:
        diagnostics: list[RetrievalChannelDiagnostic] = []
        self.last_diagnostics = diagnostics
        effective_limit = limit or settings.rag_top_k
        normalized = _normalize_query(query)
        extracted_context = _extract_context(normalized)
        if context is not None:
            extracted_context = _merge_context(extracted_context, context)
        cache_payload = {
            "query": normalized,
            "limit": effective_limit,
            "context": extracted_context.model_dump(mode="json"),
        }
        try:
            cached = await redis_runtime_service.get_json("retrieval", cache_payload)
            if cached:
                diagnostics.append(RetrievalChannelDiagnostic(channel="cache", status="OK"))
                return [RetrievalCandidate.model_validate(candidate) for candidate in json.loads(cached)]
        except Exception:
            logger.exception("retrieval cache read failed; continuing without Redis cache")
            diagnostics.append(
                RetrievalChannelDiagnostic(channel="cache", status="DEGRADED", error_type="CACHE_READ_FAILED")
            )

        try:
            keyword_candidates = await self.keyword_recall(session, normalized, effective_limit)
            diagnostics.append(RetrievalChannelDiagnostic(channel="keyword", status="OK"))
        except Exception as exc:
            logger.exception("keyword recall failed; continuing with remaining retrieval channels")
            diagnostics.append(
                RetrievalChannelDiagnostic(
                    channel="keyword",
                    status="FAILED",
                    error_type=type(exc).__name__,
                    message=str(exc)[:200],
                )
            )
            keyword_candidates = []
        try:
            vector_candidates = await self.dense_vector_recall(normalized, effective_limit)
            diagnostics.append(RetrievalChannelDiagnostic(channel="dense_vector", status="OK"))
        except Exception as exc:
            logger.exception("dense vector recall failed; continuing with remaining retrieval channels")
            diagnostics.append(
                RetrievalChannelDiagnostic(
                    channel="dense_vector",
                    status="FAILED",
                    error_type=type(exc).__name__,
                    message=str(exc)[:200],
                )
            )
            vector_candidates = []
        try:
            rule_candidates = await self.structured_rule_recall(session, normalized, effective_limit, extracted_context)
            diagnostics.append(RetrievalChannelDiagnostic(channel="structured_rule", status="OK"))
        except Exception as exc:
            logger.exception("structured rule recall failed; continuing with remaining retrieval channels")
            diagnostics.append(
                RetrievalChannelDiagnostic(
                    channel="structured_rule",
                    status="FAILED",
                    error_type=type(exc).__name__,
                    message=str(exc)[:200],
                )
            )
            rule_candidates = []
        fused = rrf_fuse([keyword_candidates, vector_candidates, rule_candidates])
        reranked = heuristic_rerank(normalized, fused, extracted_context)
        threshold = _threshold_for_query(normalized)
        filtered = [candidate for candidate in reranked if (candidate.rerank_score or 0) >= threshold]
        result = filtered[:effective_limit]
        try:
            await redis_runtime_service.set_json(
                "retrieval",
                cache_payload,
                json.dumps([candidate.model_dump(mode="json") for candidate in result], ensure_ascii=False),
                120,
            )
        except Exception:
            logger.exception("retrieval cache write failed; continuing without Redis cache")
            diagnostics.append(
                RetrievalChannelDiagnostic(channel="cache", status="DEGRADED", error_type="CACHE_WRITE_FAILED")
            )
        return result

    async def keyword_recall(
        self, session: AsyncSession, query: str, limit: int
    ) -> list[RetrievalCandidate]:
        keywords = _extract_keywords(query)
        if not keywords:
            return []
        conditions = [KbChunk.content.like(f"%{keyword}%") for keyword in keywords]
        rows = (
            await session.execute(
                select(KbChunk, KbDocument)
                .join(KbDocument, KbDocument.id == KbChunk.document_id)
                .where(KbDocument.status == "READY", or_(*conditions))
                .order_by(KbDocument.updated_at.desc(), KbChunk.chunk_index.asc())
                .limit(limit * 2)
            )
        ).all()
        candidates: list[RetrievalCandidate] = []
        for chunk, document in rows:
            matched_terms = [keyword for keyword in keywords if keyword in chunk.content]
            if not matched_terms:
                continue
            score = min(1.0, 0.35 + 0.15 * len(matched_terms))
            candidates.append(
                RetrievalCandidate(
                    candidate_id=f"chunk:{chunk.id}",
                    source_type="keyword",
                    content=chunk.content,
                    document_id=str(document.id),
                    chunk_id=str(chunk.id),
                    rule_id=None,
                    metadata={
                        "file_name": document.original_name,
                        "matched_terms": matched_terms,
                        "keyword_score": score,
                    },
                    original_score=score,
                )
            )
        return sorted(candidates, key=lambda item: item.original_score, reverse=True)[:limit]

    async def dense_vector_recall(self, query: str, limit: int) -> list[RetrievalCandidate]:
        try:
            embedding = MockEmbeddingClient(settings.embedding_dimension)
            hits = await qdrant_store.search(embedding.embed(query), limit)
        except Exception:
            logger.exception("qdrant dense vector search failed")
            return []
        return [_vector_hit_to_candidate(hit) for hit in hits]

    async def structured_rule_recall(
        self,
        session: AsyncSession,
        query: str,
        limit: int,
        context: RetrievalQueryContext,
    ) -> list[RetrievalCandidate]:
        now = datetime.now()
        after_sale_type = context.after_sale_type or _extract_after_sale_type(query)
        query_stmt = (
            select(AfterSaleRule, AfterSaleRuleCondition)
            .join(AfterSaleRuleCondition, AfterSaleRuleCondition.rule_id == AfterSaleRule.id)
            .where(
                AfterSaleRule.status == "ACTIVE",
                AfterSaleRule.effective_from <= now,
                or_(AfterSaleRule.effective_to.is_(None), AfterSaleRule.effective_to > now),
            )
            .order_by(AfterSaleRule.priority.desc(), AfterSaleRule.effective_from.desc())
            .limit(limit * 3)
        )
        if after_sale_type:
            query_stmt = query_stmt.where(AfterSaleRuleCondition.after_sale_type.in_([after_sale_type, "ANY"]))
        rows = (await session.execute(query_stmt)).all()
        candidates: list[RetrievalCandidate] = []
        for rule, condition in rows:
            matched, matched_conditions = _condition_matches(condition, context)
            if not matched:
                continue
            score = min(1.0, 0.55 + 0.08 * len(matched_conditions) + rule.priority / 100)
            candidates.append(
                RetrievalCandidate(
                    candidate_id=f"rule:{rule.id}",
                    source_type="structured_rule",
                    content=rule.content,
                    document_id=None,
                    chunk_id=None,
                    rule_id=str(rule.id),
                    metadata={
                        "rule_code": rule.rule_code,
                        "rule_title": rule.title,
                        "rule_version": rule.version.version_code,
                        "effective_from": rule.effective_from.isoformat(),
                        "effective_to": rule.effective_to.isoformat() if rule.effective_to else None,
                        "matched_conditions": matched_conditions,
                        "priority": rule.priority,
                    },
                    original_score=score,
                )
            )
        return candidates[:limit]


def _vector_hit_to_candidate(hit: VectorSearchHit) -> RetrievalCandidate:
    return RetrievalCandidate(
        candidate_id=f"chunk:{hit.chunk_id}",
        source_type="dense_vector",
        content=hit.content,
        document_id=str(hit.document_id),
        chunk_id=str(hit.chunk_id),
        rule_id=None,
        metadata={"file_name": hit.file_name, "payload": {"document_id": hit.document_id, "chunk_id": hit.chunk_id}},
        original_score=hit.score,
    )


def rrf_fuse(result_sets: list[list[RetrievalCandidate]], k: int = RRF_K) -> list[RetrievalCandidate]:
    merged: dict[str, RetrievalCandidate] = {}
    scores: dict[str, float] = {}
    for result_set in result_sets:
        for rank, candidate in enumerate(result_set, start=1):
            existing = merged.get(candidate.candidate_id)
            if existing is None or candidate.original_score > existing.original_score:
                merged[candidate.candidate_id] = candidate.model_copy(deep=True)
            scores[candidate.candidate_id] = scores.get(candidate.candidate_id, 0.0) + 1.0 / (k + rank)
    for candidate_id, score in scores.items():
        merged[candidate_id].fused_score = score
    return sorted(merged.values(), key=lambda item: item.fused_score or 0, reverse=True)


def heuristic_rerank(
    query: str, candidates: list[RetrievalCandidate], context: RetrievalQueryContext | None = None
) -> list[RetrievalCandidate]:
    terms = set(_extract_keywords(query))
    reranked: list[RetrievalCandidate] = []
    for candidate in candidates:
        content_terms = set(_extract_keywords(candidate.content))
        overlap = len(terms & content_terms)
        structured_bonus = 0.18 if candidate.source_type == "structured_rule" else 0.0
        order_bonus = (
            0.12 if context and context.has_specific_order and candidate.source_type == "structured_rule" else 0.0
        )
        fused = candidate.fused_score or 0.0
        candidate.rerank_score = min(1.0, fused * 8 + overlap * 0.08 + structured_bonus + order_bonus)
        candidate.decision_reason = _decision_reason(candidate, context)
        reranked.append(candidate)
    return sorted(reranked, key=lambda item: item.rerank_score or 0, reverse=True)


def _decision_reason(candidate: RetrievalCandidate, context: RetrievalQueryContext | None) -> str:
    if candidate.source_type == "structured_rule":
        if context and context.has_specific_order:
            return "命中当前订单状态适用的结构化业务规则"
        return "命中当前问题适用的结构化业务规则"
    if candidate.source_type == "keyword":
        terms = candidate.metadata.get("matched_terms", [])
        return f"命中精确关键词：{', '.join(terms)}"
    return "Dense Vector 语义检索命中"


def _condition_matches(condition: AfterSaleRuleCondition, context: RetrievalQueryContext) -> tuple[bool, list[str]]:
    matched: list[str] = []
    for field in ["product_category", "order_status", "payment_status", "shipment_status", "after_sale_type"]:
        expected = getattr(condition, field)
        actual = getattr(context, field)
        if expected is None or expected == "ANY":
            continue
        if actual is None:
            if context.has_specific_order:
                return False, matched
            matched.append(f"{field}={expected}")
            continue
        if expected != actual:
            return False, matched
        matched.append(f"{field}={expected}")
    if condition.signed_within_days is not None:
        if context.signed_days is None:
            if context.has_specific_order:
                return False, matched
            matched.append(f"signed_within_days<={condition.signed_within_days}")
            return True, matched
        if context.signed_days > condition.signed_within_days:
            return False, matched
        matched.append(f"signed_within_days<={condition.signed_within_days}")
    return True, matched


def _merge_context(base: RetrievalQueryContext, override: RetrievalQueryContext) -> RetrievalQueryContext:
    return RetrievalQueryContext(
        product_category=override.product_category or base.product_category,
        order_status=override.order_status or base.order_status,
        payment_status=override.payment_status or base.payment_status,
        shipment_status=override.shipment_status or base.shipment_status,
        signed_days=override.signed_days if override.signed_days is not None else base.signed_days,
        after_sale_type=override.after_sale_type or base.after_sale_type,
        has_specific_order=override.has_specific_order or base.has_specific_order,
    )


def _normalize_query(query: str) -> str:
    return " ".join(query.strip().split())


def _extract_context(query: str) -> RetrievalQueryContext:
    return RetrievalQueryContext(after_sale_type=_extract_after_sale_type(query))


def _extract_after_sale_type(query: str) -> str | None:
    if any(term in query for term in ["运费", "邮费", "包邮", "谁承担", "承担费用", "退货费"]):
        return "RETURN_FREIGHT"
    if any(term in query for term in ["破损", "损坏", "裂", "坏了", "质量"]):
        return "QUALITY_DAMAGE"
    if any(term in query for term in ["拆封", "退货", "退吗", "能退"]):
        return "RETURN"
    if any(term in query for term in ["退款", "退钱"]):
        return "REFUND"
    if "换货" in query:
        return "EXCHANGE"
    return None


def _snippet(content: str, max_chars: int = 260) -> str:
    clean = " ".join(content.split())
    return clean[:max_chars]


def _extract_keywords(query: str) -> list[str]:
    words = [word for word in re.split(r"[\s,，。？?、；;：:]+", query) if len(word) >= 2]
    domain_terms = [
        "退款",
        "退货",
        "换货",
        "发货",
        "物流",
        "快递",
        "订单",
        "拆封",
        "售后",
        "质量",
        "库存",
        "支付",
        "运费",
        "邮费",
        "承担",
        "包邮",
        "破损",
        "损坏",
        "包装",
        "凭证",
        "照片",
        "视频",
        "二次销售",
        "影响二次销售",
    ]
    terms = [term for term in domain_terms if term in query]
    for word in words:
        if word not in terms:
            terms.append(word)
    return terms[:8]


def _threshold_for_query(query: str) -> float:
    support_terms = ["退款", "退货", "运费", "邮费", "破损", "损坏", "坏了", "质量", "拆封", "售后", "凭证"]
    if any(term in query for term in support_terms):
        return 0.08
    return settings.rag_min_retrieval_score


def dedupe_candidates(candidates: Iterable[RetrievalCandidate]) -> list[RetrievalCandidate]:
    seen: set[str] = set()
    result: list[RetrievalCandidate] = []
    for candidate in candidates:
        if candidate.candidate_id in seen:
            continue
        seen.add(candidate.candidate_id)
        result.append(candidate)
    return result


knowledge_service = KnowledgeService()
