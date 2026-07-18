# Local container environment

Set `OPENAI_API_KEY` in your operating-system environment or copy
`platform/docker/.env.example` to `platform/docker/.env` and replace its placeholders.
Never commit the real values.

From the repository root:

```bash
docker compose -f platform/docker/docker-compose.yml up --build
```

Endpoints:

- Web UI: http://localhost:3000
- Spring Boot health: http://localhost:8080/api/v1/health
- Spring Boot actuator: http://localhost:8080/actuator/health
- Python health: http://localhost:8000/api/v1/health
- Python API docs: http://localhost:8000/docs
- LiteLLM Gateway: http://localhost:4000
- Keycloak: http://localhost:8090
- Keycloak administration: http://localhost:8090/admin (local credentials: `admin` / `admin`)
- PostgreSQL: localhost:5432 (`online_interview` business database and separate `keycloak` identity database)

Keycloak realms, users, credentials, roles and sessions persist in PostgreSQL across container restarts. Candidate self-registration is available from the UI. The default Keycloak realm role is `candidate`. Assign the `interviewer` realm role only through Keycloak administration. The React application keeps access and refresh tokens only in the Keycloak JavaScript adapter memory; it does not persist tokens or business data in browser `localStorage`.

Stop the environment with:

```bash
docker compose -f platform/docker/docker-compose.yml down
```

## Native build commands

```bash
cd apps/web-ui && npm install && npm test && npm run build
cd apps/interview-orchestrator && ./gradlew clean test bootJar
cd apps/ai-service && uv sync --extra dev && uv run --extra dev pytest
```

## Phase 3A AI request path

`Spring Boot -> Python AI service -> LiteLLM -> OpenAI`

Only LiteLLM receives `OPENAI_API_KEY`. React and Spring Boot never receive the provider key.
Use distinct random values for `LITELLM_MASTER_KEY` and `AI_SERVICE_TOKEN` outside local development.
