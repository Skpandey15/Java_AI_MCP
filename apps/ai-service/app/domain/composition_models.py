from pydantic import Field

from app.domain.question_models import ApiModel, GenerationUsage


class ComposeRequest(ApiModel):
    skills: list[str] = Field(min_length=1, max_length=20)
    difficulty: str = "MEDIUM"
    question_count: int = Field(ge=1, le=20)
    existing_prompts: list[str] = Field(default_factory=list, max_length=200)
    grounding: list[str] = Field(default_factory=list, max_length=20)
    max_rounds: int = Field(default=3, ge=1, le=5)


class ComposedQuestion(ApiModel):
    prompt: str
    topic: str = ""
    max_score: int = 10


class ComposeResponse(ApiModel):
    questions: list[ComposedQuestion]
    rounds: int
    trace: list[str]
    usage: GenerationUsage | None = None
