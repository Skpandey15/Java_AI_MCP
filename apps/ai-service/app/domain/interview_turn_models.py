"""Request/response models for the Adaptive AI Interviewer turn engine.

The orchestrator brokers all tools (blueprint, reuse-checked bank questions, knowledge
snippets) and passes the results here; the agent reasons over them and returns the next
move. Field aliases are camelCase so the Java orchestrator round-trips cleanly.
"""

from uuid import UUID

from pydantic import Field

from app.domain.question_models import ApiModel, GenerationUsage


class TranscriptEntry(ApiModel):
    skill: str = Field(min_length=1, max_length=200)
    question: str = Field(min_length=1, max_length=8000)
    answer: str = Field(default="", max_length=20000)


class SkillMastery(ApiModel):
    skill: str = Field(min_length=1, max_length=200)
    confidence: int = Field(ge=0, le=100)
    evidence: int = Field(ge=0, le=100)


class CandidateQuestion(ApiModel):
    """A bank question the orchestrator already reuse-checked (safe to ask)."""

    question_id: UUID
    skill: str = Field(min_length=1, max_length=200)
    difficulty: str = Field(min_length=1, max_length=40)
    prompt: str = Field(min_length=1, max_length=8000)


class KnowledgeSnippet(ApiModel):
    """A grounding chunk the agent may cite when generating a follow-up."""

    chunk_id: UUID
    file_name: str = Field(default="", max_length=400)
    content: str = Field(default="", max_length=8000)


class TurnBudget(ApiModel):
    turns_remaining: int = Field(ge=0, le=100)
    token_budget: int = Field(ge=0)


class NextTurnRequest(ApiModel):
    session_id: UUID
    interview_id: UUID
    skills: list[str] = Field(default_factory=list, max_length=50)
    target_difficulty: str = Field(default="MEDIUM", max_length=40)
    passing_percentage: int = Field(default=0, ge=0, le=100)
    transcript: list[TranscriptEntry] = Field(default_factory=list, max_length=100)
    skill_mastery: list[SkillMastery] = Field(default_factory=list, max_length=50)
    candidate_questions: list[CandidateQuestion] = Field(default_factory=list, max_length=50)
    knowledge_snippets: list[KnowledgeSnippet] = Field(default_factory=list, max_length=50)
    budget: TurnBudget


class AskedQuestion(ApiModel):
    skill: str
    difficulty: str
    source: str  # BANK | GENERATED
    question_id: UUID | None = None
    prompt: str
    citation_chunk_ids: list[UUID] = Field(default_factory=list)


class AnswerEvaluation(ApiModel):
    skill: str
    score: int = Field(ge=0, le=100)
    confidence: int = Field(ge=0, le=100)
    rationale: str = Field(default="", max_length=4000)


class FinalAssessment(ApiModel):
    summary: str = Field(default="", max_length=8000)
    per_skill: list[SkillMastery] = Field(default_factory=list)


class NextTurnResponse(ApiModel):
    action: str  # ASK | CONCLUDE
    rationale: str = Field(default="", max_length=4000)
    question: AskedQuestion | None = None
    last_answer_evaluation: AnswerEvaluation | None = None
    final_assessment: FinalAssessment | None = None
    usage: GenerationUsage | None = None
