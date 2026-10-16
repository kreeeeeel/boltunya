param([switch]$SkipChecks, [switch]$SkipShortcut, [string]$Version='2.6.0', [string]$OutputDirectory='complie')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version must be major.minor.patch' }
    $compileDirectory=if([System.IO.Path]::IsPathRooted($OutputDirectory)) {
        [System.IO.Path]::GetFullPath($OutputDirectory)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $projectRoot $OutputDirectory))
    }
    $env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:/tiho-no-unix-sockets'
    foreach ($directory in @('web','desktop')) {
        if (-not (Test-Path -LiteralPath "$directory/node_modules")) {
            npm.cmd ci --prefix $directory
            if ($LASTEXITCODE -ne 0) { throw "Dependencies failed: $directory" }
        }
    }
    if (-not $SkipChecks) {
        npm.cmd run check --prefix web
        if ($LASTEXITCODE -ne 0) { throw 'Frontend checks failed' }
        npm.cmd run check --prefix desktop
        if ($LASTEXITCODE -ne 0) { throw 'Electron checks failed' }
        & ./gradlew.bat check --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Kotlin checks failed' }
    }
    & ./scripts/create-icon.ps1 -OutputPath (Join-Path $projectRoot 'desktop/assets/boltunya.ico') -PngPath (Join-Path $projectRoot 'desktop/assets/boltunya.png')
    & ./gradlew.bat :backend:prepareDesktop "-PappVersion=$Version" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Backend and web build failed' }
    $runtime=Join-Path $projectRoot 'build/runtime'
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'bin/java.exe'))) {
        $jdkBin=Split-Path (Get-Command java.exe).Source
        & (Join-Path $jdkBin 'jlink.exe') --output $runtime --strip-debug --no-header-files --no-man-pages --add-modules java.base,java.desktop,java.net.http,jdk.httpserver,jdk.crypto.ec,java.logging,java.management,jdk.management,jdk.unsupported,java.naming,java.sql,jdk.charsets
        if ($LASTEXITCODE -ne 0) { throw 'Bundled Java runtime build failed' }
    }
    $unpackName="boltunya-$Version-" + [guid]::NewGuid().ToString('N').Substring(0,12)
    npm.cmd run package --prefix desktop -- --config.extraMetadata.version=$Version "--config.portable.unpackDirName=$unpackName" "--config.directories.output=$compileDirectory" --publish never
    if ($LASTEXITCODE -ne 0) { throw 'Electron portable build failed' }
    $executable=Join-Path $compileDirectory 'Болтунья.exe'
    (Get-FileHash -Algorithm SHA256 -LiteralPath $executable).Hash.ToLowerInvariant() + '  Болтунья.exe' | Set-Content -Encoding utf8 (Join-Path $compileDirectory 'SHA256.txt')
    if(-not $SkipShortcut) {
        $shortcutShell=New-Object -ComObject WScript.Shell
        $shortcut=$shortcutShell.CreateShortcut((Join-Path $projectRoot 'Болтунья.lnk'))
        $shortcut.TargetPath=$executable
        $shortcut.WorkingDirectory=$compileDirectory
        $shortcut.IconLocation=$executable + ',0'
        $shortcut.Description='Болтунья — Telegram'
        $shortcut.Save()
    }
    Write-Output "Application: $executable"
} finally { Pop-Location }
