from fastapi import APIRouter, Header, HTTPException, status

from app.config import settings
from app.domain.embedding_models import CreateEmbeddingsRequest, CreateEmbeddingsResponse
from app.llm.embedding_client import LiteLLMEmbeddingClient
from app.llm.litellm_client import ModelGatewayError

router = APIRouter(prefix="/internal/v1", tags=["embeddings"])
client = LiteLLMEmbeddingClient()


@router.post("/embeddings:create", response_model=CreateEmbeddingsResponse)
def create_embeddings(
    request: CreateEmbeddingsRequest,
    x_service_token: str = Header(default=""),
) -> CreateEmbeddingsResponse:
    if x_service_token != settings.ai_service_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid service token",
        )
    try:
        embeddings, model = client.embed(request.texts)
    except ModelGatewayError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail="Embedding generation failed"
        ) from exc
    if len(embeddings) != len(request.texts):
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY, detail="Embedding count mismatch"
        )
    return CreateEmbeddingsResponse(model_policy=model, embeddings=embeddings)
