from datetime import UTC, datetime

from fastapi import FastAPI
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import BaseModel

from app.api.assessment_routes import router as assessment_router
from app.api.composition_routes import router as composition_router
from app.api.embedding_routes import router as embedding_router
from app.api.question_routes import router as question_router
from app.api.topic_routes import router as topic_router
from app.observability import configure_logging, request_context
from app.tracing import configure_tracing

configure_logging()


class HealthResponse(BaseModel):
    service: str
    status: str
    timestamp: datetime


app = FastAPI(
    title="Online Interview AI Service",
    version="0.1.0",
    description="Question generation, RAG and evaluation capabilities.",
)
app.middleware("http")(request_context)
configure_tracing(app)
app.include_router(question_router)
app.include_router(embedding_router)
app.include_router(assessment_router)
app.include_router(composition_router)
app.include_router(topic_router)


@app.get("/api/v1/health", response_model=HealthResponse, tags=["platform"])
def health() -> HealthResponse:
    return HealthResponse(
        service="ai-service",
        status="UP",
        timestamp=datetime.now(UTC),
    )


@app.get("/metrics", include_in_schema=False)
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
