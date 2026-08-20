[CmdletBinding()]
param([string]$EnvironmentFile)

$ErrorActionPreference = 'Stop'
$plannerRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = (Resolve-Path (Join-Path $plannerRoot '..')).Path
$environmentPath = if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) {
    Join-Path $plannerRoot '.env'
} else { [System.IO.Path]::GetFullPath($EnvironmentFile) }

if (-not (Test-Path $environmentPath -PathType Leaf)) { throw "Planner environment file not found: $environmentPath" }
foreach ($line in Get-Content $environmentPath) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#') -or -not $trimmed.Contains('=')) { continue }
    $name, $value = $trimmed.Split('=', 2)
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim().Trim('"'), 'Process')
}

# Command mode runs on Windows, so replace container-only paths from .env.
$env:AUTOMATION_REPOSITORY_ROOT = $workspaceRoot
$env:AUTOMATION_ALLOWED_DIRECTORY = 'DemoMart'
$env:SERVER_ADDRESS = '127.0.0.1'

$listener = Get-NetTCPConnection -LocalPort 8100 -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw 'Port 8100 is already in use. Stop the Docker planner before starting Windows command mode.' }

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $candidate = 'C:\ProgramData\Jenkins\.jenkins\tools\hudson.tasks.Maven_MavenInstallation\Maven3\bin\mvn.cmd'
    if (Test-Path $candidate -PathType Leaf) { $maven = Get-Item $candidate }
}
if (-not $maven) { throw 'Maven was not found in PATH or the Jenkins Maven installation.' }

Push-Location $plannerRoot
try {
    & $maven.FullName -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Planner build failed.' }
    & java -jar (Join-Path $plannerRoot 'target\jira-ai-planner-0.0.1-SNAPSHOT.jar')
} finally { Pop-Location }
