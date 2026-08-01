import logging
import secrets

from fastapi import APIRouter, Header, HTTPException, status

from app.application.topic_agent import TopicAgent
from app.config import settings
from app.domain.topic_models import SuggestTopicsRequest, SuggestTopicsResponse
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["topics"])
agent = TopicAgent()
logger = logging.getLogger(__name__)


@router.post("/topics:suggest", response_model=SuggestTopicsResponse)
def suggest_topics(
    request: SuggestTopicsRequest, x_service_token: str = Header(default="")
) -> SuggestTopicsResponse:
    if not secrets.compare_digest(x_service_token, settings.ai_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid service token")
    try:
        response = agent.suggest(request)
        logger.info("Topics suggested", extra={
            "event": "ai.topics_suggested", "topicCount": len(response.topics)})
        return response
    except (ModelGatewayError, ValueError) as exc:
        logger.exception("Topic suggestion failed", extra={"event": "ai.topics_failed"})
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,
                            detail="Topic suggestion failed") from exc
