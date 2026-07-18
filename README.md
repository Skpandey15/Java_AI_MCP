# Java_AI_MCP

## Online Interview Platform

An AI-assisted online interview platform for the Java and AI ecosystems.

### Core capabilities

- Candidate and interviewer login/registration
- Role-based dashboards
- Interview creation, scheduling, and assignment
- Direct LLM-generated interview questions
- RAG-grounded questions from approved knowledge sources
- Spring Boot workflow orchestration
- Python-based LLM and RAG services
- LiteLLM AI Gateway for centralized OpenAI access, budgets and observability
- Bidirectional MCP integration for internal platform tools and approved external tools
- React web application
- Auditable answer evaluation and results

### Architecture

See the complete [High-Level Design and Low-Level Design](docs/architecture-design.md).

### Planned stack

- Java 21+ and Spring Boot 3.x
- Python, FastAPI, and an LLM/RAG framework
- React and TypeScript
- Keycloak
- PostgreSQL and pgvector
- Redis and Kafka
- LiteLLM Gateway and OpenAI
- Model Context Protocol (MCP)
- Docker and Kubernetes

### Status

Phase 1 foundation is implemented:

- React/TypeScript web application
- Java 21 Spring Boot orchestrator
- Python 3.12 FastAPI AI service
- Health endpoints and initial tests
- Dockerfiles, Docker Compose and GitHub Actions CI
- Shared API-contract directories

Phase 2A identity foundation is also implemented:

- Keycloak OIDC with PKCE and candidate self-registration
- Administrative interviewer-role assignment
- Spring Security OAuth2 resource server
- PostgreSQL, Flyway and JPA user profiles
- PostgreSQL-backed Keycloak state in a separate `keycloak` database
- In-memory browser tokens only; no business data or tokens in `localStorage`
- Role-protected React dashboard routes
- Interview definition, publishing, assignment and candidate-dashboard APIs
- Server-controlled interview sessions and PostgreSQL answer autosave
- LiteLLM-governed direct AI question generation with structured output

See the [implementation plan](docs/implementation-plan.md).

### Build tools

- Web UI: Vite + npm
- Interview orchestrator: Gradle 8.12 Wrapper with Groovy DSL
- AI service: uv with a committed dependency lockfile

### Local startup

```bash
docker compose -f platform/docker/docker-compose.yml up --build
```
