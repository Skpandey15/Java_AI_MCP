import logging
import secrets

from fastapi import APIRouter, Header, HTTPException, status

from app.application.assessment_agent import AssessmentAgent
from app.config import settings
from app.domain.assessment_models import (
    DraftFeedbackRequest,
    DraftFeedbackResponse,
    EvaluateAnswersRequest,
    EvaluateAnswersResponse,
)
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["assessment"])
agent = AssessmentAgent()
logger = logging.getLogger(__name__)


def _authorize(token: str) -> None:
    if not secrets.compare_digest(token, settings.ai_service_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid service token")


@router.post("/answers:evaluate", response_model=EvaluateAnswersResponse)
def evaluate_answers(
    request: EvaluateAnswersRequest,
    x_service_token: str = Header(default=""),
) -> EvaluateAnswersResponse:
    _authorize(x_service_token)
    try:
        response = agent.evaluate(request)
        logger.info(
            "Answer evaluation completed",
            extra={"event": "ai.answers_evaluated", "sessionId": str(request.session_id),
                   "answerCount": len(response.evaluations)},
        )
        return response
    except (ModelGatewayError, ValueError) as exc:
        logger.exception("Answer evaluation failed",
                         extra={"event": "ai.answer_evaluation_failed",
                                "sessionId": str(request.session_id)})
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,
                            detail="Answer evaluation failed") from exc


@router.post("/feedback:draft", response_model=DraftFeedbackResponse)
def draft_feedback(
    request: DraftFeedbackRequest,
    x_service_token: str = Header(default=""),
) -> DraftFeedbackResponse:
    _authorize(x_service_token)
    try:
        response = agent.draft_feedback(request)
        logger.info(
            "Coaching feedback drafted",
            extra={"event": "ai.feedback_drafted", "sessionId": str(request.session_id),
                   "leakageSafe": response.leakage.safe,
                   "leakageFlags": len(response.leakage.flags)},
        )
        return response
    except (ModelGatewayError, ValueError) as exc:
        logger.exception("Coaching feedback failed",
                         extra={"event": "ai.feedback_failed",
                                "sessionId": str(request.session_id)})
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,
                            detail="Coaching feedback failed") from exc
