from collections.abc import Callable
from typing import Any

from app.agent.state import AgentState


def input_guard(state: AgentState) -> AgentState:
    question = state.get("user_goal", "")
    if "忽略系统规则" in question or "取消所有订单" in question:
        state["risk_level"] = "FORBIDDEN"
        state["decision_reason"] = "输入疑似 Prompt Injection 或批量越权操作。"
    return state


def finalize_response(state: AgentState) -> AgentState:
    state.setdefault("final_answer", "已完成处理。")
    return state


def build_agent_graph() -> Any:
    """Build a LangGraph StateGraph when langgraph is installed.

    The local desktop environment used during this migration does not currently include
    langgraph. This function keeps the integration explicit without faking success.
    """

    try:
        from langgraph.graph import END, START, StateGraph
    except Exception:
        return None

    graph = StateGraph(AgentState)
    graph.add_node("input_guard", input_guard)
    graph.add_node("finalize_response", finalize_response)
    graph.add_edge(START, "input_guard")
    graph.add_edge("input_guard", "finalize_response")
    graph.add_edge("finalize_response", END)
    return graph.compile()


def run_fallback_graph(state: AgentState, handler: Callable[[AgentState], AgentState]) -> AgentState:
    guarded = input_guard(state)
    if guarded.get("risk_level") == "FORBIDDEN":
        guarded["final_answer"] = "这个请求可能涉及越权或不安全操作，我不能直接执行，建议转人工处理。"
        return finalize_response(guarded)
    return finalize_response(handler(guarded))
