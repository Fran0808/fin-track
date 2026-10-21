[CmdletBinding()]
param(
    [switch]$SkipAndroid,
    [switch]$SkipUi,
    [switch]$SkipApi,
    [switch]$Offline,
    [switch]$Plan
)

$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'lib/common.ps1')
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$checks = @()
if (-not $SkipApi) {
    $arguments = @('test', '-Dtest=*Test,!*PersistenceTest,!AppTests', '-Dmail.sync.enabled=false',
        '-Dspring.jpa.hibernate.ddl-auto=validate',
        '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:1/fintrack_review_no_database')
    if ($Offline) { $arguments += '-o' }
    $checks += @{ Directory = 'api'; Command = '.\mvnw.cmd'; Arguments = $arguments }
}
if (-not $SkipUi) {
    foreach ($arguments in @(@('test'), @('run', 'lint'), @('run', 'build'))) {
        $checks += @{ Directory = 'ui'; Command = 'npm.cmd'; Arguments = $arguments }
    }
}
if (-not $SkipAndroid) {
    $arguments = @('testDebugUnitTest', 'assembleDebug')
    if ($Offline) { $arguments += '--offline' }
    $checks += @{ Directory = 'android'; Command = '.\gradlew.bat'; Arguments = $arguments }
}
if ($checks.Count -eq 0) { throw 'Select at least one module to verify.' }
$failures = 0
foreach ($check in $checks) {
    Write-Output ("[{0}] {1} {2}" -f $check.Directory, $check.Command, ($check.Arguments -join ' '))
    if ($Plan) { continue }
    Push-Location (Join-Path $projectRoot $check.Directory)
    try {
        Invoke-CheckedCommand -Command $check.Command -Arguments $check.Arguments
    } catch {
        $failures++
        Write-Warning $_.Exception.Message
    } finally { Pop-Location }
}
if ($failures -gt 0) { throw "$failures verification command(s) failed." }
if (-not $Plan) {
    Write-Output 'Selected checks passed. Database integration, devices and live ingestion were not exercised.'
}
