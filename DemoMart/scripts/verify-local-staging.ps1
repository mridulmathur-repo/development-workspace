[CmdletBinding()]
param([int]$TimeoutSeconds = 180)

$ErrorActionPreference = 'Stop'

$checks = [ordered]@{
    'Service Registry' = 'http://127.0.0.1:8762/actuator/health'
    'Identity Service' = 'http://127.0.0.1:8091/actuator/health'
    'Catalog Service' = 'http://127.0.0.1:8092/actuator/health'
    'Order Service' = 'http://127.0.0.1:8093/actuator/health'
    'API Gateway' = 'http://127.0.0.1:8090/actuator/health'
    'React Frontend' = 'http://127.0.0.1:5174/health'
}

foreach ($check in $checks.GetEnumerator()) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastResult = 'No response'
    do {
        try {
            $response = Invoke-RestMethod -Uri $check.Value -TimeoutSec 5
            if ($response.status -eq 'UP') {
                Write-Host "$($check.Key) is UP."
                $lastResult = $null
                break
            }
            $lastResult = "Status was $($response.status)"
        } catch {
            $lastResult = $_.Exception.Message
        }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)

    if ($lastResult) {
        throw "$($check.Key) did not become healthy within $TimeoutSeconds seconds. Last result: $lastResult"
    }
}

$homeResponse = Invoke-WebRequest -Uri 'http://127.0.0.1:5174/' -TimeoutSec 10
if ($homeResponse.StatusCode -ne 200 -or $homeResponse.Content -notmatch 'DemoMart') {
    throw 'The frontend health endpoint passed, but the DemoMart home page did not load correctly.'
}

Write-Host 'All DemoMart local staging checks passed. Open http://localhost:5174'
