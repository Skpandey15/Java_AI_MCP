param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dev', 'uat', 'prod')]
    [string]$Environment,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^sha256:[a-f0-9]{64}$')]
    [string]$WebUiDigest,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^sha256:[a-f0-9]{64}$')]
    [string]$OrchestratorDigest,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^sha256:[a-f0-9]{64}$')]
    [string]$AiServiceDigest
)

$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot "../kubernetes/overlays/$Environment/kustomization.yaml"
$path = [System.IO.Path]::GetFullPath($path)
$content = Get-Content -LiteralPath $path -Raw

$images = [ordered]@{
    'ghcr.io/skpandey15/java-ai-mcp-web-ui' = $WebUiDigest
    'ghcr.io/skpandey15/java-ai-mcp-interview-orchestrator' = $OrchestratorDigest
    'ghcr.io/skpandey15/java-ai-mcp-ai-service' = $AiServiceDigest
}

foreach ($entry in $images.GetEnumerator()) {
    $escapedName = [regex]::Escape($entry.Key)
    $pattern = "(?m)^\s*-\s*\{name:\s*$escapedName,\s*(?:newTag|digest):\s*[^}]+\}\s*$"
    $replacement = "  - {name: $($entry.Key), digest: $($entry.Value)}"
    $updated = [regex]::Replace($content, $pattern, $replacement)
    if ($updated -eq $content) {
        throw "Could not update image entry for $($entry.Key) in $path"
    }
    $content = $updated
}

[System.IO.File]::WriteAllText($path, $content)
Write-Output "Updated $Environment to immutable image digests."
