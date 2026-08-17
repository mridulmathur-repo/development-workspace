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
    # Do not use --volumes: local H2 data should survive a normal stop.
    & $composeExecutable --env-file $environmentPath down
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose failed to stop DemoMart.'
    }
}
finally {
    Pop-Location
}
