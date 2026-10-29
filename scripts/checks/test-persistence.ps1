[CmdletBinding()]
param(
    [string]$PgBinPath,
    [string]$JavaHome,
    [switch]$Offline,
    [switch]$Plan
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $root 'scripts/lib/common.ps1')
if ($Plan) {
    Write-Output 'Check Java 21 (optional -JavaHome) and the installed PostgreSQL command-line tools.'
    Write-Output 'Create a new temporary PostgreSQL cluster on a free loopback port with private synthetic credentials.'
    Write-Output 'Create three disposable suite databases and seven disposable migration databases.'
    Write-Output 'Run provenance/metadata/operation/capture-origin/source-diagnostics/manual/remove-instruments migration regressions and Maven verify with all three persistence suites enabled and email scheduling disabled.'
    Write-Output 'Stop only the temporary cluster, remove its directory and restore process environment variables.'
    return
}
$javaCommand = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' }
    elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
try { $ErrorActionPreference = 'Continue'; $javaVersion = (& $javaCommand -version 2>&1 | Out-String) }
finally { $ErrorActionPreference = 'Stop' }
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21[.\"]') {
    throw 'Java 21 is required. Set JAVA_HOME or supply -JavaHome pointing at the intended JDK.'
}
if (-not $PgBinPath) {
    $initdb = Get-Command initdb -ErrorAction SilentlyContinue
    if (-not $initdb) { throw 'PostgreSQL tools not found. Supply -PgBinPath or add PostgreSQL bin to PATH.' }
    $PgBinPath = Split-Path -Parent $initdb.Source
}
foreach ($tool in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe', 'psql.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PgBinPath $tool) -PathType Leaf)) { throw "Missing PostgreSQL tool: $tool" }
}
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
try { $portProbe.Start(); $port = $portProbe.LocalEndpoint.Port } finally { $portProbe.Stop() }
$suffix = [guid]::NewGuid().ToString('N')
$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixture = Join-Path $temporaryRoot "fintrack-persistence-$suffix"
$dataPath = Join-Path $fixture 'data'
$passwordPath = Join-Path $fixture 'password.txt'
$logPath = Join-Path $fixture 'postgres.log'
$password = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$databaseNames = @{
    APP_TEST_DATABASE_URL = "fintrack_app_eval_$suffix"
    DEPLOYMENT_TEST_DATABASE_URL = "fintrack_deployment_eval_$suffix"
    CLASSIFICATION_TEST_DATABASE_URL = "fintrack_classification_eval_$suffix"
}
$testEnvironment = @{
    TEST_DB_USER = 'postgres'; TEST_DB_PASSWORD = $password
    SPRING_DATASOURCE_USERNAME = 'postgres'; SPRING_DATASOURCE_PASSWORD = $password
    CLASSIFICATION_TEST_DATABASE_USER = 'postgres'; CLASSIFICATION_TEST_DATABASE_PASSWORD = $password
    GMAIL_SYNC_ENABLED = 'false'; SPRING_PROFILES_ACTIVE = 'dev'
    PGPASSWORD = $password; PGCONNECT_TIMEOUT = '5'
}
if ($JavaHome) { $testEnvironment['JAVA_HOME'] = $JavaHome }
foreach ($name in $databaseNames.Keys) {
    $hostName = if ($name -eq 'CLASSIFICATION_TEST_DATABASE_URL') { '127.0.0.1' } else { 'localhost' }
    $testEnvironment[$name] = "jdbc:postgresql://${hostName}:$port/$($databaseNames[$name])"
}
$testEnvironment['SPRING_DATASOURCE_URL'] = $testEnvironment['DEPLOYMENT_TEST_DATABASE_URL']
$previousEnvironment = @{}
foreach ($name in $testEnvironment.Keys) { $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$started = $false
$safeToRemove = $true
try {
    New-Item -ItemType Directory -Path $fixture | Out-Null
    [IO.File]::WriteAllText($passwordPath, $password, [Text.Encoding]::ASCII)
    foreach ($name in $testEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $testEnvironment[$name], 'Process') }
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'initdb.exe') -Arguments @(
        '-D', $dataPath, '-U', 'postgres', '--auth=scram-sha-256', '--encoding=UTF8', '--locale=C', "--pwfile=$passwordPath")
    Remove-Item -LiteralPath $passwordPath
    # A failed start may have created a process: always try to stop this exact data directory.
    $safeToRemove = $false
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'pg_ctl.exe') -Arguments @(
        '-D', $dataPath, '-l', $logPath, '-o', "-h localhost -p $port", '-w', '-t', '30', 'start')
    $started = $true
    $provenanceDatabase = "fintrack_provenance_eval_$suffix"
    $metadataDatabase = "fintrack_metadata_eval_$suffix"
    $operationsDatabase = "fintrack_operations_eval_$suffix"
    $captureOriginDatabase = "fintrack_capture_origin_eval_$suffix"
    $sourcesDatabase = "fintrack_sources_eval_$suffix"
    $manualDatabase = "fintrack_manual_eval_$suffix"
    $removeInstrumentsDatabase = "fintrack_remove_instruments_eval_$suffix"
    foreach ($database in @($databaseNames.Values) + @($provenanceDatabase, $metadataDatabase, $operationsDatabase, $captureOriginDatabase, $sourcesDatabase, $manualDatabase, $removeInstrumentsDatabase)) {
        Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'createdb.exe') -Arguments @(
            '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', $database)
    }
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $provenanceDatabase, '-f', (Join-Path $root 'scripts/migrations/test_ingestion_provenance.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $metadataDatabase, '-f', (Join-Path $root 'scripts/migrations/test_remove_transaction_metadata.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $operationsDatabase, '-f', (Join-Path $root 'scripts/migrations/test_transaction_operation_type.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $captureOriginDatabase, '-f', (Join-Path $root 'scripts/migrations/test_transaction_capture_origin.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $sourcesDatabase, '-f', (Join-Path $root 'scripts/migrations/test_source_diagnostics.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $manualDatabase, '-f', (Join-Path $root 'scripts/migrations/test_manual_transactions.sql'))
    Invoke-CheckedCommand -Command (Join-Path $PgBinPath 'psql.exe') -Arguments @(
        '-X', '-h', '127.0.0.1', '-p', "$port", '-U', 'postgres', '--no-password', '-v', 'ON_ERROR_STOP=1',
        '-d', $removeInstrumentsDatabase, '-f', (Join-Path $root 'scripts/migrations/test_remove_financial_instruments.sql'))
    Push-Location (Join-Path $root 'api')
    try {
        $arguments = @('verify', '-Dmail.sync.enabled=false', '-Dspring.jpa.show-sql=false')
        if ($Offline) { $arguments += '-o' }
        Invoke-CheckedCommand -Command '.\mvnw.cmd' -Arguments $arguments
    } finally { Pop-Location }
    Write-Output 'Migration regression and backend verification passed against newly created disposable databases.'
} finally {
    try {
        if (-not $safeToRemove) {
            & (Join-Path $PgBinPath 'pg_ctl.exe') -D $dataPath -m fast -w -t 30 stop
            if ($LASTEXITCODE -eq 0) { $safeToRemove = $true }
            elseif (-not $started) {
                & (Join-Path $PgBinPath 'pg_ctl.exe') -D $dataPath status | Out-Null
                $safeToRemove = $LASTEXITCODE -eq 3
            }
            if (-not $safeToRemove) { throw "Temporary PostgreSQL cleanup needs attention at $fixture; directory preserved." }
        }
    } finally {
        foreach ($name in $previousEnvironment.Keys) { [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
        $resolved = [IO.Path]::GetFullPath($fixture)
        $expected = [IO.Path]::GetFullPath((Join-Path $temporaryRoot "fintrack-persistence-$suffix"))
        if ($safeToRemove -and $resolved -eq $expected -and
            (Split-Path -Parent $resolved) -eq $temporaryRoot.TrimEnd('\', '/') -and
            (Split-Path -Leaf $resolved) -match '^fintrack-persistence-[a-f0-9]{32}$' -and
            (Test-Path -LiteralPath $resolved)) {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
