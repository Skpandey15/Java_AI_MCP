from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.api import assessment_routes, composition_routes, topic_routes
from app.application.assessment_agent import AssessmentAgent
from app.application.composition_agent import CompositionAgent
from app.config import settings
from app.domain.assessment_models import (
    DraftFeedbackResponse,
    EvaluateAnswersResponse,
    LeakageVerdict,
)
from app.domain.composition_models import ComposedQuestion, ComposeResponse
from app.llm.litellm_client import ModelGatewayError
from app.main import app

USAGE = {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2,
         "estimated_cost_usd": 0.0, "latency_ms": 1}
HEADERS = {"X-Service-Token": settings.ai_service_token}
client = TestClient(app)


def _post(path, body, token=settings.ai_service_token):
    return client.post(path, json=body, headers={"X-Service-Token": token}).status_code


class FakeChat:
    def __init__(self, responses):
        self.responses = list(responses)

    def complete_json(self, system, user, schema):
        return self.responses.pop(0), USAGE


def test_evaluate_clamps_and_maps():
    aid = uuid4()
    agent = AssessmentAgent(FakeChat([{"evaluations": [
        {"answer_id": str(aid), "suggested_score": 999, "confidence": 2.0,
         "justification": "ok", "strengths": ["s"], "gaps": ["g"]},
        {"answer_id": str(uuid4()), "suggested_score": 1, "confidence": 0.1,
         "justification": "x", "strengths": [], "gaps": []},
    ]}]))
    req = {"sessionId": str(uuid4()), "items": [
        {"answerId": str(aid), "questionPrompt": "Q", "maxScore": 10, "answerText": "A"}]}
    from app.domain.assessment_models import EvaluateAnswersRequest
    resp = agent.evaluate(EvaluateAnswersRequest.model_validate(req))
    assert len(resp.evaluations) == 1
    assert resp.evaluations[0].suggested_score == 10  # clamped to max
    assert resp.evaluations[0].confidence == 1.0


def test_draft_feedback_uses_sanitized_when_flagged():
    from app.domain.assessment_models import DraftFeedbackRequest
    agent = AssessmentAgent(FakeChat([
        {"feedback": "raw", "strengths": ["a"], "growth_areas": ["b"]},
        {"safe": False, "flags": ["leak"], "sanitized_feedback": "clean"},
    ]))
    req = DraftFeedbackRequest.model_validate({
        "sessionId": str(uuid4()), "outcome": "NOT_SELECTED", "passed": False,
        "items": [{"questionPrompt": "Q", "maxScore": 10, "awardedScore": 3, "answerText": "A"}]})
    resp = agent.draft_feedback(req)
    assert resp.feedback == "clean"
    assert resp.leakage.safe is False and resp.leakage.flags == ["leak"]


def test_compose_loops_and_dedupes():
    from app.domain.composition_models import ComposeRequest
    agent = CompositionAgent(FakeChat([
        {"questions": [{"prompt": "Explain cache aside pattern usage extra", "topic": "c"},
                       {"prompt": "Design a multi level cache hierarchy system", "topic": "c"}]},
        {"reviews": [{"accept": True, "reason": ""}, {"accept": True, "reason": ""}]},
        {"questions": [{"prompt": "Compare write through and write back caching", "topic": "c"}]},
        {"reviews": [{"accept": True, "reason": ""}]},
    ]))
    req = ComposeRequest.model_validate({
        "skills": ["Caching"], "difficulty": "HARD", "questionCount": 2, "maxRounds": 2,
        "existingPrompts": ["Explain cache aside pattern usage"]})
    resp = agent.compose(req)
    assert len(resp.questions) == 2
    assert resp.rounds == 2  # first round dropped the duplicate, needed a second


def test_compose_mixed_types_and_validation():
    from app.domain.composition_models import ComposeRequest
    opts = ["Alpha", "Beta", "Gamma", "Delta"]
    agent = CompositionAgent(FakeChat([
        {"questions": [
            {"prompt": "Single choice question one", "topic": "t", "type": "MCQ_SINGLE",
             "options": opts, "correctAnswers": ["Alpha"]},
            {"prompt": "Multiple choice question two", "topic": "t", "type": "MCQ_MULTIPLE",
             "options": opts, "correctAnswers": ["Alpha", "Beta"]},
            {"prompt": "Rejected short answer question three", "topic": "t", "type": "SHORT_TEXT",
             "options": [], "correctAnswers": []},
            {"prompt": "Accepted short answer question four", "topic": "t", "type": "SHORT_TEXT",
             "options": [], "correctAnswers": []},
            {"prompt": "Descriptive open ended question five", "topic": "t", "type": "LONG_TEXT",
             "options": [], "correctAnswers": []},
            {"prompt": "Malformed single choice six", "topic": "t", "type": "MCQ_SINGLE",
             "options": opts, "correctAnswers": ["Alpha", "Beta"]},
            {"prompt": "Another descriptive question seven here", "topic": "t", "type": "LONG_TEXT",
             "options": [], "correctAnswers": []},
        ]},
        {"reviews": [{"accept": True, "reason": ""}, {"accept": True, "reason": ""},
                     {"accept": False, "reason": "off topic"}, {"accept": True, "reason": ""},
                     {"accept": True, "reason": ""}, {"accept": True, "reason": ""},
                     {"accept": True, "reason": ""}]},
    ]))
    req = ComposeRequest.model_validate({
        "skills": ["APIs"], "difficulty": "MEDIUM", "questionCount": 4, "maxRounds": 1,
        "composition": {"mcqSingle": 1, "mcqMultiple": 1, "shortText": 1, "longText": 1}})
    resp = agent.compose(req)
    assert len(resp.questions) == 4
    kinds = {q.type for q in resp.questions}
    assert kinds == {"MCQ_SINGLE", "MCQ_MULTIPLE", "SHORT_TEXT", "LONG_TEXT"}
    mcq = next(q for q in resp.questions if q.type == "MCQ_SINGLE")
    assert mcq.correct_answers == ["Alpha"] and len(mcq.options) == 4
    assert any("malformed" in line for line in resp.trace)
    assert any("quota full" in line for line in resp.trace)
    assert any("off topic" in line for line in resp.trace)


def test_chat_client_parses(monkeypatch):
    import io
    import json as _json

    from app.llm import chat_client

    class FakeResp(io.BytesIO):
        def __enter__(self):
            return self

        def __exit__(self, *a):
            return False

    payload = {"choices": [{"message": {"content": _json.dumps({"ok": 1})}}],
               "usage": {"prompt_tokens": 3, "completion_tokens": 4, "total_tokens": 7}}
    monkeypatch.setattr(chat_client, "urlopen",
                        lambda *a, **k: FakeResp(_json.dumps(payload).encode()))
    parsed, usage = chat_client.ChatClient().complete_json("s", "u", {"type": "object"})
    assert parsed == {"ok": 1}
    assert usage["total_tokens"] == 7


def test_chat_client_gateway_error(monkeypatch):
    from urllib.error import URLError

    from app.llm import chat_client

    def boom(*a, **k):
        raise URLError("down")

    monkeypatch.setattr(chat_client, "urlopen", boom)
    with pytest.raises(ModelGatewayError):
        chat_client.ChatClient().complete_json("s", "u", {"type": "object"})


def test_evaluate_route(monkeypatch):
    resp = EvaluateAnswersResponse(session_id=uuid4(), evaluations=[], usage=None)
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"evaluate": staticmethod(lambda r: resp)})())
    body = {"sessionId": str(uuid4()), "items": [
        {"answerId": str(uuid4()), "questionPrompt": "Q", "maxScore": 5}]}
    assert _post("/internal/v1/answers:evaluate", body) == 200
    assert _post("/internal/v1/answers:evaluate", body, "wrong") == 401


def test_evaluate_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"evaluate": staticmethod(raiser)})())
    body = {"sessionId": str(uuid4()), "items": [
        {"answerId": str(uuid4()), "questionPrompt": "Q", "maxScore": 5}]}
    assert _post("/internal/v1/answers:evaluate", body) == 502


def test_feedback_route(monkeypatch):
    resp = DraftFeedbackResponse(session_id=uuid4(), feedback="f", strengths=[], growth_areas=[],
                                 leakage=LeakageVerdict(safe=True, flags=[]), usage=None)
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"draft_feedback": staticmethod(lambda r: resp)})())
    body = {"sessionId": str(uuid4()), "outcome": "PASSED", "passed": True,
            "items": [{"questionPrompt": "Q", "maxScore": 5, "awardedScore": 4}]}
    assert client.post("/internal/v1/feedback:draft", json=body, headers=HEADERS).status_code == 200


def test_model_answers_agent():
    from app.domain.assessment_models import ModelAnswersRequest
    agent = AssessmentAgent(FakeChat([
        {"answers": [{"content": "**Correct answer:** Heap"},
                     {"content": "**Correct answer:** PUT"}]},
    ]))
    req = ModelAnswersRequest.model_validate({"items": [
        {"questionPrompt": "Which JVM area holds short-lived objects?", "type": "LONG_TEXT"},
        {"questionPrompt": "Idempotent HTTP method?", "type": "MCQ_SINGLE",
         "options": ["PUT", "POST"], "correctAnswers": ["PUT"]}]})
    resp = agent.model_answers(req)
    assert len(resp.answers) == 2
    assert resp.answers[0].content.startswith("**Correct answer:**")


def test_model_answers_route(monkeypatch):
    from app.domain.assessment_models import ModelAnswer, ModelAnswersResponse
    resp = ModelAnswersResponse(answers=[ModelAnswer(content="c")], usage=None)
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"model_answers": staticmethod(lambda r: resp)})())
    body = {"items": [{"questionPrompt": "Q", "type": "LONG_TEXT"}]}
    assert _post("/internal/v1/answers:model", body) == 200
    assert _post("/internal/v1/answers:model", body, "no") == 401


def test_model_answers_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"model_answers": staticmethod(raiser)})())
    body = {"items": [{"questionPrompt": "Q", "type": "LONG_TEXT"}]}
    assert _post("/internal/v1/answers:model", body) == 502


def test_explain_answer_agent():
    from app.domain.assessment_models import ExplainAnswerRequest
    agent = AssessmentAgent(FakeChat([
        {"content": "**What you answered:** X\n**Why it's not fully correct:** ...\n"
                    "**Correct answer:** PUT\n**Example:** ..."},
    ]))
    req = ExplainAnswerRequest.model_validate({
        "questionPrompt": "Idempotent HTTP method?", "type": "MCQ_SINGLE",
        "options": ["PUT", "POST"], "correctAnswers": ["PUT"],
        "candidateAnswer": "POST", "maxScore": 5, "awardedScore": 0})
    resp = agent.explain_answer(req)
    assert resp.content.startswith("**What you answered:**")


def test_explain_answer_agent_handles_blank_answer():
    from app.domain.assessment_models import ExplainAnswerRequest
    agent = AssessmentAgent(FakeChat([{"content": "No answer submitted. Correct answer: ..."}]))
    req = ExplainAnswerRequest.model_validate({
        "questionPrompt": "Explain TCP handshake", "type": "LONG_TEXT",
        "candidateAnswer": "   ", "maxScore": 10})
    resp = agent.explain_answer(req)
    assert "No answer submitted" in resp.content


def test_explain_answer_route(monkeypatch):
    from app.domain.assessment_models import ExplainAnswerResponse
    resp = ExplainAnswerResponse(content="c", usage=None)
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"explain_answer": staticmethod(lambda r: resp)})())
    body = {"questionPrompt": "Q", "type": "LONG_TEXT", "candidateAnswer": "A", "maxScore": 5}
    assert _post("/internal/v1/answers:explain", body) == 200
    assert _post("/internal/v1/answers:explain", body, "no") == 401


def test_explain_answer_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(assessment_routes, "agent",
                        type("A", (), {"explain_answer": staticmethod(raiser)})())
    body = {"questionPrompt": "Q", "type": "LONG_TEXT", "candidateAnswer": "A", "maxScore": 5}
    assert _post("/internal/v1/answers:explain", body) == 502


def test_topic_agent_dedupes():
    from app.application.topic_agent import TopicAgent
    from app.domain.topic_models import SuggestTopicsRequest
    agent = TopicAgent(FakeChat([{"topics": ["Streams", "streams", "Collections", " Generics "]}]))
    resp = agent.suggest(SuggestTopicsRequest.model_validate(
        {"technologies": ["Java"], "difficulty": "HARD"}))
    assert resp.topics == ["Streams", "Collections", "Generics"]


def test_topics_route(monkeypatch):
    from app.domain.topic_models import SuggestTopicsResponse
    resp = SuggestTopicsResponse(topics=["Streams"], usage=None)
    monkeypatch.setattr(topic_routes, "agent",
                        type("A", (), {"suggest": staticmethod(lambda r: resp)})())
    body = {"technologies": ["Java"], "difficulty": "HARD"}
    assert _post("/internal/v1/topics:suggest", body) == 200
    assert _post("/internal/v1/topics:suggest", body, "no") == 401


def test_topics_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(topic_routes, "agent",
                        type("A", (), {"suggest": staticmethod(raiser)})())
    assert _post("/internal/v1/topics:suggest", {"technologies": ["Java"]}) == 502


def test_compose_route(monkeypatch):
    resp = ComposeResponse(questions=[ComposedQuestion(prompt="p", topic="t")],
                           rounds=1, trace=[], usage=None)
    monkeypatch.setattr(composition_routes, "agent",
                        type("A", (), {"compose": staticmethod(lambda r: resp)})())
    body = {"skills": ["Caching"], "difficulty": "HARD", "questionCount": 1}
    assert _post("/internal/v1/questions:compose", body) == 200
    assert _post("/internal/v1/questions:compose", body, "no") == 401


def test_compose_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(composition_routes, "agent",
                        type("A", (), {"compose": staticmethod(raiser)})())
    body = {"skills": ["Caching"], "difficulty": "HARD", "questionCount": 1}
    assert _post("/internal/v1/questions:compose", body) == 502
