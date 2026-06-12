Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$ThesisRoot = $PSScriptRoot

Write-Host "Checking dependencies..."
if (!(Get-Command pandoc -ErrorAction SilentlyContinue)) {
    Write-Error "Pandoc is not installed or not in PATH."
    exit 1
}

$RequiredFiles = @(
    "meta\gzu-thesis-template.docx",
    "chapters\abstract-keywords.md",
    "chapters\chapter-01-introduction.md",
    "chapters\chapter-02-related-tech.md",
    "chapters\chapter-03-analysis-design.md",
    "chapters\chapter-04-implementation.md",
    "chapters\chapter-05-testing.md",
    "chapters\chapter-06-conclusion.md"
)

foreach ($File in $RequiredFiles) {
    $FilePath = Join-Path $ThesisRoot $File
    if (!(Test-Path $FilePath)) {
        Write-Error "Missing required file: $FilePath"
        exit 1
    }
}

$CurrentDir = Join-Path $ThesisRoot "current"
if (!(Test-Path $CurrentDir)) {
    New-Item -ItemType Directory -Force -Path $CurrentDir | Out-Null
}

$ThesisFull = Join-Path $CurrentDir "thesis-assembled.md"
$ThesisFinal = Join-Path $CurrentDir "thesis-working-draft.docx"

Write-Host "Assembling Markdown..."
Clear-Content -Path $ThesisFull -ErrorAction SilentlyContinue

$ChapterFiles = @(
    "chapters\abstract-keywords.md",
    "chapters\chapter-01-introduction.md",
    "chapters\chapter-02-related-tech.md",
    "chapters\chapter-03-analysis-design.md",
    "chapters\chapter-04-implementation.md",
    "chapters\chapter-05-testing.md",
    "chapters\chapter-06-conclusion.md"
)

foreach ($File in $ChapterFiles) {
    $FilePath = Join-Path $ThesisRoot $File
    Get-Content -Path $FilePath -Encoding UTF8 -Raw | Add-Content -Path $ThesisFull -Encoding UTF8 -NoNewline
    Add-Content -Path $ThesisFull "`n`n" -Encoding UTF8
}

$ResourcePath = @(
    $ThesisRoot,
    (Join-Path $ThesisRoot "evidence\diagrams"),
    (Join-Path $ThesisRoot "evidence\screenshots")
)
$DocsImages = Join-Path $Root "docs\images"
if (Test-Path $DocsImages) {
    $ResourcePath += $DocsImages
}
$ResourcePathString = $ResourcePath -join ";"

$TemplatePath = Join-Path $ThesisRoot "meta\gzu-thesis-template.docx"

Write-Host "Running Pandoc..."
pandoc $ThesisFull -o $ThesisFinal `
    --reference-doc=$TemplatePath `
    --toc --toc-depth=3 `
    -f markdown -t docx `
    --resource-path="$ResourcePathString"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Pandoc build failed."
    exit 1
}

Write-Host "Build successful!"
Write-Host "thesis-assembled.md: $ThesisFull"
Write-Host "thesis-working-draft.docx: $ThesisFinal"

