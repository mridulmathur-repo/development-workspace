[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [Parameter(Mandatory = $true)][string]$DeployRoot,
    [Parameter(Mandatory = $true)][string]$NssmPath,
    [Parameter(Mandatory = $true)][string]$JavaExecutable,
    [Parameter(Mandatory = $true)][string]$NodeExecutable
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    throw 'Jenkins credential demomart-staging-jwt-secret was not supplied.'
}
if ([string]::IsNullOrWhiteSpace($env:INTERNAL_SERVICE_KEY)) {
    throw 'Jenkins credential demomart-staging-internal-key was not supplied.'
}

$sourceRootPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$deployRootPath = [System.IO.Path]::GetFullPath($DeployRoot)
$expectedDeployRoot = [System.IO.Path]::GetFullPath('C:\Apps\DemoMart')
if ($deployRootPath -ne $expectedDeployRoot) {
    throw "For safety, local staging must deploy to $expectedDeployRoot. Received: $deployRootPath"
}

foreach ($requiredFile in @($NssmPath, $JavaExecutable, $NodeExecutable)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required executable was not found: $requiredFile"
    }
}

$artifacts = [ordered]@{
    'DemoMart-Registry' = @{
        Port = 8762; Module = 'service-registry'; Memory = '160m'; Environment = @('SERVER_PORT=8762')
    }
    'DemoMart-Identity' = @{
        Port = 8091; Module = 'identity-service'; Memory = '256m'; Environment = @(
            'SERVER_PORT=8091',
            'EUREKA_URL=http://127.0.0.1:8762/eureka/',
            "JWT_SECRET=$env:JWT_SECRET",
            'SEED_DEMO_USERS=true'
        )
    }
    'DemoMart-Catalog' = @{
        Port = 8092; Module = 'catalog-service'; Memory = '256m'; Environment = @(
            'SERVER_PORT=8092',
            'EUREKA_URL=http://127.0.0.1:8762/eureka/',
            "JWT_SECRET=$env:JWT_SECRET",
            "INTERNAL_SERVICE_KEY=$env:INTERNAL_SERVICE_KEY"
        )
    }
    'DemoMart-Order' = @{
        Port = 8093; Module = 'order-service'; Memory = '256m'; Environment = @(
            'SERVER_PORT=8093',
            'EUREKA_URL=http://127.0.0.1:8762/eureka/',
            "JWT_SECRET=$env:JWT_SECRET",
            "INTERNAL_SERVICE_KEY=$env:INTERNAL_SERVICE_KEY"
        )
    }
    'DemoMart-Gateway' = @{
        Port = 8090; Module = 'api-gateway'; Memory = '192m'; Environment = @(
            'SERVER_PORT=8090',
            'EUREKA_URL=http://127.0.0.1:8762/eureka/'
        )
    }
}

function Invoke-Nssm {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & $NssmPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "NSSM command failed: $($Arguments -join ' ')"
    }
}

function Stop-StagingService([string]$Name) {
    $service = Get-Service -Name $Name -ErrorAction SilentlyContinue
    if ($service -and $service.Status -ne 'Stopped') {
        Stop-Service -Name $Name -Force
        $service.WaitForStatus('Stopped', [TimeSpan]::FromSeconds(30))
    }
}

function Stop-PortListener([int]$Port) {
    $listenerProcessIds = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($listenerProcessId in $listenerProcessIds) {
        Write-Host "Stopping previous process $listenerProcessId on staging port $Port."
        Stop-Process -Id $listenerProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Set-ServiceCommon([string]$Name, [string]$Application, [string]$Parameters,
                           [string]$WorkingDirectory, [string[]]$Environment) {
    if (-not (Get-Service -Name $Name -ErrorAction SilentlyContinue)) {
        Invoke-Nssm install $Name $Application
    } else {
        Invoke-Nssm set $Name Application $Application
    }
    Invoke-Nssm set $Name AppParameters $Parameters
    Invoke-Nssm set $Name AppDirectory $WorkingDirectory
    Invoke-Nssm set $Name AppEnvironmentExtra @Environment
    Invoke-Nssm set $Name AppStdout (Join-Path $WorkingDirectory 'service.out.log')
    Invoke-Nssm set $Name AppStderr (Join-Path $WorkingDirectory 'service.err.log')
    Invoke-Nssm set $Name AppNoConsole 1
    Invoke-Nssm set $Name Start SERVICE_AUTO_START
    Invoke-Nssm set $Name AppExit Default Restart
    Invoke-Nssm set $Name AppThrottle 5000
}

$allServiceNames = @($artifacts.Keys) + 'DemoMart-Frontend'
$servicesToStop = [array]$allServiceNames.Clone()
[array]::Reverse($servicesToStop)
foreach ($serviceName in $servicesToStop) {
    Stop-StagingService $serviceName
}
foreach ($port in @(5174, 8090, 8093, 8092, 8091, 8762)) {
    Stop-PortListener $port
}

New-Item -ItemType Directory -Path $deployRootPath -Force | Out-Null

foreach ($entry in $artifacts.GetEnumerator()) {
    $serviceName = $entry.Key
    $settings = $entry.Value
    $module = $settings.Module
    $sourceJar = Join-Path $sourceRootPath "$module\target\$module-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
        throw "Build artifact not found: $sourceJar"
    }

    $moduleRoot = Join-Path $deployRootPath $module
    $dataRoot = Join-Path $moduleRoot 'data'
    New-Item -ItemType Directory -Path $moduleRoot, $dataRoot -Force | Out-Null
    $deployedJar = Join-Path $moduleRoot 'app.jar'
    Copy-Item -LiteralPath $sourceJar -Destination $deployedJar -Force

    $serviceEnvironment = @($settings.Environment)
    if ($module -in @('identity-service', 'catalog-service', 'order-service')) {
        $databaseName = $module.Replace('-service', '')
        $databasePath = (Join-Path $dataRoot $databaseName).Replace('\', '/')
        $serviceEnvironment += "DB_URL=jdbc:h2:file:$databasePath;AUTO_SERVER=TRUE"
    }

    $javaParameters = "-Xms64m -Xmx$($settings.Memory) -jar `"$deployedJar`""
    Set-ServiceCommon $serviceName $JavaExecutable $javaParameters $moduleRoot $serviceEnvironment
}

$frontendSource = Join-Path $sourceRootPath 'frontend'
$frontendDist = Join-Path $frontendSource 'dist'
if (-not (Test-Path -LiteralPath (Join-Path $frontendDist 'index.html') -PathType Leaf)) {
    throw "React build output was not found: $frontendDist"
}

$frontendRoot = Join-Path $deployRootPath 'frontend'
$deployedDist = Join-Path $frontendRoot 'dist'
$runtimeRoot = Join-Path $deployRootPath 'runtime'
New-Item -ItemType Directory -Path $frontendRoot, $deployedDist, $runtimeRoot -Force | Out-Null
Copy-Item -Path (Join-Path $frontendDist '*') -Destination $deployedDist -Recurse -Force
Copy-Item -LiteralPath (Join-Path $frontendSource 'server.mjs') -Destination (Join-Path $frontendRoot 'server.mjs') -Force
$deployedNode = Join-Path $runtimeRoot 'node.exe'
Copy-Item -LiteralPath $NodeExecutable -Destination $deployedNode -Force
Set-ServiceCommon 'DemoMart-Frontend' $deployedNode 'server.mjs' $frontendRoot @(
    'PORT=5174',
    'GATEWAY_URL=http://127.0.0.1:8090'
)

Start-Service 'DemoMart-Registry'
(Get-Service 'DemoMart-Registry').WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
foreach ($serviceName in @('DemoMart-Identity', 'DemoMart-Catalog', 'DemoMart-Order', 'DemoMart-Gateway', 'DemoMart-Frontend')) {
    Start-Service $serviceName
}

Write-Host "DemoMart artifacts deployed to $deployRootPath and staging services restarted."
