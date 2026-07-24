import asyncio

from sqlalchemy import select

from app.core.security import AuthenticatedUser
from app.db.models import KbChunk, UserAccount
from app.db.session import dispose_engine, session_factory
from app.services.agent_service import AgentService
from app.services.conversation_service import ConversationService
from app.services.document_processing_service import document_processing_service
from app.services.knowledge_service import knowledge_service

POLICY = """退款处理规则
本文件仅用于项目演示。
退款一般按原支付路径退回。订单取消后，系统会进入退款处理中状态。
未发货订单通常会在审核通过后 1 到 3 个工作日内完成退款处理。
已发货或已签收订单，需要先进入售后审核，确认商品、配件和包装情况后再处理退款。
商品拆封但未影响二次销售时，可以提交退货申请；明显使用、配件缺失或包装严重损坏时需要人工确认。"""
QUESTION = "退款一般如何处理？"


async def main() -> None:
    factory = session_factory()
    async with factory() as session:
        admin = (await session.execute(select(UserAccount).where(UserAccount.username == "admin"))).scalar_one()
        customer = (await session.execute(select(UserAccount).where(UserAccount.username == "user"))).scalar_one()
        document = await document_processing_service.save_upload(
            session,
            "e2e-refund-policy.md",
            POLICY.encode(),
            admin.id,
        )
        await document_processing_service.retry(session, document.id)
        await document_processing_service.process_next_pending(session)
        await session.refresh(document)
        chunks = (await session.execute(select(KbChunk).where(KbChunk.document_id == document.id))).scalars().all()
        sources = await knowledge_service.search(session, QUESTION)

        conversation = await ConversationService().create(
            session,
            AuthenticatedUser(customer.id, customer.username, customer.display_name, customer.role),
            "E2E knowledge conversation",
        )
        response = await AgentService().chat(
            session,
            AuthenticatedUser(customer.id, customer.username, customer.display_name, customer.role),
            conversation.id,
            QUESTION,
        )
        print(f"document_status={document.status}")
        print(f"chunk_count={len(chunks)}")
        print(f"search_sources={len(sources)}")
        print(f"chat_need_human={response.needHuman}")
        print(f"chat_sources={len(response.sources)}")
        print(f"chat_answer={response.answer}")

        if document.status != "READY" or not chunks or not sources or response.needHuman or not response.sources:
            raise SystemExit(1)


async def run() -> None:
    try:
        await main()
    finally:
        await dispose_engine()


if __name__ == "__main__":
    asyncio.run(run())
