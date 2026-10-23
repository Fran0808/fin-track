[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$Serial,
    [Parameter(Mandatory = $true)]
    [string]$PythonPath,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB was not found.' }
& $PythonPath (Join-Path $PSScriptRoot 'capture-reboot.py') --adb $AdbPath --serial $Serial
if ($LASTEXITCODE -ne 0) { throw 'Isolated reboot acceptance failed.' }
