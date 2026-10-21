[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$Sync,
    [switch]$AllowFinancialWrites
)

$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'lib/common.ps1')
$origin = Get-ApiOrigin $BaseUrl
if ($Sync -and -not $AllowFinancialWrites) {
    throw 'Email ingestion writes financial records. Supply -Sync -AllowFinancialWrites only when authorized.'
}
if ([string]::IsNullOrWhiteSpace($env:FINTRACK_WEB_JWT)) {
    throw 'Set FINTRACK_WEB_JWT privately in this process for the intended user.'
}
$headers = @{ Authorization = "Bearer $env:FINTRACK_WEB_JWT" }
try {
    $connection = Invoke-RestMethod -Uri "$origin/api/v1/emails/test-connection" -Method Get -Headers $headers -TimeoutSec 15
    if ($connection.status -ne 'OK') { throw 'Connection was not successful.' }
    Write-Output 'Email connection check passed.'
    if ($Sync) {
        $result = Invoke-RestMethod -Uri "$origin/api/v1/emails/sync" -Method Post -Headers $headers -TimeoutSec 120
        if ($result.status -ne 'SUCCESS') { throw 'Ingestion was not successful.' }
        Write-Output ("Email sync passed: scanned={0}, processed={1}, saved={2}." -f
            $result.scannedCount, $result.processedInBatch, $result.savedCount)
    }
} catch { throw 'Email operation failed; response details suppressed to protect financial data and credentials.' }
