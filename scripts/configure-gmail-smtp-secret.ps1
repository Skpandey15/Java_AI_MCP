param(
    [string]$Namespace = 'online-interview-dev',
    [string]$Sender = 'sunil4.java.ai.expert@gmail.com'
)

$ErrorActionPreference = 'Stop'
$securePassword = Read-Host 'Enter the 16-character Google App Password' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainPassword)) {
        throw 'The Google App Password cannot be empty.'
    }

    # Send secret material to kubectl over stdin. It is not placed in the process command line,
    # shell history, source tree, or script output.
    $secretPatch = @{
        apiVersion = 'v1'
        kind = 'Secret'
        metadata = @{name = 'platform-secrets'; namespace = $Namespace}
        type = 'Opaque'
        stringData = @{
            MAIL_USERNAME = $Sender
            MAIL_PASSWORD = $plainPassword
            KEYCLOAK_SMTP_PASSWORD = $plainPassword
        }
    } | ConvertTo-Json -Depth 6 -Compress

    $secretPatch | kubectl apply --server-side --field-manager=gmail-smtp-config -f -
    if ($LASTEXITCODE -ne 0) { throw 'kubectl failed to store the SMTP credentials.' }
    Write-Output 'Gmail SMTP credentials stored successfully.'
} finally {
    if ($pointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
    $plainPassword = $null
    $securePassword.Dispose()
}
