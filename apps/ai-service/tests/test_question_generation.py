import json
from urllib.error import URLError
from uuid import uuid4

from fastapi.testclient import TestClient

from app.api import question_routes
from app.application.question_generator import QuestionGenerator
from app.config import settings
from app.domain.question_models import (
    GeneratedQuestion,
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
    GenerationUsage,
    GroundingChunk,
    QuestionComposition,
)
from app.llm.litellm_client import LiteLLMClient, ModelGatewayError
from app.main import app
from app.quality_metrics import record_failure, record_success

client = TestClient(app)


def test_rejects_missing_service_token() -> None:
    response = client.post(
        "/internal/v1/questions:generate",
        json={
            "request_id": str(uuid4()),
            "interview_id": str(uuid4()),
            "skills": ["Java"],
            "difficulty": "HARD",
            "question_count": 1,
            "question_composition": {
                "mcq_single": 0,
                "mcq_multiple": 0,
                "short_text": 0,
                "long_text": 1,
            },
        },
    )
    assert response.status_code == 401


def test_returns_schema_valid_questions(monkeypatch) -> None:
    request_id = uuid4()
    interview_id = uuid4()

    def fake_generate(request):
        return GenerateQuestionsResponse(
            request_id=request.request_id,
            interview_id=request.interview_id,
            model_policy="interview-question-model",
            prompt_version="direct-question-v1",
            questions=[
                GeneratedQuestion(
                    order=1,
                    prompt="Explain Java virtual threads.",
                    max_score=10,
                    type="LONG_TEXT",
                    options=[],
                    correct_answers=[],
                )
            ],
        )

    monkeypatch.setattr(question_routes.generator, "generate", fake_generate)
    response = client.post(
        "/internal/v1/questions:generate",
        headers={"X-Service-Token": settings.ai_service_token},
        json={
            "request_id": str(request_id),
            "interview_id": str(interview_id),
            "skills": ["Java"],
            "difficulty": "HARD",
            "question_count": 1,
            "question_composition": {
                "mcq_single": 0,
                "mcq_multiple": 0,
                "short_text": 0,
                "long_text": 1,
            },
        },
    )
    assert response.status_code == 200
    assert response.json()["questions"][0]["order"] == 1


def test_route_records_usage_and_hides_generation_failures(monkeypatch) -> None:
    def generated(request):
        return GenerateQuestionsResponse(
            request_id=request.request_id,
            interview_id=request.interview_id,
            model_policy="policy",
            prompt_version="v1",
            questions=[GeneratedQuestion(
                order=1,
                prompt="Explain a reliable distributed system design.",
                max_score=10,
                type="LONG_TEXT",
            )],
            usage=GenerationUsage(
                prompt_tokens=20,
                completion_tokens=10,
                total_tokens=30,
                estimated_cost_usd=0.001,
                latency_ms=200,
            ),
        )

    payload = {
        "request_id": str(uuid4()),
        "interview_id": str(uuid4()),
        "skills": ["Java"],
        "difficulty": "MEDIUM",
        "question_count": 1,
        "question_composition": {
            "mcq_single": 0, "mcq_multiple": 0, "short_text": 0, "long_text": 1,
        },
    }
    monkeypatch.setattr(question_routes.generator, "generate", generated)
    assert client.post(
        "/internal/v1/questions:generate",
        headers={"X-Service-Token": settings.ai_service_token},
        json=payload,
    ).status_code == 200

    monkeypatch.setattr(
        question_routes.generator,
        "generate",
        lambda request: (_ for _ in ()).throw(ValueError("unsafe model output")),
    )
    failed = client.post(
        "/internal/v1/questions:generate",
        headers={"X-Service-Token": settings.ai_service_token},
        json=payload,
    )
    assert failed.status_code == 502
    assert failed.json()["detail"] == "Question generation failed"


def test_generator_enforces_mixed_question_composition() -> None:
    class FakeClient:
        def generate(self, prompt: str, question_count: int):
            assert "1 MCQ_SINGLE" in prompt
            assert "1 SHORT_TEXT" in prompt
            return [
                {
                    "order": 1,
                    "prompt": "Which Java collection guarantees unique values?",
                    "max_score": 10,
                    "type": "MCQ_SINGLE",
                    "options": ["List", "Set", "Queue", "Deque"],
                    "correct_answers": ["Set"],
                },
                {
                    "order": 2,
                    "prompt": "Name one benefit of constructor injection.",
                    "max_score": 10,
                    "type": "SHORT_TEXT",
                    "options": [],
                    "correct_answers": [],
                },
            ], "interview-question-model"

    request = GenerateQuestionsRequest(
        request_id=uuid4(),
        interview_id=uuid4(),
        skills=["Java", "Spring"],
        difficulty="MEDIUM",
        question_count=2,
        question_composition=QuestionComposition(
            mcq_single=1,
            mcq_multiple=0,
            short_text=1,
            long_text=0,
        ),
    )

    response = QuestionGenerator(client=FakeClient()).generate(request)

    assert [question.type for question in response.questions] == ["MCQ_SINGLE", "SHORT_TEXT"]


def test_generator_requires_supplied_citation_ids_for_grounded_questions() -> None:
    citation_id = uuid4()

    class FakeClient:
        def generate(self, prompt: str, question_count: int):
            assert str(citation_id) in prompt
            assert "<reference_material>" in prompt
            return [{
                "order": 1,
                "prompt": "What is the stated Java record property?",
                "max_score": 10,
                "type": "LONG_TEXT",
                "options": [],
                "correct_answers": [],
                "citation_ids": [str(citation_id)],
            }], "model"

    request = GenerateQuestionsRequest(
        request_id=uuid4(),
        interview_id=uuid4(),
        skills=["Java"],
        difficulty="MEDIUM",
        question_count=1,
        question_composition=QuestionComposition(
            mcq_single=0, mcq_multiple=0, short_text=0, long_text=1
        ),
        grounding_context=[GroundingChunk(
            citation_id=citation_id,
            source_name="java.md",
            content="A record is an immutable data carrier.",
        )],
    )

    response = QuestionGenerator(client=FakeClient()).generate(request)

    assert response.questions[0].citation_ids == [citation_id]


def test_generator_rejects_unknown_grounding_citation() -> None:
    class FakeClient:
        def generate(self, prompt: str, question_count: int):
            return [{
                "order": 1,
                "prompt": "What does this Java reference explain?",
                "max_score": 10,
                "type": "LONG_TEXT",
                "options": [],
                "correct_answers": [],
                "citation_ids": [str(uuid4())],
            }], "model"

    request = GenerateQuestionsRequest(
        request_id=uuid4(),
        interview_id=uuid4(),
        skills=["Java"],
        difficulty="MEDIUM",
        question_count=1,
        question_composition=QuestionComposition(
            mcq_single=0, mcq_multiple=0, short_text=0, long_text=1
        ),
        grounding_context=[GroundingChunk(
            citation_id=uuid4(), source_name="java.md", content="Java reference."
        )],
    )

    try:
        QuestionGenerator(client=FakeClient()).generate(request)
        raise AssertionError("Expected unknown citation rejection")
    except ValueError as exc:
        assert "unknown citation" in str(exc)


def test_litellm_client_captures_usage_cost_and_latency(monkeypatch) -> None:
    payload = {
        "model": "resolved-model",
        "choices": [{"message": {"content": json.dumps({"questions": []})}}],
        "usage": {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150},
    }

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return None

        def read(self):
            return json.dumps(payload).encode()

    monkeypatch.setattr("app.llm.litellm_client.urlopen", lambda request, timeout: Response())
    questions, model, usage = LiteLLMClient().generate("prompt", 1)
    assert questions == []
    assert model == "resolved-model"
    assert usage["total_tokens"] == 150
    assert usage["estimated_cost_usd"] > 0
    assert usage["latency_ms"] >= 0

    monkeypatch.setattr(
        "app.llm.litellm_client.urlopen",
        lambda request, timeout: (_ for _ in ()).throw(URLError("offline")),
    )
    try:
        LiteLLMClient().generate("prompt", 1)
        raise AssertionError("expected gateway error")
    except ModelGatewayError:
        pass


def test_quality_metrics_and_metrics_endpoint() -> None:
    record_success("test-policy", 10, 5, 0.001, 250)
    record_failure()

    response = client.get("/metrics")

    assert response.status_code == 200
    assert "ai_question_generations_total" in response.text
    assert "ai_question_generation_tokens_total" in response.text
