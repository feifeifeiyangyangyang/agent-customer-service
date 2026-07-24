from datetime import datetime
from uuid import uuid4

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import ForbiddenError, NotFoundError
from app.core.security import AuthenticatedUser
from app.db.models import ChatConversation, ChatMessage
from app.schemas.conversation import ConversationResponse, MessageResponse


def conversation_response(row: ChatConversation) -> ConversationResponse:
    return ConversationResponse(
        id=row.id,
        conversationNo=row.conversation_no,
        title=row.title,
        status=row.status,
        createdAt=row.created_at,
        updatedAt=row.updated_at,
    )


def message_response(row: ChatMessage) -> MessageResponse:
    return MessageResponse(
        id=row.id,
        role=row.role,
        content=row.content,
        sourcesJson=row.sources_json,
        retrievalScore=float(row.retrieval_score) if row.retrieval_score is not None else None,
        confidenceLevel=row.confidence_level,
        needHuman=row.need_human,
        createdAt=row.created_at,
    )


class ConversationService:
    async def create(self, session: AsyncSession, user: AuthenticatedUser, title: str | None) -> ConversationResponse:
        now = datetime.now()
        row = ChatConversation(
            user_id=user.user_id,
            conversation_no="CV" + uuid4().hex[:16].upper(),
            title=title or "用户客服会话",
            status="ACTIVE",
            created_at=now,
            updated_at=now,
        )
        session.add(row)
        await session.commit()
        await session.refresh(row)
        return conversation_response(row)

    async def require_owned(
        self, session: AsyncSession, user: AuthenticatedUser, conversation_id: int
    ) -> ChatConversation:
        row = await session.get(ChatConversation, conversation_id)
        if row is None:
            raise NotFoundError("会话不存在")
        if row.user_id != user.user_id and user.role != "ADMIN":
            raise ForbiddenError("不能访问其他用户的会话")
        return row

    async def messages(
        self, session: AsyncSession, user: AuthenticatedUser, conversation_id: int
    ) -> list[MessageResponse]:
        await self.require_owned(session, user, conversation_id)
        rows = (
            (
                await session.execute(
                    select(ChatMessage)
                    .where(ChatMessage.conversation_id == conversation_id)
                    .order_by(ChatMessage.created_at.asc())
                )
            )
            .scalars()
            .all()
        )
        return [message_response(row) for row in rows]

    async def clear_messages(self, session: AsyncSession, user: AuthenticatedUser, conversation_id: int) -> None:
        await self.require_owned(session, user, conversation_id)
        await session.execute(delete(ChatMessage).where(ChatMessage.conversation_id == conversation_id))
        await session.commit()
