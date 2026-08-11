param(
    [Parameter(Mandatory = $true)]
    [string]$RenderedDirectory,
    [Parameter(Mandatory = $true)]
    [string]$GitOpsDirectory
)

$ErrorActionPreference = 'Stop'

foreach ($environment in @('dev', 'uat', 'prod')) {
    $applicationPath = Join-Path $GitOpsDirectory "application-$environment.yaml"
    if (-not (Test-Path -LiteralPath $applicationPath)) {
        throw "Argo CD application is missing: $applicationPath"
    }

    $application = Get-Content -LiteralPath $applicationPath -Raw
    if ($application -notmatch "path:\s+platform/kubernetes/overlays/$environment") {
        throw "$environment application targets the wrong Kustomize overlay"
    }
    if ($application -notmatch 'targetRevision:\s+main') {
        throw "$environment application must reconcile the protected main branch"
    }
}

$devApplication = Get-Content -LiteralPath (Join-Path $GitOpsDirectory 'application-dev.yaml') -Raw
if ($devApplication -notmatch 'enabled:\s+true' -or
    $devApplication -notmatch 'prune:\s+true' -or
    $devApplication -notmatch 'selfHeal:\s+true') {
    throw 'Development must enable automatic sync, pruning and self-healing'
}

foreach ($environment in @('uat', 'prod')) {
    $application = Get-Content -LiteralPath (Join-Path $GitOpsDirectory "application-$environment.yaml") -Raw
    if ($application -notmatch 'enabled:\s+false') {
        throw "$environment must require an explicit Argo CD synchronization"
    }
}

$renderedFiles = Get-ChildItem -LiteralPath $RenderedDirectory -Filter '*.yaml' |
    Where-Object { $_.BaseName -in @('local', 'dev', 'uat', 'prod') }
foreach ($file in $renderedFiles) {
    $manifest = Get-Content -LiteralPath $file.FullName -Raw
    # 'local' is the only environment that runs locally-built images (imported into k3d
    # by tag). dev now pulls the CI-published ghcr images by immutable digest — the same
    # as uat/prod — so a merge to main auto-deploys it via the promote-dev job. Only
    # 'local' is exempt from the immutable-digest invariant.
    if ($file.BaseName -ne 'local' -and
        $manifest -notmatch 'image:\s+ghcr\.io/skpandey15/\S+@sha256:[a-f0-9]{64}') {
        throw "$($file.BaseName) does not render immutable application image digests"
    }
    if ($manifest -notmatch 'argocd\.argoproj\.io/sync-wave:\s+["'']?0') {
        throw "$($file.BaseName) does not render the migration sync wave"
    }
}

Write-Output 'Phase 4C GitOps policy checks passed.'
