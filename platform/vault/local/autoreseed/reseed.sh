#!/bin/sh
# Idempotent reseed for the LOCAL, dev-mode (ephemeral, in-memory) Vault.
#
# Dev-mode Vault ignores persistent storage, so a Vault or cluster restart wipes its
# KV data, Kubernetes auth method, policy, and role. External Secrets then can no longer
# log in ("InvalidProviderConfig"), the generated Secret goes stale, and Argo CD reports
# the app Degraded. This script restores that state from a recovery seed that lives in a
# Kubernetes Secret (etcd survives restarts). It is run on a schedule by the
# vault-autoreseed CronJob and is safe to run repeatedly: every step is idempotent, and
# the application secret is only written when Vault is missing it, so live edits made
# directly in Vault are never clobbered.
set -eu

VAULT_ADDR="http://vault.vault.svc:8200"
export VAULT_ADDR
VAULT_TOKEN="$(cat /seed/root-token)"
export VAULT_TOKEN

# Wait briefly for Vault to answer. If it never does this tick, exit cleanly and let the
# next scheduled run retry — a not-yet-ready Vault is not a failure worth alerting on.
attempt=0
while [ "$attempt" -lt 15 ]; do
  if vault status >/dev/null 2>&1; then
    break
  fi
  attempt=$((attempt + 1))
  sleep 2
done
if ! vault status >/dev/null 2>&1; then
  echo "vault not reachable yet; will retry on the next schedule"
  exit 0
fi

# KV v2 secrets engine at secret/
if ! vault secrets list -format=json | grep -q '"secret/"'; then
  vault secrets enable -path=secret kv-v2
fi

# Kubernetes auth method + its cluster config
if ! vault auth list -format=json | grep -q '"kubernetes/"'; then
  vault auth enable kubernetes
fi
vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443

# Policy + role that the External Secrets ServiceAccount logs in against
vault policy write online-interview-dev /bootstrap/policy.hcl
vault write auth/kubernetes/role/online-interview-dev \
  bound_service_account_names=external-secrets-vault \
  bound_service_account_namespaces=online-interview-dev \
  audience=vault \
  token_policies=online-interview-dev \
  token_type=batch \
  token_ttl=15m \
  token_max_ttl=30m

# Application secret payload — seed only when absent so a live Vault value is never overwritten.
if vault kv get secret/online-interview/dev >/dev/null 2>&1; then
  echo "secret/online-interview/dev already present; left unchanged"
else
  vault kv put secret/online-interview/dev @/seed/payload.json
  echo "seeded secret/online-interview/dev from the persisted recovery payload"
fi

echo "vault reseed reconcile complete"
