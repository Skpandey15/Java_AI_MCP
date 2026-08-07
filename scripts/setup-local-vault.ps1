[CmdletBinding()]
param(
    [string]$ApplicationNamespace = 'online-interview-dev',
    [string]$VaultNamespace = 'vault',
    [string]$ExternalSecretsNamespace = 'external-secrets',
    [string]$VaultChartVersion = '0.34.0',
    [string]$ExternalSecretsChartVersion = '2.8.0',
    [switch]$SkipSecretMigration
)

$ErrorActionPreference = 'Stop'

function Invoke-Native {
    param([Parameter(Mandatory)][scriptblock]$Command, [string]$FailureMessage)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

foreach ($command in 'helm', 'kubectl') {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command is required but was not found on PATH."
    }
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
    throw 'No active Kubernetes context is available.'
}

Write-Output "Installing local-only Vault and External Secrets Operator into context '$context'."
Write-Warning 'Vault dev mode is ephemeral and must never be used for UAT or production.'

$repos = helm repo list -o json | ConvertFrom-Json
if (-not ($repos | Where-Object name -eq 'hashicorp')) {
    Invoke-Native { helm repo add hashicorp https://helm.releases.hashicorp.com } 'Unable to add the HashiCorp Helm repository.'
}
if (-not ($repos | Where-Object name -eq 'external-secrets')) {
    Invoke-Native { helm repo add external-secrets https://charts.external-secrets.io } 'Unable to add the External Secrets Helm repository.'
}
Invoke-Native { helm repo update } 'Unable to update Helm repositories.'

Invoke-Native {
    helm upgrade --install external-secrets external-secrets/external-secrets `
        --namespace $ExternalSecretsNamespace --create-namespace `
        --version $ExternalSecretsChartVersion `
        --set installCRDs=true `
        --wait --timeout 5m
} 'External Secrets Operator installation failed.'

$tokenBytes = New-Object byte[] 32
$existingVaultValues = $null
helm status vault --namespace $VaultNamespace *> $null
if ($LASTEXITCODE -eq 0) {
    $existingVaultValues = helm get values vault --namespace $VaultNamespace -o json | ConvertFrom-Json
}
if ($existingVaultValues.server.dev.devRootToken) {
    $devRootToken = [string]$existingVaultValues.server.dev.devRootToken
}
else {
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($tokenBytes)
    $random.Dispose()
    $devRootToken = ([BitConverter]::ToString($tokenBytes) -replace '-', '').ToLowerInvariant()
}
$valuesFile = New-TemporaryFile
try {
    @"
injector:
  enabled: false
server:
  dev:
    enabled: true
    devRootToken: $devRootToken
  serviceAccount:
    create: true
ui:
  enabled: true
"@ | Set-Content -LiteralPath $valuesFile -Encoding utf8

    Invoke-Native {
        helm upgrade --install vault hashicorp/vault `
            --namespace $VaultNamespace --create-namespace `
            --version $VaultChartVersion `
            --values $valuesFile `
            --wait --timeout 5m
    } 'Vault installation failed.'
}
finally {
    Remove-Item -LiteralPath $valuesFile -Force -ErrorAction SilentlyContinue
    $devRootToken = $null
    [Array]::Clear($tokenBytes, 0, $tokenBytes.Length)
}

Invoke-Native {
    kubectl -n $VaultNamespace wait --for=condition=Ready pod/vault-0 --timeout=2m
} 'Vault pod did not become Ready.'

Invoke-Native {
    kubectl -n $VaultNamespace exec vault-0 -- sh -ec `
        'export VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID"; vault secrets list -format=json | grep -q ''"secret/"'' || vault secrets enable -path=secret kv-v2; vault auth list -format=json | grep -q ''"kubernetes/"'' || vault auth enable kubernetes; vault write auth/kubernetes/config kubernetes_host=https://kubernetes.default.svc:443'
} 'Unable to enable Vault KV v2 or Kubernetes authentication.'

$policy = Get-Content -LiteralPath (Join-Path $PSScriptRoot '../platform/vault/policies/online-interview-dev.hcl') -Raw
$policy | kubectl -n $VaultNamespace exec -i vault-0 -- sh -ec `
    'export VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID"; vault policy write online-interview-dev -'
if ($LASTEXITCODE -ne 0) { throw 'Unable to install the development Vault policy.' }

Invoke-Native {
    kubectl -n $VaultNamespace exec vault-0 -- sh -ec `
        'export VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID"; vault write auth/kubernetes/role/online-interview-dev bound_service_account_names=external-secrets-vault bound_service_account_namespaces=online-interview-dev audience=vault token_policies=online-interview-dev token_type=batch token_ttl=15m token_max_ttl=30m'
} 'Unable to configure the development Vault Kubernetes role.'

$sourceSecret = $null
if (-not $SkipSecretMigration) {
    $sourceSecret = kubectl -n $ApplicationNamespace get secret platform-secrets -o json 2>$null | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $sourceSecret) {
        throw "Secret/$ApplicationNamespace/platform-secrets does not exist. Seed Vault separately or rerun with -SkipSecretMigration."
    }

    $payload = @{}
    foreach ($entry in $sourceSecret.data.PSObject.Properties) {
        $payload[$entry.Name] = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([string]$entry.Value))
    }
    $payload | ConvertTo-Json -Compress | kubectl -n $VaultNamespace exec -i vault-0 -- sh -ec `
        'export VAULT_TOKEN="$VAULT_DEV_ROOT_TOKEN_ID"; vault kv put secret/online-interview/dev - >/dev/null'
    if ($LASTEXITCODE -ne 0) { throw 'Unable to migrate platform-secrets into Vault.' }
    $payload.Clear()
}

$devOverlay = Join-Path $PSScriptRoot '../platform/kubernetes/overlays/dev'
$localVaultResources = Join-Path $PSScriptRoot '../platform/vault/local/vault-secret-store.yaml'
Invoke-Native {
    kubectl apply -f $localVaultResources
} 'Unable to apply the Vault service account and SecretStore.'
Invoke-Native {
    kubectl -n $ApplicationNamespace apply -f (Join-Path $devOverlay 'external-secret.yaml')
} 'Unable to apply the development ExternalSecret.'

$tlsDirectory = Join-Path ([IO.Path]::GetTempPath()) ("vault-tls-" + [Guid]::NewGuid().ToString('N'))
try {
    Invoke-Native {
        python (Join-Path $PSScriptRoot 'generate-local-vault-tls.py') $tlsDirectory
    } 'Unable to generate the local Vault TLS certificate.'
    kubectl -n $VaultNamespace create secret tls vault-dev-tls `
        --cert (Join-Path $tlsDirectory 'tls.crt') `
        --key (Join-Path $tlsDirectory 'tls.key') `
        --dry-run=client -o yaml | kubectl apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to install the local Vault TLS certificate.' }
}
finally {
    if (Test-Path -LiteralPath $tlsDirectory) {
        Remove-Item -LiteralPath $tlsDirectory -Recurse -Force
    }
}

Invoke-Native {
    kubectl apply -f (Join-Path $PSScriptRoot '../platform/vault/local/ingress.yaml')
} 'Unable to apply the restricted local Vault ingress.'

if ($sourceSecret) {
    Invoke-Native { kubectl -n $ApplicationNamespace delete secret platform-secrets --wait=true } 'Unable to hand Secret ownership to External Secrets Operator.'
    Invoke-Native { kubectl -n $ApplicationNamespace annotate externalsecret platform-secrets force-sync=$(Get-Date -UFormat %s) --overwrite } 'Unable to request an immediate secret synchronization.'
}

Invoke-Native {
    kubectl -n $ApplicationNamespace wait --for=condition=Ready secretstore/platform-secret-store --timeout=2m
} 'Vault SecretStore did not become Ready.'
Invoke-Native {
    kubectl -n $ApplicationNamespace wait --for=condition=Ready externalsecret/platform-secrets --timeout=2m
} 'ExternalSecret did not become Ready.'

if ($sourceSecret) {
    $synced = kubectl -n $ApplicationNamespace get secret platform-secrets -o json | ConvertFrom-Json
    $sourceKeys = @($sourceSecret.data.PSObject.Properties.Name | Sort-Object)
    $syncedKeys = @($synced.data.PSObject.Properties.Name | Sort-Object)
    $different = @($sourceKeys | Where-Object { [string]$sourceSecret.data.$_ -ne [string]$synced.data.$_ })
    if ($LASTEXITCODE -ne 0 -or (Compare-Object $sourceKeys $syncedKeys) -or $different.Count -ne 0) {
        throw 'Synchronized secret validation failed. Vault retains the migrated source payload for recovery.'
    }
}

# --- Self-healing reseed -------------------------------------------------------------
# Dev-mode Vault is in-memory: a Vault or cluster restart wipes its auth method, policy,
# role, and KV data, breaking External Secrets until this script is re-run by hand. Persist
# a recovery seed (root token + secret payload) into a Kubernetes Secret (etcd survives
# restarts) and install a CronJob that reconciles Vault from it automatically. See
# platform/vault/local/autoreseed/reseed.sh. Local development only.
$autoReseedDir = Join-Path $PSScriptRoot '../platform/vault/local/autoreseed'
$devRootTokenForSeed = (helm get values vault --namespace $VaultNamespace -o json | ConvertFrom-Json).server.dev.devRootToken
if ([string]::IsNullOrWhiteSpace($devRootTokenForSeed)) {
    throw 'Could not resolve the Vault dev root token needed for the auto-reseed seed.'
}

$syncedForSeed = kubectl -n $ApplicationNamespace get secret platform-secrets -o json | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or -not $syncedForSeed) { throw 'Unable to read the synchronized secret for the auto-reseed payload.' }
$seedPayload = @{}
foreach ($entry in $syncedForSeed.data.PSObject.Properties) {
    $seedPayload[$entry.Name] = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([string]$entry.Value))
}

$payloadFile = New-TemporaryFile
try {
    # UTF-8 WITHOUT a BOM — a BOM would corrupt the JSON that `vault kv put @payload.json` reads.
    [IO.File]::WriteAllText($payloadFile, ($seedPayload | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))

    kubectl -n $VaultNamespace create secret generic vault-seed `
        --from-literal=root-token=$devRootTokenForSeed `
        --from-file=payload.json=$payloadFile `
        --dry-run=client -o yaml | kubectl apply -f - | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Unable to persist the Vault reseed seed secret.' }
}
finally {
    Remove-Item -LiteralPath $payloadFile -Force -ErrorAction SilentlyContinue
    $seedPayload.Clear()
    $devRootTokenForSeed = $null
}

kubectl -n $VaultNamespace create configmap vault-bootstrap `
    --from-file=reseed.sh=(Join-Path $autoReseedDir 'reseed.sh') `
    --from-file=policy.hcl=(Join-Path $PSScriptRoot '../platform/vault/policies/online-interview-dev.hcl') `
    --dry-run=client -o yaml | kubectl apply -f - | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to install the Vault bootstrap ConfigMap.' }

Invoke-Native {
    kubectl apply -f (Join-Path $autoReseedDir 'reseed-cronjob.yaml')
} 'Unable to install the Vault auto-reseed CronJob.'

# Prove it works now instead of waiting for the first scheduled tick.
kubectl -n $VaultNamespace delete job vault-autoreseed-initial --ignore-not-found | Out-Null
Invoke-Native {
    kubectl -n $VaultNamespace create job vault-autoreseed-initial --from=cronjob/vault-autoreseed
} 'Unable to launch the initial Vault reseed verification job.'

Write-Output 'HashiCorp Vault integration is ready. Application secret values were not printed or written to disk.'
Write-Output 'Self-healing reseed installed (CronJob vault/vault-autoreseed) — a restart now recovers automatically.'
