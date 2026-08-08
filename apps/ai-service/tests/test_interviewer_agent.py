from uuid import uuid4

from fastapi.testclient import TestClient

from app.api import interview_routes
from app.application.interviewer_agent import InterviewerAgent
from app.config import settings
from app.domain.interview_turn_models import (
    CandidateQuestion,
    KnowledgeSnippet,
    NextTurnRequest,
    NextTurnResponse,
    SkillMastery,
    TranscriptEntry,
    TurnBudget,
)
from app.llm.litellm_client import ModelGatewayError
from app.main import app

USAGE = {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2,
         "estimated_cost_usd": 0.0, "latency_ms": 1}
client = TestClient(app)


class FakeChat:
    def __init__(self, response):
        self.response = response
        self.calls = 0

    def complete_json(self, system, user, schema):
        self.calls += 1
        return self.response, USAGE


def _req(*, turns_remaining=5, transcript=None, candidates=None, snippets=None,
         mastery=None, skills=None):
    return NextTurnRequest(
        session_id=uuid4(), interview_id=uuid4(),
        skills=["Concurrency"] if skills is None else skills,
        target_difficulty="HARD", passing_percentage=70,
        transcript=transcript or [], skill_mastery=mastery or [],
        candidate_questions=candidates or [], knowledge_snippets=snippets or [],
        budget=TurnBudget(turns_remaining=turns_remaining, token_budget=1000))


def _llm(action="ASK", question=None, evaluation=None, final=None, rationale="r"):
    return {
        "action": action, "rationale": rationale,
        "last_answer_evaluation": evaluation
        or {"skill": "Concurrency", "score": 80, "confidence": 70, "rationale": "ok"},
        "question": question or {"source": "BANK", "question_id": "", "skill": "",
                                 "difficulty": "", "prompt": "", "citation_chunk_ids": []},
        "final_assessment": final or {"summary": "done", "per_skill": []},
    }


def test_asks_offered_bank_question():
    q = CandidateQuestion(question_id=uuid4(), skill="Concurrency",
                          difficulty="HARD", prompt="Explain the JMM.")
    agent = InterviewerAgent(FakeChat(_llm(question={
        "source": "BANK", "question_id": str(q.question_id), "skill": "x",
        "difficulty": "x", "prompt": "model-made-up", "citation_chunk_ids": []})))
    resp = agent.next_turn(_req(candidates=[q]))
    assert resp.action == "ASK"
    assert resp.question.source == "BANK"
    assert resp.question.question_id == q.question_id
    assert resp.question.prompt == "Explain the JMM."  # authoritative, not the model's text


def test_generated_question_must_be_grounded():
    snippet = KnowledgeSnippet(chunk_id=uuid4(), file_name="gc.md", content="G1 tuning")
    agent = InterviewerAgent(FakeChat(_llm(question={
        "source": "GENERATED", "question_id": "", "skill": "GC", "difficulty": "HARD",
        "prompt": "How would you tune G1?", "citation_chunk_ids": [str(snippet.chunk_id)]})))
    resp = agent.next_turn(_req(snippets=[snippet], mastery=[
        SkillMastery(skill="Concurrency", confidence=50, evidence=1)], transcript=[
        TranscriptEntry(skill="Concurrency", question="Q1", answer="A1")]))
    assert resp.action == "ASK"
    assert resp.question.source == "GENERATED"
    assert resp.question.citation_chunk_ids == [snippet.chunk_id]
    assert resp.last_answer_evaluation.score == 80


def test_ungrounded_generated_falls_back_to_bank():
    q = CandidateQuestion(question_id=uuid4(), skill="Concurrency",
                          difficulty="HARD", prompt="Bank Q")
    agent = InterviewerAgent(FakeChat(_llm(question={
        "source": "GENERATED", "question_id": "", "skill": "GC", "difficulty": "HARD",
        "prompt": "ungrounded", "citation_chunk_ids": []})))
    resp = agent.next_turn(_req(candidates=[q]))
    assert resp.action == "ASK"
    assert resp.question.source == "BANK"
    assert resp.question.question_id == q.question_id


def test_invalid_bank_id_falls_back_to_first_offered():
    q = CandidateQuestion(question_id=uuid4(), skill="Concurrency",
                          difficulty="HARD", prompt="Bank Q")
    agent = InterviewerAgent(FakeChat(_llm(question={
        "source": "BANK", "question_id": str(uuid4()), "skill": "", "difficulty": "",
        "prompt": "", "citation_chunk_ids": []})))
    resp = agent.next_turn(_req(candidates=[q]))
    assert resp.question.question_id == q.question_id


def test_concludes_when_no_valid_question_and_nothing_offered():
    agent = InterviewerAgent(FakeChat(_llm(question={
        "source": "BANK", "question_id": str(uuid4()), "skill": "", "difficulty": "",
        "prompt": "", "citation_chunk_ids": []})))
    resp = agent.next_turn(_req(candidates=[]))
    assert resp.action == "CONCLUDE"
    assert resp.final_assessment is not None


def test_budget_exhausted_forces_conclude_even_if_model_asks():
    q = CandidateQuestion(question_id=uuid4(), skill="Concurrency",
                          difficulty="HARD", prompt="Bank Q")
    agent = InterviewerAgent(FakeChat(_llm(action="ASK", question={
        "source": "BANK", "question_id": str(q.question_id), "skill": "", "difficulty": "",
        "prompt": "", "citation_chunk_ids": []})))
    resp = agent.next_turn(_req(turns_remaining=0, candidates=[q]))
    assert resp.action == "CONCLUDE"


def test_conclude_maps_final_assessment_and_evaluation_clamps():
    agent = InterviewerAgent(FakeChat(_llm(
        action="CONCLUDE",
        evaluation={"skill": "", "score": 999, "confidence": "bad", "rationale": "r"},
        final={"summary": "great", "per_skill": [
            {"skill": "Concurrency", "confidence": 90, "evidence": 3},
            {"skill": "", "confidence": 1, "evidence": 1}]})))
    resp = agent.next_turn(_req(transcript=[
        TranscriptEntry(skill="Concurrency", question="Q1", answer="A1")]))
    assert resp.action == "CONCLUDE"
    assert resp.final_assessment.summary == "great"
    assert len(resp.final_assessment.per_skill) == 1  # blank-skill row dropped
    assert resp.last_answer_evaluation.score == 100  # clamped
    assert resp.last_answer_evaluation.confidence == 0  # non-int -> default


def test_no_evaluation_before_first_answer_and_final_fallback():
    agent = InterviewerAgent(FakeChat(_llm(action="CONCLUDE", final="not-a-dict")))
    resp = agent.next_turn(_req(transcript=[], mastery=[], skills=[]))
    assert resp.last_answer_evaluation is None
    assert resp.final_assessment.per_skill == []


def test_route_authorizes_and_maps(monkeypatch):
    resp = NextTurnResponse(action="CONCLUDE", rationale="done")
    monkeypatch.setattr(interview_routes, "agent",
                        type("A", (), {"next_turn": staticmethod(lambda r: resp)})())
    body = {"sessionId": str(uuid4()), "interviewId": str(uuid4()),
            "budget": {"turnsRemaining": 3, "tokenBudget": 1000}}
    ok = client.post("/internal/v1/interview:next-turn", json=body,
                     headers={"X-Service-Token": settings.ai_service_token})
    assert ok.status_code == 200
    assert ok.json()["action"] == "CONCLUDE"
    bad = client.post("/internal/v1/interview:next-turn", json=body,
                      headers={"X-Service-Token": "wrong"})
    assert bad.status_code == 401


def test_route_gateway_error(monkeypatch):
    def raiser(r):
        raise ModelGatewayError("x")
    monkeypatch.setattr(interview_routes, "agent",
                        type("A", (), {"next_turn": staticmethod(raiser)})())
    body = {"sessionId": str(uuid4()), "interviewId": str(uuid4()),
            "budget": {"turnsRemaining": 3, "tokenBudget": 1000}}
    resp = client.post("/internal/v1/interview:next-turn", json=body,
                       headers={"X-Service-Token": settings.ai_service_token})
    assert resp.status_code == 502
