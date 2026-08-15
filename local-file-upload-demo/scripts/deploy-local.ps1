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
$nssm = 'C:\Tools\NSSM\nssm.exe'
$serviceName = 'FileUpload-Staging'

New-Item -ItemType Directory -Path $deployRootPath -Force | Out-Null
New-Item -ItemType Directory -Path $uploadsPath -Force | Out-Null

# Stop the Windows service first. This is independent from Jenkins, so the
# deployment step can complete while NSSM keeps the application running.
if (-not (Test-Path -LiteralPath $nssm)) {
    throw "NSSM was not found at $nssm. Install and configure the staging service first."
}

$service = Get-Service -Name $serviceName -ErrorAction SilentlyContinue
if ($null -eq $service) {
    throw "Windows service $serviceName was not found. Configure it with NSSM first."
}

& $nssm stop $serviceName | Out-Null

# Port 8082 is reserved for this staging application. This also cleans up a
# previous direct Jenkins launch left over from an interrupted deployment.
$listeners = Get-NetTCPConnection -LocalPort $applicationPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique
foreach ($listenerPid in $listeners) {
    Write-Host "Stopping previous staging process $listenerPid on port $applicationPort."
    Stop-Process -Id $listenerPid -Force
}

Copy-Item -LiteralPath $sourceJarPath -Destination $jarPath -Force
& $nssm start $serviceName | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not start Windows service $serviceName." }

Write-Host "Restarted Windows service $serviceName. Uploads are stored in $uploadsPath"
