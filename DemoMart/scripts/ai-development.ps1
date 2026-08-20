[CmdletBinding()]
param([string]$PatchFile = $env:AI_PATCH_FILE)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($PatchFile)) {
    throw 'AI_PATCH_FILE is required. The agent must supply a reviewed unified patch; refusing to fabricate a code change.'
}
$patchPath = [System.IO.Path]::GetFullPath($PatchFile)
if (-not (Test-Path $patchPath -PathType Leaf)) { throw "Patch file not found: $patchPath" }
Push-Location $root
try {
    git apply --check -- $patchPath
    if ($LASTEXITCODE -ne 0) { throw 'The proposed patch does not apply cleanly.' }
    git apply -- $patchPath
    if ($LASTEXITCODE -ne 0) { throw 'The proposed patch could not be applied.' }
    if (Test-Path 'pom.xml') { mvn -q test }
    if (Test-Path 'frontend\package.json') { Push-Location frontend; npm run build; Pop-Location }
    if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed after applying the patch.' }
    Write-Host 'AI patch applied and regression checks passed.'
} finally { Pop-Location }
