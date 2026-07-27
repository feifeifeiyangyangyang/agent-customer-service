from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base


class UserAccount(Base):
    __tablename__ = "user_account"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    display_name: Mapped[str] = mapped_column(String(128), nullable=False)
    role: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class ProductCatalog(Base):
    __tablename__ = "product_catalog"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    product_code: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    product_name: Mapped[str] = mapped_column(String(128), nullable=False)
    category: Mapped[str] = mapped_column(String(64), nullable=False)
    sale_status: Mapped[str] = mapped_column(String(32), nullable=False)
    price: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    stock_quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    dispatch_rule: Mapped[str] = mapped_column(String(255), nullable=False)
    after_sale_rule: Mapped[str] = mapped_column(String(512), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class CustomerOrder(Base):
    __tablename__ = "customer_order"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    order_no: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    user_id: Mapped[int] = mapped_column(ForeignKey("user_account.id"), nullable=False)
    product_id: Mapped[int] = mapped_column(ForeignKey("product_catalog.id"), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    amount: Mapped[Decimal] = mapped_column(Numeric(10, 2), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    paid_at: Mapped[datetime | None] = mapped_column(DateTime)
    expected_ship_at: Mapped[datetime | None] = mapped_column(DateTime)
    shipped_at: Mapped[datetime | None] = mapped_column(DateTime)
    signed_at: Mapped[datetime | None] = mapped_column(DateTime)
    receiver_name: Mapped[str] = mapped_column(String(64), nullable=False)
    receiver_phone: Mapped[str] = mapped_column(String(32), nullable=False)
    receiver_address: Mapped[str] = mapped_column(String(255), nullable=False)
    remark: Mapped[str | None] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)

    product: Mapped[ProductCatalog] = relationship(lazy="joined")


class ShipmentEvent(Base):
    __tablename__ = "shipment_event"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    order_id: Mapped[int] = mapped_column(ForeignKey("customer_order.id"), nullable=False)
    carrier: Mapped[str | None] = mapped_column(String(64))
    tracking_no: Mapped[str | None] = mapped_column(String(64))
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    location: Mapped[str | None] = mapped_column(String(128))
    event_note: Mapped[str] = mapped_column(String(255), nullable=False)
    event_time: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class ChatConversation(Base):
    __tablename__ = "chat_conversation"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("user_account.id"))
    conversation_no: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class ChatMessage(Base):
    __tablename__ = "chat_message"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    conversation_id: Mapped[int] = mapped_column(ForeignKey("chat_conversation.id"), nullable=False)
    role: Mapped[str] = mapped_column(String(32), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    sources_json: Mapped[str | None] = mapped_column(Text)
    retrieval_score: Mapped[Decimal | None] = mapped_column(Numeric(8, 4))
    confidence_level: Mapped[str | None] = mapped_column(String(32))
    need_human: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class KbDocument(Base):
    __tablename__ = "kb_document"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    original_name: Mapped[str] = mapped_column(String(512), nullable=False)
    storage_name: Mapped[str] = mapped_column(String(128), nullable=False)
    storage_path: Mapped[str] = mapped_column(String(1024), nullable=False)
    file_type: Mapped[str] = mapped_column(String(32), nullable=False)
    file_size: Mapped[int] = mapped_column(BigInteger, nullable=False)
    file_sha256: Mapped[str | None] = mapped_column(String(64), unique=True)
    uploaded_by: Mapped[int | None] = mapped_column(ForeignKey("user_account.id"))
    lock_version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    chunk_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    failure_reason: Mapped[str | None] = mapped_column(String(512))
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class DocumentProcessingTask(Base):
    __tablename__ = "document_processing_task"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(ForeignKey("kb_document.id"), unique=True, nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    max_retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=3)
    next_retry_at: Mapped[datetime | None] = mapped_column(DateTime)
    started_at: Mapped[datetime | None] = mapped_column(DateTime)
    finished_at: Mapped[datetime | None] = mapped_column(DateTime)
    error_message: Mapped[str | None] = mapped_column(String(1000))
    lock_version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class KbChunk(Base):
    __tablename__ = "kb_chunk"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    document_id: Mapped[int] = mapped_column(ForeignKey("kb_document.id"), nullable=False)
    chunk_index: Mapped[int] = mapped_column(Integer, nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    content_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    char_count: Mapped[int] = mapped_column(Integer, nullable=False)
    vector_point_id: Mapped[str] = mapped_column(String(64), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)
    __table_args__ = (UniqueConstraint("document_id", "chunk_index", name="uk_kb_chunk_document_index"),)


class ChatMessageSource(Base):
    __tablename__ = "chat_message_source"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    message_id: Mapped[int] = mapped_column(ForeignKey("chat_message.id"), nullable=False)
    document_id: Mapped[int] = mapped_column(ForeignKey("kb_document.id"), nullable=False)
    chunk_id: Mapped[int | None] = mapped_column(ForeignKey("kb_chunk.id"))
    rank_no: Mapped[int] = mapped_column(Integer, nullable=False)
    retrieval_score: Mapped[Decimal] = mapped_column(Numeric(8, 4), nullable=False)
    snippet_snapshot: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class SupportTicket(Base):
    __tablename__ = "support_ticket"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("user_account.id"))
    ticket_no: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    conversation_id: Mapped[int] = mapped_column(ForeignKey("chat_conversation.id"), nullable=False)
    category: Mapped[str] = mapped_column(String(32), nullable=False)
    description: Mapped[str] = mapped_column(Text, nullable=False)
    contact: Mapped[str | None] = mapped_column(String(255))
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    handler_id: Mapped[int | None] = mapped_column(ForeignKey("user_account.id"))
    priority: Mapped[str] = mapped_column(String(32), nullable=False, default="NORMAL")
    handling_note: Mapped[str | None] = mapped_column(Text)
    resolution: Mapped[str | None] = mapped_column(Text)
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime)
    lock_version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)


class TicketOperationLog(Base):
    __tablename__ = "ticket_operation_log"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    ticket_id: Mapped[int] = mapped_column(ForeignKey("support_ticket.id"), nullable=False)
    operator_id: Mapped[int] = mapped_column(ForeignKey("user_account.id"), nullable=False)
    previous_status: Mapped[str | None] = mapped_column(String(32))
    next_status: Mapped[str] = mapped_column(String(32), nullable=False)
    operation_note: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class ModelRuntimeConfig(Base):
    __tablename__ = "model_runtime_config"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    temperature: Mapped[Decimal] = mapped_column(Numeric(3, 2), nullable=False)
    top_k: Mapped[int] = mapped_column(Integer, nullable=False)
    min_retrieval_score: Mapped[Decimal] = mapped_column(Numeric(4, 3), nullable=False, default=Decimal("0.350"))
    mock_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class AfterSaleRuleVersion(Base):
    __tablename__ = "after_sale_rule_version"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    version_code: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    effective_from: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    effective_to: Mapped[datetime | None] = mapped_column(DateTime)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class AfterSaleRule(Base):
    __tablename__ = "after_sale_rule"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    rule_code: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    version_id: Mapped[int] = mapped_column(ForeignKey("after_sale_rule_version.id"), nullable=False)
    title: Mapped[str] = mapped_column(String(128), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    after_sale_type: Mapped[str] = mapped_column(String(32), nullable=False)
    priority: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    effective_from: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    effective_to: Mapped[datetime | None] = mapped_column(DateTime)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now, onupdate=datetime.now)

    version: Mapped[AfterSaleRuleVersion] = relationship(lazy="joined")


class AfterSaleRuleCondition(Base):
    __tablename__ = "after_sale_rule_condition"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    rule_id: Mapped[int] = mapped_column(ForeignKey("after_sale_rule.id"), nullable=False)
    product_category: Mapped[str | None] = mapped_column(String(64))
    order_status: Mapped[str | None] = mapped_column(String(32))
    payment_status: Mapped[str | None] = mapped_column(String(32))
    shipment_status: Mapped[str | None] = mapped_column(String(32))
    signed_within_days: Mapped[int | None] = mapped_column(Integer)
    after_sale_type: Mapped[str | None] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)

    rule: Mapped[AfterSaleRule] = relationship(lazy="joined")


class AgentRun(Base):
    __tablename__ = "agent_run"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), unique=True, nullable=False)
    thread_id: Mapped[str] = mapped_column(String(128), nullable=False)
    conversation_id: Mapped[int] = mapped_column(ForeignKey("chat_conversation.id"), nullable=False)
    user_id: Mapped[int] = mapped_column(ForeignKey("user_account.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    intent: Mapped[str | None] = mapped_column(String(64))
    risk_level: Mapped[str | None] = mapped_column(String(32))
    started_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime)
    final_answer: Mapped[str | None] = mapped_column(Text)
    error_type: Mapped[str | None] = mapped_column(String(64))
    request_id: Mapped[str] = mapped_column(String(64), nullable=False)
    model_name: Mapped[str | None] = mapped_column(String(128))
    config_version: Mapped[str | None] = mapped_column(String(64))
    prompt_version: Mapped[str | None] = mapped_column(String(64))
    provider_latency_ms: Mapped[int | None] = mapped_column(Integer)
    prompt_tokens: Mapped[int | None] = mapped_column(Integer)
    completion_tokens: Mapped[int | None] = mapped_column(Integer)


class AgentStep(Base):
    __tablename__ = "agent_step"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), nullable=False)
    node_name: Mapped[str] = mapped_column(String(64), nullable=False)
    input_summary: Mapped[str | None] = mapped_column(Text)
    output_summary: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    duration_ms: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    error_summary: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class AgentToolCall(Base):
    __tablename__ = "agent_tool_call"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), nullable=False)
    tool_name: Mapped[str] = mapped_column(String(128), nullable=False)
    redacted_arguments_json: Mapped[str] = mapped_column(Text, nullable=False)
    result_summary: Mapped[str | None] = mapped_column(Text)
    success: Mapped[bool] = mapped_column(Boolean, nullable=False)
    retry_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    duration_ms: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class AgentActionRequest(Base):
    __tablename__ = "agent_action_request"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), nullable=False)
    action_type: Mapped[str] = mapped_column(String(64), nullable=False)
    target_order_id: Mapped[int | None] = mapped_column(ForeignKey("customer_order.id"))
    action_payload_json: Mapped[str] = mapped_column(Text, nullable=False)
    risk_level: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(32), nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(128), unique=True, nullable=False)
    lock_version: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    created_by: Mapped[int] = mapped_column(ForeignKey("user_account.id"), nullable=False)
    approved_by: Mapped[int | None] = mapped_column(ForeignKey("user_account.id"))
    approval_note: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)
    approved_at: Mapped[datetime | None] = mapped_column(DateTime)
    executed_at: Mapped[datetime | None] = mapped_column(DateTime)


class AgentFeedback(Base):
    __tablename__ = "agent_feedback"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), nullable=False)
    rating: Mapped[str] = mapped_column(String(32), nullable=False)
    reason: Mapped[str | None] = mapped_column(String(512))
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


class AgentRetrievalTrace(Base):
    __tablename__ = "agent_retrieval_trace"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    run_id: Mapped[str] = mapped_column(String(64), nullable=False)
    candidate_id: Mapped[str] = mapped_column(String(128), nullable=False)
    source_type: Mapped[str] = mapped_column(String(32), nullable=False)
    document_id: Mapped[str | None] = mapped_column(String(64))
    chunk_id: Mapped[str | None] = mapped_column(String(64))
    rule_id: Mapped[str | None] = mapped_column(String(64))
    original_score: Mapped[Decimal] = mapped_column(Numeric(10, 6), nullable=False)
    fused_score: Mapped[Decimal | None] = mapped_column(Numeric(10, 6))
    rerank_score: Mapped[Decimal | None] = mapped_column(Numeric(10, 6))
    selected: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    decision_reason: Mapped[str | None] = mapped_column(String(255))
    metadata_json: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, nullable=False, default=datetime.now)


Index("idx_customer_order_user_created", CustomerOrder.user_id, CustomerOrder.created_at)
Index("idx_shipment_event_order_time", ShipmentEvent.order_id, ShipmentEvent.event_time)
Index("idx_after_sale_rule_effective", AfterSaleRule.status, AfterSaleRule.effective_from, AfterSaleRule.effective_to)
Index(
    "idx_after_sale_rule_condition_lookup",
    AfterSaleRuleCondition.after_sale_type,
    AfterSaleRuleCondition.order_status,
)
Index("idx_agent_run_thread", AgentRun.thread_id)
Index("idx_agent_step_run_created", AgentStep.run_id, AgentStep.created_at)
Index("idx_agent_tool_call_run_created", AgentToolCall.run_id, AgentToolCall.created_at)
Index("idx_agent_action_status_created", AgentActionRequest.status, AgentActionRequest.created_at)
Index("idx_agent_retrieval_trace_run", AgentRetrievalTrace.run_id, AgentRetrievalTrace.created_at)
