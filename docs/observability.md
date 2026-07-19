# Phase 3A.4 observability operations

## Scope

Phase 3A.4 establishes local structured logging, request correlation, metrics collection and log aggregation.

- Spring Boot emits JSON logs through Logback.
- FastAPI emits JSON logs through the Python logging package.
- `X-Request-ID` propagates from Spring Boot to FastAPI and LiteLLM.
- Spring Boot publishes Micrometer Prometheus metrics at `/actuator/prometheus`.
- The optional Docker Compose observability profile runs Prometheus, Loki, Promtail, Grafana and an OTLP collector receiver.

The OpenTelemetry Collector is ready to receive OTLP traffic; full distributed trace instrumentation is intentionally a later step.

## Data-safety policy

Application logs must never contain:

- Authorization headers, access/refresh tokens or service tokens
- OpenAI or LiteLLM keys
- Candidate answer content
- Correct answers, prompts or hidden rubrics
- Reviewer feedback
- Passwords or database credentials

Lifecycle logs contain only event names, internal UUIDs, non-sensitive state and model-policy aliases. Java and Python formatters mask bearer tokens and provider-key patterns as a defense in depth. PostgreSQL audit records remain separate from operational logs.

## Start locally

```powershell
docker compose -f platform/docker/docker-compose.yml --profile observability up -d --build
```

Endpoints:

| Service | URL |
|---|---|
| Application | http://localhost:3000 |
| Orchestrator metrics | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 |
| Loki | http://localhost:3100 |
| OTLP gRPC/HTTP | localhost:4317 / localhost:4318 |

Grafana local credentials default to `admin/admin`. Override `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` outside local development.

## Useful Loki queries

```logql
{service="interview-orchestrator"} | json
{service="ai-service"} | json
{service="interview-orchestrator"} | json | requestId="<request-id>"
```

## Verification

1. Open the application and perform an interview operation.
2. Copy the `X-Request-ID` response header from the browser network panel.
3. Search that ID in Grafana Explore using the Loki data source.
4. Confirm the same ID appears in Spring Boot and downstream AI-service logs for AI generation.
5. Confirm prompts, answers, tokens and feedback are absent.
