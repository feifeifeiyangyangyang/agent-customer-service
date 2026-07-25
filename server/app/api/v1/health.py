import logging
from typing import Any

from fastapi import APIRouter
from fastapi.responses import JSONResponse
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


@router.get("/readiness", response_model=None)
async def readiness() -> Any:
    checks = {
        "mysql": await _check_mysql(),
        "redis": await _check_redis(),
        "qdrant": await _check_qdrant(),
    }
    if all(checks.values()):
        return ApiResponse.ok({"status": "UP", "checks": checks})
    return JSONResponse(
        status_code=503,
        content=ApiResponse.error("服务依赖未就绪").model_dump(mode="json")
        | {"data": {"status": "DEGRADED", "checks": checks}},
    )


@router.get("/health", response_model=None)
async def health() -> Any:
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
