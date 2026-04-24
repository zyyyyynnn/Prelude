[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $PSCommandPath
$rootDir = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\..'))
$frontendDir = Join-Path $rootDir 'interview-frontend'
$outputDir = Join-Path $rootDir 'output\demo'
$screenshotsDir = Join-Path $outputDir 'screenshots'
$artifactsDir = Join-Path $outputDir '.artifacts'
$manifestPath = Join-Path $outputDir 'manifest.md'
$expectedFiles = @(
  '01-login.png',
  '02-register.png',
  '03-interview-workbench.png',
  '04-interview-stage-technical.png',
  '05-interview-stage-deep-dive.png',
  '06-interview-report.png',
  '07-replay.png',
  '08-resumes-filled.png',
  '09-settings-llm.png',
  '10-settings-profile.png',
  '11-analytics-filled.png'
)

function Ensure-Directory {
  param([string]$Path)

  if (-not (Test-Path -LiteralPath $Path)) {
    New-Item -ItemType Directory -Path $Path -Force | Out-Null
  }
}

Ensure-Directory $outputDir
Ensure-Directory $screenshotsDir

Get-ChildItem -LiteralPath $screenshotsDir -Filter '*.png' -File -ErrorAction SilentlyContinue |
  Remove-Item -Force

if (Test-Path -LiteralPath $manifestPath) {
  Remove-Item -LiteralPath $manifestPath -Force
}

if (Test-Path -LiteralPath $artifactsDir) {
  Remove-Item -LiteralPath $artifactsDir -Recurse -Force
}

& (Join-Path $scriptDir 'start-demo.ps1')
& (Join-Path $scriptDir 'reset-demo.ps1')

Push-Location $frontendDir
try {
  & npm run capture:demo
  if ($LASTEXITCODE -ne 0) {
    throw "Playwright capture failed with exit code $LASTEXITCODE."
  }
} finally {
  Pop-Location
}

$missingFiles = @($expectedFiles | Where-Object {
  -not (Test-Path -LiteralPath (Join-Path $screenshotsDir $_))
})

if ($missingFiles.Count -gt 0) {
  throw "Missing screenshots: $($missingFiles -join ', ')"
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
  throw "Manifest not found: $manifestPath"
}

Write-Host "Demo screenshots: $screenshotsDir"
Write-Host "Demo manifest:    $manifestPath"
