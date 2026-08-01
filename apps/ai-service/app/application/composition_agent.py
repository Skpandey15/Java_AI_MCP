"""Interview-composition agent.

A genuine agent loop (not a single call): it plans a per-type blueprint, then repeats
generate -> self-critique -> reuse-check, keeping only questions the critic accepts and
regenerating the remaining gap, until the blueprint is met or max_rounds is hit. The
number of rounds is data-dependent, and the critic (LLM-as-judge) drives control flow.
It composes the full mix of question types (single/multiple MCQ, short and long text),
not just descriptive questions.
"""

import re

from app.domain.composition_models import (
    ComposedQuestion,
    ComposeRequest,
    ComposeResponse,
    GenerationUsage,
)
from app.llm.chat_client import ChatClient

_TYPE_LABEL = {
    "MCQ_SINGLE": "single-answer multiple-choice",
    "MCQ_MULTIPLE": "multiple-answer multiple-choice",
    "SHORT_TEXT": "short-answer (a few sentences)",
    "LONG_TEXT": "descriptive / open-ended",
}

_GEN_SCHEMA = {
    "type": "object",
    "properties": {"questions": {"type": "array", "items": {
        "type": "object",
        "properties": {
            "prompt": {"type": "string"},
            "topic": {"type": "string"},
            "type": {"type": "string",
                     "enum": ["MCQ_SINGLE", "MCQ_MULTIPLE", "SHORT_TEXT", "LONG_TEXT"]},
            "options": {"type": "array", "items": {"type": "string"}},
            "correctAnswers": {"type": "array", "items": {"type": "string"}},
        },
        "required": ["prompt", "topic", "type", "options", "correctAnswers"],
        "additionalProperties": False}}},
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


def _valid_structure(qtype: str, options: list[str], correct: list[str]) -> bool:
    """MCQ questions need >=2 distinct options and correct answers drawn from them."""
    if qtype in ("MCQ_SINGLE", "MCQ_MULTIPLE"):
        if len(set(options)) < 2 or not correct or not set(correct).issubset(set(options)):
            return False
        if qtype == "MCQ_SINGLE" and len(correct) != 1:
            return False
    return True


def _blueprint(req: ComposeRequest) -> dict[str, int]:
    """Target count per question type, defaulting to all long-text."""
    c = req.composition
    if c and c.total > 0:
        return {"MCQ_SINGLE": c.mcq_single, "MCQ_MULTIPLE": c.mcq_multiple,
                "SHORT_TEXT": c.short_text, "LONG_TEXT": c.long_text}
    return {"MCQ_SINGLE": 0, "MCQ_MULTIPLE": 0, "SHORT_TEXT": 0, "LONG_TEXT": req.question_count}


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
        target = _blueprint(req)
        total = sum(target.values())
        accepted: list[ComposedQuestion] = []
        by_type: dict[str, int] = {t: 0 for t in target}
        trace: list[str] = []
        usages: list[dict] = []
        rounds = 0
        skills = ", ".join(req.skills)
        context = ("\n\nGround questions in this reference material only:\n"
                   + "\n---\n".join(req.grounding)) if req.grounding else ""
        while len(accepted) < total and rounds < req.max_rounds:
            rounds += 1
            gap = {t: target[t] - by_type[t] for t in target if target[t] - by_type[t] > 0}
            mix = "; ".join(f"{n} {_TYPE_LABEL[t]} ({t})" for t, n in gap.items())
            avoid = req.existing_prompts + [q.prompt for q in accepted]
            hint = f"\nPrevious round feedback: {trace[-1]}" if trace else ""
            gen, gu = self.client.complete_json(
                "You design fair, unambiguous, non-duplicative technical interview questions.",
                (f"Generate {req.difficulty} interview questions on: {skills}. Produce exactly "
                 f"this mix by type: {mix}. For MCQ_SINGLE/MCQ_MULTIPLE include 4 distinct "
                 "options and set correctAnswers to the exact correct option text(s) — exactly "
                 "one for MCQ_SINGLE, one or more for MCQ_MULTIPLE. For SHORT_TEXT/LONG_TEXT set "
                 "options and correctAnswers to empty arrays. Each question must cover a distinct "
                 "sub-topic and must NOT duplicate any of these existing questions:\n- "
                 + "\n- ".join(avoid[:60] or ["(none)"]) + hint + context),
                _GEN_SCHEMA)
            usages.append(gu)
            candidates = []
            for q in gen.get("questions", []):
                if not q.get("prompt"):
                    continue
                qtype = q.get("type", "LONG_TEXT")
                opts = [str(o) for o in q.get("options", [])] if "MCQ" in qtype else []
                corr = [str(o) for o in q.get("correctAnswers", [])] if "MCQ" in qtype else []
                candidates.append(ComposedQuestion(
                    prompt=str(q.get("prompt", "")), topic=str(q.get("topic", "")),
                    type=qtype, options=opts, correct_answers=corr))
            crit, cu = self.client.complete_json(
                "You are a strict interview-question reviewer.",
                (f"For {req.difficulty} questions on {skills}, review each question below in "
                 "order. Accept only if it is on-topic, clearly at the target difficulty, "
                 "unambiguous, and not a near-duplicate of another. For multiple-choice "
                 "questions also confirm the marked correct answer is truly correct and the "
                 "other options are plausible but wrong. Return a review per question with "
                 "accept and a short reason.\n\n"
                 + "\n".join(
                     f"{i + 1}. [{c.type}] {c.prompt}"
                     + (f" options={c.options} correct={c.correct_answers}"
                        if "MCQ" in c.type else "")
                     for i, c in enumerate(candidates))),
                _CRIT_SCHEMA)
            usages.append(cu)
            reviews = crit.get("reviews", [])
            for idx, cand in enumerate(candidates):
                verdict = reviews[idx] if idx < len(reviews) else {"accept": True, "reason": ""}
                dup = _is_dup(cand.prompt, req.existing_prompts + [q.prompt for q in accepted])
                structural = _valid_structure(cand.type, cand.options, cand.correct_answers)
                room = by_type.get(cand.type, 0) < target.get(cand.type, 0)
                if verdict.get("accept") and not dup and structural and room:
                    accepted.append(cand)
                    by_type[cand.type] += 1
                else:
                    why = ("duplicate" if dup else "malformed MCQ" if not structural
                           else "type quota full" if not room
                           else str(verdict.get("reason", ""))[:60])
                    trace.append(f"round {rounds}: rejected [{cand.type}] ({why})")
            trace.append(f"round {rounds}: {len(accepted)}/{total} accepted")
        return ComposeResponse(
            questions=accepted[:total], rounds=rounds, trace=trace, usage=_sum(usages))
