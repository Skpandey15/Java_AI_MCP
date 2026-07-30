param(
    [Parameter(Mandatory = $true)]
    [string]$RenderedDirectory
)

$ErrorActionPreference = 'Stop'

$environments = @('local', 'dev', 'uat', 'prod')
foreach ($environment in $environments) {
    $path = Join-Path $RenderedDirectory "$environment.yaml"
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Rendered manifest is missing: $path"
    }

    $manifest = Get-Content -LiteralPath $path -Raw
    if ($manifest -notmatch 'name: SPRING_FLYWAY_ENABLED\s+value: "false"') {
        throw "$environment does not disable Flyway in the orchestrator Deployment"
    }
    if ($manifest -notmatch 'name: validate-migrated-schema') {
        throw "$environment does not contain the migrated-schema validation init container"
    }
    if ($manifest -notmatch 'name: web-ui-runtime-config') {
        throw "$environment does not contain web UI runtime configuration"
    }
    if ($manifest -match 'image:\s+\S+:(latest|main-stable)\s*$') {
        throw "$environment contains a mutable latest/main-stable image without a digest"
    }
}

foreach ($environment in @('uat', 'prod')) {
    $manifest = Get-Content -LiteralPath (Join-Path $RenderedDirectory "$environment.yaml") -Raw
    if ($manifest -match '(?m)^\s+- start-dev\s*$') {
        throw "$environment runs Keycloak in development mode"
    }
}

foreach ($environment in @('dev', 'uat', 'prod')) {
    $manifest = Get-Content -LiteralPath (Join-Path $RenderedDirectory "$environment.yaml") -Raw
    if ($manifest -notmatch '--proxy-headers=xforwarded') {
        throw "$environment does not configure Keycloak reverse-proxy headers"
    }
}

foreach ($environment in @('dev', 'uat', 'prod')) {
    $manifest = Get-Content -LiteralPath (Join-Path $RenderedDirectory "$environment.yaml") -Raw
    if ($manifest -notmatch '(?m)^apiVersion: external-secrets\.io/v1\s*$') {
        throw "$environment does not use the supported external-secrets.io/v1 API"
    }
    if ($manifest -match '(?m)^apiVersion: external-secrets\.io/v1beta1\s*$') {
        throw "$environment uses the unsupported external-secrets.io/v1beta1 API"
    }
    if ($manifest -match '(?m)^kind: Secret\s*$') {
        throw "$environment contains a deployable plaintext Kubernetes Secret"
    }
    if ($manifest -notmatch '(?m)^\s+refreshPolicy: Periodic\s*$' -or
        $manifest -notmatch '(?m)^\s+refreshInterval: 15m\s*$' -or
        $manifest -notmatch '(?m)^\s+deletionPolicy: Retain\s*$') {
        throw "$environment does not enforce the ExternalSecret rotation and retention policy"
    }
    if ($manifest -notmatch '(?m)^kind: Certificate\s*$' -or
        $manifest -notmatch '(?m)^\s+secretName: online-interview-tls\s*$') {
        throw "$environment does not declare the managed TLS certificate"
    }
    if ($manifest -notmatch '(?m)^kind: Ingress\s*$' -or
        $manifest -notmatch 'nginx\.ingress\.kubernetes\.io/force-ssl-redirect: "true"' -or
        $manifest -notmatch '(?m)^\s+tls:\s*$') {
        throw "$environment does not enforce TLS at ingress"
    }
    if ($manifest -match '(?m)^\s+host: (ai|litellm)\.') {
        throw "$environment exposes an internal AI dependency through public ingress"
    }
    if ($manifest -match '(?i)(replace-before-apply|change-me|changeme)') {
        throw "$environment contains a placeholder secret value"
    }
}

$prodManifest = Get-Content -LiteralPath (Join-Path $RenderedDirectory 'prod.yaml') -Raw
if ($prodManifest -notmatch '(?m)^\s+name: letsencrypt-prod\s*$') {
    throw 'prod does not use the production certificate issuer'
}

Write-Output 'Kubernetes manifest security policy checks passed.'
