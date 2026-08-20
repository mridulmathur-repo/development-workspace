[CmdletBinding()]
param([string]$PatchFile = $env:AI_PATCH_FILE)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repositoryRoot = (& git -C $projectRoot rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repositoryRoot)) { throw 'DemoMart is not inside a Git repository.' }
if ([string]::IsNullOrWhiteSpace($PatchFile)) {
    throw 'AI_PATCH_FILE is required. The agent must supply a reviewed unified patch; refusing to fabricate a code change.'
}
$patchPath = [System.IO.Path]::GetFullPath($PatchFile)
if (-not (Test-Path $patchPath -PathType Leaf)) { throw "Patch file not found: $patchPath" }
Push-Location $repositoryRoot
try {
    $patchFiles = @(& git apply --numstat -- $patchPath | ForEach-Object { ($_ -split "`t")[-1].Replace('\', '/') })
    if ($LASTEXITCODE -ne 0 -or $patchFiles.Count -eq 0) { throw 'The patch is empty or unreadable.' }
    $invalidPath = $patchFiles | Where-Object { $_ -notlike 'DemoMart/*' -or $_ -match '(^|/)\.\.(/|$)' } | Select-Object -First 1
    if ($invalidPath) { throw "Patch attempts to change a file outside DemoMart: $invalidPath" }
    git apply --check -- $patchPath
    if ($LASTEXITCODE -ne 0) { throw 'The proposed patch does not apply cleanly.' }
    git apply -- $patchPath
    if ($LASTEXITCODE -ne 0) { throw 'The proposed patch could not be applied.' }
    Push-Location $projectRoot
    try {
        if (Test-Path 'pom.xml') {
            & mvn.cmd -q test
            if ($LASTEXITCODE -ne 0) { throw 'Maven regression tests failed after applying the patch.' }
        }
        if (Test-Path 'frontend\package.json') {
            Push-Location frontend
            try {
                & npm.cmd run build
                if ($LASTEXITCODE -ne 0) { throw 'React production build failed after applying the patch.' }
            } finally { Pop-Location }
        }
    } finally { Pop-Location }
    Write-Host 'AI patch applied and regression checks passed.'
} finally { Pop-Location }
