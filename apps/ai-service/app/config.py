from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", case_sensitive=False)

    litellm_base_url: str = "http://localhost:4000"
    litellm_master_key: str = "local-development-key"
    interview_question_model: str = "interview-question-model"
    ai_service_token: str = "local-ai-service-token"
    prompt_version: str = "direct-question-v1"


settings = Settings()
