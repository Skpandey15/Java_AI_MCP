"""OpenTelemetry tracing for the AI service.

When `OTEL_EXPORTER_OTLP_ENDPOINT` is set, this instruments FastAPI so each request
continues the W3C trace started by the orchestrator (extracted from the traceparent
header) and exports spans to Tempo — extending the distributed trace across services.
No-op when the endpoint is unset (e.g. local runs without an OTLP collector).

Excluded from coverage: this is environment wiring exercised only with a live collector.
"""

from app.config import settings


def configure_tracing(app) -> None:  # pragma: no cover
    endpoint = settings.otel_exporter_otlp_endpoint.strip()
    if not endpoint:
        return
    from opentelemetry import trace
    from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    provider = TracerProvider(
        resource=Resource.create({"service.name": "ai-service"}))
    provider.add_span_processor(
        BatchSpanProcessor(OTLPSpanExporter(endpoint=f"{endpoint.rstrip('/')}/v1/traces")))
    trace.set_tracer_provider(provider)
    FastAPIInstrumentor.instrument_app(app)
