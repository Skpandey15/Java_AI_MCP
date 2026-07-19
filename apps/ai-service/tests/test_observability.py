import json
import logging

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import app
from app.observability import JsonFormatter

client = TestClient(app)


def test_preserves_safe_request_id() -> None:
    response = client.get("/api/v1/health", headers={"X-Request-ID": "test-request-123"})
    assert response.status_code == 200
    assert response.headers["X-Request-ID"] == "test-request-123"


def test_replaces_unsafe_request_id() -> None:
    response = client.get("/api/v1/health", headers={"X-Request-ID": "unsafe request id"})
    assert response.status_code == 200
    assert response.headers["X-Request-ID"] != "unsafe request id"


def test_json_formatter_redacts_bearer_tokens_and_provider_keys() -> None:
    record = logging.LogRecord(
        name="test",
        level=logging.ERROR,
        pathname=__file__,
        lineno=1,
        msg="Authorization Bearer secret.token-value key sk-sensitive12345",
        args=(),
        exc_info=None,
    )
    payload = json.loads(JsonFormatter().format(record))
    assert "secret.token-value" not in payload["message"]
    assert "sk-sensitive12345" not in payload["message"]
    assert payload["message"].count("[REDACTED]") == 2


def test_prod_profile_rejects_local_credentials() -> None:
    try:
        Settings(app_environment="prod")
    except ValueError as exc:
        assert "must be supplied outside local development" in str(exc)
    else:
        raise AssertionError("Production profile accepted local development credentials")
