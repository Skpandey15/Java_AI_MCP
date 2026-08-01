"""Topic-suggestion agent: given the chosen technologies, propose distinct, interview-relevant
sub-topics the interviewer can pick from to focus question generation."""

from app.domain.question_models import GenerationUsage
from app.domain.topic_models import SuggestTopicsRequest, SuggestTopicsResponse
from app.llm.chat_client import ChatClient

_SCHEMA = {
    "type": "object",
    "properties": {"topics": {"type": "array", "items": {"type": "string"}}},
    "required": ["topics"],
    "additionalProperties": False,
}


class TopicAgent:
    def __init__(self, client: ChatClient | None = None) -> None:
        self.client = client or ChatClient()

    def suggest(self, request: SuggestTopicsRequest) -> SuggestTopicsResponse:
        technologies = ", ".join(request.technologies)
        system = (
            "You are a technical interview planner. Given one or more technologies, list the "
            "distinct, interview-relevant sub-topics an interviewer might focus questions on. "
            "Use concise topic names (2-4 words), specific and non-overlapping, ordered by "
            "importance, and cover every technology provided. Do not repeat the technology names "
            "themselves as topics."
        )
        user = (
            f"Technologies: {technologies}\n"
            f"Target difficulty: {request.difficulty}\n"
            f"Return up to {request.max_topics} topics."
        )
        parsed, usage = self.client.complete_json(system, user, _SCHEMA)
        seen: set[str] = set()
        topics: list[str] = []
        for item in parsed.get("topics", []):
            name = str(item).strip()
            key = name.lower()
            if name and key not in seen:
                seen.add(key)
                topics.append(name)
        return SuggestTopicsResponse(
            topics=topics[: request.max_topics],
            usage=GenerationUsage(
                prompt_tokens=usage["prompt_tokens"],
                completion_tokens=usage["completion_tokens"],
                total_tokens=usage["total_tokens"],
                estimated_cost_usd=usage["estimated_cost_usd"],
                latency_ms=usage["latency_ms"],
            ),
        )
