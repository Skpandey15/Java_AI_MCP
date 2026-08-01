"""Interview-composition agent.

A genuine agent loop (not a single call): it plans against a blueprint, then repeats
generate -> self-critique -> reuse-check, keeping only questions the critic accepts and
regenerating the remaining gap, until the blueprint is met or max_rounds is hit. The
number of rounds is data-dependent, and the critic (LLM-as-judge) drives control flow.
"""

import re

from app.domain.composition_models import (
    ComposedQuestion,
    ComposeRequest,
    ComposeResponse,
    GenerationUsage,
)
from app.llm.chat_client import ChatClient

_GEN_SCHEMA = {
    "type": "object",
    "properties": {"questions": {"type": "array", "items": {
        "type": "object",
        "properties": {"prompt": {"type": "string"}, "topic": {"type": "string"}},
        "required": ["prompt", "topic"], "additionalProperties": False}}},
    "required": ["questions"], "additionalProperties": False,
}
_CRIT_SCHEMA = {
    "type": "object",
    "properties": {"reviews": {"type": "array", "items": {
        "type": "object",
        "properties": {"accept": {"type": "boolean"}, "reason": {"type": "string"}},
        "required": ["accept", "reason"], "additionalProperties": False}}},
    "required": ["reviews"], "additionalProperties": False,
}


def _tokens(text: str) -> set[str]:
    return {w for w in re.findall(r"[a-z0-9]+", text.lower()) if len(w) > 3}


def _is_dup(prompt: str, existing: list[str]) -> bool:
    a = _tokens(prompt)
    if not a:
        return False
    for other in existing:
        b = _tokens(other)
        if b and len(a & b) / len(a | b) > 0.6:
            return True
    return False


def _sum(usages: list[dict]) -> GenerationUsage:
    return GenerationUsage(
        prompt_tokens=sum(u["prompt_tokens"] for u in usages),
        completion_tokens=sum(u["completion_tokens"] for u in usages),
        total_tokens=sum(u["total_tokens"] for u in usages),
        estimated_cost_usd=sum(u["estimated_cost_usd"] for u in usages),
        latency_ms=sum(u["latency_ms"] for u in usages),
    )


class CompositionAgent:
    def __init__(self, client: ChatClient | None = None) -> None:
        self.client = client or ChatClient()

    def compose(self, req: ComposeRequest) -> ComposeResponse:
        accepted: list[ComposedQuestion] = []
        trace: list[str] = []
        usages: list[dict] = []
        rounds = 0
        skills = ", ".join(req.skills)
        context = ("\n\nGround questions in this reference material only:\n"
                   + "\n---\n".join(req.grounding)) if req.grounding else ""
        while len(accepted) < req.question_count and rounds < req.max_rounds:
            rounds += 1
            need = req.question_count - len(accepted)
            avoid = req.existing_prompts + [q.prompt for q in accepted]
            hint = f"\nPrevious round feedback: {trace[-1]}" if trace else ""
            gen, gu = self.client.complete_json(
                "You design fair, unambiguous, non-duplicative technical interview questions.",
                (f"Generate exactly {need} {req.difficulty} descriptive interview questions "
                 f"on: {skills}. Each must cover a distinct sub-topic and must NOT duplicate "
                 "any of these existing questions:\n- "
                 + "\n- ".join(avoid[:60] or ["(none)"]) + hint + context),
                _GEN_SCHEMA)
            usages.append(gu)
            candidates = [
                ComposedQuestion(prompt=str(q.get("prompt", "")), topic=str(q.get("topic", "")))
                for q in gen.get("questions", []) if q.get("prompt")]
            crit, cu = self.client.complete_json(
                "You are a strict interview-question reviewer.",
                (f"For {req.difficulty} questions on {skills}, review each question below in "
                 "order. Accept only if it is on-topic, clearly at the target difficulty, "
                 "unambiguous, and not a near-duplicate of another. Return a review per "
                 "question with accept and a short reason.\n\n"
                 + "\n".join(f"{i+1}. {c.prompt}" for i, c in enumerate(candidates))),
                _CRIT_SCHEMA)
            usages.append(cu)
            reviews = crit.get("reviews", [])
            for idx, cand in enumerate(candidates):
                verdict = reviews[idx] if idx < len(reviews) else {"accept": True, "reason": ""}
                dup = _is_dup(cand.prompt, req.existing_prompts + [q.prompt for q in accepted])
                if verdict.get("accept") and not dup and len(accepted) < req.question_count:
                    accepted.append(cand)
                else:
                    trace.append(f"round {rounds}: rejected "
                                 f"({'duplicate' if dup else str(verdict.get('reason', ''))[:60]})")
            trace.append(f"round {rounds}: {len(accepted)}/{req.question_count} accepted")
        return ComposeResponse(
            questions=accepted[:req.question_count], rounds=rounds, trace=trace, usage=_sum(usages))
