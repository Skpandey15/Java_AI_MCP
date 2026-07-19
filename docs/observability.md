# Phase 3A.4 observability operations

## Scope

Phase 3A.4 establishes local structured logging, request correlation, metrics collection and log aggregation.

- Spring Boot emits JSON logs through Logback.
- FastAPI emits JSON logs through the Python logging package.
- `X-Request-ID` propagates from Spring Boot to FastAPI and LiteLLM.
- Spring Boot publishes Micrometer Prometheus metrics at `/actuator/prometheus`.
- The optional Docker Compose observability profile runs Prometheus, Loki, Promtail, Grafana and an OTLP collector receiver.

The OpenTelemetry Collector is ready to receive OTLP traffic; full distributed trace instrumentation is intentionally a later step.

## Environment profiles

| Profile | Intended use | Application logging | Health details | Configuration policy |
|---|---|---|---|---|
| `local` | Developer workstation/Compose | Application DEBUG, framework INFO | Visible locally | Safe local defaults |
| `dev` | Shared development | Application DEBUG, framework INFO | Authorized only | External overrides supported |
| `uat` | Acceptance testing | Application INFO, framework WARN | Hidden | Database, Keycloak, CORS and service credentials required |
| `prod` | Production | Safe business INFO, framework WARN | Hidden | Strict external configuration; no local credential fallback |

Select the same environment for Spring Boot and FastAPI with `APP_ENVIRONMENT`. Docker Compose maps it to `SPRING_PROFILES_ACTIVE` for Java. FastAPI validates `APP_ENVIRONMENT` directly.

```powershell
$env:APP_ENVIRONMENT = "local"
docker compose -f platform/docker/docker-compose.yml --profile observability up -d --build
```

For UAT and production, inject all required values from the deployment secret/configuration system. Do not use `.env` files as the production secret store. The React application uses public build-time `VITE_*` configuration; secrets must never be embedded in its build.

Log levels are adjustable by profile, but the sensitive-data exclusion policy is not. DEBUG never enables payload, token, answer, prompt or feedback logging.

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
