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
$composeCommand = Get-Command docker-compose.exe -ErrorAction SilentlyContinue
if (-not $composeCommand) {
    $dockerCommand = Get-Command docker.exe -ErrorAction Stop
    $dockerResources = Split-Path -Parent (Split-Path -Parent $dockerCommand.Source)
    $composeCandidate = Join-Path $dockerResources 'cli-plugins\docker-compose.exe'
    if (Test-Path -LiteralPath $composeCandidate -PathType Leaf) {
        $composeCommand = Get-Item -LiteralPath $composeCandidate
    }
}
if (-not $composeCommand) {
    throw 'Docker Compose was not found. Install the Docker Compose plugin first.'
}
$composeExecutable = $composeCommand.Source
if ([string]::IsNullOrWhiteSpace($composeExecutable)) {
    $composeExecutable = $composeCommand.FullName
}

Push-Location $projectRoot
try {
    & $composeExecutable --env-file $environmentPath up -d --build --wait --wait-timeout 420
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose failed to build or start DemoMart.'
    }
    & $composeExecutable --env-file $environmentPath ps
    Write-Host 'DemoMart containers are healthy. Open http://localhost:5174'
}
finally {
    Pop-Location
}
