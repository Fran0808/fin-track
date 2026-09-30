param (
    [ValidateSet("INCOME", "EXPENSE")][string]$FlowType = "INCOME",
    [string]$Amount = "25.50",
    [string]$Contact = "Juan Perez",
    [ValidateSet("SenderPayment", "TeYapearon", "Anonymous", "RecibisteYape", "Expense", "Custom")][string]$Format = "SenderPayment",
    [string]$CustomTitle = "",
    [string]$CustomText = "",
    [string]$Device = "",
    [string]$AdbPath = ""
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Yape Notification Simulator & Validator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. Resolve ADB Executable
if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        $AdbPath = "adb"
    } elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
        $AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    } else {
        $localProps = Join-Path (Split-Path -Parent $PSScriptRoot) "android\local.properties"
        if (Test-Path $localProps) {
            $sdkLine = Get-Content $localProps | Where-Object { $_ -match "^sdk\.dir=(.*)" }
            if ($sdkLine) {
                $candidate = Join-Path ($sdkLine -replace "^sdk\.dir=", "").Trim().Replace("\\", "\") "platform-tools\adb.exe"
                if (Test-Path $candidate) { $AdbPath = $candidate }
            }
        }
    }
}

if (-not $AdbPath -or (-not (Test-Path $AdbPath) -and $AdbPath -ne "adb")) {
    Write-Host "[ERROR] Could not locate adb.exe. Ensure Android SDK platform-tools is installed." -ForegroundColor Red
    exit 1
}

# 2. Check Device Connectivity
$deviceArgs = if (-not [string]::IsNullOrWhiteSpace($Device)) { @("-s", $Device) } else { @() }
$deviceList = @(& $AdbPath @deviceArgs devices | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "List of devices" })

if ($deviceList.Count -eq 0) {
    Write-Host "[ERROR] No online Android device or emulator detected via ADB." -ForegroundColor Red
    Write-Host "        Start an emulator or connect a device with USB Debugging enabled." -ForegroundColor Yellow
    exit 1
}

$activeDevice = ($deviceList[0] -split "\s+")[0]
Write-Host "[ADB] Target Device: $activeDevice" -ForegroundColor DarkGray

# 3. Construct Notification Payload
$title = ""
$bodyText = ""

switch ($Format) {
    "SenderPayment" {
        $title = "Confirmación de Pago"
        $bodyText = "$Contact te envió un pago por S/ $Amount. El cód. de seguridad es: 468"
    }
    "TeYapearon" {
        $title = "¡Te yapearon!"
        $bodyText = "$Contact te envió S/ $Amount a tu Yape"
    }
    "Anonymous" {
        $title = "¡Te yapearon!"
        $bodyText = "Te enviaron S/ $Amount a tu Yape"
    }
    "RecibisteYape" {
        $title = "¡Te yapearon!"
        $bodyText = "Recibiste un yape de S/ $Amount"
    }
    "Expense" {
        $title = "Yapeaste"
        $bodyText = "Yapeaste S/ $Amount a $Contact"
    }
    "Custom" {
        $title = $CustomTitle
        $bodyText = $CustomText
        if ([string]::IsNullOrWhiteSpace($title) -or [string]::IsNullOrWhiteSpace($bodyText)) {
            Write-Host "[ERROR] For -Format Custom, both -CustomTitle and -CustomText must be provided." -ForegroundColor Red
            exit 1
        }
    }
}

$tag = "yape_sim_" + (Get-Date -Format "HHmmss")

Write-Host "`n[Payload to Inject]" -ForegroundColor Yellow
Write-Host " Title : $title"
Write-Host " Text  : $bodyText"
Write-Host " Tag   : $tag"

# 4. Clear Logcat Buffer
& $AdbPath @deviceArgs shell logcat -c

# 5. Inject Notification via ADB
Write-Host "`n[Injecting] Posting notification to Android notification tray..." -NoNewline
$postCmd = "cmd notification post -S bigtext -t '`"$title`"' $tag '`"$bodyText`"'"
$postRes = & $AdbPath @deviceArgs shell $postCmd
Write-Host " [POSTED]" -ForegroundColor Green

# 6. Capture and Validate Logcat Output
Write-Host "[Validating] Waiting for YapeNotificationListenerService..." -ForegroundColor Yellow
Start-Sleep -Seconds 1

$logs = & $AdbPath @deviceArgs shell logcat -d -s NotificationListener SyncWorker

$intercepted = $logs | Where-Object { $_ -match "Intercepted notification for package" }
$parsed = $logs | Where-Object { $_ -match "Parsed successfully:" }
$sync = $logs | Where-Object { $_ -match "SyncWorker" }

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Validation Results" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if ($intercepted) {
    Write-Host "[1/3] Notification Interception : [OK]" -ForegroundColor Green
} else {
    Write-Host "[1/3] Notification Interception : [NOT DETECTED]" -ForegroundColor Red
    Write-Host "      Ensure 'Notification Access' is granted to FinTrack in Android Settings." -ForegroundColor Yellow
}

if ($parsed) {
    Write-Host "[2/3] Parser Extraction         : [OK]" -ForegroundColor Green
    $parseLine = ($parsed | Select-Object -Last 1)
    if ($parseLine -match "Parsed successfully:\s*(.*)$") {
        Write-Host "      Details: $($matches[1])" -ForegroundColor Cyan
    }
} else {
    Write-Host "[2/3] Parser Extraction         : [FAILED]" -ForegroundColor Red
    Write-Host "      The notification text was not recognized by any YapeRegex pattern." -ForegroundColor Yellow
}

if ($sync) {
    Write-Host "[3/3] Background Sync Worker    : [TRIGGERED]" -ForegroundColor Green
} else {
    Write-Host "[3/3] Background Sync Worker    : [PENDING / QUEUED]" -ForegroundColor DarkGray
}

Write-Host "========================================`n" -ForegroundColor Cyan
