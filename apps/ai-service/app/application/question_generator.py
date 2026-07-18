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
            "Use sequential order values beginning at 1. Allocate 10 points per question."
        )
        raw_questions, model = self.client.generate(prompt, request.question_count)
        questions = [GeneratedQuestion.model_validate(question) for question in raw_questions]
        if len(questions) != request.question_count:
            raise ValueError("Model returned an unexpected question count")
        return GenerateQuestionsResponse(
            request_id=request.request_id,
            interview_id=request.interview_id,
            model_policy=model,
            prompt_version=settings.prompt_version,
            questions=questions,
        )
