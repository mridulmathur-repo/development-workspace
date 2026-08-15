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
$pidPath = Join-Path $deployRootPath 'file-upload-app.pid'
$logPath = Join-Path $deployRootPath 'file-upload-app.log'
$errorLogPath = Join-Path $deployRootPath 'file-upload-app-error.log'
$applicationPort = 8082

New-Item -ItemType Directory -Path $deployRootPath -Force | Out-Null
New-Item -ItemType Directory -Path $uploadsPath -Force | Out-Null

# Stop only the process whose PID was created by this script. Never stop every Java process.
if (Test-Path -LiteralPath $pidPath) {
    $existingPid = [int](Get-Content -LiteralPath $pidPath -Raw).Trim()
    $existingProcess = Get-Process -Id $existingPid -ErrorAction SilentlyContinue
    if ($null -ne $existingProcess) {
        Stop-Process -Id $existingPid -Force
        $existingProcess.WaitForExit(15000)
    }
    Remove-Item -LiteralPath $pidPath -Force
}

# A previously interrupted Jenkins deployment can leave an app listening even when
# its PID file is no longer accurate. Port 8082 is reserved for this staging app.
$listeners = Get-NetTCPConnection -LocalPort $applicationPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
foreach ($listenerPid in $listeners) {
    Write-Host "Stopping previous process $listenerPid listening on port $applicationPort."
    Stop-Process -Id $listenerPid -Force
}

Copy-Item -LiteralPath $sourceJarPath -Destination $jarPath -Force
$java = (Get-Command java -ErrorAction Stop).Source

# The child process inherits these variables; uploaded files stay outside Jenkins' workspace.
$env:FILE_STORAGE_LOCATION = $uploadsPath
$env:SERVER_PORT = $applicationPort.ToString()
# Prevent Jenkins from keeping this long-running web server attached to the build.
$env:JENKINS_NODE_COOKIE = 'file-upload-staging-service'

$process = Start-Process -FilePath $java `
    -ArgumentList '-jar', $jarPath `
    -WorkingDirectory $deployRootPath `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError $errorLogPath `
    -PassThru

$process.Id | Set-Content -LiteralPath $pidPath -NoNewline
Write-Host "Started file-upload-app with PID $($process.Id). Uploads are stored in $uploadsPath"
