import uuid
from datetime import datetime
from hashlib import sha256
from pathlib import Path

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.exceptions import AppError, NotFoundError
from app.db.models import DocumentProcessingTask, KbChunk, KbDocument
from app.embeddings.mock_embedding import MockEmbeddingClient
from app.rag.chunker import chunk_text
from app.rag.document_parser import extract_text
from app.repositories.qdrant_store import VectorChunkPayload, qdrant_store

MAX_UPLOAD_BYTES = 10 * 1024 * 1024


class DocumentProcessingService:
    async def save_upload(
        self,
        session: AsyncSession,
        original_name: str,
        content: bytes,
        uploaded_by: int,
    ) -> KbDocument:
        if not content:
            raise AppError("上传文件不能为空", 400)
        if len(content) > MAX_UPLOAD_BYTES:
            raise AppError("单个知识库文件不能超过 10MB", 400)
        file_type = _file_type(original_name)
        digest = sha256(content).hexdigest()
        existing = (
            await session.execute(select(KbDocument).where(KbDocument.file_sha256 == digest))
        ).scalar_one_or_none()
        if existing is not None:
            return existing

        storage_name = f"{digest}.{file_type}"
        storage_path = _storage_root() / storage_name
        storage_path.write_bytes(content)
        now = datetime.now()
        row = KbDocument(
            original_name=original_name,
            storage_name=storage_name,
            storage_path=str(storage_path),
            file_type=file_type,
            file_size=len(content),
            file_sha256=digest,
            uploaded_by=uploaded_by,
            status="PENDING",
            chunk_count=0,
            created_at=now,
            updated_at=now,
        )
        session.add(row)
        await session.flush()
        session.add(DocumentProcessingTask(document_id=row.id, status="PENDING", created_at=now, updated_at=now))
        await session.commit()
        await session.refresh(row)
        return row

    async def retry(self, session: AsyncSession, document_id: int) -> None:
        document = await session.get(KbDocument, document_id)
        if document is None:
            raise NotFoundError("文档不存在")
        task = (
            await session.execute(
                select(DocumentProcessingTask).where(DocumentProcessingTask.document_id == document_id)
            )
        ).scalar_one_or_none()
        now = datetime.now()
        document.status = "PENDING"
        document.failure_reason = None
        document.updated_at = now
        if task is None:
            session.add(
                DocumentProcessingTask(document_id=document.id, status="PENDING", created_at=now, updated_at=now)
            )
        else:
            task.status = "PENDING"
            task.error_message = None
            task.next_retry_at = None
            task.updated_at = now
        await session.commit()

    async def delete(self, session: AsyncSession, document_id: int) -> None:
        document = await session.get(KbDocument, document_id)
        if document is None:
            return
        await session.execute(delete(KbChunk).where(KbChunk.document_id == document_id))
        await session.execute(delete(DocumentProcessingTask).where(DocumentProcessingTask.document_id == document_id))
        try:
            await qdrant_store.delete_document(document_id)
        except Exception:
            pass
        path = Path(document.storage_path)
        if path.exists() and path.is_file():
            path.unlink()
        await session.delete(document)
        await session.commit()

    async def process_next_pending(self, session: AsyncSession) -> bool:
        task = (
            await session.execute(
                select(DocumentProcessingTask)
                .where(DocumentProcessingTask.status == "PENDING")
                .order_by(DocumentProcessingTask.created_at.asc())
                .limit(1)
            )
        ).scalar_one_or_none()
        if task is None:
            return False
        await self.process_task(session, task.id)
        return True

    async def process_task(self, session: AsyncSession, task_id: int) -> None:
        task = await session.get(DocumentProcessingTask, task_id)
        if task is None:
            raise NotFoundError("文档处理任务不存在")
        document = await session.get(KbDocument, task.document_id)
        if document is None:
            raise NotFoundError("文档不存在")

        now = datetime.now()
        task.status = "PROCESSING"
        task.started_at = now
        task.updated_at = now
        document.status = "PROCESSING"
        document.updated_at = now
        await session.commit()

        try:
            content = Path(document.storage_path).read_bytes()
            text = extract_text(document.original_name, content)
            chunks = chunk_text(text)
            if not chunks:
                raise AppError("文档未提取到有效文本", 400)

            await session.execute(delete(KbChunk).where(KbChunk.document_id == document.id))
            rows: list[tuple[KbChunk, str]] = []
            for chunk in chunks:
                point_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"kb:{document.id}:{chunk.index}:{chunk.content_hash}"))
                row = KbChunk(
                    document_id=document.id,
                    chunk_index=chunk.index,
                    content=chunk.content,
                    content_hash=chunk.content_hash,
                    char_count=chunk.char_count,
                    vector_point_id=point_id,
                    created_at=datetime.now(),
                    updated_at=datetime.now(),
                )
                session.add(row)
                rows.append((row, point_id))
            await session.flush()

            embedding = MockEmbeddingClient(settings.embedding_dimension)
            await qdrant_store.upsert_chunks(
                [
                    (
                        point_id,
                        embedding.embed(row.content),
                        VectorChunkPayload(
                            document_id=document.id,
                            chunk_id=row.id,
                            file_name=document.original_name,
                            content=row.content,
                        ),
                    )
                    for row, point_id in rows
                ]
            )

            finished_at = datetime.now()
            document.status = "READY"
            document.chunk_count = len(rows)
            document.failure_reason = None
            document.updated_at = finished_at
            task.status = "COMPLETED"
            task.finished_at = finished_at
            task.error_message = None
            task.updated_at = finished_at
            await session.commit()
        except Exception as exc:
            await session.rollback()
            await self._mark_failed(session, document.id, task.id, str(exc))

    async def _mark_failed(self, session: AsyncSession, document_id: int, task_id: int, reason: str) -> None:
        document = await session.get(KbDocument, document_id)
        task = await session.get(DocumentProcessingTask, task_id)
        now = datetime.now()
        if document is not None:
            document.status = "FAILED"
            document.failure_reason = reason[:512]
            document.updated_at = now
        if task is not None:
            task.status = "FAILED"
            task.error_message = reason[:1000]
            task.finished_at = now
            task.updated_at = now
        await session.commit()


def _storage_root() -> Path:
    root = Path(settings.document_storage_path)
    if not root.is_absolute():
        root = Path.cwd() / root
    root.mkdir(parents=True, exist_ok=True)
    return root


def _file_type(filename: str) -> str:
    return filename.rsplit(".", 1)[-1].lower() if "." in filename else "txt"


document_processing_service = DocumentProcessingService()
