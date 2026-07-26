from __future__ import annotations

import asyncio
from collections import defaultdict
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db.session import dispose_engine, session_factory
from app.schemas.retrieval import RetrievalCandidate, RetrievalQueryContext
from app.services.knowledge_service import KnowledgeService, heuristic_rerank, rrf_fuse
from evals.common import DATASET_VERSION, EVAL_CONFIG_VERSION, base_parser, load_cases, now_ms, ratio, write_report

PLANS = {
    "keyword_only": ("keyword",),
    "dense_only": ("dense",),
    "keyword_dense": ("keyword", "dense"),
    "all_raw": ("keyword", "dense", "structured"),
    "all_rrf": ("keyword", "dense", "structured", "rrf"),
    "all_rrf_rerank": ("keyword", "dense", "structured", "rrf", "rerank"),
}


async def _recall(
    service: KnowledgeService,
    session: AsyncSession,
    query: str,
    channels: tuple[str, ...],
    limit: int,
) -> list[RetrievalCandidate]:
    result_sets: list[list[RetrievalCandidate]] = []
    context = RetrievalQueryContext()
    if "keyword" in channels:
        result_sets.append(await service.keyword_recall(session, query, limit))
    if "dense" in channels:
        result_sets.append(await service.dense_vector_recall(query, limit))
    if "structured" in channels:
        result_sets.append(await service.structured_rule_recall(session, query, limit, context))
    if "rrf" in channels:
        candidates = rrf_fuse(result_sets)
    else:
        candidates = [candidate for result_set in result_sets for candidate in result_set]
    if "rerank" in channels:
        candidates = heuristic_rerank(query, candidates, context)
    return candidates[:limit]


def _rank_of_expected(candidates: list[RetrievalCandidate], expected_source_types: list[str]) -> int | None:
    expected = set(expected_source_types)
    if not expected:
        return None
    for index, candidate in enumerate(candidates, start=1):
        if candidate.source_type in expected:
            return index
    return None


async def run() -> None:
    parser = base_parser("Run retrieval-channel ablation evaluation.")
    parser.add_argument("--top-k", type=int, default=settings.rag_top_k)
    args = parser.parse_args()
    cases = [case for case in load_cases(args.dataset) if case.expected_source_types]
    service = KnowledgeService()
    factory = session_factory()
    failures: dict[str, list[dict[str, Any]]] = defaultdict(list)
    plan_metrics: dict[str, dict[str, Any]] = {}
    status = "completed"
    dependency_error: dict[str, str] | None = None

    try:
        async with factory() as session:
            for plan_name, channels in PLANS.items():
                hits = 0
                reciprocal_rank_total = 0.0
                latency_total = 0
                category_totals: dict[str, int] = defaultdict(int)
                category_hits: dict[str, int] = defaultdict(int)
                for case in cases:
                    started = now_ms()
                    candidates = await _recall(service, session, case.question, channels, args.top_k)
                    latency_total += max(0, now_ms() - started)
                    rank = _rank_of_expected(candidates, case.expected_source_types)
                    category_totals[case.category] += 1
                    if rank is not None:
                        hits += 1
                        category_hits[case.category] += 1
                        reciprocal_rank_total += 1 / rank
                    else:
                        failures[plan_name].append(
                            {
                                "case_id": case.case_id,
                                "category": case.category,
                                "question": case.question,
                                "expected_source_types": case.expected_source_types,
                                "actual_source_types": [candidate.source_type for candidate in candidates],
                                "top_candidates": [
                                    {
                                        "candidate_id": candidate.candidate_id,
                                        "source_type": candidate.source_type,
                                        "score": candidate.rerank_score
                                        or candidate.fused_score
                                        or candidate.original_score,
                                    }
                                    for candidate in candidates[:3]
                                ],
                            }
                        )
                plan_metrics[plan_name] = {
                    "channels": channels,
                    "recall_at_k": ratio(hits, len(cases)),
                    "mrr": round(reciprocal_rank_total / len(cases), 4) if cases else None,
                    "average_latency_ms": round(latency_total / len(cases), 2) if cases else None,
                    "category_recall_at_k": {
                        category: ratio(category_hits[category], total)
                        for category, total in sorted(category_totals.items())
                    },
                }
    except Exception as exc:
        status = "dependency_unavailable"
        dependency_error = {
            "type": type(exc).__name__,
            "message": str(exc),
        }
    finally:
        await dispose_engine()

    report: dict[str, Any] = {
        "status": status,
        "dataset_version": DATASET_VERSION,
        "eval_config_version": EVAL_CONFIG_VERSION,
        "case_count": len(cases),
        "top_k": args.top_k,
        "embedding_mode": "mock" if settings.embedding_mock_enabled else "openai_compatible",
        "warning": (
            "当前 Dense 通道使用 Mock Embedding，消融结果只能验证工程链路，不能代表真实语义召回质量。"
            if settings.embedding_mock_enabled
            else None
        ),
        "plans": plan_metrics,
        "failures": failures,
    }
    if dependency_error:
        report["dependency_error"] = dependency_error
        report["notes"] = [
            "Retrieval ablation requires reachable MySQL and optional Qdrant services.",
            "This dependency failure is reported explicitly instead of being treated as low recall.",
        ]
    write_report(report, args.output, args.pretty)


if __name__ == "__main__":
    asyncio.run(run())
