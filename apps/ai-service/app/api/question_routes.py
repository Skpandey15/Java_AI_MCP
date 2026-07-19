import logging

from fastapi import APIRouter, Header, HTTPException, status

from app.application.question_generator import QuestionGenerator
from app.config import settings
from app.domain.question_models import GenerateQuestionsRequest, GenerateQuestionsResponse
from app.llm.litellm_client import ModelGatewayError

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
        logger.info(
            "Question generation completed",
            extra={
                "event": "ai.questions_generated",
                "interviewId": str(request.interview_id),
                "generationRequestId": str(request.request_id),
                "modelPolicy": response.model_policy,
            },
        )
        return response
    except (ModelGatewayError, ValueError) as exc:
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
