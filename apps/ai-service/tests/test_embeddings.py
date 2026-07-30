import json
from urllib.error import URLError

from fastapi.testclient import TestClient

from app.api import embedding_routes
from app.llm.embedding_client import LiteLLMEmbeddingClient
from app.llm.litellm_client import ModelGatewayError
from app.main import app


def test_embedding_route_auth_and_success(monkeypatch):
    class FakeClient:
        def embed(self, texts):
            return [[0.1, 0.2] for _ in texts], "embedding-policy"

    monkeypatch.setattr(embedding_routes, "client", FakeClient())
    client = TestClient(app)
    assert client.post("/internal/v1/embeddings:create", json={"texts": ["one"]}).status_code == 401

    response = client.post(
        "/internal/v1/embeddings:create",
        headers={"X-Service-Token": "local-ai-service-token"},
        json={"texts": ["one", "two"]},
    )
    assert response.status_code == 200
    assert response.json() == {
        "modelPolicy": "embedding-policy",
        "embeddings": [[0.1, 0.2], [0.1, 0.2]],
    }


def test_embedding_route_rejects_gateway_and_count_mismatch(monkeypatch):
    class FailingClient:
        def embed(self, texts):
            raise ModelGatewayError("failed")

    client = TestClient(app)
    monkeypatch.setattr(embedding_routes, "client", FailingClient())
    failed = client.post(
        "/internal/v1/embeddings:create",
        headers={"X-Service-Token": "local-ai-service-token"},
        json={"texts": ["one"]},
    )
    assert failed.status_code == 502

    monkeypatch.setattr(
        embedding_routes,
        "client",
        type("MismatchClient", (), {"embed": lambda self, texts: ([], "model")})(),
    )
    mismatch = client.post(
        "/internal/v1/embeddings:create",
        headers={"X-Service-Token": "local-ai-service-token"},
        json={"texts": ["one"]},
    )
    assert mismatch.status_code == 502


def test_litellm_embedding_client_orders_results(monkeypatch):
    payload = {
        "model": "resolved-model",
        "data": [
            {"index": 1, "embedding": [2.0]},
            {"index": 0, "embedding": [1.0]},
        ],
    }

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, *args):
            return None

        def read(self):
            return json.dumps(payload).encode()

    monkeypatch.setattr("app.llm.embedding_client.urlopen", lambda request, timeout: Response())
    embeddings, model = LiteLLMEmbeddingClient().embed(["one", "two"])
    assert embeddings == [[1.0], [2.0]]
    assert model == "resolved-model"

    def fail(request, timeout):
        raise URLError("offline")

    monkeypatch.setattr("app.llm.embedding_client.urlopen", fail)
    try:
        LiteLLMEmbeddingClient().embed(["one"])
        raise AssertionError("expected ModelGatewayError")
    except ModelGatewayError:
        pass
