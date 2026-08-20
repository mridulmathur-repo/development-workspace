[CmdletBinding()]
param([string]$EnvironmentFile)
$ErrorActionPreference = 'Stop'
$parameters = @{}
if (-not [string]::IsNullOrWhiteSpace($EnvironmentFile)) { $parameters.EnvironmentFile = $EnvironmentFile }
& (Join-Path $PSScriptRoot 'start-docker.ps1') @parameters
if ($LASTEXITCODE -ne 0) { throw 'Local Docker deployment failed.' }
& (Join-Path $PSScriptRoot 'verify-docker-staging.ps1') @parameters
if ($LASTEXITCODE -ne 0) { throw 'Local Docker deployment verification failed.' }
