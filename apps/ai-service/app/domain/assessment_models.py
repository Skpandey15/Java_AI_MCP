from uuid import UUID

from pydantic import Field

from app.domain.question_models import ApiModel, GenerationUsage


# ---- Evaluation agent (suggest-only) ----

class EvaluationItem(ApiModel):
    answer_id: UUID
    question_prompt: str = Field(min_length=1, max_length=4000)
    max_score: int = Field(ge=1, le=100)
    answer_text: str = Field(default="", max_length=20000)
    topic: str = Field(default="", max_length=200)


class EvaluateAnswersRequest(ApiModel):
    session_id: UUID
    items: list[EvaluationItem] = Field(min_length=1, max_length=50)


class AnswerEvaluation(ApiModel):
    answer_id: UUID
    suggested_score: int = Field(ge=0)
    confidence: float = Field(ge=0, le=1)
    justification: str
    strengths: list[str] = Field(default_factory=list)
    gaps: list[str] = Field(default_factory=list)


class EvaluateAnswersResponse(ApiModel):
    session_id: UUID
    evaluations: list[AnswerEvaluation]
    usage: GenerationUsage | None = None


# ---- Coaching agent (privileged reasoning + leakage-gated) ----

class FeedbackItem(ApiModel):
    question_prompt: str = Field(min_length=1, max_length=4000)
    topic: str = Field(default="", max_length=200)
    max_score: int = Field(ge=1, le=100)
    awarded_score: int = Field(ge=0)
    answer_text: str = Field(default="", max_length=20000)
    evaluator_feedback: str = Field(default="", max_length=4000)


class DraftFeedbackRequest(ApiModel):
    session_id: UUID
    outcome: str = Field(max_length=40)
    passed: bool
    items: list[FeedbackItem] = Field(min_length=1, max_length=50)


class LeakageVerdict(ApiModel):
    safe: bool
    flags: list[str] = Field(default_factory=list)


class DraftFeedbackResponse(ApiModel):
    session_id: UUID
    feedback: str
    strengths: list[str] = Field(default_factory=list)
    growth_areas: list[str] = Field(default_factory=list)
    leakage: LeakageVerdict
    usage: GenerationUsage | None = None
