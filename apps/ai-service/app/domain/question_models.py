from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        validate_by_alias=True,
        validate_by_name=True,
        serialize_by_alias=True,
    )


class QuestionComposition(ApiModel):
    mcq_single: int = Field(ge=0, le=100)
    mcq_multiple: int = Field(ge=0, le=100)
    short_text: int = Field(ge=0, le=100)
    long_text: int = Field(ge=0, le=100)

    @property
    def total(self) -> int:
        return self.mcq_single + self.mcq_multiple + self.short_text + self.long_text


class GenerateQuestionsRequest(ApiModel):
    request_id: UUID
    interview_id: UUID
    skills: list[str] = Field(min_length=1, max_length=20)
    difficulty: str
    question_count: int = Field(ge=1, le=100)
    question_composition: QuestionComposition

    @model_validator(mode="after")
    def composition_matches_total(self) -> "GenerateQuestionsRequest":
        if self.question_composition.total != self.question_count:
            raise ValueError("Question composition total must equal question count")
        return self


class GeneratedQuestion(ApiModel):
    order: int = Field(ge=1)
    prompt: str = Field(min_length=10, max_length=4000)
    max_score: int = Field(ge=1, le=100)
    type: Literal["MCQ_SINGLE", "MCQ_MULTIPLE", "SHORT_TEXT", "LONG_TEXT"]
    options: list[str] = Field(default_factory=list)
    correct_answers: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_type_details(self) -> "GeneratedQuestion":
        if self.type in {"MCQ_SINGLE", "MCQ_MULTIPLE"}:
            if len(self.options) < 2:
                raise ValueError("MCQ questions require at least two options")
            if not self.correct_answers or not set(self.correct_answers).issubset(self.options):
                raise ValueError("MCQ correct answers must reference configured options")
            if self.type == "MCQ_SINGLE" and len(self.correct_answers) != 1:
                raise ValueError("MCQ_SINGLE requires exactly one correct answer")
        elif self.options or self.correct_answers:
            raise ValueError("Text questions cannot include options or correct answers")
        return self


class GenerateQuestionsResponse(ApiModel):
    request_id: UUID
    interview_id: UUID
    model_policy: str
    prompt_version: str
    questions: list[GeneratedQuestion]
