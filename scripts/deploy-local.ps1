#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Build one (or all) app image(s), load them into the local k3d cluster, and roll out the
  deployment(s) — the whole "see my change in the running UI" loop in one command.

.DESCRIPTION
  This is the local hands-on deploy documented in docs/runbooks/deployment.md, automated.
  It handles the two non-obvious bits:
    * the interview-orchestrator jar is built inside a Gradle+JDK25 container, because local
      Gradle is broken on this machine;
    * a Flyway migration (new src/main/resources/db/migration/V*.sql) must be applied by a
      one-off Job BEFORE the orchestrator rollout — pass -Migrate to do that.

  Requires: Docker, k3d, kubectl on PATH, and the 'dev' k3d cluster running.

.PARAMETER Service
  web-ui | ai-service | interview-orchestrator | all

.PARAMETER Tag
  Image tag to build/deploy. Defaults to a unique timestamp so the rollout always restarts.

.PARAMETER Migrate
  Run the Flyway migration Job (with the freshly built orchestrator image) before rolling out
  the orchestrator. Use whenever the change added a db/migration/V*.sql file. Implies the
  orchestrator is (re)built.

.EXAMPLE
  ./scripts/deploy-local.ps1 -Service ai-service
.EXAMPLE
  ./scripts/deploy-local.ps1 -Service interview-orchestrator -Migrate
.EXAMPLE
  ./scripts/deploy-local.ps1 -Service all -Tag r7
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateSet('web-ui', 'ai-service', 'interview-orchestrator', 'all')]
  [string]$Service,
  [string]$Tag = "local-$(Get-Date -Format 'MMdd-HHmmss')",
  [switch]$Migrate,
  [string]$Cluster = 'dev',
  [string]$Namespace = 'online-interview'
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Info($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    $msg" -ForegroundColor Green }

function Invoke-Checked {
  param([scriptblock]$Cmd, [string]$What)
  & $Cmd
  if ($LASTEXITCODE -ne 0) { throw "$What failed (exit $LASTEXITCODE)" }
}

function Build-Image {
  param([string]$Name, [string]$Context, [string[]]$BuildArgs = @())
  Info "Building ${Name}:$Tag"
  Invoke-Checked { docker build -q @BuildArgs -t "${Name}:$Tag" $Context } "docker build $Name"
}

function Build-Orchestrator {
  $orch = Join-Path $RepoRoot 'apps/interview-orchestrator'
  Info "Building orchestrator bootJar in gradle:9.6.1-jdk25 (local Gradle is unusable here)"
  Invoke-Checked {
    docker run --rm -v "${orch}:/app" -v gradle_cache:/home/gradle/.gradle -w /app `
      gradle:9.6.1-jdk25 gradle bootJar --no-daemon
  } "orchestrator bootJar"
  Info "Containerizing the prebuilt jar (Dockerfile.local)"
  Invoke-Checked {
    docker build -q -f (Join-Path $orch 'Dockerfile.local') -t "online-interview-orchestrator:$Tag" $orch
  } "docker build orchestrator"
}

function Import-ToK3d {
  param([string]$Image)
  Info "Importing $Image into k3d cluster '$Cluster'"
  Invoke-Checked { k3d image import "$Image" -c $Cluster } "k3d image import"
}

function Rollout {
  param([string]$Deploy, [string]$Image)
  Info "Rolling out $Deploy"
  Invoke-Checked { kubectl -n $Namespace set image "deploy/$Deploy" "${Deploy}=$Image" } "set image $Deploy"
  Invoke-Checked { kubectl -n $Namespace rollout status "deploy/$Deploy" --timeout=180s } "rollout $Deploy"
  Ok "$Deploy is live on $Image"
}

function Invoke-Migration {
  param([string]$Image)
  $job = "database-migration-adhoc-$(Get-Date -Format 'MMddHHmmss')"
  Info "Running Flyway migration Job '$job' with $Image"
  $manifest = @"
apiVersion: batch/v1
kind: Job
metadata:
  name: $job
  namespace: $Namespace
spec:
  backoffLimit: 1
  activeDeadlineSeconds: 300
  ttlSecondsAfterFinished: 300
  template:
    metadata:
      labels:
        app.kubernetes.io/name: database-migration
    spec:
      restartPolicy: Never
      serviceAccountName: platform-runtime
      initContainers:
        - name: wait-for-postgres
          image: pgvector/pgvector:0.8.5-pg17-bookworm
          imagePullPolicy: IfNotPresent
          command: [sh, -c, "until pg_isready -h postgres -U interview; do sleep 2; done"]
      containers:
        - name: migrate
          image: $Image
          imagePullPolicy: Never
          args: [--spring.main.web-application-type=none, --spring.flyway.enabled=true]
          envFrom:
            - configMapRef: {name: platform-config}
          env:
            - {name: DATABASE_PASSWORD, valueFrom: {secretKeyRef: {name: platform-secrets, key: DATABASE_PASSWORD}}}
            - {name: AI_SERVICE_TOKEN,  valueFrom: {secretKeyRef: {name: platform-secrets, key: AI_SERVICE_TOKEN}}}
            - {name: SPRING_FLYWAY_ENABLED, value: "true"}
            - {name: OBJECT_STORAGE_ENABLED, value: "false"}
            - {name: MESSAGING_ENABLED, value: "false"}
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: {drop: [ALL]}
"@
  $manifest | kubectl apply -f - | Out-Null
  try {
    Invoke-Checked { kubectl -n $Namespace wait --for=condition=complete "job/$job" --timeout=180s } "migration wait"
    Ok "Migration applied"
    kubectl -n $Namespace logs "job/$job" 2>$null | Select-String 'now at version|Successfully applied' | ForEach-Object { Ok $_.ToString().Trim() }
  } finally {
    kubectl -n $Namespace delete "job/$job" --ignore-not-found | Out-Null
  }
}

# ---- main ----
$deployWebUi = $Service -in @('web-ui', 'all')
$deployAi    = $Service -in @('ai-service', 'all')
$deployOrch  = ($Service -in @('interview-orchestrator', 'all')) -or $Migrate

Info "Deploying [$Service] to '$Cluster'/$Namespace at tag '$Tag'$(if ($Migrate) {' (with migration)'})"

if ($deployWebUi) {
  Build-Image 'online-interview-web-ui' (Join-Path $RepoRoot 'apps/web-ui')
  Import-ToK3d "online-interview-web-ui:$Tag"
}
if ($deployAi) {
  Build-Image 'online-interview-ai-service' (Join-Path $RepoRoot 'apps/ai-service')
  Import-ToK3d "online-interview-ai-service:$Tag"
}
if ($deployOrch) {
  Build-Orchestrator
  Import-ToK3d "online-interview-orchestrator:$Tag"
}

# Migration must run (with the new orchestrator image) BEFORE the orchestrator rollout.
if ($Migrate) { Invoke-Migration "online-interview-orchestrator:$Tag" }

if ($deployWebUi) { Rollout 'web-ui' "online-interview-web-ui:$Tag" }
if ($deployAi)    { Rollout 'ai-service' "online-interview-ai-service:$Tag" }
if ($deployOrch)  { Rollout 'interview-orchestrator' "online-interview-orchestrator:$Tag" }

Info "Done. App: http://interview.localhost:8081"
