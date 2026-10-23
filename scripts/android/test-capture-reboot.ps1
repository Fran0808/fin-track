[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$Serial,
    [Parameter(Mandatory = $true)]
    [string]$PythonPath,
    [ValidateSet('reboot', 'doze')]
    [string]$Scenario = 'reboot',
    [switch]$KillDuringIdle,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB was not found.' }
if ($KillDuringIdle -and $Scenario -ne 'doze') { throw 'Idle kill requires Doze scenario.' }
$scenarioArguments = @()
if ($KillDuringIdle) { $scenarioArguments += '--kill-during-idle' }
& $PythonPath (Join-Path $PSScriptRoot 'capture-reboot.py') --adb $AdbPath --serial $Serial --scenario $Scenario @scenarioArguments
if ($LASTEXITCODE -ne 0) { throw 'Isolated lifecycle acceptance failed.' }
