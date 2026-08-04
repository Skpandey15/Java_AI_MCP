#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Run focused local CI and continuously deploy changed services to the k3d dev cluster.

.DESCRIPTION
  Detects changes under the three application directories, validates only affected services,
  and delegates image build/import/rollout to deploy-local.ps1. In watch mode it debounces file
  changes and ignores generated build/cache directories. A changed Flyway migration
  automatically starts PostgreSQL if needed, runs the migration before rollout, and restores
  PostgreSQL to its original replica count. This script never deletes namespaces, StatefulSets,
  persistent volume claims, or persistent volumes.

.EXAMPLE
  ./scripts/local-ci-cd.ps1
  Run CI/CD once for application changes relative to HEAD.

.EXAMPLE
  ./scripts/local-ci-cd.ps1 -Watch
  Watch application source files and continuously validate and deploy affected services.

.EXAMPLE
  ./scripts/local-ci-cd.ps1 -Service ai-service -SkipTests
  Force a one-time AI service deployment without running local CI.
#>
[CmdletBinding()]
param(
  [ValidateSet('changed', 'web-ui', 'ai-service', 'interview-orchestrator', 'all')]
  [string]$Service = 'changed',
  [switch]$Watch,
  [switch]$RunInitial,
  [switch]$SkipTests,
  [ValidateRange(1, 60)]
  [int]$PollSeconds = 2,
  [ValidateRange(1, 120)]
  [int]$DebounceSeconds = 3,
  [string]$Cluster = 'dev',
  [string]$Namespace = 'online-interview'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$DeployScript = Join-Path $PSScriptRoot 'deploy-local.ps1'
$MigrationPrefix = 'apps/interview-orchestrator/src/main/resources/db/migration/'
$ServiceOrder = @('web-ui', 'ai-service', 'interview-orchestrator')
$IgnoredSegments = @('/node_modules/', '/dist/', '/build/', '/.gradle/', '/.pytest_cache/', '/.ruff_cache/', '/__pycache__/', '/.venv/')

function Write-Info($Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok($Message) { Write-Host "    $Message" -ForegroundColor Green }
function Write-Warn($Message) { Write-Host "    $Message" -ForegroundColor Yellow }

function Invoke-Checked {
  param([scriptblock]$Command, [string]$Description)
  & $Command
  if ($LASTEXITCODE -ne 0) { throw "$Description failed (exit $LASTEXITCODE)" }
}

function Assert-Command {
  param([string]$Name)
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command '$Name' was not found on PATH."
  }
}

function Assert-LocalEnvironment {
  foreach ($command in @('git', 'docker', 'k3d', 'kubectl')) { Assert-Command $command }
  if (-not $SkipTests) {
    if ($script:SelectedServices -contains 'web-ui') { Assert-Command 'npm' }
    if ($script:SelectedServices -contains 'ai-service') { Assert-Command 'uv' }
  }
  Invoke-Checked { k3d cluster get $Cluster | Out-Null } "find k3d cluster '$Cluster'"
  $context = kubectl config current-context
  if ($LASTEXITCODE -ne 0) { throw 'read kubectl context failed' }
  if ($context -ne "k3d-$Cluster") {
    throw "Current kubectl context is '$context'; expected 'k3d-$Cluster'. Refusing to deploy."
  }
  Invoke-Checked { kubectl get namespace $Namespace | Out-Null } "find namespace '$Namespace'"
}

function Convert-ToRepoPath {
  param([string]$Path)
  return $Path.Replace('\', '/').TrimStart([char[]]@('.', '/'))
}

function Get-ServicesForPaths {
  param([string[]]$Paths)
  $selected = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
  foreach ($rawPath in $Paths) {
    $path = Convert-ToRepoPath $rawPath
    if ($path.StartsWith('apps/web-ui/')) { [void]$selected.Add('web-ui') }
    if ($path.StartsWith('apps/ai-service/')) { [void]$selected.Add('ai-service') }
    if ($path.StartsWith('apps/interview-orchestrator/')) { [void]$selected.Add('interview-orchestrator') }
    if ($path.StartsWith('platform/docker/') -or $path.StartsWith('platform/kubernetes/')) {
      foreach ($name in $ServiceOrder) { [void]$selected.Add($name) }
    }
  }
  return @($ServiceOrder | Where-Object { $selected.Contains($_) })
}

function Get-GitChangedPaths {
  $paths = @()
  $paths += @(git -c "safe.directory=$($RepoRoot.Replace('\', '/'))" diff --name-only HEAD -- apps platform)
  if ($LASTEXITCODE -ne 0) { throw 'git diff failed' }
  $paths += @(git -c "safe.directory=$($RepoRoot.Replace('\', '/'))" ls-files --others --exclude-standard -- apps platform)
  if ($LASTEXITCODE -ne 0) { throw 'git untracked-file scan failed' }
  return @($paths | Where-Object { $_ } | Sort-Object -Unique)
}

function Get-WatchSnapshot {
  $snapshot = @{}
  foreach ($rootName in @('apps', 'platform')) {
    $root = Join-Path $RepoRoot $rootName
    Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
      $relative = $_.FullName.Substring($RepoRoot.Length).TrimStart([char[]]@('\', '/')).Replace('\', '/')
      $sentinel = "/$relative/"
      $ignored = $false
      foreach ($segment in $IgnoredSegments) {
        if ($sentinel.Contains($segment)) { $ignored = $true; break }
      }
      if (-not $ignored) { $snapshot[$relative] = "$($_.Length):$($_.LastWriteTimeUtc.Ticks)" }
    }
  }
  return $snapshot
}

function Compare-Snapshot {
  param([hashtable]$Before, [hashtable]$After)
  $changed = [System.Collections.Generic.HashSet[string]]::new()
  foreach ($path in $Before.Keys) {
    if (-not $After.ContainsKey($path) -or $Before[$path] -ne $After[$path]) { [void]$changed.Add($path) }
  }
  foreach ($path in $After.Keys) {
    if (-not $Before.ContainsKey($path)) { [void]$changed.Add($path) }
  }
  return @($changed | Sort-Object)
}

function Test-MigrationChanged {
  param([string[]]$Paths)
  return [bool]($Paths | Where-Object { (Convert-ToRepoPath $_).StartsWith($MigrationPrefix) } | Select-Object -First 1)
}

function Invoke-ServiceTests {
  param([string]$Name)
  if ($SkipTests) { Write-Warn "Skipping tests for $Name"; return }
  Write-Info "Local CI: $Name"
  switch ($Name) {
    'web-ui' {
      Push-Location (Join-Path $RepoRoot 'apps/web-ui')
      try {
        Invoke-Checked { npm ci } 'web-ui npm ci'
        Invoke-Checked { npm test } 'web-ui tests'
        Invoke-Checked { npm run build } 'web-ui build'
      } finally { Pop-Location }
    }
    'ai-service' {
      Push-Location (Join-Path $RepoRoot 'apps/ai-service')
      try {
        Invoke-Checked { uv sync --frozen --extra dev } 'ai-service dependency sync'
        Invoke-Checked { uv run --extra dev ruff check . } 'ai-service lint'
        Invoke-Checked { uv run --extra dev pytest --cov=app --cov-fail-under=95 } 'ai-service tests'
        Invoke-Checked { uv run python -m evaluation.evaluator evaluation/release-dataset.jsonl --output ai-quality-report.json } 'ai-service quality gate'
      } finally { Pop-Location }
    }
    'interview-orchestrator' {
      $orch = Join-Path $RepoRoot 'apps/interview-orchestrator'
      Invoke-Checked {
        docker run --rm -v "${orch}:/app" -v gradle_cache:/home/gradle/.gradle -w /app `
          gradle:9.6.1-jdk25 gradle clean test jacocoTestCoverageVerification bootJar --no-daemon
      } 'interview-orchestrator tests'
    }
  }
  Write-Ok "$Name CI passed"
}

function Get-PostgresReplicas {
  $value = kubectl -n $Namespace get statefulset/postgres -o 'jsonpath={.spec.replicas}'
  if ($LASTEXITCODE -ne 0) { throw 'read PostgreSQL replica count failed' }
  return [int]$value
}

function Invoke-Deploy {
  param([string]$Name, [bool]$Migrate, [string]$Tag)
  $postgresReplicas = $null
  try {
    if ($Migrate) {
      $postgresReplicas = Get-PostgresReplicas
      if ($postgresReplicas -eq 0) {
        Write-Info 'Temporarily starting PostgreSQL for Flyway migration'
        Invoke-Checked { kubectl -n $Namespace scale statefulset/postgres --replicas=1 } 'start PostgreSQL'
        Invoke-Checked { kubectl -n $Namespace rollout status statefulset/postgres --timeout=180s } 'wait for PostgreSQL'
      }
    }
    $arguments = @('-Service', $Name, '-Tag', $Tag, '-Cluster', $Cluster, '-Namespace', $Namespace)
    if ($Migrate) { $arguments += '-Migrate' }
    & $DeployScript @arguments
    if (-not $?) { throw "deployment failed for $Name" }
  } finally {
    if ($Migrate -and $postgresReplicas -eq 0) {
      Write-Info 'Restoring PostgreSQL to zero replicas'
      Invoke-Checked { kubectl -n $Namespace scale statefulset/postgres --replicas=0 } 'restore PostgreSQL replica count'
    }
  }
}

function Test-DeploymentHealth {
  param([string]$Name)
  $desired = kubectl -n $Namespace get "deployment/$Name" -o 'jsonpath={.spec.replicas}'
  if ($LASTEXITCODE -ne 0) { throw "read $Name deployment failed" }
  if ([int]$desired -eq 0) {
    Write-Warn "$Name is intentionally scaled to zero; image reference updated, readiness check skipped"
    return
  }
  Invoke-Checked { kubectl -n $Namespace rollout status "deployment/$Name" --timeout=180s } "$Name rollout health"
  $available = kubectl -n $Namespace get "deployment/$Name" -o 'jsonpath={.status.availableReplicas}'
  $availableCount = if ($available) { [int]$available } else { 0 }
  if ($LASTEXITCODE -ne 0 -or $availableCount -lt [int]$desired) {
    throw "$Name is not fully available ($available/$desired replicas)"
  }
  Write-Ok "$Name is healthy ($availableCount/$desired replicas available)"
}

function Invoke-Pipeline {
  param([string[]]$Paths, [string[]]$ForcedServices = @())
  $services = if ($ForcedServices.Count -gt 0) { $ForcedServices } else { Get-ServicesForPaths $Paths }
  if ($services.Count -eq 0) { Write-Warn 'No deployable application changes detected'; return }
  $script:SelectedServices = @($services)
  Assert-LocalEnvironment
  $migrate = Test-MigrationChanged $Paths
  $tag = "local-ci-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
  Write-Info "Affected services: $($services -join ', ')"
  if ($migrate) { Write-Info 'Flyway migration change detected' }
  foreach ($name in $services) { Invoke-ServiceTests $name }
  foreach ($name in $services) {
    Invoke-Deploy -Name $name -Migrate:($migrate -and $name -eq 'interview-orchestrator') -Tag $tag
    Test-DeploymentHealth $name
  }
  Write-Ok "Local CI/CD completed at tag $tag"
}

Push-Location $RepoRoot
try {
  $forced = if ($Service -eq 'all') { $ServiceOrder } elseif ($Service -ne 'changed') { @($Service) } else { @() }
  if (-not $Watch) {
    $paths = Get-GitChangedPaths
    Invoke-Pipeline -Paths $paths -ForcedServices $forced
    return
  }

  Write-Info "Watching apps/ and platform/ every $PollSeconds second(s); press Ctrl+C to stop"
  $snapshot = Get-WatchSnapshot
  if ($RunInitial) {
    $initialPaths = Get-GitChangedPaths
    Invoke-Pipeline -Paths $initialPaths -ForcedServices $forced
  }
  while ($true) {
    Start-Sleep -Seconds $PollSeconds
    $next = Get-WatchSnapshot
    $changed = Compare-Snapshot -Before $snapshot -After $next
    if ($changed.Count -eq 0) { continue }
    Start-Sleep -Seconds $DebounceSeconds
    $settled = Get-WatchSnapshot
    $changed += Compare-Snapshot -Before $next -After $settled
    $snapshot = $settled
    $changed = @($changed | Sort-Object -Unique)
    Write-Info "Detected $($changed.Count) changed path(s)"
    try { Invoke-Pipeline -Paths $changed -ForcedServices $forced }
    catch { Write-Host "==> Local CI/CD failed: $($_.Exception.Message)" -ForegroundColor Red }
  }
} finally {
  Pop-Location
}
