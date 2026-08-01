"""Assess & Coach agents.

Two MCP-host style agent steps that reason with schema-validated output:

* the evaluation agent scores descriptive answers against the question intent and
  returns *suggestions* (score + confidence + justification) for the interviewer to
  accept or edit — it never becomes the score of record;
* the coaching agent drafts a candidate-facing development plan from privileged
  context, then runs a distinct leakage-guard step that strips exact scores, pass
  thresholds, expected answers and protected-characteristic language before the draft
  is handed back for interviewer approval.
"""

from uuid import UUID

from app.domain.assessment_models import (
    AnswerEvaluation,
    DraftFeedbackRequest,
    DraftFeedbackResponse,
    EvaluateAnswersRequest,
    EvaluateAnswersResponse,
    GenerationUsage,
    LeakageVerdict,
)
from app.llm.chat_client import ChatClient

_STR_ARRAY = {"type": "array", "items": {"type": "string"}}

_EVAL_SCHEMA = {
    "type": "object",
    "properties": {
        "evaluations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "answer_id": {"type": "string"},
                    "suggested_score": {"type": "integer"},
                    "confidence": {"type": "number"},
                    "justification": {"type": "string"},
                    "strengths": _STR_ARRAY,
                    "gaps": _STR_ARRAY,
                },
                "required": [
                    "answer_id", "suggested_score", "confidence",
                    "justification", "strengths", "gaps",
                ],
                "additionalProperties": False,
            },
        }
    },
    "required": ["evaluations"],
    "additionalProperties": False,
}

_DRAFT_SCHEMA = {
    "type": "object",
    "properties": {
        "feedback": {"type": "string"},
        "strengths": _STR_ARRAY,
        "growth_areas": _STR_ARRAY,
    },
    "required": ["feedback", "strengths", "growth_areas"],
    "additionalProperties": False,
}

_GUARD_SCHEMA = {
    "type": "object",
    "properties": {
        "safe": {"type": "boolean"},
        "flags": _STR_ARRAY,
        "sanitized_feedback": {"type": "string"},
    },
    "required": ["safe", "flags", "sanitized_feedback"],
    "additionalProperties": False,
}


def _sum_usage(*usages: dict) -> GenerationUsage:
    return GenerationUsage(
        prompt_tokens=sum(u["prompt_tokens"] for u in usages),
        completion_tokens=sum(u["completion_tokens"] for u in usages),
        total_tokens=sum(u["total_tokens"] for u in usages),
        estimated_cost_usd=sum(u["estimated_cost_usd"] for u in usages),
        latency_ms=sum(u["latency_ms"] for u in usages),
    )


class AssessmentAgent:
    def __init__(self, client: ChatClient | None = None) -> None:
        self.client = client or ChatClient()

    def evaluate(self, request: EvaluateAnswersRequest) -> EvaluateAnswersResponse:
        limits = {item.answer_id: item.max_score for item in request.items}
        lines = []
        for item in request.items:
            lines.append(
                f"answer_id: {item.answer_id}\n"
                f"topic: {item.topic or 'general'}\n"
                f"max_score: {item.max_score}\n"
                f"question: {item.question_prompt}\n"
                f"candidate_answer:\n{item.answer_text or '(no answer provided)'}\n---"
            )
        system = (
            "You are a fair, calibrated technical interview grader. For each answer, first "
            "reason about how well it addresses the question, then assign an integer score "
            "between 0 and the answer's max_score. Draft scores, re-check them for calibration "
            "and consistency across answers, then finalise. Provide a concise justification, "
            "concrete strengths and gaps, and a confidence between 0 and 1. Echo each answer_id "
            "exactly. These are suggestions for a human reviewer, not final grades."
        )
        parsed, usage = self.client.complete_json(system, "\n".join(lines), _EVAL_SCHEMA)
        evaluations = []
        for row in parsed.get("evaluations", []):
            try:
                answer_id = UUID(str(row["answer_id"]))
            except (KeyError, ValueError):
                continue
            cap = limits.get(answer_id)
            if cap is None:
                continue
            score = max(0, min(cap, int(row.get("suggested_score", 0))))
            confidence = max(0.0, min(1.0, float(row.get("confidence", 0.0))))
            evaluations.append(AnswerEvaluation(
                answer_id=answer_id,
                suggested_score=score,
                confidence=confidence,
                justification=str(row.get("justification", "")),
                strengths=[str(s) for s in row.get("strengths", [])],
                gaps=[str(g) for g in row.get("gaps", [])],
            ))
        return EvaluateAnswersResponse(
            session_id=request.session_id,
            evaluations=evaluations,
            usage=_sum_usage(usage),
        )

    def draft_feedback(self, request: DraftFeedbackRequest) -> DraftFeedbackResponse:
        context = []
        for item in request.items:
            context.append(
                f"topic: {item.topic or 'general'}\n"
                f"question: {item.question_prompt}\n"
                f"candidate_answer:\n{item.answer_text or '(no answer provided)'}\n"
                f"internal_score: {item.awarded_score}/{item.max_score}\n"
                f"reviewer_note: {item.evaluator_feedback or '(none)'}\n---"
            )
        # Step 1 — draft from privileged context.
        draft_system = (
            "You are a supportive technical interview coach writing feedback the candidate "
            "will read. Use the internal scores and reviewer notes to identify real strengths "
            "and the most important growth areas, and give a concrete, encouraging development "
            "plan (topics to study and how to practise). Do NOT reveal numeric scores, the "
            "pass/fail threshold, how close they were to passing, verbatim expected answers, or "
            "any other candidate. Be specific and actionable, not generic."
        )
        outcome_line = f"outcome: {request.outcome} (passed={request.passed})\n\n"
        draft, draft_usage = self.client.complete_json(
            draft_system, outcome_line + "\n".join(context), _DRAFT_SCHEMA)

        # Step 2 — distinct leakage-guard pass over the draft.
        guard_system = (
            "You are a compliance filter for candidate-facing interview feedback. Reject or "
            "rewrite anything that reveals: exact numeric scores; the pass/fail threshold or how "
            "close the candidate was; verbatim expected answers or hidden grading criteria; any "
            "reference to other candidates; or protected-characteristic language (race, gender, "
            "age, religion, disability, nationality, etc.). Return safe=true only if the text is "
            "already clean. Always return sanitized_feedback: the feedback with every violation "
            "removed while keeping it constructive. List each violation in flags."
        )
        guard, guard_usage = self.client.complete_json(
            guard_system, str(draft.get("feedback", "")), _GUARD_SCHEMA)

        safe = bool(guard.get("safe", False))
        flags = [str(f) for f in guard.get("flags", [])]
        final_feedback = str(draft.get("feedback", "")) if safe \
            else str(guard.get("sanitized_feedback", "") or draft.get("feedback", ""))
        return DraftFeedbackResponse(
            session_id=request.session_id,
            feedback=final_feedback,
            strengths=[str(s) for s in draft.get("strengths", [])],
            growth_areas=[str(g) for g in draft.get("growth_areas", [])],
            leakage=LeakageVerdict(safe=safe, flags=flags),
            usage=_sum_usage(draft_usage, guard_usage),
        )
