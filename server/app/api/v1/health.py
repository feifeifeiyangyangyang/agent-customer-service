import logging

from fastapi import APIRouter
from sqlalchemy import text

from app.db.session import session_factory
from app.repositories.qdrant_store import qdrant_store
from app.schemas.common import ApiResponse
from app.services.redis_runtime_service import redis_runtime_service

router = APIRouter(tags=["health"])
logger = logging.getLogger(__name__)


@router.get("/liveness")
async def liveness() -> ApiResponse[dict[str, str]]:
    return ApiResponse.ok({"status": "UP"})


@router.get("/readiness")
async def readiness() -> ApiResponse[dict[str, object]]:
    checks = {
        "mysql": await _check_mysql(),
        "redis": await _check_redis(),
        "qdrant": await _check_qdrant(),
    }
    status = "UP" if all(checks.values()) else "DEGRADED"
    return ApiResponse.ok({"status": status, "checks": checks})


@router.get("/health")
async def health() -> ApiResponse[dict[str, object]]:
    return await readiness()


async def _check_mysql() -> bool:
    try:
        async with session_factory()() as session:
            await session.execute(text("SELECT 1"))
        return True
    except Exception:
        logger.exception("readiness check failed: mysql")
        return False


async def _check_redis() -> bool:
    try:
        return await redis_runtime_service.ping()
    except Exception:
        logger.exception("readiness check failed: redis")
        return False


async def _check_qdrant() -> bool:
    try:
        return await qdrant_store.is_ready()
    except Exception:
        logger.exception("readiness check failed: qdrant")
        return False
