from fastapi import APIRouter, FastAPI
from fastapi.testclient import TestClient
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor


def _instrumented_app() -> FastAPI:
    router = APIRouter()

    @router.get("/included")
    def included_route() -> dict[str, str]:
        return {"status": "ok"}

    app = FastAPI()
    app.include_router(router)
    FastAPIInstrumentor.instrument_app(app)
    return app


def test_included_router_is_compatible_with_fastapi_instrumentation() -> None:
    with TestClient(_instrumented_app()) as client:
        response = client.get("/included")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_included_router_preflight_does_not_crash_instrumentation() -> None:
    with TestClient(_instrumented_app()) as client:
        response = client.options(
            "/included",
            headers={
                "Origin": "http://localhost:3000",
                "Access-Control-Request-Method": "GET",
            },
        )

    assert response.status_code < 500
