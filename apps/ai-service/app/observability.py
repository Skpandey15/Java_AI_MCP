import contextvars
import json
import logging
import re
import sys
import time
from datetime import UTC, datetime
from uuid import uuid4

from fastapi import Request, Response

_request_id: contextvars.ContextVar[str] = contextvars.ContextVar("request_id", default="")
_SAFE_REQUEST_ID = re.compile(r"^[A-Za-z0-9._-]{1,100}$")
_SENSITIVE_VALUES = (
    re.compile(r"(?i)Bearer\s+[A-Za-z0-9._~+/=-]+"),
    re.compile(r"sk-[A-Za-z0-9_-]{8,}"),
)


def redact(value: str) -> str:
    redacted = value
    for pattern in _SENSITIVE_VALUES:
        redacted = pattern.sub("[REDACTED]", redacted)
    return redacted


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, object] = {
            "timestamp": datetime.now(UTC).isoformat(),
            "level": record.levelname,
            "service": "ai-service",
            "logger": record.name,
            "message": redact(record.getMessage()),
        }
        request_id = get_request_id()
        if request_id:
            payload["requestId"] = request_id
        for key in ("event", "interviewId", "generationRequestId", "modelPolicy", "statusCode"):
            value = getattr(record, key, None)
            if value is not None:
                payload[key] = value
        if record.exc_info:
            payload["exceptionType"] = record.exc_info[0].__name__
        return json.dumps(payload, separators=(",", ":"))


def configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)


def get_request_id() -> str:
    return _request_id.get()


async def request_context(request: Request, call_next) -> Response:
    supplied = request.headers.get("X-Request-ID", "")
    request_id = supplied if _SAFE_REQUEST_ID.fullmatch(supplied) else str(uuid4())
    token = _request_id.set(request_id)
    started = time.monotonic()
    try:
        response = await call_next(request)
        response.headers["X-Request-ID"] = request_id
        logging.getLogger("ai_service.http").info(
            "Request completed",
            extra={
                "event": "http.request_completed",
                "statusCode": response.status_code,
            },
        )
        return response
    except Exception:
        logging.getLogger("ai_service.http").exception(
            "Request failed",
            extra={"event": "http.request_failed", "statusCode": 500},
        )
        raise
    finally:
        elapsed_ms = round((time.monotonic() - started) * 1000)
        logging.getLogger("ai_service.http").debug(
            "Request timing", extra={"event": "http.request_timing", "elapsedMs": elapsed_ms}
        )
        _request_id.reset(token)
