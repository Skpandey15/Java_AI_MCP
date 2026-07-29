# Phase 4B — Kubernetes packaging

Phase 4B packages the Online Interview platform with Kustomize. It does not install Argo CD or promote releases; those are Phase 4C.

## Layout

- `base`: PostgreSQL, Keycloak, LiteLLM, web UI, orchestrator, AI service, Services, probes, resources, persistence and Flyway migration Job
- `overlays/local`: k3d/Rancher Desktop defaults, NodePort fallbacks, and Traefik Ingress
- `overlays/dev`, `uat`, `prod`: separate namespaces, immutable application image tags and External Secrets
- production additionally defines three PodDisruptionBudgets

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

CI performs the same four renders.

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

Open these URLs:

- Web UI: `http://interview.localtest.me:8081`
- Orchestrator API: `http://api.interview.localtest.me:8081`
- Keycloak: `http://auth.interview.localtest.me:8081`
- AI service: `http://ai.interview.localtest.me:8081`
- LiteLLM: `http://litellm.interview.localtest.me:8081`

Confirm the route objects with:

```powershell
kubectl -n online-interview get ingress
kubectl -n online-interview describe ingress online-interview
```

These routes become usable only after their backend pods are Running and Ready. If the k3d cluster was created without host port `8081` mapped to load-balancer port `80`, recreate that mapping or use `kubectl port-forward service/traefik -n kube-system 8081:80` as a temporary fallback.

## Migration control

The `database-migration` Job runs Flyway with the exact orchestrator image selected by the overlay. The orchestrator Deployment uses an init container that validates the migrated schema before application startup. Before every shared-environment rollout:

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
