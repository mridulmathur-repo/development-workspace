[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$principal = [Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Run this script from PowerShell opened with Run as administrator.'
}

$nssm = 'C:\Tools\NSSM\nssm.exe'
$serviceName = 'FileUpload-Staging'
$java = 'C:\Program Files\Common Files\Oracle\Java\javapath\java.exe'
$deployRoot = 'C:\Apps\FileUpload'
$jar = Join-Path $deployRoot 'file-upload-app.jar'

if (-not (Test-Path -LiteralPath $nssm)) { throw "NSSM was not found at $nssm." }
if (-not (Test-Path -LiteralPath $java)) { throw "Java was not found at $java." }
if (-not (Test-Path -LiteralPath $jar)) { throw "Deploy the application once so this JAR exists: $jar" }

function Invoke-Nssm {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & $nssm @Arguments
    if ($LASTEXITCODE -ne 0) { throw "NSSM command failed: $($Arguments -join ' ')" }
}

if (Get-Service -Name $serviceName -ErrorAction SilentlyContinue) {
    Stop-Service -Name $serviceName -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Invoke-Nssm remove $serviceName confirm
}

Invoke-Nssm install $serviceName $java
Invoke-Nssm set $serviceName AppParameters ('-jar "' + $jar + '"')
Invoke-Nssm set $serviceName AppDirectory $deployRoot
Invoke-Nssm set $serviceName AppEnvironmentExtra 'FILE_STORAGE_LOCATION=C:\Apps\FileUpload\uploads' 'SERVER_PORT=8082'
Invoke-Nssm set $serviceName AppStdout (Join-Path $deployRoot 'file-upload-app.log')
Invoke-Nssm set $serviceName AppStderr (Join-Path $deployRoot 'file-upload-app-error.log')
Invoke-Nssm set $serviceName AppNoConsole 1
Invoke-Nssm set $serviceName Start SERVICE_AUTO_START
Invoke-Nssm set $serviceName AppExit Default Restart
Invoke-Nssm set $serviceName AppThrottle 5000

Write-Host "Configured Windows service $serviceName. Jenkins can now restart it during staging deployment."
