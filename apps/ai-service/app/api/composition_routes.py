import logging
import secrets

from fastapi import APIRouter, Header, HTTPException, status

from app.application.composition_agent import CompositionAgent
from app.config import settings
from app.domain.composition_models import ComposeRequest, ComposeResponse
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["composition"])
agent = CompositionAgent()
logger = logging.getLogger(__name__)


@router.post("/questions:compose", response_model=ComposeResponse)
def compose(request: ComposeRequest, x_service_token: str = Header(default="")) -> ComposeResponse:
    if not secrets.compare_digest(x_service_token, settings.ai_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid service token")
    try:
        response = agent.compose(request)
        logger.info("Interview composed", extra={
            "event": "ai.interview_composed", "rounds": response.rounds,
            "questionCount": len(response.questions)})
        return response
    except (ModelGatewayError, ValueError) as exc:
        logger.exception("Composition failed", extra={"event": "ai.composition_failed"})
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,
                            detail="Interview composition failed") from exc
