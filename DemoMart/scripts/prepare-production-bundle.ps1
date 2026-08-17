[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [Parameter(Mandatory = $true)][string]$BundleRoot
)

$ErrorActionPreference = 'Stop'

$sourceRootPath = (Resolve-Path -LiteralPath $SourceRoot).Path
$bundleRootPath = [System.IO.Path]::GetFullPath($BundleRoot)
$expectedBundleRoot = [System.IO.Path]::GetFullPath((Join-Path $sourceRootPath 'build\production-bundle'))
if ($bundleRootPath -ne $expectedBundleRoot) {
    throw "For safety, the production bundle must be created at $expectedBundleRoot. Received: $bundleRootPath"
}

if (Test-Path -LiteralPath $bundleRootPath) {
    Remove-Item -LiteralPath $bundleRootPath -Recurse -Force
}

$artifactRoot = Join-Path $bundleRootPath 'artifacts'
$frontendTarget = Join-Path $bundleRootPath 'frontend-root\demomart'
$dockerTarget = Join-Path $bundleRootPath 'docker'
$nginxTarget = Join-Path $bundleRootPath 'nginx'
$scriptTarget = Join-Path $bundleRootPath 'scripts'
New-Item -ItemType Directory -Path $artifactRoot, $frontendTarget, $dockerTarget, $nginxTarget, $scriptTarget -Force | Out-Null

$jars = [ordered]@{
    'service-registry\target\service-registry-0.0.1-SNAPSHOT.jar' = 'service-registry.jar'
    'identity-service\target\identity-service-0.0.1-SNAPSHOT.jar' = 'identity-service.jar'
    'catalog-service\target\catalog-service-0.0.1-SNAPSHOT.jar' = 'catalog-service.jar'
    'order-service\target\order-service-0.0.1-SNAPSHOT.jar' = 'order-service.jar'
    'api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar' = 'api-gateway.jar'
}

foreach ($jar in $jars.GetEnumerator()) {
    $sourceJar = Join-Path $sourceRootPath $jar.Key
    if (-not (Test-Path -LiteralPath $sourceJar -PathType Leaf)) {
        throw "Required build artifact was not found: $sourceJar"
    }
    Copy-Item -LiteralPath $sourceJar -Destination (Join-Path $artifactRoot $jar.Value) -Force
}

$frontendDist = Join-Path $sourceRootPath 'frontend\dist'
if (-not (Test-Path -LiteralPath (Join-Path $frontendDist 'index.html') -PathType Leaf)) {
    throw "React production output was not found: $frontendDist"
}
Copy-Item -Path (Join-Path $frontendDist '*') -Destination $frontendTarget -Recurse -Force

Copy-Item -Path (Join-Path $sourceRootPath 'deployment\docker\*') -Destination $dockerTarget -Recurse -Force
Copy-Item -LiteralPath (Join-Path $sourceRootPath 'deployment\nginx\demomart.conf') -Destination $nginxTarget -Force
Copy-Item -LiteralPath (Join-Path $sourceRootPath 'scripts\deploy-ec2-production.sh') -Destination $scriptTarget -Force

Write-Host "DemoMart production bundle created at $bundleRootPath"
