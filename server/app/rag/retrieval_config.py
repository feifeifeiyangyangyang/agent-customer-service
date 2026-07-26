from dataclasses import dataclass

from app.core.config import settings


@dataclass(frozen=True)
class RetrievalScoringConfig:
    rrf_k: int = 60
    support_min_threshold: float = 0.08
    support_threshold_relaxation: float = 0.27
    support_terms: tuple[str, ...] = (
        "退款",
        "退货",
        "运费",
        "邮费",
        "破损",
        "损坏",
        "坏了",
        "质量",
        "拆封",
        "售后",
        "凭证",
    )

    def threshold_for_query(self, query: str, configured_min_score: float | None = None) -> float:
        base_score = configured_min_score if configured_min_score is not None else settings.rag_min_retrieval_score
        if any(term in query for term in self.support_terms):
            return max(self.support_min_threshold, base_score - self.support_threshold_relaxation)
        return base_score


retrieval_scoring_config = RetrievalScoringConfig()
