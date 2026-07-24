from dataclasses import dataclass
from typing import Any

from qdrant_client import AsyncQdrantClient
from qdrant_client.http.models import (
    Distance,
    FieldCondition,
    Filter,
    FilterSelector,
    MatchValue,
    PointStruct,
    VectorParams,
)

from app.core.config import settings

COLLECTION_NAME = "zhifutong_kb_chunks"


@dataclass(frozen=True)
class VectorChunkPayload:
    document_id: int
    chunk_id: int
    file_name: str
    content: str


@dataclass(frozen=True)
class VectorSearchHit:
    document_id: int
    chunk_id: int
    file_name: str
    content: str
    score: float


class QdrantKnowledgeStore:
    def __init__(self) -> None:
        self._client: AsyncQdrantClient | None = None

    async def upsert_chunks(self, chunks: list[tuple[str, list[float], VectorChunkPayload]]) -> None:
        if not chunks:
            return
        await self.ensure_collection()
        points = [
            PointStruct(
                id=point_id,
                vector=vector,
                payload={
                    "document_id": payload.document_id,
                    "chunk_id": payload.chunk_id,
                    "file_name": payload.file_name,
                    "content": payload.content,
                },
            )
            for point_id, vector, payload in chunks
        ]
        await self._client_or_create().upsert(collection_name=COLLECTION_NAME, points=points, wait=True)

    async def search(self, query_vector: list[float], limit: int) -> list[VectorSearchHit]:
        await self.ensure_collection()
        client = self._client_or_create()
        raw_result = await client.query_points(
            collection_name=COLLECTION_NAME,
            query=query_vector,
            limit=limit,
            with_payload=True,
        )
        points = getattr(raw_result, "points", raw_result)
        hits: list[VectorSearchHit] = []
        for point in points:
            payload = getattr(point, "payload", {}) or {}
            hits.append(
                VectorSearchHit(
                    document_id=int(payload["document_id"]),
                    chunk_id=int(payload["chunk_id"]),
                    file_name=str(payload["file_name"]),
                    content=str(payload["content"]),
                    score=float(getattr(point, "score", 0.0)),
                )
            )
        return hits

    async def delete_document(self, document_id: int) -> None:
        await self.ensure_collection()
        selector = FilterSelector(
            filter=Filter(must=[FieldCondition(key="document_id", match=MatchValue(value=document_id))])
        )
        await self._client_or_create().delete(collection_name=COLLECTION_NAME, points_selector=selector, wait=True)

    async def ensure_collection(self) -> None:
        client = self._client_or_create()
        try:
            await client.get_collection(COLLECTION_NAME)
            return
        except Exception:
            await client.create_collection(
                collection_name=COLLECTION_NAME,
                vectors_config=VectorParams(size=settings.embedding_dimension, distance=Distance.COSINE),
            )

    def _client_or_create(self) -> AsyncQdrantClient:
        if self._client is None:
            kwargs: dict[str, Any] = {"host": settings.qdrant_host, "port": settings.qdrant_port}
            self._client = AsyncQdrantClient(**kwargs)
        return self._client


qdrant_store = QdrantKnowledgeStore()
