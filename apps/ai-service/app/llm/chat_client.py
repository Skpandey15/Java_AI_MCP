import json
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import settings
from app.llm.litellm_client import ModelGatewayError
from app.observability import get_request_id


class ChatClient:
    """Generic structured chat-completion via the LiteLLM gateway.

    Used by the assessment agents (answer evaluation + candidate coaching) so they
    reason with schema-validated output instead of free text. All model traffic still
    goes through LiteLLM with the scoped virtual key, budgets and telemetry.
    """

    def complete_json(self, system: str, user: str, schema: dict) -> tuple[dict, dict]:
        body = {
            "model": settings.interview_question_model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "structured", "strict": True, "schema": schema},
            },
        }
        request = Request(
            f"{settings.litellm_base_url}/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={
                "Authorization": f"Bearer {settings.litellm_api_key}",
                "Content-Type": "application/json",
                "X-Request-ID": get_request_id(),
            },
            method="POST",
        )
        started = time.monotonic()
        try:
            with urlopen(request, timeout=120) as response:
                payload = json.loads(response.read())
        except (HTTPError, URLError, TimeoutError) as exc:
            raise ModelGatewayError("LiteLLM request failed") from exc
        parsed = json.loads(payload["choices"][0]["message"]["content"])
        usage = payload.get("usage", {})
        prompt_tokens = int(usage.get("prompt_tokens", 0))
        completion_tokens = int(usage.get("completion_tokens", 0))
        estimated_cost = (
            prompt_tokens * settings.input_cost_per_million_tokens_usd
            + completion_tokens * settings.output_cost_per_million_tokens_usd
        ) / 1_000_000
        return parsed, {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": int(usage.get("total_tokens", prompt_tokens + completion_tokens)),
            "estimated_cost_usd": estimated_cost,
            "latency_ms": round((time.monotonic() - started) * 1000),
        }
