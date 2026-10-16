param([int]$Port=3099)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$previousUrl=$env:TIHO_TEST_URL
$reports=Join-Path $projectRoot 'build/reports'
New-Item -ItemType Directory -Force -Path $reports | Out-Null
$serverScript=Join-Path $projectRoot 'web/node_modules/serve/build/main.js'
$webOutput=Join-Path $projectRoot 'web/out'
$server=Start-Process -FilePath node.exe -WindowStyle Hidden -PassThru -WorkingDirectory $projectRoot `
    -ArgumentList @("`"$serverScript`"","`"$webOutput`"",'-l',"$Port",'--no-clipboard') `
    -RedirectStandardOutput (Join-Path $reports 'web-server.log') `
    -RedirectStandardError (Join-Path $reports 'web-server-errors.log')
Push-Location $projectRoot
try {
    $env:TIHO_TEST_URL="http://127.0.0.1:$Port"
    $ready=$false
    $deadline=(Get-Date).AddSeconds(20)
    do {
        try { $ready=(Invoke-WebRequest -UseBasicParsing $env:TIHO_TEST_URL -TimeoutSec 2).StatusCode -eq 200 }
        catch { $ready=$false }
        if(-not $ready) { Start-Sleep -Milliseconds 250 }
    } until($ready -or (Get-Date) -ge $deadline)
    if(-not $ready) { throw 'Static web server did not become ready.' }
    npm.cmd run test:e2e --prefix web
    if($LASTEXITCODE -ne 0) { throw 'Browser tests failed.' }
} finally {
    Stop-Process -Id $server.Id -ErrorAction SilentlyContinue
    $env:TIHO_TEST_URL=$previousUrl
    Pop-Location
}
