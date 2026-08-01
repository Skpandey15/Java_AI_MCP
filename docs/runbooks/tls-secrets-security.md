# TLS, secrets and security validation

## Scope

The `dev`, `uat` and `prod` overlays terminate public TLS at an NGINX Ingress.
Only the web UI, orchestrator API and Keycloak are published. AI service,
LiteLLM, PostgreSQL, Redis, Kafka and MinIO remain cluster-internal.

Certificates are managed by cert-manager:

- development and UAT use `ClusterIssuer/letsencrypt-staging`
- production uses `ClusterIssuer/letsencrypt-prod`
- each environment writes its certificate to `Secret/online-interview-tls`
- HTTP requests are redirected to HTTPS by the ingress controller

The issuer resources and DNS records are platform prerequisites because their
ACME account, challenge solver and DNS credentials are infrastructure-specific.

## Secret source and rotation

Non-local overlays never contain a Kubernetes `Secret` or a Kustomize
`secretGenerator`. External Secrets Operator reads the environment payload from
`ClusterSecretStore/platform-secret-store`:

- `online-interview/dev`
- `online-interview/uat`
- `online-interview/prod`

The external payload must provide:

`DATABASE_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `KEYCLOAK_ADMIN_USERNAME`,
`KEYCLOAK_ADMIN_PASSWORD`, `LITELLM_MASTER_KEY`, `LITELLM_API_KEY`,
`AI_SERVICE_TOKEN`, `MCP_AUTHORIZATION_SECRET`, `REDIS_PASSWORD`,
`MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `OPENAI_API_KEY`.

`LITELLM_MASTER_KEY` is admin-only and is injected **only** into the LiteLLM pod.
`LITELLM_API_KEY` is the scoped gateway key the AI service uses and must be a
**required** payload entry — a missing key blocks the ai-service pod from starting
(the `secretKeyRef` cannot mount). Until LiteLLM runs with a `database_url` and a
minted virtual key, set `LITELLM_API_KEY` to the same value as `LITELLM_MASTER_KEY`;
once virtual keys are enabled, rotate `LITELLM_API_KEY` to a budgeted scoped key
without any code or manifest change.

External Secrets refreshes every 15 minutes. Deletion policy is `Retain` so a
temporary provider or operator failure cannot delete the last synchronized
runtime Secret. Rotate the upstream value, wait for the ExternalSecret Ready
condition, and restart consumers that do not reload environment variables.

```powershell
kubectl -n online-interview-prod get externalsecret platform-secrets
kubectl -n online-interview-prod describe externalsecret platform-secrets
kubectl -n online-interview-prod rollout restart deployment
```

Never print, decode, export or commit the generated `platform-secrets` object.

## Deployment prerequisites

Before applying a remote overlay, confirm:

1. NGINX Ingress Controller is installed and publishes an external address.
2. cert-manager is installed.
3. `letsencrypt-staging` or `letsencrypt-prod` exists and is Ready.
4. External Secrets Operator and `platform-secret-store` are Ready.
5. Public DNS for the three environment hosts resolves to the ingress address.

Render and enforce repository policies:

```powershell
$rendered = Join-Path $env:TEMP 'online-interview-manifests'
New-Item -ItemType Directory -Force -Path $rendered | Out-Null
foreach ($environment in 'local','dev','uat','prod') {
    kubectl kustomize "platform/kubernetes/overlays/$environment" |
        Set-Content "$rendered/$environment.yaml"
}
./platform/kubernetes/validate-manifests.ps1 -RenderedDirectory $rendered
```

The CI gate rejects remote overlays that contain native Secrets, placeholder
secret values, missing ExternalSecret rotation/retention controls, missing TLS
certificates, missing HTTPS redirects, public AI dependency hosts, or a
non-production certificate issuer in production.

## Verification after deployment

```powershell
kubectl -n online-interview-prod wait `
  --for=condition=Ready certificate/online-interview-tls --timeout=5m
kubectl -n online-interview-prod wait `
  --for=condition=Ready externalsecret/platform-secrets --timeout=2m
curl.exe --fail --head https://interview.example.com
curl.exe --fail https://api.interview.example.com/actuator/health/readiness
```

Also verify that HTTP returns a redirect, the certificate chain and hostnames
are valid, obsolete TLS versions are rejected at the load balancer, and the
internal AI, LiteLLM and data-service DNS names are not publicly resolvable.

## Incident actions

- Certificate renewal failure: inspect the Certificate, Order and Challenge,
  correct DNS/ingress reachability, then allow cert-manager to retry.
- Secret synchronization failure: keep the retained Secret in service, restore
  provider/operator connectivity and confirm the ExternalSecret Ready condition.
- Suspected secret exposure: revoke and rotate upstream credentials immediately,
  restart affected workloads, inspect audit logs, and invalidate dependent
  sessions or tokens.
- TLS private-key exposure: issue a new certificate Secret and revoke the
  compromised certificate where the certificate authority supports revocation.
