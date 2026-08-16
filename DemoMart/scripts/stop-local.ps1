$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $projectRoot '.run'

foreach ($name in @('api-gateway','order-service','catalog-service','identity-service','service-registry')) {
    $pidFile = Join-Path $runRoot "$name.pid"
    if (-not (Test-Path $pidFile)) { continue }
    $processId = [int](Get-Content $pidFile)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -in @('java','javaw')) {
        Stop-Process -Id $processId
        Write-Host "Stopped $name."
    }
    Remove-Item -LiteralPath $pidFile -Force
}

