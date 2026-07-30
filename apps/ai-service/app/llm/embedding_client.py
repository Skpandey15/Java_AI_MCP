import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import settings
from app.llm.litellm_client import ModelGatewayError
from app.observability import get_request_id


class LiteLLMEmbeddingClient:
    def embed(self, texts: list[str]) -> tuple[list[list[float]], str]:
        body = {"model": settings.knowledge_embedding_model, "input": texts}
        request = Request(
            f"{settings.litellm_base_url}/v1/embeddings",
            data=json.dumps(body).encode(),
            headers={
                "Authorization": f"Bearer {settings.litellm_master_key}",
                "Content-Type": "application/json",
                "X-Request-ID": get_request_id(),
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=65) as response:
                payload = json.loads(response.read())
        except (HTTPError, URLError, TimeoutError) as exc:
            raise ModelGatewayError("LiteLLM embedding request failed") from exc
        ordered = sorted(payload["data"], key=lambda item: item["index"])
        return [item["embedding"] for item in ordered], payload.get(
            "model", settings.knowledge_embedding_model
        )
