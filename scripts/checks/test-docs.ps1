[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $projectRoot
try {
    $documents = @(& git ls-files --cached --others --exclude-standard -- '*.md' '.env.example')
    if ($LASTEXITCODE -ne 0) { throw 'Could not list repository documentation.' }
    $failures = @()
    foreach ($document in $documents) {
        if (-not (Test-Path -LiteralPath $document -PathType Leaf)) { continue }
        $parent = Split-Path -Parent (Join-Path $projectRoot $document)
        $text = Get-Content -LiteralPath $document -Raw
        if ($document -eq '.env.example') {
            foreach ($match in [regex]::Matches($text, 'docs/[a-zA-Z0-9_./-]+\.md\b')) {
                if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $match.Value) -PathType Leaf)) {
                    $failures += "$document -> $($match.Value)"
                }
            }
        }
        foreach ($match in [regex]::Matches($text, '\]\(([^)\s]+)\)')) {
            $target = $match.Groups[1].Value.Split('#')[0]
            if (-not $target -or $target -match '^[a-zA-Z]+:') { continue }
            $target = [Uri]::UnescapeDataString($target)
            if (-not (Test-Path -LiteralPath (Join-Path $parent $target))) {
                $failures += "$document -> $target"
            }
        }
        foreach ($match in [regex]::Matches($text, 'scripts[/\\][a-zA-Z0-9_./\\-]+\.ps1')) {
            if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $match.Value))) {
                $failures += "$document -> $($match.Value)"
            }
        }
    }
    if ($failures.Count) { throw ("Documentation references do not resolve:`n" + ($failures -join "`n")) }
    Write-Output 'Repository documentation file links, template documentation paths and rooted script references resolve.'
} finally { Pop-Location }
