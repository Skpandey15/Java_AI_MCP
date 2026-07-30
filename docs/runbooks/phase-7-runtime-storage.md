# Phase 7 local production runtime

## Runtime responsibilities

- PostgreSQL remains authoritative for business state, identity data, document
  metadata, extracted chunks, embeddings, citations and the transactional outbox.
- Redis provides atomic cross-replica MCP quota counters. If Redis is temporarily
  unavailable, governed MCP calls fail closed so a backend transition cannot
  reset or bypass the authorization quota.
- MinIO stores original knowledge-source objects. PostgreSQL stores the immutable
  object key, byte size and SHA-256 digest; ingestion fetches content through the
  object API.
- Kafka carries committed outbox events with idempotent producer settings. The
  local Kubernetes topology is intentionally one broker; production environments
  must use three or more brokers or a managed Kafka service.

All four stateful services use persistent volume claims. Redis AOF is enabled
with one-second fsync. Kafka, PostgreSQL and MinIO use durable data volumes.

## Local backup

Run from the repository root:

```powershell
.\scripts\backup-local-runtime.ps1
```

The backup contains custom-format dumps for both PostgreSQL databases, a MinIO
knowledge bucket archive and SHA-256 checksums. Copy completed backup directories
away from the cluster host. A PVC is persistence, not a backup.

## Restore drill

Restore is deliberately explicit and temporarily stops Keycloak and the
orchestrator:

```powershell
.\scripts\restore-local-runtime.ps1 `
  -BackupDirectory .\backups\online-interview-YYYYMMDD-HHMMSS `
  -ConfirmRestore
```

Run a restore drill after schema changes and at least quarterly. After restore,
verify Keycloak login, collection ownership, RAG retrieval, citation display and
outbox publication.

## Production differences

The included stateful workloads provide production-like behavior on a local
single-node K3d cluster. A real multi-node production environment must replace
single-instance PostgreSQL, Kafka, Redis and MinIO with replicated operators or
managed services, encrypted transport, off-site backups and tested recovery
objectives.
