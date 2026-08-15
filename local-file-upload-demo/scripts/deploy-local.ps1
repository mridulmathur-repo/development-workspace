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
$applicationPort = 8082

New-Item -ItemType Directory -Path $deployRootPath -Force | Out-Null
New-Item -ItemType Directory -Path $uploadsPath -Force | Out-Null

# Port 8082 is reserved for this staging application. Stop only its current listener.
$listeners = Get-NetTCPConnection -LocalPort $applicationPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
foreach ($listenerPid in $listeners) {
    Write-Host "Stopping previous staging process $listenerPid on port $applicationPort."
    Stop-Process -Id $listenerPid -Force
}

Copy-Item -LiteralPath $sourceJarPath -Destination $jarPath -Force
$java = (Get-Command java -ErrorAction Stop).Source

# Launch through cmd.exe START so Java runs independently and Jenkins can finish its step.
$env:FILE_STORAGE_LOCATION = $uploadsPath
$env:SERVER_PORT = $applicationPort.ToString()
$env:JENKINS_NODE_COOKIE = 'file-upload-staging-service'
$command = "start `"FileUpload Staging`" /min `"$java`" -jar `"$jarPath`" 1>> `"$logPath`" 2>> `"$errorLogPath`""
cmd.exe /c $command
if ($LASTEXITCODE -ne 0) {
    throw 'Could not start the staging application in the background.'
}

Write-Host "Started file-upload-app in the background. Uploads are stored in $uploadsPath"
