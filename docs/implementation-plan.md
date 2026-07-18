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

- Keycloak OIDC login and registration
- Candidate and interviewer profile mapping
- PostgreSQL/Flyway persistence
- Interview definitions, assignments and scheduling
- Candidate upcoming-interview dashboard
- Session state machine, server timer and answer autosave
- Manual question sets for deterministic end-to-end testing

## Phase 3 — AI generation and evaluation

- LiteLLM gateway and Kubernetes secret integration
- Direct OpenAI-backed question generation through the Python service
- Structured response schemas and prompt/model versioning
- Kafka, transactional outbox and asynchronous answer evaluation

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
