import os
from functools import lru_cache

from pydantic import BaseModel, Field


def _env(name: str, default: str) -> str:
    return os.getenv(name, default)


def _env_int(name: str, default: int) -> int:
    return int(os.getenv(name, str(default)))


def _env_float(name: str, default: float) -> float:
    return float(os.getenv(name, str(default)))


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.lower() in {"1", "true", "yes", "on"}


class Settings(BaseModel):
    mysql_host: str = Field(default_factory=lambda: _env("MYSQL_HOST", "127.0.0.1"))
    mysql_port: int = Field(default_factory=lambda: _env_int("MYSQL_PORT", 3307))
    mysql_database: str = Field(default_factory=lambda: _env("MYSQL_DATABASE", "smart_customer_service"))
    mysql_username: str = Field(default_factory=lambda: _env("MYSQL_USERNAME", "root"))
    mysql_password: str = Field(default_factory=lambda: _env("MYSQL_PASSWORD", "change_me"))

    redis_host: str = Field(default_factory=lambda: _env("REDIS_HOST", "127.0.0.1"))
    redis_port: int = Field(default_factory=lambda: _env_int("REDIS_PORT", 6379))
    redis_password: str = Field(default_factory=lambda: _env("REDIS_PASSWORD", ""))

    server_port: int = Field(default_factory=lambda: _env_int("SERVER_PORT", 18080))
    jwt_secret: str = Field(
        default_factory=lambda: _env("JWT_SECRET", "dev-secret-change-me-at-least-32-chars"), min_length=32
    )
    access_token_ttl_minutes: int = Field(default_factory=lambda: _env_int("ACCESS_TOKEN_TTL_MINUTES", 30))
    refresh_token_ttl_days: int = Field(default_factory=lambda: _env_int("REFRESH_TOKEN_TTL_DAYS", 7))
    chat_rate_limit_per_minute: int = Field(default_factory=lambda: _env_int("CHAT_RATE_LIMIT_PER_MINUTE", 20))

    demo_admin_username: str = Field(default_factory=lambda: _env("DEMO_ADMIN_USERNAME", "admin"))
    demo_admin_password: str = Field(default_factory=lambda: _env("DEMO_ADMIN_PASSWORD", "admin123"))
    demo_customer_username: str = Field(default_factory=lambda: _env("DEMO_CUSTOMER_USERNAME", "user"))
    demo_customer_password: str = Field(default_factory=lambda: _env("DEMO_CUSTOMER_PASSWORD", "123456"))

    llm_mock_enabled: bool = Field(default_factory=lambda: _env_bool("LLM_MOCK_ENABLED", True))
    llm_api_key: str = Field(default_factory=lambda: _env("LLM_API_KEY", ""))
    llm_base_url: str = Field(default_factory=lambda: _env("LLM_BASE_URL", "https://api.deepseek.com"))
    llm_model_name: str = Field(default_factory=lambda: _env("LLM_MODEL_NAME", "deepseek-v4-flash"))
    llm_temperature: float = Field(default_factory=lambda: _env_float("LLM_TEMPERATURE", 0.2))

    qdrant_host: str = Field(default_factory=lambda: _env("QDRANT_HOST", "127.0.0.1"))
    qdrant_port: int = Field(default_factory=lambda: _env_int("QDRANT_PORT", 6333))
    rag_top_k: int = Field(default_factory=lambda: _env_int("RAG_TOP_K", 5))
    rag_min_retrieval_score: float = Field(default_factory=lambda: _env_float("RAG_MIN_RETRIEVAL_SCORE", 0.35))
    document_storage_path: str = Field(default_factory=lambda: _env("DOCUMENT_STORAGE_PATH", "./data/documents"))
    embedding_mock_enabled: bool = Field(default_factory=lambda: _env_bool("EMBEDDING_MOCK_ENABLED", True))
    embedding_dimension: int = Field(default_factory=lambda: _env_int("EMBEDDING_DIMENSION", 384))

    cors_origins: list[str] = ["http://127.0.0.1:5173", "http://localhost:5173"]

    @property
    def database_url(self) -> str:
        return (
            f"mysql+aiomysql://{self.mysql_username}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}?charset=utf8mb4"
        )

    @property
    def sync_database_url(self) -> str:
        return (
            f"mysql+pymysql://{self.mysql_username}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}?charset=utf8mb4"
        )


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
