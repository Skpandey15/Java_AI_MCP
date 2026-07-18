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

Architecture and detailed design completed. Project scaffolding is the next phase.
