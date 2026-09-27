param (
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DeviceToken = ""
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " WalletPulse REST API Smoke Tests" -ForegroundColor Cyan
Write-Host " Target: $BaseUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 0. Resolve Device Token if not provided
if ([string]::IsNullOrWhiteSpace($DeviceToken)) {
    Write-Host "`n[Auto-Detect] DeviceToken not supplied via argument. Querying local database..." -ForegroundColor Yellow
    try {
        $env:PGPASSWORD = 'admin'
        $dbToken = (psql -h 127.0.0.1 -U postgres -d finance_db -t -A -c "SELECT device_pairing_token FROM users LIMIT 1;" 2>$null)
        if (-not [string]::IsNullOrWhiteSpace($dbToken)) {
            $DeviceToken = $dbToken.Trim()
            Write-Host "             Found token for user in DB: $($DeviceToken.Substring(0, [Math]::Min(15, $DeviceToken.Length)))..." -ForegroundColor Green
        }
    } catch {
        Write-Host "             Could not auto-query PostgreSQL. Proceeding with fallback." -ForegroundColor DarkGray
    }
}

# 1. Security Check: Verify Anonymous Access is Rejected
Write-Host "`n[1/5] Testing Security Boundary: Anonymous GET /api/v1/analytics/summary..." -NoNewline
try {
    $null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/analytics/summary" -Method Get -ErrorAction Stop
    Write-Host " [FAILED] (Anonymous request should have been rejected with 401)" -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host " [PASSED] (HTTP 401 Unauthorized as expected)" -ForegroundColor Green
    } else {
        Write-Host " [FAILED] (Expected 401, got $statusCode)" -ForegroundColor Red
    }
}

# 2. Public Verification Endpoint Check
Write-Host "`n[2/5] Testing POST /api/v1/user/pairing-info/verify (Invalid Token)..." -NoNewline
try {
    $null = Invoke-RestMethod -Uri "$BaseUrl/api/v1/user/pairing-info/verify" -Method Post -Headers @{ "X-Device-Token" = "wp_dev_invalid_probe" } -ContentType "application/json" -Body "{}" -ErrorAction Stop
    Write-Host " [FAILED] (Invalid token should return 401)" -ForegroundColor Red
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host " [PASSED] (HTTP 401 Invalid Token as expected)" -ForegroundColor Green
    } else {
        Write-Host " [FAILED] (Expected 401, got $statusCode)" -ForegroundColor Red
    }
}

# 3. Verify Device Token Authenticated Endpoint
if (-not [string]::IsNullOrWhiteSpace($DeviceToken)) {
    Write-Host "`n[3/5] Testing POST /api/v1/user/pairing-info/verify (Valid Token)..." -NoNewline
    try {
        $verifyRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/user/pairing-info/verify" -Method Post -Headers @{ "X-Device-Token" = $DeviceToken } -ContentType "application/json" -Body "{}" -ErrorAction Stop
        if ($verifyRes.valid -eq $true) {
            Write-Host " [PASSED]" -ForegroundColor Green
            Write-Host "      Paired User: $($verifyRes.userEmail)"
            Write-Host "      User ID    : $($verifyRes.userId)"
        } else {
            Write-Host " [FAILED] (Token rejected by server)" -ForegroundColor Red
        }
    } catch {
        Write-Host " [FAILED] $($_.Exception.Message)" -ForegroundColor Red
    }

    # 4. Ingest Single Transaction with X-Device-Token
    $txHash = [System.Guid]::NewGuid().ToString("N") + [System.Guid]::NewGuid().ToString("N")
    $txBody = @{
        amount = 5.00
        flowType = "EXPENSE"
        contactName = "Smoke Test Yape Contact"
        channel = "YAPE"
        transactionDate = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
        transactionHash = $txHash
        rawNotificationText = "¡Enviaste un pago a Smoke Test Yape Contact por S/ 5.00!"
    } | ConvertTo-Json

    Write-Host "`n[4/5] Testing Mobile Ingestion with X-Device-Token (POST /api/v1/transactions/sync)..." -NoNewline
    try {
        $ingestRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/sync" -Method Post -Body $txBody -ContentType "application/json" -Headers @{ "X-Device-Token" = $DeviceToken } -ErrorAction Stop
        Write-Host " [PASSED]" -ForegroundColor Green
        Write-Host "      Transaction ID: $($ingestRes.id)"
        Write-Host "      Amount        : S/ $($ingestRes.amount)"
        Write-Host "      Channel       : $($ingestRes.channel)"

        # 5. Test Deduplication Idempotency
        Write-Host "`n[5/5] Testing Deduplication Idempotency (Re-send same transaction hash)..." -NoNewline
        $dupRes = Invoke-RestMethod -Uri "$BaseUrl/api/v1/transactions/sync" -Method Post -Body $txBody -ContentType "application/json" -Headers @{ "X-Device-Token" = $DeviceToken } -ErrorAction Stop
        if ($dupRes.id -eq $ingestRes.id) {
            Write-Host " [PASSED] (Duplicate safely ignored, identical ID returned)" -ForegroundColor Green
        } else {
            Write-Host " [FAILED] (Duplicate created a new entry with ID $($dupRes.id))" -ForegroundColor Red
        }

        # Clean up test transaction from DB
        try {
            $env:PGPASSWORD = 'admin'
            $null = (psql -h 127.0.0.1 -U postgres -d finance_db -c "DELETE FROM transactions WHERE transaction_hash = '$txHash';" 2>$null)
            Write-Host "`n[Clean-up] Removed smoke test transaction ($txHash) from database." -ForegroundColor DarkGray
        } catch {}
    } catch {
        Write-Host " [FAILED] $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "`n[3-5/5] Skipping authenticated tests because no DeviceToken was found or provided." -ForegroundColor Yellow
    Write-Host "       Pass -DeviceToken 'wp_dev_...' to execute authenticated smoke tests." -ForegroundColor DarkGray
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " Smoke Tests Completed" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan