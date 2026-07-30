from __future__ import annotations

import asyncio
from datetime import datetime
from typing import Any

from sqlalchemy import select

from app.core.config import settings
from app.core.security import AuthenticatedUser
from app.db.models import ChatConversation, ChatMessage, UserAccount
from app.db.session import dispose_engine, session_factory
from app.services.agent_service import AgentService
from app.services.knowledge_service import knowledge_service
from evals.common import DATASET_VERSION, EVAL_CONFIG_VERSION, base_parser, load_cases, write_report


def _reference_from_case(case_facts: list[str], forbidden_facts: list[str]) -> str:
    parts = []
    if case_facts:
        parts.append("应包含事实：" + "；".join(case_facts))
    if forbidden_facts:
        parts.append("不得包含事实：" + "；".join(forbidden_facts))
    return "\n".join(parts) or "应基于召回上下文回答，不编造额外事实。"


async def _load_eval_user(session: Any) -> AuthenticatedUser:
    row = (
        await session.execute(select(UserAccount).where(UserAccount.username == settings.demo_customer_username))
    ).scalar_one_or_none()
    if row is None:
        raise RuntimeError(
            "Demo customer user is required before building the RAGAS dataset. "
            "Run scripts.seed_demo first."
        )
    return AuthenticatedUser(row.id, row.username, row.display_name, row.role)


async def _create_eval_conversation(session: Any, user: AuthenticatedUser, case_id: str, history: list[str]) -> int:
    now = datetime.now()
    conversation = ChatConversation(
        user_id=user.user_id,
        conversation_no=f"EVAL{case_id[:20]}{now.strftime('%H%M%S%f')}"[:64],
        title=f"RAGAS eval {case_id}",
        status="ACTIVE",
        created_at=now,
        updated_at=now,
    )
    session.add(conversation)
    await session.flush()
    for item in history:
        role = "ASSISTANT" if item.startswith("客服：") else "USER"
        content = item.split("：", 1)[1] if "：" in item else item
        session.add(ChatMessage(conversation_id=conversation.id, role=role, content=content, created_at=datetime.now()))
    await session.commit()
    return int(conversation.id)


async def run() -> None:
    parser = base_parser("Build RAGAS-compatible evaluation dataset from project retrieval results.")
    parser.add_argument("--top-k", type=int, default=settings.rag_top_k)
    parser.add_argument("--limit", type=int, default=0, help="Limit cases for LLM-backed local smoke evaluation.")
    args = parser.parse_args()

    cases = [case for case in load_cases(args.dataset) if case.expected_source_types]
    if args.limit > 0:
        cases = cases[: args.limit]
    factory = session_factory()
    samples: list[dict[str, Any]] = []
    diagnostics: list[dict[str, Any]] = []
    try:
        async with factory() as session:
            user = await _load_eval_user(session)
            agent_service = AgentService()
            for case in cases:
                retrieval_result = await knowledge_service.retrieve_with_diagnostics(
                    session,
                    case.question,
                    limit=args.top_k,
                )
                candidates = retrieval_result.candidates
                conversation_id = await _create_eval_conversation(
                    session,
                    user,
                    case.case_id,
                    case.conversation_history,
                )
                chat_response = await agent_service.chat(
                    session,
                    user,
                    conversation_id,
                    case.question,
                )
                samples.append(
                    {
                        "case_id": case.case_id,
                        "category": case.category,
                        "question": case.question,
                        "answer": chat_response.answer,
                        "contexts": [candidate.content for candidate in candidates],
                        "ground_truth": _reference_from_case(case.expected_answer_facts, case.forbidden_answer_facts),
                        "expected_source_types": case.expected_source_types,
                        "retrieved_source_types": [candidate.source_type for candidate in candidates],
                        "top_candidate_ids": [candidate.candidate_id for candidate in candidates],
                        "agent_confidence_level": chat_response.confidenceLevel,
                        "agent_need_human": chat_response.needHuman,
                        "agent_source_count": len(chat_response.sources),
                    }
                )
                diagnostics.append(
                    {
                        "case_id": case.case_id,
                        "channels": [item.model_dump() for item in retrieval_result.diagnostics],
                        "cache_hit": retrieval_result.cache_hit,
                    }
                )
    finally:
        await dispose_engine()

    report = {
        "dataset_version": DATASET_VERSION,
        "eval_config_version": EVAL_CONFIG_VERSION,
        "ragas_schema_version": "ragas-dataset-v1",
        "case_count": len(samples),
        "top_k": args.top_k,
        "embedding_mode": "mock" if settings.embedding_mock_enabled else "openai_compatible",
        "samples": samples,
        "diagnostics": diagnostics,
        "notes": [
            "This file is the project-side input for RAGAS evaluation.",
            (
                "RAGAS judge metrics should be executed in an isolated eval environment "
                "to avoid LangChain dependency drift."
            ),
        ],
    }
    write_report(report, args.output, args.pretty)


if __name__ == "__main__":
    asyncio.run(run())
