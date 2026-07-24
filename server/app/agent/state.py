from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    thread_id: str
    run_id: str
    conversation_id: int
    authenticated_user_id: int
    user_role: str
    messages: list[dict[str, str]]
    user_goal: str
    intent: str
    entities: dict[str, Any]
    selected_tools: list[str]
    tool_results: list[dict[str, Any]]
    retrieved_chunks: list[dict[str, Any]]
    retrieval_score: float
    risk_level: str
    requires_confirmation: bool
    pending_action_id: int | None
    approval_decision: str | None
    decision_reason: str | None
    error: str | None
    final_answer: str | None
