function Invoke-CheckedCommand {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code ${LASTEXITCODE}: $Command" }
}

function Get-ApiOrigin {
    param([string]$BaseUrl)
    $uri = $null
    if (-not [Uri]::TryCreate($BaseUrl, [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo -or
        $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment) {
        throw 'BaseUrl must be an HTTP(S) origin without credentials, path, query or fragment.'
    }
    if ($uri.Scheme -eq 'http' -and -not $uri.IsLoopback) {
        throw 'Use HTTPS for a remote API; HTTP is allowed only on loopback.'
    }
    return $uri.GetLeftPart([UriPartial]::Authority)
}
