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

Stop the environment with:

```bash
docker compose -f platform/docker/docker-compose.yml down
```

## Native build commands

```bash
cd apps/web-ui && npm install && npm test && npm run build
cd apps/interview-orchestrator && gradle clean test bootJar
cd apps/ai-service && uv sync --extra dev && uv run --extra dev pytest
```
