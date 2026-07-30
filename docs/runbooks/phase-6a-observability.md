# Phase 6A observability runbook

## Service objectives

| Signal | Objective | Window |
|---|---:|---:|
| Orchestrator availability | 99.9% successful non-5xx requests | 30 days |
| API latency | p95 below 750 ms | 5 minutes |
| MCP execution reliability | At least 95% successful calls | 10 minutes |
| RAG generation | No sustained failed generations | 15 minutes |

Alerts use short windows for fast detection. Release and capacity decisions use
the 30-day objective. Health probes and expected 4xx responses do not consume the
availability error budget.

## Orchestrator unavailable

1. Confirm `up{job="interview-orchestrator"}` and the readiness endpoint.
2. Check recent deployment, pod/container restart and database connectivity logs.
3. Roll back the latest deployment if the failure started immediately after it.
4. If the database is unavailable, stop writes and follow the recovery runbook.
5. Close the incident only after five consecutive healthy scrapes.

## API error budget burn

1. Break down `http_server_requests_seconds_count` by URI, method and status.
2. Use the trace ID from a failing structured log to open the trace in Tempo.
3. Check database saturation, downstream AI errors and authentication failures.
4. Disable the affected optional capability or roll back; do not suppress the alert.
5. Record duration, affected requests and consumed error budget.

## API latency

1. Compare HTTP p95 with database pool, JVM, MCP and RAG duration metrics.
2. Inspect a slow trace and identify the longest server/client span.
3. Check CPU throttling, memory pressure and database connection wait.
4. Reduce optional retrieval limits or AI concurrency if degradation is downstream.
5. Escalate capacity changes through the Phase 6B autoscaling process.

## MCP failures

1. Group `mcp_tool_failures_total` by server, tool and reason.
2. Check approval expiry, quotas and timeout metrics before treating failures as defects.
3. Correlate the context ID with immutable MCP audit events.
4. Disable only the affected registry tool if output scanning or authorization is failing.
5. Never bypass approval, resource binding, scanning or idempotency during recovery.

## RAG generation

1. Group `rag_generation_total` by outcome.
2. Compare retrieval hit, similarity, citation and embedding-client metrics.
3. Validate the selected collection contains READY documents and embeddings.
4. Degrade to direct generation only when the interview configuration explicitly permits it.
5. Preserve retrieval evaluation evidence for the incident review.

## Trace and log safety

Trace attributes, metric tags and operational logs must not contain prompts,
candidate answers, correct answers, feedback, tokens, authorization headers,
provider keys or database credentials. Use only request, trace, interview,
session, execution and context identifiers.
