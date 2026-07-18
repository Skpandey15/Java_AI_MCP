from datetime import UTC, datetime

from fastapi import FastAPI
from pydantic import BaseModel


class HealthResponse(BaseModel):
    service: str
    status: str
    timestamp: datetime


app = FastAPI(
    title="Online Interview AI Service",
    version="0.1.0",
    description="Question generation, RAG and evaluation capabilities.",
)


@app.get("/api/v1/health", response_model=HealthResponse, tags=["platform"])
def health() -> HealthResponse:
    return HealthResponse(
        service="ai-service",
        status="UP",
        timestamp=datetime.now(UTC),
    )
