from pydantic import Field

from app.domain.question_models import ApiModel, GenerationUsage


class SuggestTopicsRequest(ApiModel):
    technologies: list[str] = Field(min_length=1, max_length=20)
    difficulty: str = "MEDIUM"
    max_topics: int = Field(default=24, ge=1, le=60)


class SuggestTopicsResponse(ApiModel):
    topics: list[str]
    usage: GenerationUsage | None = None


class TopicDetailsRequest(ApiModel):
    ecosystem: str = Field(min_length=1, max_length=100)
    technology: str = Field(min_length=1, max_length=100)
    topic: str = Field(min_length=1, max_length=200)


class TopicDetailsResponse(ApiModel):
    title: str
    content: str
    usage: GenerationUsage | None = None
