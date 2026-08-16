param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $projectRoot '.run'
$logRoot = Join-Path $projectRoot 'logs'
New-Item -ItemType Directory -Force -Path $runRoot, $logRoot | Out-Null

$java = Get-Command java.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $java) {
    $java = Get-ChildItem 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}
if (-not $java) { throw 'Java was not found. Install JDK 17 or newer.' }
$javaRoot = Split-Path -Parent (Split-Path -Parent $java)

if (-not $SkipBuild) {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $maven) { $maven = 'C:\ProgramData\Jenkins\.jenkins\tools\hudson.tasks.Maven_MavenInstallation\Maven3\bin\mvn.cmd' }
    if (-not (Test-Path $maven)) { throw 'Maven was not found. Configure Maven in PATH or Jenkins.' }
    $env:JAVA_HOME = $javaRoot
    $localMavenRepo = Join-Path $projectRoot '.m2-repository'
    & $maven "-Dmaven.repo.local=$localMavenRepo" -f (Join-Path $projectRoot 'pom.xml') -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'DemoMart Maven build failed.' }
}

function Start-DemoService([string]$name, [string]$jar, [string]$healthUrl) {
    $pidFile = Join-Path $runRoot "$name.pid"
    if (Test-Path $pidFile) {
        $existingId = [int](Get-Content $pidFile)
        if (Get-Process -Id $existingId -ErrorAction SilentlyContinue) {
            Write-Host "$name is already running (PID $existingId)."
            return
        }
    }
    $moduleRoot = Split-Path -Parent (Split-Path -Parent $jar)
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WorkingDirectory $moduleRoot `
        -RedirectStandardOutput (Join-Path $logRoot "$name.out.log") `
        -RedirectStandardError (Join-Path $logRoot "$name.err.log") -WindowStyle Hidden -PassThru
    Set-Content -Path $pidFile -Value $process.Id
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 2
        try {
            $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 3
            if ($health.status -eq 'UP') { Write-Host "$name is UP."; return }
        } catch { }
    } while ((Get-Date) -lt $deadline)
    throw "$name did not become healthy. Check $logRoot\$name.err.log"
}

Start-DemoService 'service-registry' (Join-Path $projectRoot 'service-registry\target\service-registry-0.0.1-SNAPSHOT.jar') 'http://localhost:8762/actuator/health'
Start-DemoService 'identity-service' (Join-Path $projectRoot 'identity-service\target\identity-service-0.0.1-SNAPSHOT.jar') 'http://localhost:8091/actuator/health'
Start-DemoService 'catalog-service' (Join-Path $projectRoot 'catalog-service\target\catalog-service-0.0.1-SNAPSHOT.jar') 'http://localhost:8092/actuator/health'
Start-DemoService 'order-service' (Join-Path $projectRoot 'order-service\target\order-service-0.0.1-SNAPSHOT.jar') 'http://localhost:8093/actuator/health'
Start-DemoService 'api-gateway' (Join-Path $projectRoot 'api-gateway\target\api-gateway-0.0.1-SNAPSHOT.jar') 'http://localhost:8090/actuator/health'

Write-Host 'DemoMart backend is ready. Eureka: http://localhost:8762 | Gateway: http://localhost:8090'
