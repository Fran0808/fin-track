param (
    [switch]$SkipAndroid = $false,
    [switch]$SkipUi = $false,
    [switch]$SkipApi = $false
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " FinTrack Full Monorepo Verification" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir

$results = @()

# 1. Backend Verification (Java 21 / Spring Boot 3)
if (-not $SkipApi) {
    Write-Host "`n[1/3] Verifying Backend (api): Running Maven Tests..." -ForegroundColor Yellow
    $apiDir = Join-Path $projectRoot "api"
    $apiStart = Get-Date
    Push-Location $apiDir
    try {
        & mvn clean test
        $apiExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $apiDuration = [Math]::Round(((Get-Date) - $apiStart).TotalSeconds, 1)
    $results += [PSCustomObject]@{
        Module   = "Backend (api)"
        Command  = "mvn clean test"
        Status   = if ($apiExit -eq 0) { "PASSED" } else { "FAILED" }
        Duration = "${apiDuration}s"
    }
} else {
    Write-Host "`n[1/3] Backend (api) verification skipped by flag." -ForegroundColor DarkGray
}

# 2. Frontend Verification (React 19 / TypeScript / Vite)
if (-not $SkipUi) {
    Write-Host "`n[2/3] Verifying Frontend (ui): Running Vitest & Vite Build..." -ForegroundColor Yellow
    $uiDir = Join-Path $projectRoot "ui"
    $uiStart = Get-Date
    Push-Location $uiDir
    try {
        & npm test
        $testExit = $LASTEXITCODE
        if ($testExit -eq 0) {
            & npm run build
            $uiExit = $LASTEXITCODE
        } else {
            $uiExit = $testExit
        }
    } finally {
        Pop-Location
    }
    $uiDuration = [Math]::Round(((Get-Date) - $uiStart).TotalSeconds, 1)
    $results += [PSCustomObject]@{
        Module   = "Frontend (ui)"
        Command  = "npm test && npm run build"
        Status   = if ($uiExit -eq 0) { "PASSED" } else { "FAILED" }
        Duration = "${uiDuration}s"
    }
} else {
    Write-Host "`n[2/3] Frontend (ui) verification skipped by flag." -ForegroundColor DarkGray
}

# 3. Android Verification (Kotlin / Jetpack Compose)
if (-not $SkipAndroid) {
    Write-Host "`n[3/3] Verifying Android (android): Running Gradle Unit Tests..." -ForegroundColor Yellow
    $androidDir = Join-Path $projectRoot "android"
    $gradlew = Join-Path $androidDir "gradlew.bat"
    $androidStart = Get-Date
    Push-Location $androidDir
    try {
        & cmd /c "$gradlew testDebugUnitTest"
        $androidExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    $androidDuration = [Math]::Round(((Get-Date) - $androidStart).TotalSeconds, 1)
    $results += [PSCustomObject]@{
        Module   = "Android (android)"
        Command  = "gradlew testDebugUnitTest"
        Status   = if ($androidExit -eq 0) { "PASSED" } else { "FAILED" }
        Duration = "${androidDuration}s"
    }
} else {
    Write-Host "`n[3/3] Android (android) verification skipped by flag." -ForegroundColor DarkGray
}

# Summary Table
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Verification Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$anyFailed = $false
foreach ($res in $results) {
    $color = if ($res.Status -eq "PASSED") { "Green" } else { "Red" }
    if ($res.Status -ne "PASSED") { $anyFailed = $true }
    Write-Host (" {0,-18} | {1,-30} | {2,-8} | {3,6}" -f $res.Module, $res.Command, $res.Status, $res.Duration) -ForegroundColor $color
}

Write-Host "========================================" -ForegroundColor Cyan

if ($anyFailed) {
    Write-Host " Verification finished with failures." -ForegroundColor Red
    exit 1
} else {
    Write-Host " All modules verified successfully! Everything is green." -ForegroundColor Green
    exit 0
}
