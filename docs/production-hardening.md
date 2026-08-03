# Production hardening guide

The local k3d setup is intentionally single-node and self-contained. This document records
the gaps that must be closed for a real production deployment and the recommended approach for
each. These are environment/overlay concerns, not application-code changes.

## 1. Secrets management

**Now:** credentials live in Kubernetes `Secret`s (`platform-secrets`) — base64, *not* encrypted
at rest unless etcd encryption is enabled. Affected values: `DATABASE_PASSWORD`,
`OPENAI_API_KEY`, `MAIL_PASSWORD`, `AI_SERVICE_TOKEN`, `MCP_AUTHORIZATION_SECRET`,
`REDIS_PASSWORD`, `MINIO_*`.

**Production:**
- Enable **etcd encryption-at-rest** (`EncryptionConfiguration`) on the cluster.
- Source secrets from a manager instead of committing them: **External Secrets Operator**
  (AWS Secrets Manager / Azure Key Vault / GCP Secret Manager) or **Sealed Secrets** / **Vault**.
- Rotate the LiteLLM key, service tokens, and DB passwords on a schedule.
- Never bake secrets into images or Git. (`scripts/.lan-certs/` and secret values are gitignored.)

## 2. High availability & data durability

**Now:** Postgres, Kafka, Redis, and MinIO run as single-replica StatefulSets on one node.

**Production:**
- **Postgres:** managed (RDS / Cloud SQL / Azure DB) or **CloudNativePG** / Patroni with
  streaming replication + automated backups (PITR).
- **Kafka:** ≥3 brokers, `replication.factor=3`, `min.insync.replicas=2` (KRaft quorum ≥3).
- **Redis:** Sentinel or Cluster mode.
- **Object storage:** managed S3/GCS instead of single-node MinIO.
- **Stateless services** (orchestrator, ai-service, web-ui): ≥2 replicas with
  **PodDisruptionBudgets**, topology-spread/anti-affinity, and an HPA on CPU/latency.

## 3. Multi-tenancy

**Now:** the app is tenant-aware end to end — every row carries `tenant_id`, queries are
tenant-scoped, and `TenantClaim` requires a valid `tenant_id` JWT claim. But the Keycloak realm
uses a **hardcoded claim mapper** that stamps `tenant_id=default` on everyone, so effectively a
single tenant.

**Production:**
- Emit `tenant_id` from a **per-user Keycloak attribute** (or a realm/organization per tenant)
  instead of the hardcoded mapper.
- Add a tenant-onboarding flow (create tenant → provision admin → scoped invitations).
- The data layer already enforces isolation, so this is primarily an identity-provider change.

## 4. Observability at scale

**Now:** Grafana + Loki + Tempo run in-cluster with **persistent volumes** (survive restarts),
72h retention. Both services export traces; logs are searchable by `requestId`, traces by
`traceId`.

**Production:**
- Back Loki and Tempo with **object storage** (S3/GCS) rather than local PVs, with lifecycle-based
  retention.
- Add **Alertmanager** rules on the RED/USE and golden signals; wire SLO burn-rate alerts.
- Consider a managed backend (Grafana Cloud / Datadog) if you prefer not to self-host.

## 5. Delivery & safety nets already in place

- Flyway migrations, kustomize overlays per environment, CI with a coverage gate.
- NetworkPolicies (default-deny + allow-lists), PodSecurity (baseline enforce).
- Transactional outbox for event integrity; resilience executor (retry + circuit breaker);
  rate limiting; correlation IDs; graceful shutdown.
- Scheduled finalizer that auto-submits expired interview sessions so none dead-end.
