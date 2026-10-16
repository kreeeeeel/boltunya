param([Parameter(Mandatory=$true)][string]$OutputPath,[Parameter(Mandatory=$true)][string]$PngPath)
$ErrorActionPreference='Stop'
node (Join-Path $PSScriptRoot 'render-icon.cjs') $OutputPath $PngPath
if ($LASTEXITCODE -ne 0) { throw 'Icon rendering failed. Install Chromium with: npx --prefix web playwright install chromium' }
