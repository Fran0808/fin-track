[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$DeviceToken = $env:FINTRACK_DEVICE_TOKEN
)

$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'lib/common.ps1')
$origin = Get-ApiOrigin $BaseUrl

function Assert-Unauthorized {
    param([string]$Path, [string]$Method, [hashtable]$Headers = @{})
    $status = 0
    try {
        $null = Invoke-RestMethod -Uri "$origin$Path" -Method $Method -Headers $Headers -TimeoutSec 15
    } catch {
        if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    }
    if ($status -ne 401) { throw 'Security probe failed: expected HTTP 401 (or the API could not be reached).' }
    Write-Output 'Unauthorized access rejected.'
}

Assert-Unauthorized -Path '/api/v1/analytics/summary' -Method Get
Assert-Unauthorized -Path '/api/v1/user/pairing-info/verify' -Method Post -Headers @{
    'X-Device-Token' = 'wp_dev_invalid_probe'
}
if (-not [string]::IsNullOrWhiteSpace($DeviceToken)) {
    try {
        $result = Invoke-RestMethod -Uri "$origin/api/v1/user/pairing-info/verify" -Method Post `
            -Headers @{ 'X-Device-Token' = $DeviceToken } -TimeoutSec 15
    } catch { throw 'Device credential verification failed; response details suppressed.' }
    if ($result.valid -ne $true) { throw 'Device credential was rejected.' }
    Write-Output 'Supplied device credential accepted.'
} else {
    Write-Output 'Authenticated probe skipped: FINTRACK_DEVICE_TOKEN was not supplied.'
}
Write-Output 'API security probes passed. No transactions were inserted or deleted.'
