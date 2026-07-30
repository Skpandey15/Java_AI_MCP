param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [string]$Namespace = "online-interview",
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"
if (-not $ConfirmRestore) {
    throw "Restore replaces database and knowledge-object data. Re-run with -ConfirmRestore."
}
if ($Namespace -notmatch '^online-interview(?:-[a-z0-9-]+)?$') {
    throw "Refusing unexpected namespace '$Namespace'."
}

$backupRoot = [System.IO.Path]::GetFullPath($BackupDirectory)
$required = @("online_interview.dump", "keycloak.dump", "knowledge-documents.tgz", "SHA256SUMS")
foreach ($file in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $backupRoot $file) -PathType Leaf)) {
        throw "Backup is incomplete: missing $file."
    }
}
Get-Content -LiteralPath (Join-Path $backupRoot "SHA256SUMS") | ForEach-Object {
    if ($_ -notmatch '^([0-9a-f]{64})  (.+)$') { throw "Invalid checksum manifest." }
    $actual = (Get-FileHash -Algorithm SHA256 `
        -LiteralPath (Join-Path $backupRoot $Matches[2])).Hash.ToLowerInvariant()
    if ($actual -ne $Matches[1]) { throw "Checksum mismatch for $($Matches[2])." }
}

$postgresPod = kubectl -n $Namespace get pod -l app.kubernetes.io/name=postgres `
    -o jsonpath='{.items[0].metadata.name}'
$minioPod = kubectl -n $Namespace get pod -l app.kubernetes.io/name=minio `
    -o jsonpath='{.items[0].metadata.name}'
if (-not $postgresPod -or -not $minioPod) {
    throw "PostgreSQL and MinIO must be running before restore."
}

kubectl -n $Namespace scale deployment interview-orchestrator keycloak --replicas=0
try {
    kubectl -n $Namespace cp (Join-Path $backupRoot "online_interview.dump") `
        "${postgresPod}:/tmp/online_interview.dump"
    kubectl -n $Namespace cp (Join-Path $backupRoot "keycloak.dump") `
        "${postgresPod}:/tmp/keycloak.dump"
    kubectl -n $Namespace exec $postgresPod -- pg_restore -U interview `
        --clean --if-exists --no-owner --dbname online_interview /tmp/online_interview.dump
    kubectl -n $Namespace exec $postgresPod -- pg_restore -U interview `
        --clean --if-exists --no-owner --dbname keycloak /tmp/keycloak.dump

    kubectl -n $Namespace cp (Join-Path $backupRoot "knowledge-documents.tgz") `
        "${minioPod}:/tmp/knowledge-documents.tgz"
    kubectl -n $Namespace exec $minioPod -- sh -c `
        'rm -rf /tmp/knowledge-restore && mkdir -p /tmp/knowledge-restore && tar -C /tmp/knowledge-restore -xzf /tmp/knowledge-documents.tgz && mc alias set phase7 http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mb --ignore-existing phase7/knowledge-documents && mc mirror --overwrite --remove /tmp/knowledge-restore/knowledge-documents phase7/knowledge-documents'
} finally {
    kubectl -n $Namespace exec $postgresPod -- rm -f `
        /tmp/online_interview.dump /tmp/keycloak.dump
    kubectl -n $Namespace exec $minioPod -- rm -rf `
        /tmp/knowledge-restore /tmp/knowledge-documents.tgz
    kubectl -n $Namespace scale deployment interview-orchestrator keycloak --replicas=1
}
Write-Host "Restore completed from: $backupRoot"
