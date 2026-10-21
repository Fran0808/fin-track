[CmdletBinding()]
param(
    [string]$OutputPath = '',
    [switch]$Install,
    [string]$Serial = '',
    [string]$SdkPath = '',
    [string]$AdbPath = ''
)

$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'lib/common.ps1')
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$androidDir = Join-Path $projectRoot 'android'
if ($Install -and [string]::IsNullOrWhiteSpace($Serial)) { throw 'Installation requires an explicit -Serial.' }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_SDK_ROOT }
if (-not $SdkPath) {
    $properties = Join-Path $androidDir 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $sdkLine = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $SdkPath = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
}
if (-not $SdkPath) { $SdkPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$aapt = Get-ChildItem -Path (Join-Path $SdkPath 'build-tools/*/aapt2.exe') -ErrorAction SilentlyContinue |
    Where-Object { $_.Directory.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Directory.Name } -Descending | Select-Object -First 1
if (-not $aapt) { throw 'Android SDK aapt2 is required to verify the APK metadata. Supply -SdkPath.' }
if ($Install) {
    if (-not $AdbPath) { $AdbPath = Join-Path $SdkPath 'platform-tools/adb.exe' }
    if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB was not found.' }
    $state = & $AdbPath -s $Serial get-state 2>&1
    if ($LASTEXITCODE -ne 0 -or ($state | Out-String).Trim() -ne 'device') { throw 'Selected device is not ready.' }
}
Push-Location $androidDir
try { Invoke-CheckedCommand -Command '.\gradlew.bat' -Arguments @('assembleDebug') }
finally { Pop-Location }
$builtApk = Join-Path $androidDir 'app/build/outputs/apk/debug/app-debug.apk'
$badging = & $aapt.FullName dump badging $builtApk 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the built APK.' }
$package = (($badging | Out-String) -split "`n" | Where-Object { $_ -match '^package:' }) -join ''
if ($package -notmatch "name='com.financemanager.listener' versionCode='(\d+)' versionName='([^']+)'") {
    throw 'Unexpected APK identity or missing version metadata.'
}
$versionCode = $Matches[1]
$versionName = $Matches[2]
$gradle = Get-Content -LiteralPath (Join-Path $androidDir 'app/build.gradle.kts') -Raw
if ($gradle -notmatch 'versionCode\s*=\s*(\d+)' -or $Matches[1] -ne $versionCode) { throw 'APK versionCode differs from Gradle.' }
if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"' -or $Matches[1] -ne $versionName) { throw 'APK versionName differs from Gradle.' }
if (-not $OutputPath) { $OutputPath = Join-Path $androidDir 'build/deliveries/FinTrack.apk' }
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Path (Split-Path -Parent $OutputPath) -Force | Out-Null
if ($OutputPath -ne [IO.Path]::GetFullPath($builtApk)) { Copy-Item -LiteralPath $builtApk -Destination $OutputPath -Force }
Write-Output "APK verified: FinTrack $versionName ($versionCode)"
Write-Output "Path: $OutputPath"
Write-Output ("SHA-256: {0}" -f (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash)
if ($Install) {
    Invoke-CheckedCommand -Command $AdbPath -Arguments @('-s', $Serial, 'install', '-r', $OutputPath)
    $installed = & $AdbPath -s $Serial shell dumpsys package com.financemanager.listener 2>&1
    if ($LASTEXITCODE -ne 0 -or ($installed | Out-String) -notmatch "versionCode=$versionCode\b" -or
        ($installed | Out-String) -notmatch ("versionName=" + [regex]::Escape($versionName) + '(\s|$)')) {
        throw 'Installed application version could not be verified.'
    }
    Write-Output 'Installation and installed version verified. Runtime capture and synchronization were not tested.'
}
