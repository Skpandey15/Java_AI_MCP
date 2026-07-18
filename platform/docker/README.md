# Local container environment

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
- Keycloak: http://localhost:8090
- Keycloak administration: http://localhost:8090/admin (local credentials: `admin` / `admin`)
- PostgreSQL: localhost:5432 (`online_interview` database)

Candidate self-registration is available from the UI. The default Keycloak realm role is `candidate`. Assign the `interviewer` realm role only through Keycloak administration.

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
