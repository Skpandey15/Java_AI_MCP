import json
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import settings
from app.observability import get_request_id


class ModelGatewayError(RuntimeError):
    pass


class LiteLLMClient:
    def generate(self, prompt: str, question_count: int) -> tuple[list[dict], str, dict]:
        schema = {
            "type": "object",
            "properties": {
                "questions": {
                    "type": "array",
                    "minItems": question_count,
                    "maxItems": question_count,
                    "items": {
                        "type": "object",
                        "properties": {
                            "order": {"type": "integer", "minimum": 1},
                            "prompt": {"type": "string"},
                            "max_score": {"type": "integer", "minimum": 1, "maximum": 100},
                            "type": {
                                "type": "string",
                                "enum": ["MCQ_SINGLE", "MCQ_MULTIPLE", "SHORT_TEXT", "LONG_TEXT"],
                            },
                            "options": {"type": "array", "items": {"type": "string"}},
                            "correct_answers": {"type": "array", "items": {"type": "string"}},
                            "citation_ids": {
                                "type": "array",
                                "items": {"type": "string", "format": "uuid"},
                            },
                        },
                        "required": [
                            "order", "prompt", "max_score", "type", "options",
                            "correct_answers", "citation_ids"
                        ],
                        "additionalProperties": False,
                    },
                }
            },
            "required": ["questions"],
            "additionalProperties": False,
        }
        body = {
            "model": settings.interview_question_model,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "You create fair, unambiguous technical interview questions. "
                        "Return only the requested structured result. Include correct answers "
                        "only in the structured correct_answers field."
                    ),
                },
                {"role": "user", "content": prompt},
            ],
            "response_format": {
                "type": "json_schema",
                "json_schema": {"name": "interview_questions", "strict": True, "schema": schema},
            },
        }
        request = Request(
            f"{settings.litellm_base_url}/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={
                "Authorization": f"Bearer {settings.litellm_master_key}",
                "Content-Type": "application/json",
                "X-Request-ID": get_request_id(),
            },
            method="POST",
        )
        started = time.monotonic()
        try:
            with urlopen(request, timeout=65) as response:
                payload = json.loads(response.read())
        except (HTTPError, URLError, TimeoutError) as exc:
            raise ModelGatewayError("LiteLLM request failed") from exc
        content = payload["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        model = payload.get("model", settings.interview_question_model)
        usage = payload.get("usage", {})
        prompt_tokens = int(usage.get("prompt_tokens", 0))
        completion_tokens = int(usage.get("completion_tokens", 0))
        estimated_cost = (
            prompt_tokens * settings.input_cost_per_million_tokens_usd
            + completion_tokens * settings.output_cost_per_million_tokens_usd
        ) / 1_000_000
        return parsed["questions"], model, {
            "prompt_tokens": prompt_tokens,
            "completion_tokens": completion_tokens,
            "total_tokens": int(usage.get(
                "total_tokens", prompt_tokens + completion_tokens
            )),
            "estimated_cost_usd": estimated_cost,
            "latency_ms": round((time.monotonic() - started) * 1000),
        }
