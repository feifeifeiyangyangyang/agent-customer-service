from typing import Any, Literal

from pydantic import BaseModel, Field

SourceType = Literal["keyword", "dense_vector", "structured_rule"]


class RetrievalQueryContext(BaseModel):
    product_category: str | None = None
    order_status: str | None = None
    payment_status: str | None = None
    shipment_status: str | None = None
    signed_days: int | None = None
    after_sale_type: str | None = None
    has_specific_order: bool = False


class RetrievalCandidate(BaseModel):
    candidate_id: str
    source_type: SourceType
    content: str
    document_id: str | None
    chunk_id: str | None
    rule_id: str | None
    metadata: dict[str, Any] = Field(default_factory=dict)
    original_score: float
    fused_score: float | None = None
    rerank_score: float | None = None
    decision_reason: str | None = None


class RetrievalChannelDiagnostic(BaseModel):
    channel: SourceType | Literal["cache"]
    status: Literal["OK", "FAILED", "DEGRADED"]
    error_type: str | None = None
    message: str | None = None
