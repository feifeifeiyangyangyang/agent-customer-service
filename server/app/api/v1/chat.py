from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import AuthenticatedUser, current_user
from app.db.session import get_session
from app.schemas.chat import ChatRequest, ChatResponse
from app.schemas.common import ApiResponse
from app.services.agent_service import AgentService
from app.services.conversation_service import ConversationService

router = APIRouter(tags=["chat"])
agent_service = AgentService()
conversation_service = ConversationService()


@router.post("/chat")
async def chat(
    request: ChatRequest,
    user: AuthenticatedUser = Depends(current_user),
    session: AsyncSession = Depends(get_session),
) -> ApiResponse[ChatResponse]:
    conversation_id = request.conversationId
    if conversation_id is None:
        conversation = await conversation_service.create(session, user, "用户客服会话")
        conversation_id = conversation.id
    else:
        await conversation_service.require_owned(session, user, conversation_id)
    return ApiResponse.ok(await agent_service.chat(session, user, conversation_id, request.question))


@router.post("/agent/chat")
async def agent_chat(
    request: ChatRequest,
    user: AuthenticatedUser = Depends(current_user),
    session: AsyncSession = Depends(get_session),
) -> ApiResponse[ChatResponse]:
    return await chat(request, user, session)
