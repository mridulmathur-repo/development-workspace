[CmdletBinding()]
param(
    [string]$EnvironmentFile
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$EnvironmentFile = if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    Join-Path $projectRoot '.env'
}
else {
    $EnvironmentFile
}
$environmentPath = [System.IO.Path]::GetFullPath($EnvironmentFile)

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Docker environment file not found: $environmentPath"
}

Push-Location $projectRoot
try {
    # Do not use --volumes: local H2 data should survive a normal stop.
    & docker compose --env-file $environmentPath down
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose failed to stop DemoMart.'
    }
}
finally {
    Pop-Location
}
