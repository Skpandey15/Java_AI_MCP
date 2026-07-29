# Phase 4B — Kubernetes packaging

Phase 4B packages the Online Interview platform with Kustomize. It does not install Argo CD or promote releases; those are Phase 4C.

## Layout

- `base`: PostgreSQL, Keycloak, LiteLLM, web UI, orchestrator, AI service, Services, probes, resources, persistence and Flyway migration Job
- `overlays/local`: k3d/Rancher Desktop defaults, NodePort fallbacks, and Traefik Ingress
- `overlays/dev`, `uat`, `prod`: separate namespaces, immutable application image tags and External Secrets
- production additionally defines three PodDisruptionBudgets
- the web UI reads `/config.js` at runtime, allowing one immutable image to be promoted with environment-specific API and Keycloak endpoints
- Keycloak client redirect URIs and web origins are generated separately for each environment

No real credential is committed. The local overlay contains development-only defaults and an explicit OpenAI placeholder. Shared overlays expect External Secrets Operator and a `ClusterSecretStore` named `platform-secret-store`.

## Required secret keys

The generated `platform-secrets` Secret must contain:

- `DATABASE_PASSWORD`
- `KEYCLOAK_DB_PASSWORD`
- `KEYCLOAK_ADMIN_USERNAME`
- `KEYCLOAK_ADMIN_PASSWORD`
- `LITELLM_MASTER_KEY`
- `AI_SERVICE_TOKEN`
- `OPENAI_API_KEY`

Private GHCR packages additionally require a `ghcr-pull` docker-registry Secret in the target namespace. Use a fine-grained token that can read packages; never commit it.

## Render validation

From the repository root in PowerShell:

```powershell
kubectl kustomize platform/kubernetes/overlays/local | Out-Null
kubectl kustomize platform/kubernetes/overlays/dev | Out-Null
kubectl kustomize platform/kubernetes/overlays/uat | Out-Null
kubectl kustomize platform/kubernetes/overlays/prod | Out-Null
```

CI performs the same four renders, validates built-in resource schemas with
`kubeconform`, and enforces Phase 4B policies with
`platform/kubernetes/validate-manifests.ps1`.

## Rancher Desktop / k3d local validation

Confirm the cluster first:

```powershell
kubectl config current-context
kubectl cluster-info
```

Create the private-registry pull secret. Replace the values at the prompt; do not paste the token into source files:

```powershell
kubectl create namespace online-interview --dry-run=client -o yaml | kubectl apply -f -
kubectl -n online-interview create secret docker-registry ghcr-pull --docker-server=ghcr.io --docker-username=Skpandey15 --docker-password="$env:GHCR_TOKEN" --dry-run=client -o yaml | kubectl apply -f -
```

Render and apply the local overlay, then replace its placeholder secret values from environment variables:

```powershell
kubectl apply -k platform/kubernetes/overlays/local
kubectl -n online-interview create secret generic platform-secrets --from-literal=DATABASE_PASSWORD=interview --from-literal=KEYCLOAK_DB_PASSWORD=keycloak --from-literal=KEYCLOAK_ADMIN_USERNAME=admin --from-literal=KEYCLOAK_ADMIN_PASSWORD=admin --from-literal=LITELLM_MASTER_KEY="$env:LITELLM_MASTER_KEY" --from-literal=AI_SERVICE_TOKEN="$env:AI_SERVICE_TOKEN" --from-literal=OPENAI_API_KEY="$env:OPENAI_API_KEY" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n online-interview rollout restart deployment/litellm deployment/ai-service deployment/interview-orchestrator
kubectl -n online-interview wait --for=condition=complete job/database-migration --timeout=5m
kubectl -n online-interview get pods,svc,pvc,jobs
```

The local overlay includes a Traefik Ingress. In the standard k3d setup, host port `8081` forwards to the cluster's HTTP entrypoint, and `localtest.me` resolves to `127.0.0.1` without editing the Windows hosts file.

For the authenticated browser flow, use the reserved `.localhost` Ingress
routes. Browsers treat `.localhost` names as secure development contexts, which
is required for OIDC PKCE:

- Web UI: `http://interview.localhost:8081`

The browser redirects to Keycloak and sends API requests through the
service-specific Ingress routes below:

- Web UI: `http://interview.localhost:8081`
- Orchestrator API: `http://api.interview.localhost:8081`
- Keycloak: `http://auth.interview.localhost:8081`
- AI service: `http://ai.interview.localhost:8081`
- LiteLLM: `http://litellm.interview.localhost:8081`

Confirm the route objects with:

```powershell
kubectl -n online-interview get ingress
kubectl -n online-interview describe ingress online-interview
```

These routes become usable only after their backend pods are Running and Ready. If the k3d cluster was created without host port `8081` mapped to load-balancer port `80`, recreate that mapping or use `kubectl port-forward service/traefik -n kube-system 8081:80` as a temporary fallback.

## Migration control

The `database-migration` Job is the only workload allowed to run Flyway and uses
the exact orchestrator image selected by the overlay. Normal orchestrator
containers set `SPRING_FLYWAY_ENABLED=false`. The Deployment init container
retries Hibernate schema validation until the migration has completed, so an
application replica cannot become ready against an older schema. Before every
shared-environment rollout:

1. update all three application images to the same tested immutable release SHA or digest;
2. delete the completed migration Job so it can be recreated;
3. apply the overlay;
4. wait for the Job to complete;
5. only then assess Deployment rollout health.

```powershell
kubectl -n online-interview-dev delete job database-migration --ignore-not-found
kubectl apply -k platform/kubernetes/overlays/dev
kubectl -n online-interview-dev wait --for=condition=complete job/database-migration --timeout=5m
kubectl -n online-interview-dev rollout status deployment/interview-orchestrator --timeout=5m
```

Do not use the mutable `main` tag in UAT or production. The initial shared overlays are pinned to the Phase 4A merge commit; Phase 4C will automate digest promotion.

## Shared-environment prerequisites

- External Secrets Operator CRDs/controller
- `ClusterSecretStore/platform-secret-store`
- secret records at `online-interview/dev`, `online-interview/uat`, and `online-interview/prod`
- `ghcr-pull` in each target namespace when packages are private
- environment-specific DNS/TLS and Keycloak realm client URLs before public exposure
- a suitable default StorageClass or an explicit storage-class patch

Stateful production services should ultimately use managed PostgreSQL and managed identity infrastructure. This base keeps the current self-contained architecture deployable for Phase 4B validation.

The shared UAT and production overlays run Keycloak with the production `start`
command, external hostnames and forwarded-header processing. Local and dev retain
development mode for cluster validation. Third-party runtime images are pinned by
digest so a repeated deployment resolves to the same content.
