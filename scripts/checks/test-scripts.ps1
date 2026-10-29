[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$root = Split-Path -Parent $scriptsRoot
$passed = 0
function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
function Assert-Throws([scriptblock]$Action, [string]$Pattern) {
    $caught = $false
    try { & $Action | Out-Null } catch {
        $caught = $true
        if ($_.Exception.Message -notmatch $Pattern) { throw }
    }
    Assert-True $caught 'Expected an operation to fail.'
}
function Test-Case([string]$Name, [scriptblock]$Action) {
    & $Action
    $script:passed++
    Write-Output "PASS: $Name"
}

Test-Case 'All maintained scripts parse' {
    $files = @(Get-ChildItem -LiteralPath $scriptsRoot -Filter '*.ps1' -Recurse)
    foreach ($file in $files) {
        $tokens = $null
        $errors = $null
        $null = [Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$tokens, [ref]$errors)
        Assert-True ($errors.Count -eq 0) "Syntax error in $($file.Name): $errors"
    }
}
Test-Case 'Repository documentation links and script references resolve' {
    & (Join-Path $PSScriptRoot 'test-docs.ps1') | Out-Null
}
Test-Case 'Verifier plans all required checks without executing commands' {
    $plan = & (Join-Path $PSScriptRoot 'verify-all.ps1') -Plan | Out-String
    Assert-True ($plan -match 'mvnw.cmd test .*![*]PersistenceTest,!AppTests') 'Database suites must be excluded.'
    Assert-True ($plan -match '127.0.0.1:1/fintrack_review_no_database') 'Database must be unreachable.'
    Assert-True ($plan -match 'mail.sync.enabled=false') 'Scheduling must be disabled.'
    Assert-True ($plan -match 'npm.cmd run lint' -and $plan -match 'npm.cmd run build') 'UI lint/build are required.'
    Assert-True ($plan -match 'testDebugUnitTest assembleDebug') 'Android test/build are required.'
    Assert-Throws { & (Join-Path $PSScriptRoot 'verify-all.ps1') -SkipApi -SkipUi -SkipAndroid } 'at least one'
}
Test-Case 'API origin validation prevents credential-bearing URLs and remote HTTP' {
    . (Join-Path $scriptsRoot 'lib/common.ps1')
    Assert-True ((Get-ApiOrigin 'http://localhost:8080/') -eq 'http://localhost:8080') 'Local origin failed.'
    Assert-True ((Get-ApiOrigin 'https://example.invalid') -eq 'https://example.invalid') 'HTTPS origin failed.'
    foreach ($url in @('http://example.invalid', 'https://user:private@example.invalid', 'https://example.invalid/path', 'https://example.invalid?token=private')) {
        Assert-Throws { Get-ApiOrigin $url } 'origin|HTTPS'
    }
}
Test-Case 'API probes perform no financial operations and hide identities' {
    $global:FinTrackScriptTestRequests = @()
    function Invoke-RestMethod {
        param($Uri, $Method, $Headers, $TimeoutSec)
        $global:FinTrackScriptTestRequests += "$Method $Uri"
        if ($Headers['X-Device-Token'] -eq 'synthetic-device-credential') {
            return @{ valid = $true; userEmail = 'private@example.invalid' }
        }
        $errorResponse = [Exception]::new('Synthetic rejection')
        $errorResponse | Add-Member -NotePropertyName Response -NotePropertyValue @{ StatusCode = 401 }
        throw $errorResponse
    }
    $output = & (Join-Path $PSScriptRoot 'test-api.ps1') -DeviceToken 'synthetic-device-credential' | Out-String
    Assert-True ($global:FinTrackScriptTestRequests.Count -eq 3) 'Unexpected HTTP requests.'
    Assert-True (($global:FinTrackScriptTestRequests -join ' ') -notmatch 'transactions|sync') 'Financial operation attempted.'
    Assert-True ($output -notmatch 'private@example|synthetic-device-credential') 'Identity or credential leaked.'
}
Test-Case 'API unexpected success and transport errors fail' {
    function Invoke-RestMethod { return @{ status = 'OK' } }
    Assert-Throws { & (Join-Path $PSScriptRoot 'test-api.ps1') -DeviceToken '' } 'expected HTTP 401'
    function Invoke-RestMethod { throw 'Synthetic network failure' }
    Assert-Throws { & (Join-Path $PSScriptRoot 'test-api.ps1') -DeviceToken '' } 'expected HTTP 401'
}
$previousJwt = $env:FINTRACK_WEB_JWT
try {
    Test-Case 'Email operations require explicit credentials and write intent' {
        $env:FINTRACK_WEB_JWT = ''
        function Invoke-RestMethod { throw 'HTTP must not execute before validation.' }
        Assert-Throws { & (Join-Path $PSScriptRoot '../operations/sync-emails.ps1') } 'FINTRACK_WEB_JWT'
        $env:FINTRACK_WEB_JWT = 'synthetic-web-credential'
        Assert-Throws { & (Join-Path $PSScriptRoot '../operations/sync-emails.ps1') -Sync } 'AllowFinancialWrites'
    }
    Test-Case 'Email default is authenticated connection-only; explicit sync prints counts' {
        $env:FINTRACK_WEB_JWT = 'synthetic-web-credential'
        $global:FinTrackScriptTestRequests = @()
        function Invoke-RestMethod {
            param($Uri, $Method, $Headers, $TimeoutSec)
            Assert-True ($Headers.Authorization -eq 'Bearer synthetic-web-credential') 'Missing bearer authentication.'
            $global:FinTrackScriptTestRequests += "$Method $Uri"
            if ($Method -eq 'Get') { return @{ status = 'OK'; connectedUser = 'private@example.invalid' } }
            return @{ status = 'SUCCESS'; scannedCount = 2; processedInBatch = 2; savedCount = 1; transactions = @('PRIVATE') }
        }
        $output = & (Join-Path $PSScriptRoot '../operations/sync-emails.ps1') | Out-String
        Assert-True ($global:FinTrackScriptTestRequests.Count -eq 1 -and $global:FinTrackScriptTestRequests[0] -match '^Get ') 'Default must not ingest.'
        $output += & (Join-Path $PSScriptRoot '../operations/sync-emails.ps1') -Sync -AllowFinancialWrites | Out-String
        Assert-True ($global:FinTrackScriptTestRequests.Count -eq 3 -and $global:FinTrackScriptTestRequests[2] -match '^Post ') 'Explicit sync did not run.'
        Assert-True ($output -match 'saved=1' -and $output -notmatch 'PRIVATE|synthetic-web-credential') 'Output must contain counts only.'
    }
    Test-Case 'Partial ingestion is a failure rather than false success' {
        function Invoke-RestMethod {
            param($Method)
            if ($Method -eq 'Get') { return @{ status = 'OK' } }
            return @{ status = 'PARTIAL' }
        }
        Assert-Throws { & (Join-Path $PSScriptRoot '../operations/sync-emails.ps1') -Sync -AllowFinancialWrites } 'Email operation failed'
    }
} finally { $env:FINTRACK_WEB_JWT = $previousJwt }
Test-Case 'Ambiguous installation fails before device access' {
    Assert-Throws { & (Join-Path $PSScriptRoot '../android/build-apk.ps1') -Install } 'explicit -Serial'
}

# A temporary repository with fake native commands exercises real exit-code propagation.
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('fintrack-script-eval-' + [guid]::NewGuid().ToString('N'))
$oldLog = $env:FINTRACK_SCRIPT_TEST_LOG
$oldFail = $env:FINTRACK_SCRIPT_TEST_FAIL
$oldPath = $env:PATH
try {
    foreach ($dir in @('scripts/lib', 'scripts/checks', 'scripts/setup', 'scripts/android', 'scripts/assets', 'api', 'ui', 'android', 'bin')) {
        New-Item -ItemType Directory -Path (Join-Path $fixture $dir) -Force | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'verify-all.ps1') -Destination (Join-Path $fixture 'scripts/checks')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'test-docs.ps1') -Destination (Join-Path $fixture 'scripts/checks')
    Test-Case 'Documentation checker rejects broken template references and listed Markdown links' {
        function git {
            $global:LASTEXITCODE = 0
            return @('.env.example', 'README.md', 'docs/setup.md')
        }
        Set-Content -LiteralPath (Join-Path $fixture '.env.example') -Value '# See docs/missing.md'
        Assert-Throws { & (Join-Path $fixture 'scripts/checks/test-docs.ps1') } 'docs/missing.md'
        New-Item -ItemType Directory -Path (Join-Path $fixture 'docs') -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $fixture 'docs/setup.md') -Value '# Setup'
        Set-Content -LiteralPath (Join-Path $fixture '.env.example') -Value '# See docs/setup.md'
        Set-Content -LiteralPath (Join-Path $fixture 'README.md') -Value '[Missing](docs/missing.md)'
        Assert-Throws { & (Join-Path $fixture 'scripts/checks/test-docs.ps1') } 'README.md -> docs/missing.md'
        Set-Content -LiteralPath (Join-Path $fixture 'README.md') -Value '[Setup](docs/setup.md#setup)'
        & (Join-Path $fixture 'scripts/checks/test-docs.ps1') | Out-Null
    }
    Copy-Item -LiteralPath (Join-Path $scriptsRoot 'lib/common.ps1') -Destination (Join-Path $fixture 'scripts/lib')
    Copy-Item -LiteralPath (Join-Path $scriptsRoot 'setup/initialize-local.ps1') -Destination (Join-Path $fixture 'scripts/setup')
    Test-Case 'Local initializer plan has no effects and setup preserves private configuration' {
        function node { $global:LASTEXITCODE = 0; return 'v24.0.0' }
        function docker { $global:LASTEXITCODE = 0; return '2.40.0' }
        $initializer = Join-Path $fixture 'scripts/setup/initialize-local.ps1'
        $configuration = Join-Path $fixture '.env'
        $plan = & $initializer -Plan -InstallUiDependencies | Out-String
        Assert-True (-not (Test-Path -LiteralPath $configuration)) 'Plan created configuration.'
        Assert-True ($plan -match 'npm.cmd ci') 'Optional dependency action missing from plan.'
        Set-Content -LiteralPath (Join-Path $fixture '.env.example') -Value 'DB_PASSWORD='
        Assert-Throws { & $initializer } 'configure these .env entries privately'
        Assert-True (Test-Path -LiteralPath $configuration) 'Initial configuration was not created.'
        $privateSettings = "DB_PASSWORD=synthetic-private-password`nJWT_SECRET=synthetic-private-signing-key-at-least-32-bytes`nGOOGLE_CLIENT_ID=synthetic-client`nGOOGLE_CLIENT_SECRET=synthetic-private-client-secret`nAPP_SERVER_PUBLIC_URL=http://localhost:8080"
        Set-Content -LiteralPath $configuration -Value $privateSettings
        $before = [IO.File]::ReadAllBytes($configuration)
        $output = & $initializer | Out-String
        Assert-True ($output -notmatch 'synthetic-private') 'Initializer printed private settings.'
        Assert-True ([Convert]::ToBase64String($before) -eq [Convert]::ToBase64String([IO.File]::ReadAllBytes($configuration))) 'Existing configuration changed.'
        function docker { $global:LASTEXITCODE = 0; return '1.29.0' }
        Assert-Throws { & $initializer } 'Compose v2 or later'
        function docker { $global:LASTEXITCODE = 0; return '5.3.0' }
        & $initializer | Out-Null
        function node { $global:LASTEXITCODE = 0; return 'v20.0.0' }
        Assert-Throws { & $initializer } 'Node.js 24'
        function node { $global:LASTEXITCODE = 0; return 'v24.0.0' }
        function npm.cmd { $global:LASTEXITCODE = 9 }
        Assert-Throws { & $initializer -InstallUiDependencies } 'exit code 9'
    }
    Test-Case 'Persistence plan never starts PostgreSQL or Maven' {
        $plan = & (Join-Path $PSScriptRoot 'test-persistence.ps1') -Plan | Out-String
        Assert-True ($plan -match 'temporary PostgreSQL cluster' -and $plan -match 'three persistence suites') 'Persistence plan is incomplete.'
    }
    $env:FINTRACK_SCRIPT_TEST_LOG = Join-Path $fixture 'commands.txt'
    $env:PATH = (Join-Path $fixture 'bin') + ';' + $env:PATH
    $stub = "@echo off`r`necho %*>> `"%FINTRACK_SCRIPT_TEST_LOG%`"`r`nif `"%FINTRACK_SCRIPT_TEST_FAIL%`"==`"%1%`" exit /b 9`r`nexit /b 0`r`n"
    foreach ($file in @('api/mvnw.cmd', 'android/gradlew.bat', 'bin/npm.cmd')) {
        Set-Content -LiteralPath (Join-Path $fixture $file) -Value $stub -Encoding ascii
    }
    Test-Case 'Verifier executes wrappers and propagates failures while continuing selected checks' {
        $env:FINTRACK_SCRIPT_TEST_FAIL = 'never'
        & (Join-Path $fixture 'scripts/checks/verify-all.ps1') | Out-Null
        $commands = Get-Content -LiteralPath $env:FINTRACK_SCRIPT_TEST_LOG
        Assert-True ($commands.Count -eq 5) 'Expected five actual command invocations.'
        $env:FINTRACK_SCRIPT_TEST_FAIL = 'test'
        Assert-Throws { & (Join-Path $fixture 'scripts/checks/verify-all.ps1') -WarningAction SilentlyContinue } 'verification command\(s\) failed'
        $commands = Get-Content -LiteralPath $env:FINTRACK_SCRIPT_TEST_LOG
        Assert-True ($commands.Count -eq 10) 'Failure must not silently skip remaining checks.'
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../android/build-apk.ps1') -Destination (Join-Path $fixture 'scripts/android')
    New-Item -ItemType Directory -Path (Join-Path $fixture 'android/app/build/outputs/apk/debug') -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $fixture 'android/app/build/outputs/apk/debug/app-debug.apk') -Value 'synthetic artifact'
    Set-Content -LiteralPath (Join-Path $fixture 'android/app/build.gradle.kts') -Value 'versionCode = 17; versionName = "1.3.4"'
    $fakeAapt = Join-Path $fixture 'bin/aapt.ps1'
    Set-Content -LiteralPath $fakeAapt -Value @'
$global:LASTEXITCODE = 0
Write-Output "package: name='com.financemanager.listener' versionCode='17' versionName='1.3.4'"
'@
    $fakeAdb = Join-Path $fixture 'bin/adb.ps1'
    Set-Content -LiteralPath $fakeAdb -Value @'
if ($args[2] -eq 'get-state') { $global:LASTEXITCODE = 0; Write-Output 'device'; return }
if ($args[2] -eq 'install') { $global:LASTEXITCODE = 23; return }
throw 'Unexpected fake ADB command.'
'@
    Test-Case 'APK delivery verifies version/hash and installation failures propagate' {
        function Get-ChildItem {
            param($Path, $ErrorAction)
            return [pscustomobject]@{ FullName = $fakeAapt; Directory = @{ Name = '37.0.0' } }
        }
        $output = & (Join-Path $fixture 'scripts/android/build-apk.ps1') -SdkPath $fixture | Out-String
        Assert-True ($output -match '1.3.4 \(17\)' -and $output -match 'SHA-256: [A-F0-9]{64}') 'APK identity/hash not reported.'
        Assert-True (Test-Path -LiteralPath (Join-Path $fixture 'android/build/deliveries/FinTrack.apk')) 'Delivery artifact missing.'
        Assert-Throws {
            & (Join-Path $fixture 'scripts/android/build-apk.ps1') -SdkPath $fixture -AdbPath $fakeAdb -Install -Serial 'synthetic-device'
        } 'exit code 23'
        Set-Content -LiteralPath (Join-Path $fixture 'android/app/build.gradle.kts') -Value 'versionCode = 18; versionName = "1.3.4"'
        Assert-Throws { & (Join-Path $fixture 'scripts/android/build-apk.ps1') -SdkPath $fixture } 'versionCode differs'
    }
    Test-Case 'Capture runner rejects a non-disposable emulator before installation' {
        Set-Content -LiteralPath $fakeAdb -Value @'
$global:LASTEXITCODE = 0
if ($args[2] -eq 'get-state') { Write-Output 'device'; return }
if ($args[2] -eq 'emu') { Write-Output 'PersonalPhone'; return }
throw 'Unexpected fake ADB command; no installation is permitted.'
'@
        Assert-Throws {
            & (Join-Path $PSScriptRoot '../android/run-capture-tests.ps1') -Serial emulator-5554 -AdbPath $fakeAdb -SkipBuild
        } 'disposable FinTrackCapture'
    }
    Test-Case 'Relocated capture runner resolves artifacts and cleanup with a mocked device' {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../android/run-capture-tests.ps1') -Destination (Join-Path $fixture 'scripts/android')
        $artifacts = @{
            'app/build/outputs/apk/captureTest' = 'com.financemanager.listener.capturetest'
            'app/build/outputs/apk/androidTest/captureTest' = 'com.financemanager.listener.capturetest.test'
            'emitter/build/outputs/apk/debug' = 'com.fintrack.capture.emitter'
            'emitter/build/outputs/apk/androidTest/debug' = 'com.fintrack.capture.emitter.test'
        }
        foreach ($artifact in $artifacts.GetEnumerator()) {
            $directory = Join-Path (Join-Path $fixture 'android') $artifact.Key
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
            @{ applicationId = $artifact.Value; elements = @(@{ outputFile = 'synthetic.apk' }) } |
                ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'output-metadata.json')
            Set-Content -LiteralPath (Join-Path $directory 'synthetic.apk') -Value 'synthetic artifact'
        }
        $global:FinTrackScriptTestRequests = @()
        Set-Content -LiteralPath $fakeAdb -Value @'
$global:LASTEXITCODE = 0
$global:FinTrackScriptTestRequests += ($args -join ' ')
if ($args[2] -eq 'get-state') { Write-Output 'device'; return }
if ($args[2] -eq 'emu') { Write-Output 'FinTrackCapture'; return }
if ($args -contains 'getprop') { Write-Output '1'; return }
if ($args[2] -eq 'install') { Write-Output 'Success'; return }
if ($args -contains 'instrument') {
    if ($args -contains 'com.fintrack.capture.emitter.CaptureLifecycleTest') { Write-Output 'OK (9 tests)' }
    else { Write-Output 'OK (29 tests)' }
    return
}
if ($args -contains 'disallow_listener') { return }
throw 'Unexpected fake ADB command.'
'@
        & (Join-Path $fixture 'scripts/android/run-capture-tests.ps1') -Serial emulator-5554 -AdbPath $fakeAdb -SkipBuild | Out-Null
        Assert-True (@($global:FinTrackScriptTestRequests | Where-Object { $_ -match ' install ' }).Count -eq 4) 'Expected four isolated APK install requests.'
        Assert-True (@($global:FinTrackScriptTestRequests | Where-Object { $_ -match ' instrument ' }).Count -eq 2) 'Expected both instrumented suites.'
        Assert-True ($global:FinTrackScriptTestRequests[-1] -match 'disallow_listener') 'Listener cleanup was not requested.'
    }
    Test-Case 'Brand export generates exactly five correctly sized assets in a temporary repository' {
        New-Item -ItemType Directory -Path (Join-Path $fixture 'assets/branding') -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $root 'assets/branding/fintrack-mark-v1.png') -Destination (Join-Path $fixture 'assets/branding')
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../assets/export-brand-assets.ps1') -Destination (Join-Path $fixture 'scripts/assets')
        & (Join-Path $fixture 'scripts/assets/export-brand-assets.ps1') | Out-Null
        $assets = @{
            'ui/src/assets/fintrack-mark.png' = 192
            'ui/public/favicon.png' = 64
            'ui/public/apple-touch-icon.png' = 180
            'android/app/src/main/res/drawable-nodpi/fintrack_mark.png' = 192
            'android/app/src/main/res/drawable-nodpi/fintrack_launcher_foreground.png' = 432
        }
        foreach ($asset in $assets.GetEnumerator()) {
            $bitmap = [Drawing.Bitmap]::new((Join-Path $fixture $asset.Key))
            try { Assert-True ($bitmap.Width -eq $asset.Value -and $bitmap.Height -eq $asset.Value) 'Unexpected asset size.' }
            finally { $bitmap.Dispose() }
        }
    }
} finally {
    $env:FINTRACK_SCRIPT_TEST_LOG = $oldLog
    $env:FINTRACK_SCRIPT_TEST_FAIL = $oldFail
    $env:PATH = $oldPath
    $resolved = [IO.Path]::GetFullPath($fixture)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolved) -match '^fintrack-script-eval-[a-f0-9]{32}$') {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
Write-Output "$passed script regression groups passed. No real service or device was used."

