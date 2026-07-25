from sqlalchemy.ext.asyncio import AsyncSession

from app.agent.routing import build_rule_based_plan
from app.agent.tools.registry import TOOL_REGISTRY
from app.llm import LLMProviderError, create_llm_client
from app.schemas.agent import AgentPlan
from app.services.model_runtime_config_service import model_runtime_config_service


async def build_agent_plan(session: AsyncSession, question: str) -> AgentPlan:
    rule_plan = build_rule_based_plan(question)
    runtime = await model_runtime_config_service.get_effective(session)
    client = create_llm_client(runtime)
    try:
        llm_plan = await client.plan(question)
    except LLMProviderError:
        return rule_plan.model_copy(update={"decision_reason": f"{rule_plan.decision_reason}（LLM规划失败，规则兜底）"})
    if llm_plan is None:
        return rule_plan.model_copy(update={"decision_reason": f"{rule_plan.decision_reason}（Mock规划）"})
    return _constrain_plan(llm_plan, rule_plan)


def _constrain_plan(llm_plan: AgentPlan, rule_plan: AgentPlan) -> AgentPlan:
    if any(tool not in TOOL_REGISTRY for tool in llm_plan.required_tools):
        return rule_plan
    if rule_plan.risk_level in {"HIGH", "FORBIDDEN"}:
        return rule_plan.model_copy(update={"decision_reason": f"{rule_plan.decision_reason}（规则安全层覆盖LLM规划）"})
    if llm_plan.risk_level == "HIGH" and not llm_plan.requires_confirmation:
        return llm_plan.model_copy(update={"requires_confirmation": True})
    return llm_plan
