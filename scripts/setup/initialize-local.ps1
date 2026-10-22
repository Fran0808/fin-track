[CmdletBinding()]
param(
    [ValidateSet('Docker', 'Native')][string]$Mode = 'Docker',
    [switch]$InstallUiDependencies,
    [switch]$Plan
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $root 'scripts/lib/common.ps1')
if ($Plan) {
    Write-Output "Check Git, Node.js 24, npm and $Mode prerequisites."
    Write-Output 'Create .env from .env.example only when absent; preserve existing configuration.'
    if ($InstallUiDependencies) { Write-Output '[ui] npm.cmd ci (downloads dependencies and runs package lifecycle scripts)' }
    Write-Output 'Report missing configuration names without printing values. No service, database or Android operations.'
    return
}
foreach ($command in @('git', 'node', 'npm.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required command not found: $command" }
}
$nodeVersion = & node --version
if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v24\.') { throw 'Node.js 24 is required.' }
if ($Mode -eq 'Docker') {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker with Compose v2 or later is required.' }
    $composeVersion = & docker compose version --short
    if ($LASTEXITCODE -ne 0 -or $composeVersion -notmatch '^v?([0-9]+)\.' -or [int]$Matches[1] -lt 2) {
        throw 'Docker Compose v2 or later is required.'
    }
} else {
    $javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    if (-not (Get-Command $javaCommand -ErrorAction SilentlyContinue)) { throw 'Java 21 is required; check JAVA_HOME and PATH.' }
    # Java prints its version on stderr; PowerShell 5.1 treats that as an error record.
    try { $ErrorActionPreference = 'Continue'; $javaVersion = (& $javaCommand -version 2>&1 | Out-String) }
    finally { $ErrorActionPreference = 'Stop' }
    if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21[.\"]') { throw 'Java 21 is required.' }
    if (-not (Get-Command psql -ErrorAction SilentlyContinue)) { throw 'PostgreSQL client tools are required for native setup.' }
}
$configurationPath = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $configurationPath)) {
    # CreateNew prevents overwriting a file created concurrently by another process.
    $contents = [IO.File]::ReadAllBytes((Join-Path $root '.env.example'))
    $stream = [IO.File]::Open($configurationPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
    try { $stream.Write($contents, 0, $contents.Length) } finally { $stream.Dispose() }
    Write-Output 'Created .env from the template; supply private settings before starting the application.'
} else { Write-Output 'Preserved existing .env.' }
$settings = @{}
foreach ($line in Get-Content -LiteralPath $configurationPath) {
    if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
        $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
$missing = @('DB_PASSWORD', 'JWT_SECRET', 'GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET', 'APP_SERVER_PUBLIC_URL') |
    Where-Object { -not $settings.ContainsKey($_) -or [string]::IsNullOrWhiteSpace($settings[$_]) }
if ($settings.ContainsKey('JWT_SECRET') -and -not [string]::IsNullOrWhiteSpace($settings['JWT_SECRET']) -and
    [Text.Encoding]::UTF8.GetByteCount($settings['JWT_SECRET']) -lt 32) {
    throw 'JWT_SECRET in .env must contain at least 32 UTF-8 bytes. Existing values were preserved.'
}
if ($InstallUiDependencies) {
    Push-Location (Join-Path $root 'ui')
    try { Invoke-CheckedCommand -Command 'npm.cmd' -Arguments @('ci') } finally { Pop-Location }
}
if ($missing) { throw ('Local files prepared; configure these .env entries privately: ' + ($missing -join ', ')) }
Write-Output 'Local preparation completed. Credential validity, database access and device reachability have not been checked.'
Write-Output 'Follow docs/development/local.md to start the intended environment when authorized.'
