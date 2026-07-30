from pydantic import Field

from app.domain.question_models import ApiModel


class CreateEmbeddingsRequest(ApiModel):
    texts: list[str] = Field(min_length=1, max_length=64)


class CreateEmbeddingsResponse(ApiModel):
    model_policy: str
    embeddings: list[list[float]]
