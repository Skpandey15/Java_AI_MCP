from uuid import uuid4

from fastapi.testclient import TestClient

from app.api import question_routes
from app.application.question_generator import QuestionGenerator
from app.config import settings
from app.domain.question_models import (
    GeneratedQuestion,
    GenerateQuestionsRequest,
    GenerateQuestionsResponse,
    QuestionComposition,
)
from app.main import app

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
