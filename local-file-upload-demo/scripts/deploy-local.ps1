[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceJar,

    [Parameter(Mandatory = $true)]
    [string]$DeployRoot
)

$ErrorActionPreference = 'Stop'

$sourceJarPath = (Resolve-Path -LiteralPath $SourceJar).Path
$deployRootPath = [System.IO.Path]::GetFullPath($DeployRoot)
$uploadsPath = Join-Path $deployRootPath 'uploads'
$jarPath = Join-Path $deployRootPath 'file-upload-app.jar'
$logPath = Join-Path $deployRootPath 'file-upload-app.log'
$errorLogPath = Join-Path $deployRootPath 'file-upload-app-error.log'
$runnerPath = Join-Path $deployRootPath 'run-file-upload-staging.cmd'
$applicationPort = 8082
$taskName = 'FileUpload-Staging'

New-Item -ItemType Directory -Path $deployRootPath -Force | Out-Null
New-Item -ItemType Directory -Path $uploadsPath -Force | Out-Null

# Jenkins must not directly own a long-running Java process. A scheduled task is
# managed independently by Windows, so Jenkins can continue to the health check.
$taskQuery = "schtasks /Query /TN `"$taskName`" >nul 2>nul"
cmd.exe /c $taskQuery
if ($LASTEXITCODE -eq 0) {
    $taskEnd = "schtasks /End /TN `"$taskName`" >nul 2>nul"
    cmd.exe /c $taskEnd
}

$deadline = (Get-Date).AddSeconds(20)
while ((Get-NetTCPConnection -LocalPort $applicationPort -State Listen -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
}

if (Get-NetTCPConnection -LocalPort $applicationPort -State Listen -ErrorAction SilentlyContinue) {
    throw "The previous staging application did not release port $applicationPort."
}

Copy-Item -LiteralPath $sourceJarPath -Destination $jarPath -Force
$java = (Get-Command java -ErrorAction Stop).Source

$runner = @"
@echo off
set "FILE_STORAGE_LOCATION=$uploadsPath"
set "SERVER_PORT=$applicationPort"
"$java" -jar "$jarPath" 1>> "$logPath" 2>> "$errorLogPath"
"@
Set-Content -LiteralPath $runnerPath -Value $runner -Encoding ascii

& schtasks.exe /Create /F /TN $taskName /SC ONSTART /RU SYSTEM /RL HIGHEST /TR $runnerPath
if ($LASTEXITCODE -ne 0) {
    throw "Could not create the Windows staging task $taskName."
}

& schtasks.exe /Run /TN $taskName
if ($LASTEXITCODE -ne 0) {
    throw "Could not start the Windows staging task $taskName."
}

Write-Host "Started Windows task $taskName. Uploads are stored in $uploadsPath"
