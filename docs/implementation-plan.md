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

## Phase 4 — RAG and MCP

- Document ingestion, pgvector retrieval, reranking and citations
- Internal MCP registry and least-privilege tool policies
- Interview, question-bank, knowledge and result MCP servers
- Approved external MCP connection framework

## Phase 5 — Production hardening

- Kubernetes manifests for Rancher Desktop and production profiles
- Observability, autoscaling, backup and recovery
- Tenant isolation and security testing
- AI quality, groundedness, cost and latency evaluations
