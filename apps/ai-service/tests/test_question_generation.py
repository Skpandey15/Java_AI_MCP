from uuid import uuid4

from fastapi.testclient import TestClient

from app.api import question_routes
from app.config import settings
from app.domain.question_models import (
    GenerateQuestionsResponse,
    GeneratedQuestion,
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
            questions=[\n                GeneratedQuestion(\n                    order=1, prompt="Explain Java virtual threads.", max_score=10\n                )\n            ],
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
        },
    )
    assert response.status_code == 200
    assert response.json()["questions"][0]["order"] == 1
