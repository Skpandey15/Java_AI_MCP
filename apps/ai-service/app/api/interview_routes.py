import logging
import secrets

from fastapi import APIRouter, Header, HTTPException, status

from app.application.interviewer_agent import InterviewerAgent
from app.config import settings
from app.domain.interview_turn_models import NextTurnRequest, NextTurnResponse
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["interview"])
agent = InterviewerAgent()
logger = logging.getLogger(__name__)


def _authorize(token: str) -> None:
    if not secrets.compare_digest(token, settings.ai_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid service token")


@router.post("/interview:next-turn", response_model=NextTurnResponse)
def next_turn(
    request: NextTurnRequest,
    x_service_token: str = Header(default=""),
) -> NextTurnResponse:
    _authorize(x_service_token)
    try:
        return agent.next_turn(request)
    except ModelGatewayError as exc:
        logger.warning("interviewer agent gateway error: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Model gateway error") from exc
