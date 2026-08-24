[CmdletBinding()]
param([string]$EnvironmentFile, [switch]$Background)

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
$uiRoot = Join-Path $plannerRoot 'ui'
$uiDist = Join-Path $uiRoot 'dist'
$env:DASHBOARD_UI_PATH = $uiDist

$listener = Get-NetTCPConnection -LocalPort 8100 -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw 'Port 8100 is already in use. Stop the Docker planner before starting Windows command mode.' }

$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $maven) {
    $candidate = 'C:\ProgramData\Jenkins\.jenkins\tools\hudson.tasks.Maven_MavenInstallation\Maven3\bin\mvn.cmd'
    if (Test-Path $candidate -PathType Leaf) { $maven = Get-Item $candidate }
}
if (-not $maven) { throw 'Maven was not found in PATH or the Jenkins Maven installation.' }

$compiledIndex = Join-Path $uiDist 'index.html'
$latestUiSource = Get-ChildItem (Join-Path $uiRoot 'src'), (Join-Path $uiRoot 'index.html') -Recurse -File |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$uiBuildRequired = -not (Test-Path $compiledIndex -PathType Leaf) -or $latestUiSource.LastWriteTime -gt (Get-Item $compiledIndex).LastWriteTime
$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if ($uiBuildRequired -and $npm) {
    Push-Location $uiRoot
    try {
        & $npm.Source ci
        if ($LASTEXITCODE -ne 0) { throw 'UI dependency installation failed.' }
        & $npm.Source run build
        if ($LASTEXITCODE -ne 0) { throw 'UI production build failed.' }
    } finally { Pop-Location }
} elseif ($uiBuildRequired) {
    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) { throw 'Neither npm nor Docker was found; one is required to build the dashboard UI.' }
    & $docker.Source run --rm --volume "${uiRoot}:/ui" --workdir /ui node:22-alpine sh -c 'npm ci && npm run build'
    if ($LASTEXITCODE -ne 0) { throw 'Docker-based UI production build failed.' }
}
if (-not (Test-Path $compiledIndex -PathType Leaf)) { throw 'Compiled dashboard index.html was not created.' }

Push-Location $plannerRoot
try {
    & $maven.FullName -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Planner build failed.' }
    $jar = Join-Path $plannerRoot 'target\jira-ai-planner-0.0.1-SNAPSHOT.jar'
    if ($Background) {
        $stdout = Join-Path $plannerRoot 'target\planner-stdout.log'
        $stderr = Join-Path $plannerRoot 'target\planner-stderr.log'
        $java = (Get-Command java.exe -ErrorAction Stop).Source
        $process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $plannerRoot `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
        Set-Content -Path (Join-Path $plannerRoot 'target\planner.pid') -Value $process.Id
        Write-Host "Jira AI Planner started in background with PID $($process.Id). Logs: $stdout"
    } else {
        & java -jar $jar
    }
} finally { Pop-Location }
