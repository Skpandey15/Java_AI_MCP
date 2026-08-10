#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Test, build, and deploy the CURRENT PR branch to the single local "dev" stack via Argo CD.

.DESCRIPTION
  The dev environment is a local k3d stack (namespace online-interview-dev) exposed over LAN
  HTTPS at https://dev.interview.<LanIp>.nip.io:8443 so other devices on the same WiFi can use it.
  Argo CD is the deployer and reads from the GitHub remote, so this script:

    1. runs the three test suites (fail-fast)     -- "PR branch must be tested"
    2. builds the 3 app images and imports them into k3d
    3. writes the image tags (and LAN IP) into platform/kubernetes/overlays/dev
    4. commits + pushes the current branch to origin
    5. points the Argo CD app at this branch and syncs it, then waits for health

  TLS is issued separately by scripts/dev-ca.ps1 (install rootCA.crt once per machine).
  Requires: git, docker, k3d, kubectl (context k3d-dev) on PATH; the 'dev' cluster running.

.EXAMPLE
  ./scripts/deploy-dev.ps1                       # test + build + deploy current branch
.EXAMPLE
  ./scripts/deploy-dev.ps1 -SkipTests -Service web-ui
.EXAMPLE
  ./scripts/deploy-dev.ps1 -LanIp 192.168.1.9    # after the desktop's Wi-Fi IP changed
#>
[CmdletBinding()]
param(
  [ValidateSet('web-ui', 'ai-service', 'interview-orchestrator', 'all')]
  [string]$Service = 'all',
  [string]$Tag,
  [string]$LanIp,
  [switch]$SkipTests,
  [string]$Cluster = 'dev',
  [string]$Namespace = 'online-interview-dev',
  [string]$ArgoApp = 'online-interview-dev'
)
$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$OverlayDir = Join-Path $RepoRoot 'platform/kubernetes/overlays/dev'

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "    $m" -ForegroundColor Green }
function Warn($m) { Write-Host "    $m" -ForegroundColor Yellow }
function Run([scriptblock]$Cmd, [string]$What) { & $Cmd; if ($LASTEXITCODE -ne 0) { throw "$What failed (exit $LASTEXITCODE)" } }

# --- context -----------------------------------------------------------------
$branch = (git -C $RepoRoot rev-parse --abbrev-ref HEAD).Trim()
if ($branch -eq 'main') { throw "Refusing to deploy from 'main'. Check out a PR branch first." }
$shortSha = (git -C $RepoRoot rev-parse --short HEAD).Trim()
if (-not $Tag) { $Tag = "dev-$shortSha" }
if (-not $LanIp) {
  $LanIp = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.InterfaceAlias -match 'Wi-?Fi' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
}
if (-not $LanIp) { throw "Could not detect LAN IPv4; pass -LanIp." }
Info "branch=$branch  tag=$Tag  lanIp=$LanIp  service=$Service"

$doWeb  = $Service -in @('web-ui', 'all')
$doAi   = $Service -in @('ai-service', 'all')
$doOrch = $Service -in @('interview-orchestrator', 'all')

# --- 1. tests (fail-fast) ----------------------------------------------------
if (-not $SkipTests) {
  if ($doWeb) {
    Info "test: web-ui (vitest)"
    Push-Location (Join-Path $RepoRoot 'apps/web-ui')
    try { Run { npm ci } "web-ui npm ci"; Run { npm test } "web-ui tests" } finally { Pop-Location }
  }
  if ($doOrch) {
    Info "test: interview-orchestrator (gradle test in container)"
    $orch = Join-Path $RepoRoot 'apps/interview-orchestrator'
    Run { docker run --rm -v "${orch}:/app" -v gradle_cache:/home/gradle/.gradle -w /app gradle:9.6.1-jdk25 gradle test --no-daemon } "orchestrator tests"
  }
  if ($doAi) {
    Info "test: ai-service (pytest in container)"
    $ai = Join-Path $RepoRoot 'apps/ai-service'
    Run { docker run --rm -v "${ai}:/app" -w /app python:3.13-slim sh -c "pip install -q uv && uv sync --frozen --extra dev && uv run --extra dev pytest -q" } "ai-service tests"
  }
  Ok "all requested test suites passed"
} else { Warn "tests skipped (-SkipTests)" }

# --- 2. build + import -------------------------------------------------------
function Import-Img([string]$img) { Info "k3d import $img"; Run { k3d image import $img -c $Cluster } "k3d import $img" }

if ($doWeb) {
  Info "build local/web-ui:$Tag"
  Run { docker build -q -t "local/web-ui:$Tag" (Join-Path $RepoRoot 'apps/web-ui') } "build web-ui"
  Import-Img "local/web-ui:$Tag"
}
if ($doAi) {
  Info "build local/ai-service:$Tag"
  Run { docker build -q -t "local/ai-service:$Tag" (Join-Path $RepoRoot 'apps/ai-service') } "build ai-service"
  Import-Img "local/ai-service:$Tag"
}
if ($doOrch) {
  $orch = Join-Path $RepoRoot 'apps/interview-orchestrator'
  Info "build orchestrator bootJar (gradle:9.6.1-jdk25) + Dockerfile.local"
  Run { docker run --rm -v "${orch}:/app" -v gradle_cache:/home/gradle/.gradle -w /app gradle:9.6.1-jdk25 gradle bootJar --no-daemon } "orchestrator bootJar"
  Run { docker build -q -f (Join-Path $orch 'Dockerfile.local') -t "local/interview-orchestrator:$Tag" $orch } "build orchestrator"
  Import-Img "local/interview-orchestrator:$Tag"
}

# --- 3. write tags + LAN IP into overlays/dev --------------------------------
Info "updating overlays/dev (image tags + LAN IP)"
$kustom = Join-Path $OverlayDir 'kustomization.yaml'
$txt = Get-Content $kustom -Raw
if ($doWeb)  { $txt = $txt -replace '(name: ghcr\.io/skpandey15/java-ai-mcp-web-ui, newName: local/web-ui, newTag: )\S+', "`${1}$Tag" }
if ($doOrch) { $txt = $txt -replace '(name: ghcr\.io/skpandey15/java-ai-mcp-interview-orchestrator, newName: local/interview-orchestrator, newTag: )\S+', "`${1}$Tag" }
if ($doAi)   { $txt = $txt -replace '(name: ghcr\.io/skpandey15/java-ai-mcp-ai-service, newName: local/ai-service, newTag: )\S+', "`${1}$Tag" }
Set-Content $kustom $txt -Encoding utf8

# repoint LAN IP across every IP-bearing overlay file
$ipRegex = 'dev\.interview\.\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\.nip\.io'
$ipTarget = "dev.interview.$LanIp.nip.io"
foreach ($f in @('kustomization.yaml','ingress.yaml','web-ui-config.js','online-interview-realm.json')) {
  $p = Join-Path $OverlayDir $f
  if (Test-Path $p) { (Get-Content $p -Raw) -replace $ipRegex, $ipTarget | Set-Content $p -Encoding utf8 }
}

Info "kustomize build sanity"
Run { kubectl kustomize $OverlayDir | Out-Null } "kustomize build"

# --- 4. issue TLS for this IP + commit + push --------------------------------
Info "issuing LAN TLS cert for $LanIp"
& (Join-Path $PSScriptRoot 'dev-ca.ps1') -LanIp $LanIp -Namespace $Namespace | Out-Host

Info "commit + push branch '$branch'"
Run { git -C $RepoRoot add platform/kubernetes/overlays/dev } "git add"
git -C $RepoRoot commit -m "deploy dev: $Service @ $shortSha (lanIp $LanIp)" | Out-Host
if ($LASTEXITCODE -ne 0) { Warn "nothing new to commit (overlay unchanged)" }
Run { git -C $RepoRoot push -u origin $branch } "git push"

# --- 5. point Argo at this branch + sync + wait ------------------------------
Info "pointing Argo CD app '$ArgoApp' at branch '$branch' and syncing"
Run { kubectl apply -f (Join-Path $RepoRoot 'platform/gitops/argocd/application-dev.yaml') } "apply Argo app"
Run { kubectl -n argocd patch application $ArgoApp --type merge -p "{`"spec`":{`"source`":{`"targetRevision`":`"$branch`"}}}" } "patch targetRevision"
Run { kubectl -n argocd annotate application $ArgoApp argocd.argoproj.io/refresh=hard --overwrite } "argo refresh"

Info "waiting for Argo sync + rollout"
$deadline = (Get-Date).AddMinutes(5)
do {
  Start-Sleep 6
  $sync   = (kubectl -n argocd get application $ArgoApp -o jsonpath='{.status.sync.status}' 2>$null)
  $health = (kubectl -n argocd get application $ArgoApp -o jsonpath='{.status.health.status}' 2>$null)
  Write-Host "    sync=$sync health=$health"
} while ((($sync -ne 'Synced') -or ($health -ne 'Healthy')) -and ((Get-Date) -lt $deadline))
if ($sync -ne 'Synced' -or $health -ne 'Healthy') { Warn "Argo not fully Synced/Healthy yet — check: kubectl -n argocd get application $ArgoApp" }

Ok "Done. App: https://dev.interview.$LanIp.nip.io:8443"
Write-Host "    Nephew: install scripts/.lan-certs/rootCA.crt once, then open the URL above." -ForegroundColor Green
