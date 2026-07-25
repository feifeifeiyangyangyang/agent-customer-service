from typing import Protocol

from pydantic import BaseModel

from app.schemas.agent import AgentPlan


class GroundedAnswer(BaseModel):
    answer: str
    confidence_level: str
    need_human: bool
    cited_candidate_ids: list[str] = []


class LLMProviderError(RuntimeError):
    def __init__(self, message: str, error_type: str = "LLM_PROVIDER_ERROR") -> None:
        self.error_type = error_type
        super().__init__(message)


class LLMClient(Protocol):
    async def plan(self, question: str) -> AgentPlan | None:
        pass

    async def answer(self, question: str, evidence: str, draft_answer: str) -> GroundedAnswer | None:
        pass
