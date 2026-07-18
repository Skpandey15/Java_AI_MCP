from fastapi import APIRouter, Header, HTTPException, status

from app.application.question_generator import QuestionGenerator
from app.config import settings
from app.domain.question_models import GenerateQuestionsRequest, GenerateQuestionsResponse
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["question-generation"])
generator = QuestionGenerator()


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
        return generator.generate(request)
    except (ModelGatewayError, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Question generation failed",
        ) from exc
