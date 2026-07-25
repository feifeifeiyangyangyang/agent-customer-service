from app.llm.base import LLMClient
from app.llm.mock_client import MockLLMClient
from app.llm.openai_compatible_client import OpenAICompatibleLLMClient
from app.services.model_runtime_config_service import EffectiveModelRuntimeConfig


def create_llm_client(runtime: EffectiveModelRuntimeConfig) -> LLMClient:
    if runtime.mock_enabled:
        return MockLLMClient()
    return OpenAICompatibleLLMClient(runtime.temperature)
