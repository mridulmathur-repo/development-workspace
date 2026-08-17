[CmdletBinding()]
param(
    [string]$EnvironmentFile,
    [int]$TimeoutSeconds = 420
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
$services = @(
    'service-registry',
    'identity-service',
    'catalog-service',
    'order-service',
    'api-gateway',
    'frontend'
)

if (-not (Test-Path -LiteralPath $environmentPath -PathType Leaf)) {
    throw "Docker environment file not found: $environmentPath"
}
if (-not (Get-Command docker.exe -ErrorAction SilentlyContinue)) {
    throw 'Docker was not found in PATH.'
}

Push-Location $projectRoot
try {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $unhealthy = @()

        foreach ($service in $services) {
            $containerId = (& docker.exe compose --env-file $environmentPath ps -q $service).Trim()
            if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
                $unhealthy += "$service (not running)"
                continue
            }

            $health = (& docker.exe inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId).Trim()
            if ($LASTEXITCODE -ne 0 -or $health -ne 'healthy') {
                $unhealthy += "$service ($health)"
            }
        }

        if ($unhealthy.Count -eq 0) {
            break
        }

        Write-Host "Waiting for Docker services: $($unhealthy -join ', ')"
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)

    if ($unhealthy.Count -gt 0) {
        throw "DemoMart containers did not become healthy within $TimeoutSeconds seconds: $($unhealthy -join ', ')"
    }

    $healthResponse = Invoke-RestMethod -Uri 'http://127.0.0.1:5174/health' -TimeoutSec 10
    if ($healthResponse.status -ne 'UP') {
        throw "The frontend health endpoint returned status '$($healthResponse.status)'."
    }

    $homeResponse = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5174/' -TimeoutSec 10
    if ($homeResponse.StatusCode -ne 200 -or $homeResponse.Content -notmatch 'DemoMart') {
        throw 'The frontend container is healthy, but the DemoMart home page did not load correctly.'
    }

    & docker.exe compose --env-file $environmentPath ps
    Write-Host 'All six DemoMart Docker containers are healthy. Open http://localhost:5174'
}
finally {
    Pop-Location
}
