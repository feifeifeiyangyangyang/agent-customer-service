import logging

import httpx

from app.core.config import settings
from app.embeddings.base import EmbeddingProviderError

logger = logging.getLogger(__name__)


class OpenAICompatibleEmbeddingClient:
    def __init__(self, dimension: int) -> None:
        self._dimension = dimension

    @property
    def dimension(self) -> int:
        return self._dimension

    async def embed(self, text: str) -> list[float]:
        if not settings.embedding_api_key:
            raise EmbeddingProviderError("EMBEDDING_API_KEY is missing", "MISSING_API_KEY")
        try:
            async with httpx.AsyncClient(timeout=12) as client:
                response = await client.post(
                    f"{settings.embedding_base_url.rstrip('/')}/v1/embeddings",
                    headers={"Authorization": f"Bearer {settings.embedding_api_key}"},
                    json={"model": settings.embedding_model_name, "input": text[:4000]},
                )
                response.raise_for_status()
            vector = response.json()["data"][0]["embedding"]
            if not isinstance(vector, list) or len(vector) != self._dimension:
                raise EmbeddingProviderError("Embedding dimension mismatch", "DIMENSION_MISMATCH")
            return [float(value) for value in vector]
        except EmbeddingProviderError:
            raise
        except httpx.TimeoutException as exc:
            logger.warning("Embedding provider timeout; dense vector channel will degrade")
            raise EmbeddingProviderError("Embedding request timeout", "TIMEOUT") from exc
        except httpx.HTTPStatusError as exc:
            logger.warning(
                "Embedding provider returned HTTP %s; dense vector channel will degrade",
                exc.response.status_code,
            )
            raise EmbeddingProviderError("Embedding provider HTTP error", "HTTP_ERROR") from exc
        except Exception as exc:
            logger.warning("Embedding provider failed with %s; dense vector channel will degrade", type(exc).__name__)
            raise EmbeddingProviderError("Embedding provider failed", "PROVIDER_CALL_FAILED") from exc
