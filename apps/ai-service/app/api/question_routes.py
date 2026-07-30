import logging

from fastapi import APIRouter, Header, HTTPException, status

from app.application.question_generator import QuestionGenerator
from app.config import settings
from app.domain.question_models import GenerateQuestionsRequest, GenerateQuestionsResponse
from app.llm.litellm_client import ModelGatewayError
from app.quality_metrics import record_failure, record_success

router = APIRouter(prefix="/internal/v1", tags=["question-generation"])
generator = QuestionGenerator()
logger = logging.getLogger(__name__)


@router.post("/questions:generate", response_model=GenerateQuestionsResponse)
def generate_questions(
    request: GenerateQuestionsRequest,
    x_service_token: str = Header(default=""),
) -> GenerateQuestionsResponse:
    if x_service_token != settings.ai_service_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid service token",
        )
    try:
        response = generator.generate(request)
        if response.usage:
            record_success(
                response.model_policy,
                response.usage.prompt_tokens,
                response.usage.completion_tokens,
                response.usage.estimated_cost_usd,
                response.usage.latency_ms,
            )
        logger.info(
            "Question generation completed",
            extra={
                "event": "ai.questions_generated",
                "interviewId": str(request.interview_id),
                "generationRequestId": str(request.request_id),
                "modelPolicy": response.model_policy,
                "promptTokens": response.usage.prompt_tokens if response.usage else 0,
                "completionTokens": response.usage.completion_tokens if response.usage else 0,
                "estimatedCostUsd": response.usage.estimated_cost_usd if response.usage else 0,
                "latencyMs": response.usage.latency_ms if response.usage else 0,
            },
        )
        return response
    except (ModelGatewayError, ValueError) as exc:
        record_failure()
        logger.exception(
            "Question generation failed",
            extra={
                "event": "ai.question_generation_failed",
                "interviewId": str(request.interview_id),
                "generationRequestId": str(request.request_id),
            },
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Question generation failed",
        ) from exc
