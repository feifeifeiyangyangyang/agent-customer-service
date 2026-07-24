from datetime import datetime
from decimal import Decimal

from fastapi import APIRouter, Depends
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import AuthenticatedUser, require_admin
from app.db.models import (
    AgentActionRequest,
    AgentRun,
    ChatMessage,
    CustomerOrder,
    KbDocument,
    ModelRuntimeConfig,
    ProductCatalog,
    SupportTicket,
)
from app.db.session import get_session
from app.schemas.common import ApiResponse

router = APIRouter(tags=["admin-misc"])


@router.get("/admin/dashboard")
async def dashboard(
    _admin: AuthenticatedUser = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
) -> ApiResponse[dict[str, int]]:
    today = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
    today_consultations = int(
        (
            await session.execute(
                select(func.count())
                .select_from(ChatMessage)
                .where(ChatMessage.role == "USER", ChatMessage.created_at >= today)
            )
        ).scalar_one()
    )
    product_count = int((await session.execute(select(func.count()).select_from(ProductCatalog))).scalar_one())
    waiting_shipment = int(
        (
            await session.execute(
                select(func.count()).select_from(CustomerOrder).where(CustomerOrder.status == "WAITING_SHIPMENT")
            )
        ).scalar_one()
    )
    shipping_orders = int(
        (
            await session.execute(
                select(func.count())
                .select_from(CustomerOrder)
                .where(CustomerOrder.status.in_(["SHIPPED", "IN_TRANSIT"]))
            )
        ).scalar_one()
    )
    signed_orders = int(
        (
            await session.execute(
                select(func.count()).select_from(CustomerOrder).where(CustomerOrder.status == "SIGNED")
            )
        ).scalar_one()
    )
    pending_tickets = int(
        (
            await session.execute(
                select(func.count()).select_from(SupportTicket).where(SupportTicket.status.in_(["OPEN", "PROCESSING"]))
            )
        ).scalar_one()
    )
    ready_documents = int(
        (
            await session.execute(
                select(func.count()).select_from(KbDocument).where(KbDocument.status.in_(["READY", "COMPLETED"]))
            )
        ).scalar_one()
    )
    failed_documents = int(
        (
            await session.execute(select(func.count()).select_from(KbDocument).where(KbDocument.status == "FAILED"))
        ).scalar_one()
    )
    pending_actions = int(
        (
            await session.execute(
                select(func.count()).select_from(AgentActionRequest).where(AgentActionRequest.status == "PENDING")
            )
        ).scalar_one()
    )
    agent_runs = int((await session.execute(select(func.count()).select_from(AgentRun))).scalar_one())
    return ApiResponse.ok(
        {
            "todayConsultations": today_consultations,
            "productCount": product_count,
            "waitingShipment": waiting_shipment,
            "waitingShipmentOrders": waiting_shipment,
            "shippingOrders": shipping_orders,
            "signedOrders": signed_orders,
            "pendingTickets": pending_tickets,
            "readyDocuments": ready_documents,
            "failedDocuments": failed_documents,
            "pendingAgentActions": pending_actions,
            "agentRuns": agent_runs,
        }
    )


@router.get("/admin/model-config")
async def get_model_config(
    _admin: AuthenticatedUser = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
) -> ApiResponse[dict[str, object]]:
    row = await session.get(ModelRuntimeConfig, 1)
    if row is None:
        row = ModelRuntimeConfig(
            id=1,
            temperature=Decimal("0.20"),
            top_k=5,
            min_retrieval_score=Decimal("0.350"),
            mock_enabled=True,
            updated_at=datetime.now(),
        )
        session.add(row)
        await session.commit()
        await session.refresh(row)
    return ApiResponse.ok(
        {
            "temperature": row.temperature,
            "topK": row.top_k,
            "minRetrievalScore": row.min_retrieval_score,
            "mockEnabled": row.mock_enabled,
            "updatedAt": row.updated_at,
        }
    )


@router.put("/admin/model-config")
async def update_model_config(
    request: dict[str, object],
    _admin: AuthenticatedUser = Depends(require_admin),
    session: AsyncSession = Depends(get_session),
) -> ApiResponse[dict[str, object]]:
    row = await session.get(ModelRuntimeConfig, 1)
    if row is None:
        row = ModelRuntimeConfig(
            id=1,
            temperature=Decimal("0.20"),
            top_k=5,
            min_retrieval_score=Decimal("0.350"),
            mock_enabled=True,
            updated_at=datetime.now(),
        )
        session.add(row)
    temperature_value = request.get("temperature", row.temperature)
    top_k_value = request.get("topK", row.top_k)
    min_score_value = request.get("minRetrievalScore", row.min_retrieval_score)
    row.temperature = Decimal(str(temperature_value))
    row.top_k = int(top_k_value) if isinstance(top_k_value, int | str) else row.top_k
    row.min_retrieval_score = Decimal(str(min_score_value))
    row.mock_enabled = bool(request.get("mockEnabled", row.mock_enabled))
    row.updated_at = datetime.now()
    await session.commit()
    return await get_model_config(_admin, session)
