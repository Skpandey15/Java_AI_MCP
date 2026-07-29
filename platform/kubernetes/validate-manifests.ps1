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
    if ($manifest -notmatch '--proxy-headers=xforwarded') {
        throw "$environment does not configure Keycloak reverse-proxy headers"
    }
}

Write-Output 'Phase 4B manifest policy checks passed.'
