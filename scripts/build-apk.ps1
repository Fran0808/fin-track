param (
    [string]$OutputPath = "$([Environment]::GetFolderPath('Desktop'))\WalletPulse.apk",
    [switch]$Install = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " WalletPulse Android APK Build Tool" -ForegroundColor Cyan
Write-Host " Target Output: $OutputPath" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
$androidDir = Join-Path $projectRoot "android"
$gradlew = Join-Path $androidDir "gradlew.bat"

if (-not (Test-Path $gradlew)) {
    Write-Host "[ERROR] gradlew.bat not found at: $gradlew" -ForegroundColor Red
    exit 1
}

Write-Host "`n[1/3] Compiling Android Debug APK via Gradle..." -ForegroundColor Yellow
$startTime = Get-Date

Push-Location $androidDir
try {
    & cmd /c "$gradlew assembleDebug"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "`n[ERROR] Gradle build failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}

$elapsed = [Math]::Round(((Get-Date) - $startTime).TotalSeconds, 1)
Write-Host "[PASSED] Gradle build completed in ${elapsed}s" -ForegroundColor Green

$builtApk = Join-Path $androidDir "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $builtApk)) {
    Write-Host "`n[ERROR] Expected APK not found at: $builtApk" -ForegroundColor Red
    exit 1
}

Write-Host "`n[2/3] Copying APK to target destination..." -ForegroundColor Yellow
Copy-Item -Path $builtApk -Destination $OutputPath -Force

$item = Get-Item $OutputPath
$sizeMb = [Math]::Round(($item.Length / 1MB), 2)
$hash = (Get-FileHash -Path $OutputPath -Algorithm SHA256).Hash

Write-Host "[PASSED] APK copied successfully!" -ForegroundColor Green
Write-Host "      Location: $OutputPath"
Write-Host "      Size    : $sizeMb MB ($($item.Length) bytes)"
Write-Host "      SHA-256 : $hash"

if ($Install) {
    Write-Host "`n[3/3] Installing APK to connected Android device via ADB..." -ForegroundColor Yellow
    $adbCmd = Get-Command "adb" -ErrorAction SilentlyContinue
    if ($adbCmd) {
        & adb install -r $OutputPath
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[PASSED] APK installed successfully to device." -ForegroundColor Green
        } else {
            Write-Host "[WARNING] ADB install failed with exit code $LASTEXITCODE" -ForegroundColor Yellow
        }
    } else {
        Write-Host "[WARNING] ADB executable not found on PATH. Skipping device installation." -ForegroundColor Yellow
    }
} else {
    Write-Host "`n[3/3] Device installation skipped (use -Install to push directly via adb)." -ForegroundColor DarkGray
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Build Process Finished Successfully" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan
