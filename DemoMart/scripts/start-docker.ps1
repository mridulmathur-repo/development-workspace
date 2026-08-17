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
    throw "Docker environment file not found: $environmentPath. Copy .env.example to .env and replace both example secrets."
}
if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    throw 'Docker was not found. Install and start Docker Desktop first.'
}

Push-Location $projectRoot
try {
    & docker compose --env-file $environmentPath up -d --build --wait --wait-timeout 420
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose failed to build or start DemoMart.'
    }
    & docker compose --env-file $environmentPath ps
    Write-Host 'DemoMart containers are healthy. Open http://localhost:5174'
}
finally {
    Pop-Location
}
