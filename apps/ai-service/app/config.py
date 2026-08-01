from typing import Literal

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    app_environment: Literal["local", "dev", "uat", "prod"] = "local"
    log_level: Literal["DEBUG", "INFO", "WARNING", "ERROR"] = "INFO"
    litellm_base_url: str = "http://localhost:4000"
    # Scoped LiteLLM virtual key for this workload. Never the gateway master key:
    # the AI service must not be able to administer the gateway or bypass budgets.
    litellm_api_key: str = "local-development-key"
    interview_question_model: str = "interview-question-model"
    knowledge_embedding_model: str = "knowledge-embedding-model"
    ai_service_token: str = "local-ai-service-token"
    prompt_version: str = "direct-question-v1"
    input_cost_per_million_tokens_usd: float = 0.15
    output_cost_per_million_tokens_usd: float = 0.60

    @model_validator(mode="after")
    def reject_local_credentials_outside_local(self) -> "Settings":
        if self.app_environment in {"uat", "prod"}:
            if self.litellm_api_key == "local-development-key":
                raise ValueError("LITELLM_API_KEY must be supplied outside local development")
            if self.ai_service_token == "local-ai-service-token":
                raise ValueError("AI_SERVICE_TOKEN must be supplied outside local development")
        return self


settings = Settings()
