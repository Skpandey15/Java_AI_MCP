# LiteLLM virtual keys and budget governance

The AI service authenticates to LiteLLM with `LITELLM_API_KEY`, a **scoped virtual key** —
never the gateway master key. Until the steps below are performed, `LITELLM_API_KEY` is set
to the same value as `LITELLM_MASTER_KEY` as an interim measure: traffic works, but no budget
or rate limit is actually enforced and the key still has admin scope. This runbook turns on
real enforcement. No application code or Kubernetes Deployment change is required — only
configuration and a one-time key mint.

## Why a database is required

LiteLLM stores virtual keys, budgets and spend in Postgres. With only a `master_key` and no
`DATABASE_URL`, the gateway accepts exactly one key (the master key) and cannot enforce
per-key budgets or RPM/TPM limits. Enabling the key store is therefore a prerequisite.

## Procedure

1. **Provision a database for LiteLLM.** Either a dedicated Postgres, or a `litellm`
   database on the existing instance. Store its URL in `platform-secrets`:

   ```
   LITELLM_DATABASE_URL=postgresql://litellm:<password>@postgres:5432/litellm
   ```

2. **Give LiteLLM the database.** Apply the deployment patch and Job in
   [`platform/kubernetes/base/litellm-postgres.example.yaml`](../../platform/kubernetes/base/litellm-postgres.example.yaml)
   (wire it into the target overlay's `kustomization.yaml`). LiteLLM creates its key/budget
   schema on start.

3. **Mint the scoped key.** The `litellm-key-bootstrap` Job calls `POST /key/generate` with
   the master key and requests a key limited to the two model aliases the service uses, a
   monthly budget and an RPM cap:

   ```json
   {"key_alias":"ai-service",
    "models":["interview-question-model","knowledge-embedding-model"],
    "max_budget":50,"budget_duration":"30d","rpm_limit":60,"tpm_limit":200000}
   ```

   Read the returned `key` from the Job logs:

   ```bash
   kubectl -n <namespace> logs job/litellm-key-bootstrap
   ```

4. **Store and roll out.** Put the returned key into the secret payload as
   `LITELLM_API_KEY` (replacing the interim master-key value), then restart the AI service:

   ```bash
   kubectl -n <namespace> rollout restart deploy/ai-service
   ```

5. **Verify enforcement.** Confirm the key is budgeted and that exceeding a limit is
   rejected by the gateway (HTTP 429 / budget error), not the application:

   ```bash
   kubectl -n <namespace> exec deploy/litellm -- \
     curl -sS http://localhost:4000/key/info -H "Authorization: Bearer $LITELLM_MASTER_KEY"
   ```

## Rotation

Because the wiring is already correct, rotation is a **secret-value change only**: mint a new
key (step 3), update `platform-secrets/LITELLM_API_KEY`, restart `ai-service`. The master key
remains confined to the LiteLLM pod and is only used by the bootstrap/rotation Job.

## Related

- [`tls-secrets-security.md`](tls-secrets-security.md) — required secret payload keys,
  including `LITELLM_API_KEY` and `LITELLM_MASTER_KEY`.
