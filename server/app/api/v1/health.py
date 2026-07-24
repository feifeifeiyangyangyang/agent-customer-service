from fastapi import APIRouter

from app.schemas.common import ApiResponse

router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> ApiResponse[dict[str, str]]:
    return ApiResponse.ok({"status": "UP"})
