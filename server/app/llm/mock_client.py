from app.llm.base import GroundedAnswer, LLMClient
from app.schemas.agent import AgentPlan


class MockLLMClient(LLMClient):
    async def plan(self, question: str) -> AgentPlan | None:
        return None

    async def answer(self, question: str, evidence: str, draft_answer: str) -> GroundedAnswer | None:
        return None
