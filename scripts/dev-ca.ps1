<#
.SYNOPSIS
  Local CA + LAN TLS for the k3d "dev" stack (namespace online-interview-dev).

.DESCRIPTION
  Creates a long-lived local root CA once, then issues a leaf certificate for the
  nip.io LAN hosts and writes it into the cluster TLS secret(s) that the dev
  ingress serves. Install the emitted rootCA.crt as a Trusted Root ONCE on every
  machine that opens the app (this desktop + your nephew's laptop) and HTTPS shows
  a real padlock. Re-run with a new -LanIp after the desktop's LAN address changes;
  the root CA is reused, so already-installed clients keep trusting the new leaf.

  Requires: openssl and kubectl (context k3d-dev) on PATH.

.EXAMPLE
  pwsh ./scripts/dev-ca.ps1                 # auto-detect Wi-Fi IPv4
  pwsh ./scripts/dev-ca.ps1 -LanIp 192.168.1.6
#>
[CmdletBinding()]
param(
  [string]$LanIp,
  [string]$Namespace = 'online-interview-dev',
  [string]$CertDir   = (Join-Path $PSScriptRoot '.lan-certs'),
  [string[]]$SecretNames = @('online-interview-dev-tls'),
  [int]$LeafDays = 825,
  [int]$CaDays   = 3650
)
$ErrorActionPreference = 'Stop'

function Resolve-OpenSsl {
  $cmd = Get-Command openssl -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  foreach ($p in @("$env:ProgramFiles\Git\usr\bin\openssl.exe", "$env:ProgramFiles\Git\mingw64\bin\openssl.exe")) {
    if (Test-Path $p) { return $p }
  }
  throw "openssl not found. Install Git for Windows (bundles openssl) or add openssl to PATH."
}

function Get-WifiIPv4 {
  $ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.InterfaceAlias -match 'Wi-?Fi' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
  if (-not $ip) {
    $ip = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
      Where-Object { $_.IPAddress -like '192.168.*' -or $_.IPAddress -like '10.*' } |
      Select-Object -First 1 -ExpandProperty IPAddress
  }
  return $ip
}

if (-not $LanIp) { $LanIp = Get-WifiIPv4 }
if (-not $LanIp) { throw "Could not auto-detect a LAN IPv4. Pass -LanIp explicitly." }

$openssl = Resolve-OpenSsl
$env:MSYS_NO_PATHCONV = '1'   # stop Git-Bash openssl from mangling /CN=... subjects
New-Item -ItemType Directory -Force -Path $CertDir | Out-Null

$domain = "dev.interview.$LanIp.nip.io"
$hosts  = @($domain, "api.$domain", "auth.$domain", "ai.$domain", "litellm.$domain", "mail.$domain")

$caKey = Join-Path $CertDir 'rootCA.key'
$caCrt = Join-Path $CertDir 'rootCA.crt'
$leafKey = Join-Path $CertDir 'tls.key'
$leafCsr = Join-Path $CertDir 'tls.csr'
$leafCrt = Join-Path $CertDir 'tls.crt'
$fullchain = Join-Path $CertDir 'fullchain.crt'
$leafCnf = Join-Path $CertDir 'leaf.cnf'

# --- Root CA (created once, then reused across IP changes) ---
if (-not (Test-Path $caCrt)) {
  Write-Host "== creating root CA (once) ==" -ForegroundColor Cyan
  & $openssl genrsa -out $caKey 4096
  & $openssl req -x509 -new -nodes -key $caKey -sha256 -days $CaDays `
      -subj "/CN=Online Interview Dev Local CA/O=Online Interview" -out $caCrt
} else {
  Write-Host "== reusing existing root CA ($caCrt) ==" -ForegroundColor DarkCyan
}

# --- Leaf CSR config (SANs) ---
$san = ($hosts | ForEach-Object -Begin { $i = 0 } -Process { $i++; "DNS.$i = $_" }) -join "`n"
@"
[req]
distinguished_name = dn
req_extensions = v3_req
prompt = no
[dn]
CN = $domain
[v3_req]
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt
[alt]
$san
"@ | Set-Content -Path $leafCnf -Encoding ascii

Write-Host "== issuing leaf for: $($hosts -join ', ') ==" -ForegroundColor Cyan
if (-not (Test-Path $leafKey)) { & $openssl genrsa -out $leafKey 2048 }
& $openssl req -new -key $leafKey -subj "/CN=$domain" -config $leafCnf -out $leafCsr
& $openssl x509 -req -in $leafCsr -CA $caCrt -CAkey $caKey -CAcreateserial `
    -days $LeafDays -sha256 -extensions v3_req -extfile $leafCnf -out $leafCrt
Get-Content $leafCrt, $caCrt | Set-Content -Path $fullchain -Encoding ascii
& $openssl verify -CAfile $caCrt $leafCrt

# --- Write cluster TLS secret(s) ---
foreach ($name in $SecretNames) {
  Write-Host "== applying secret $Namespace/$name ==" -ForegroundColor Cyan
  $yaml = & kubectl -n $Namespace create secret tls $name --cert=$fullchain --key=$leafKey --dry-run=client -o yaml
  $yaml | & kubectl apply -f -
}

Write-Host ""
Write-Host "Done. App URL:  https://${domain}:8443" -ForegroundColor Green
Write-Host "Trust this root ONCE per machine (elevated PowerShell):" -ForegroundColor Yellow
Write-Host "  certutil -addstore -f Root `"$caCrt`""
Write-Host "Copy $caCrt to your nephew's laptop and run the same command there."
