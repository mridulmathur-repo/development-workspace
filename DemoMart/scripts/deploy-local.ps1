[CmdletBinding()]
param([string]$EnvironmentFile)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'start-docker.ps1') -EnvironmentFile $EnvironmentFile
if ($LASTEXITCODE -ne 0) { throw 'Local Docker deployment failed.' }
& (Join-Path $PSScriptRoot 'verify-docker-staging.ps1') -EnvironmentFile $EnvironmentFile
