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

## Phase 6 — Production hardening

- Observability, autoscaling, backup and recovery
- Tenant isolation and security testing
- AI quality, groundedness, cost and latency evaluations
