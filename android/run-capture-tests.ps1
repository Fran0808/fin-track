[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^emulator-\d+$')]
    [string]$Serial,
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB was not found.' }

function Invoke-IsolatedAdb {
    param([string[]]$Arguments)
    $output = & $AdbPath -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($Arguments -join ' ')`n$output" }
    return $output
}

$deviceState = (Invoke-IsolatedAdb -Arguments @('get-state') | Out-String).Trim()
if ($deviceState -ne 'device') { throw 'The selected emulator is not ready.' }
$deviceName = @(Invoke-IsolatedAdb -Arguments @('emu', 'avd', 'name'))[0].Trim()
if ($deviceName -ne 'FinTrackCapture') { throw 'Use the disposable FinTrackCapture emulator.' }
$booted = (Invoke-IsolatedAdb -Arguments @('shell', 'getprop', 'sys.boot_completed') | Out-String).Trim()
if ($booted -ne '1') { throw 'The selected emulator has not finished booting.' }

Push-Location $PSScriptRoot
try {
    if (-not $SkipBuild) {
        & .\gradlew.bat :app:testDebugUnitTest :app:testCaptureTestUnitTest :app:assembleCaptureTest :app:assembleCaptureTestAndroidTest :emitter:assembleDebug :emitter:assembleDebugAndroidTest --offline
        if ($LASTEXITCODE -ne 0) { throw 'The isolated test build failed.' }
    }

    $artifacts = @(
        @{ Directory = 'app/build/outputs/apk/captureTest'; Package = 'com.financemanager.listener.capturetest' }
        @{ Directory = 'app/build/outputs/apk/androidTest/captureTest'; Package = 'com.financemanager.listener.capturetest.test' }
        @{ Directory = 'emitter/build/outputs/apk/debug'; Package = 'com.fintrack.capture.emitter' }
        @{ Directory = 'emitter/build/outputs/apk/androidTest/debug'; Package = 'com.fintrack.capture.emitter.test' }
    )
    foreach ($artifact in $artifacts) {
        $metadataPath = Join-Path $artifact.Directory 'output-metadata.json'
        $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
        if ($metadata.applicationId -ne $artifact.Package -or $metadata.elements.Count -ne 1) {
            throw 'Unexpected APK identity or split configuration. Nothing will be installed from this entry.'
        }
        $entry = $metadata.elements[0]
        if ($entry.outputFile -ne [IO.Path]::GetFileName($entry.outputFile)) { throw 'Unexpected APK path.' }
        $artifact.Path = Join-Path $artifact.Directory $entry.outputFile
        if (-not (Test-Path -LiteralPath $artifact.Path)) { throw 'A test APK is missing.' }
    }
    foreach ($artifact in $artifacts) {
        Invoke-IsolatedAdb -Arguments @('install', '-r', $artifact.Path)
    }

    $testClasses = @(
        'com.financemanager.listener.CaptureDatabaseTest'
        'com.financemanager.listener.CaptureInboxTest'
        'com.financemanager.listener.PairingAddressPreferencesTest'
        'com.financemanager.listener.ParserPlatformCompatibilityTest'
        'com.financemanager.listener.NotificationDeliveryTest'
    ) -join ','
    $result = Invoke-IsolatedAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $testClasses,
        'com.financemanager.listener.capturetest.test/androidx.test.runner.AndroidJUnitRunner')
    $result | ForEach-Object { Write-Output $_ }
    $text = $result | Out-String
    if ($text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' -or $text -notmatch 'OK \(\d+ tests?\)') {
        throw 'Instrumented tests failed or did not report a successful complete run.'
    }
    $lifecycle = Invoke-IsolatedAdb -Arguments @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'com.fintrack.capture.emitter.CaptureLifecycleTest',
        'com.fintrack.capture.emitter.test/androidx.test.runner.AndroidJUnitRunner')
    $lifecycle | ForEach-Object { Write-Output $_ }
    $lifecycleText = $lifecycle | Out-String
    if ($lifecycleText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|shortMsg=' -or $lifecycleText -notmatch 'OK \(5 tests\)') {
        throw 'Process-death or listener lifecycle tests failed or did not complete.'
    }
} finally {
    try {
        Invoke-IsolatedAdb -Arguments @('shell', 'cmd', 'notification', 'disallow_listener',
            'com.financemanager.listener.capturetest/com.financemanager.listener.service.YapeNotificationListenerService') | Out-Null
    } finally { Pop-Location }
}
