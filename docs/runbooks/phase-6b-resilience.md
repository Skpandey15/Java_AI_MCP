# Phase 6B resilience and capacity runbook

## Downstream circuit open

1. Group `resilience_calls_total` and `resilience_retries_total` by dependency.
2. Verify AI-service health, LiteLLM health and provider status.
3. Do not increase retries during an outage; retries amplify downstream load.
4. The circuit probes again after `DOWNSTREAM_CIRCUIT_OPEN_SECONDS`.
5. Reduce generation concurrency or disable the affected optional workflow if failures persist.

## Database pool saturation

1. Compare active, idle and pending Hikari connections with request latency.
2. Inspect PostgreSQL active sessions, slow queries, locks and CPU before increasing the pool.
3. Maintain this capacity invariant:

   `maximum replicas × DATABASE_MAX_POOL_SIZE + operational reserve < max_connections`

4. With the provided production defaults, 12 orchestrator replicas × 20 connections
   require 240 application connections. Production PostgreSQL must therefore be
   externally sized above 260 connections or the HPA maximum/pool size must be reduced.
5. Never solve slow queries solely by increasing connections.

## Outbox dead letter

1. Query `outbox_event` where `status = 'DEAD_LETTER'`; preserve event IDs and errors.
2. Confirm Kafka reachability, authentication, topic authorization and broker ISR health.
3. Fix the broker or configuration before replaying.
4. Replay by an audited operator procedure that changes selected rows to `PENDING`,
   resets attempts and assigns a current `next_attempt_at`. Consumers must deduplicate
   by the Kafka record key, which is the immutable outbox event ID.
5. Do not delete dead-letter rows; retain them for incident evidence.

## Kafka operating contract

- UAT/production require `KAFKA_BOOTSTRAP_SERVERS` in `platform-secrets`.
- Production must use a managed or independently operated multi-broker Kafka cluster.
- Producers require `acks=all` and idempotence.
- Consumers use manual record acknowledgement and `read_committed`.
- Exhausted consumer records are routed to `<source-topic>.DLT`.
- Non-blocking retry topics can reorder events; workflows requiring ordering must use
  a stable aggregate key and blocking retry strategy.

## Autoscaling

- Production minimum replicas remain three to satisfy disruption budgets.
- CPU and memory HPA signals are deliberately conservative.
- Scale-down stabilization is five minutes to avoid flapping.
- The cluster must provide Metrics Server for resource-based HPA.
- Validate database and provider quotas before increasing HPA maxima.
