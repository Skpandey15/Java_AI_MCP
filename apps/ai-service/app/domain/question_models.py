from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_alias=True,
        validate_by_name=True,
        serialize_by_alias=True,
    )


class GenerateQuestionsRequest(ApiModel):
    request_id: UUID
    interview_id: UUID
    skills: list[str] = Field(min_length=1, max_length=20)
    difficulty: str
    question_count: int = Field(ge=1, le=20)


class GeneratedQuestion(ApiModel):
    order: int = Field(ge=1)
    prompt: str = Field(min_length=10, max_length=4000)
    max_score: int = Field(ge=1, le=100)


class GenerateQuestionsResponse(ApiModel):
    request_id: UUID
    interview_id: UUID
    model_policy: str
    prompt_version: str
    questions: list[GeneratedQuestion]
