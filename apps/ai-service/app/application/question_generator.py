from app.config import settings
from app.domain.question_models import (
    GeneratedQuestion,
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
)
from app.llm.litellm_client import LiteLLMClient


class QuestionGenerator:
    def __init__(self, client: LiteLLMClient | None = None) -> None:
        self.client = client or LiteLLMClient()

    def generate(self, request: GenerateQuestionsRequest) -> GenerateQuestionsResponse:
        prompt = (
            f"Generate exactly {request.question_count} {request.difficulty} interview questions "
            f"covering these skills: {', '.join(request.skills)}. "
            "Use this exact type composition: "
            f"{request.question_composition.mcq_single} MCQ_SINGLE, "
            f"{request.question_composition.mcq_multiple} MCQ_MULTIPLE, "
            f"{request.question_composition.short_text} SHORT_TEXT, and "
            f"{request.question_composition.long_text} LONG_TEXT. "
            "For MCQs include at least four options and the correctAnswers values. "
            "For text questions return empty options and correctAnswers arrays. "
            "Use sequential order values beginning at 1. Allocate 10 points per question."
        )
        raw_questions, model = self.client.generate(prompt, request.question_count)
        questions = [GeneratedQuestion.model_validate(question) for question in raw_questions]
        if len(questions) != request.question_count:
            raise ValueError("Model returned an unexpected question count")
        actual = {name: 0 for name in ("MCQ_SINGLE", "MCQ_MULTIPLE", "SHORT_TEXT", "LONG_TEXT")}
        for question in questions:
            actual[question.type] += 1
        expected = {
            "MCQ_SINGLE": request.question_composition.mcq_single,
            "MCQ_MULTIPLE": request.question_composition.mcq_multiple,
            "SHORT_TEXT": request.question_composition.short_text,
            "LONG_TEXT": request.question_composition.long_text,
        }
        if actual != expected:
            raise ValueError("Model returned an unexpected question composition")
        return GenerateQuestionsResponse(
            request_id=request.request_id,
            interview_id=request.interview_id,
            model_policy=model,
            prompt_version=settings.prompt_version,
            questions=questions,
        )
