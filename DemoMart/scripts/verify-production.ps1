[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

$normalizedBaseUrl = $BaseUrl.TrimEnd('/') + '/'
if (-not $normalizedBaseUrl.EndsWith('/demomart/')) {
    throw "Production URL must end with /demomart/. Received: $normalizedBaseUrl"
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$lastResult = 'No response'
do {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $normalizedBaseUrl -TimeoutSec 10
        if ($response.StatusCode -eq 200 -and $response.Content -match 'DemoMart') {
            Write-Host "DemoMart production page is available at $normalizedBaseUrl"
            return
        }
        $lastResult = "HTTP $($response.StatusCode) or the page did not contain DemoMart"
    } catch {
        $lastResult = $_.Exception.Message
    }
    Write-Host "Production is not ready yet: $lastResult"
    Start-Sleep -Seconds 5
} while ((Get-Date) -lt $deadline)

throw "DemoMart production verification failed after $TimeoutSeconds seconds. Last result: $lastResult"
