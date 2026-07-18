import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import settings


class ModelGatewayError(RuntimeError):
    pass


class LiteLLMClient:
    def generate(self, prompt: str, question_count: int) -> tuple[list[dict], str]:
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
                        },
                        "required": ["order", "prompt", "max_score"],
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
                        "Return only the requested structured result. Do not include answers."
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
            },
            method="POST",
        )
        try:
            with urlopen(request, timeout=65) as response:
                payload = json.loads(response.read())
        except (HTTPError, URLError, TimeoutError) as exc:
            raise ModelGatewayError("LiteLLM request failed") from exc
        content = payload["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        model = payload.get("model", settings.interview_question_model)
        return parsed["questions"], model
