from pydantic import BaseModel


class ChatRequest(BaseModel):
    conversationId: int | None = None
    question: str


class SourceReference(BaseModel):
    documentId: int
    fileName: str
    snippet: str
    score: float


class ChatResponse(BaseModel):
    conversationId: int
    answer: str
    sources: list[SourceReference]
    retrievalScore: float
    confidenceLevel: str
    needHuman: bool
    ticketId: int | None = None
