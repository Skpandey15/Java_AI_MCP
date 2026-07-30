# Online Interview implementation plan

## Phase 1 — Foundation

Status: implemented as initial scaffolding.

Deliverables:

- React and TypeScript web application built with Vite and npm
- Java 21 Spring Boot orchestrator built with the Gradle Wrapper and Groovy DSL
- Python 3.12 FastAPI AI service managed and locked with uv
- Versioned contract directories
- Multi-stage Dockerfiles and Docker Compose environment
- Continuous integration for all application builds and tests

Acceptance criteria:

- UI production build and component tests pass
- Spring Boot package and tests pass on Java 21
- Python lint and unit tests pass on Python 3.12
- All three images build
- Health endpoints return successful responses
- No Keycloak, Kafka, LiteLLM, MCP or Kubernetes runtime dependency is introduced prematurely

## Phase 2 — Identity and interview workflow

- Keycloak OIDC login and candidate registration (implemented in Phase 2A)
- Candidate and interviewer role mapping (foundation implemented in Phase 2A)
- PostgreSQL/Flyway user-profile persistence (implemented in Phase 2A)
- Interview definitions, publishing, assignments and scheduling (implemented in Phase 2B)
- Candidate upcoming-interview API backed by PostgreSQL (implemented in Phase 2B)
- Session state machine, server timer and optimistic answer autosave (implemented in Phase 2C)
- Manual question sets for deterministic end-to-end testing (implemented in Phase 2C)

## Phase 3 — AI generation and evaluation

- LiteLLM gateway with OpenAI key isolation (implemented for Docker in Phase 3A; Kubernetes secret follows)
- Direct OpenAI-backed question generation through the Python service (implemented in Phase 3A)
- Structured response schemas and prompt/model versioning (implemented in Phase 3A)
- Kafka, transactional outbox and asynchronous answer evaluation

### Phase 3A.1 — Typed questions and interview UX

- MCQ single-select, MCQ multi-select, short-text and long-text question models
- Interviewer preview, edit, delete and publish-completeness validation
- Candidate-specific controls with server-validated autosave
- Candidate selection by profile name/email rather than UUID entry
- Submitted-session timer and error-message cleanup
- Coding questions deferred to a sandboxed execution phase

### Phase 3A.2 — Synchronous scoring and review

- Deterministic scoring for single- and multiple-choice answers
- Interviewer submission queue, per-answer text scoring and feedback
- Final review status, total score and candidate result release
- AI evaluation remains asynchronous Phase 3B work

### Phase 3A.3 — Review production hardening

- Configurable passing percentage with immutable `PASSED` / `NOT_SELECTED` outcome snapshots
- Candidate-safe result release only after interviewer finalization
- Immutable answer-score and review-finalization audit events
- Database constraints for review/result consistency and non-negative scores
- Optimistic-lock conflict responses and request correlation IDs
- RFC 9457-style validation/conflict responses
- Paginated review queue with batched candidate/question lookups
- Duplicate-action guards, scoring progress and section-local UI feedback
- Outcome boundary tests plus container startup/Flyway verification in CI

### Phase 3A.4 — Observability foundation

- Structured JSON logs for Spring Boot and FastAPI
- Correlation-ID propagation through Spring Boot, FastAPI and LiteLLM
- Safe domain lifecycle events with sensitive-content exclusion and masking
- Micrometer/Prometheus HTTP and JVM metrics
- Optional Loki, Promtail, Grafana, Prometheus and OTLP Collector Docker profile
- Correlation and redaction regression tests
- Full distributed trace instrumentation follows after the OTLP receiver foundation

## Phase 4 — Delivery automation

### Phase 4A — Trusted application artifacts

- GitHub Actions publishes the three application images to GitHub Container Registry after successful main-branch CI
- Images receive immutable `sha-<commit>` tags plus a convenience `main` tag
- High and critical known vulnerabilities block publishing
- SPDX SBOMs are retained as workflow artifacts
- Images are signed keylessly with GitHub OIDC and receive build-provenance attestations
- Registry credentials and application secrets are never embedded in images

### Phase 4B — Kubernetes packaging

Status: implemented with Kustomize and validated on the local k3d/Rancher Desktop-compatible runtime.

- Kustomize bases with local, dev, UAT and production overlays
- Kubernetes Secrets/External Secrets integration and non-secret ConfigMaps
- Managed TLS ingress, ExternalSecret rotation/retention policy and manifest
  security validation for development, UAT and production
- Health probes, resource requests/limits and migration-job controls
- Rancher Desktop validation before shared-cluster deployment

### Phase 4C — GitOps deployment

Status: implemented declaratively; shared Argo CD installation, repository credentials and GitHub Environment approvals remain operator acceptance gates.

- Argo CD applications for dev, UAT and production
- Automatic dev reconciliation and approval-based UAT/production promotion
- Immutable image-digest promotion, health gates and documented rollback

## Phase 5 — RAG and MCP

Phase 5A.1 through 5A.3 implemented: knowledge collections, document ingestion,
deterministic chunking, pgvector embeddings, ownership-filtered retrieval and
citation-backed question generation with persisted source evidence.
Phase 5A.4 adds thresholded retrieval, repeatable precision/recall/MRR evaluation,
citation-quality indicators and Prometheus RAG observability.

- Document ingestion, pgvector retrieval, reranking and citations
- Internal MCP registry and least-privilege tool policies
- Interview, question-bank, knowledge and result MCP servers
- Approved external MCP connection framework

Phase 5B.1 implemented the database-backed internal MCP registry, strict tool
schemas, streamable-HTTP metadata and interviewer-safe registry discovery.
Phase 5B.2 adds exact workflow/actor/tool allow lists and signed, short-lived,
resource-bound authorization contexts. Phase 5B.3 adds stable MCP 2025-11-25
Streamable HTTP lifecycle, JSON/SSE transport, schema validation and a governed
host dispatcher. Phase 5B.4 adds resource-owner approval, persisted idempotency,
atomic context quotas and cancellable execution deadlines.
Phase 5B.5 and 5B.6 complete Phase 5 with sensitive-result rejection,
append-only tool audit events, MCP metrics, interviewer-owned audit discovery,
real interview/question/knowledge/result handlers and adversarial resource-binding
tests. AI scores are persisted only as pending human-review recommendations.

## Phase 6 — Production hardening

- Observability, autoscaling, backup and recovery
- Tenant isolation and security testing
- AI quality, groundedness, cost and latency evaluations

Phase 6A implemented measurable service objectives, Prometheus recording and
alert rules, a provisioned Grafana platform dashboard, OTLP trace export to
Tempo, trace-to-log navigation and incident response runbooks.
Phase 6B adds bounded downstream retry/circuit breaking, explicit Hikari and
PostgreSQL capacity controls, production HPA behavior, a transactional Kafka
outbox, idempotent producer settings, consumer DLT policy and resilience alerts.
Phase 6C adds Keycloak-propagated tenant identity, tenant-scoped candidate
registration/discovery/assignment, stateless API security headers, Kubernetes
Pod Security posture, default-deny network policies and automated dependency,
IaC and secret scanning.
Phase 6D completes Phase 6 with deterministic groundedness, relevance, citation
and safety release gates; model token/cost/latency telemetry; Prometheus alerts;
and Grafana operational views.

## Phase 7 — Local production runtime

Phase 7.1 through 7.4 are implemented:

- Redis AOF persistence and atomic, fail-closed cross-replica MCP quotas
- MinIO-backed original knowledge documents with bounded size, SHA-256 integrity
  metadata and rollback cleanup
- Persistent KRaft Kafka packaging for Kubernetes connected to the transactional
  outbox publisher
- Persistent PostgreSQL, Redis, Kafka and MinIO volumes
- Checksum-protected PostgreSQL and MinIO backup/restore scripts
- Runtime health indicators for PostgreSQL, Redis, Kafka and MinIO

The local single-node stateful topology is production-like. External production
deployments must use replicated operators or managed data services.
