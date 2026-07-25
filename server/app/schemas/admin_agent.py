from datetime import datetime

from pydantic import BaseModel, Field


class AgentActionResponse(BaseModel):
    id: int
    runId: str
    actionType: str
    targetOrderId: int | None
    actionPayloadJson: str
    riskLevel: str
    status: str
    idempotencyKey: str
    lockVersion: int
    createdBy: int
    approvedBy: int | None = None
    approvalNote: str | None = None
    createdAt: datetime
    approvedAt: datetime | None = None
    executedAt: datetime | None = None


class AgentToolCallResponse(BaseModel):
    id: int
    runId: str
    toolName: str
    redactedArgumentsJson: str
    resultSummary: str | None
    success: bool
    retryCount: int
    durationMs: int
    createdAt: datetime


class AgentStepResponse(BaseModel):
    id: int
    runId: str
    nodeName: str
    inputSummary: str | None
    outputSummary: str | None
    status: str
    durationMs: int
    errorSummary: str | None
    createdAt: datetime


class AgentRunResponse(BaseModel):
    id: int
    runId: str
    threadId: str
    conversationId: int
    userId: int
    status: str
    intent: str | None
    riskLevel: str | None
    startedAt: datetime
    completedAt: datetime | None
    finalAnswer: str | None
    errorType: str | None
    requestId: str
    modelName: str | None = None
    configVersion: str | None = None
    promptVersion: str | None = None
    providerLatencyMs: int | None = None
    promptTokens: int | None = None
    completionTokens: int | None = None
    toolCallCount: int = 0
    pendingActionCount: int = 0


class AgentRunDetailResponse(BaseModel):
    run: AgentRunResponse
    steps: list[AgentStepResponse]
    toolCalls: list[AgentToolCallResponse]
    actionRequests: list[AgentActionResponse]


class ApproveActionRequest(BaseModel):
    lockVersion: int
    approvalNote: str | None = None


class RejectActionRequest(BaseModel):
    lockVersion: int
    approvalNote: str


class ModelConfigRequest(BaseModel):
    temperature: float = Field(ge=0, le=2)
    topK: int = Field(ge=1, le=20)
    minRetrievalScore: float = Field(ge=0, le=1)
    mockEnabled: bool
