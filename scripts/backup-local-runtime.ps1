param(
    [string]$Namespace = "online-interview",
    [string]$OutputDirectory = ".\backups"
)

$ErrorActionPreference = "Stop"
if ($Namespace -notmatch '^online-interview(?:-[a-z0-9-]+)?$') {
    throw "Refusing unexpected namespace '$Namespace'."
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupRoot = Join-Path $resolvedOutput "online-interview-$stamp"
New-Item -ItemType Directory -Path $backupRoot | Out-Null

$postgresPod = kubectl -n $Namespace get pod -l app.kubernetes.io/name=postgres `
    -o jsonpath='{.items[0].metadata.name}'
$minioPod = kubectl -n $Namespace get pod -l app.kubernetes.io/name=minio `
    -o jsonpath='{.items[0].metadata.name}'
if (-not $postgresPod -or -not $minioPod) {
    throw "PostgreSQL and MinIO must be running before backup."
}

kubectl -n $Namespace exec $postgresPod -- pg_dump -U interview -Fc `
    -f /tmp/online_interview.dump online_interview
kubectl -n $Namespace exec $postgresPod -- pg_dump -U interview -Fc `
    -f /tmp/keycloak.dump keycloak
kubectl -n $Namespace cp "${postgresPod}:/tmp/online_interview.dump" `
    (Join-Path $backupRoot "online_interview.dump")
kubectl -n $Namespace cp "${postgresPod}:/tmp/keycloak.dump" `
    (Join-Path $backupRoot "keycloak.dump")
kubectl -n $Namespace exec $postgresPod -- rm -f `
    /tmp/online_interview.dump /tmp/keycloak.dump

kubectl -n $Namespace exec $minioPod -- sh -c `
    'rm -rf /tmp/knowledge-backup && mkdir -p /tmp/knowledge-backup && mc alias set phase7 http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mirror phase7/knowledge-documents /tmp/knowledge-backup/knowledge-documents && tar -C /tmp/knowledge-backup -czf /tmp/knowledge-documents.tgz .'
kubectl -n $Namespace cp "${minioPod}:/tmp/knowledge-documents.tgz" `
    (Join-Path $backupRoot "knowledge-documents.tgz")
kubectl -n $Namespace exec $minioPod -- rm -rf `
    /tmp/knowledge-backup /tmp/knowledge-documents.tgz

$checksums = Get-ChildItem -LiteralPath $backupRoot -File | ForEach-Object {
    $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName
    "$($hash.Hash.ToLowerInvariant())  $($_.Name)"
}
Set-Content -LiteralPath (Join-Path $backupRoot "SHA256SUMS") -Value $checksums
Write-Host "Backup completed: $backupRoot"
