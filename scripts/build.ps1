param([switch]$SkipChecks, [switch]$SkipShortcut, [string]$Version='2.6.0', [string]$OutputDirectory='complie')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $parsedVersion = $null
    if (-not [Version]::TryParse($Version, [ref]$parsedVersion) -or $parsedVersion.Build -lt 0 -or $parsedVersion.Revision -ge 0 -or $Version.Split('.').Count -ne 3 -or @($Version.Replace('.', '').ToCharArray() | Where-Object { -not '0123456789'.Contains([string]$_) }).Count -gt 0) { throw 'Version must be major.minor.patch' }
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
    & ./gradlew.bat :backend:contract:client:typescript:generateClient --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'API client generation failed' }
    if (-not $SkipChecks) {
        node.exe --test scripts/tests/render-icon.test.cjs
        if ($LASTEXITCODE -ne 0) { throw 'Build script checks failed' }
        npm.cmd run check --prefix web
        if ($LASTEXITCODE -ne 0) { throw 'Frontend checks failed' }
        npm.cmd run check --prefix desktop
        if ($LASTEXITCODE -ne 0) { throw 'Electron checks failed' }
        npm.cmd test --prefix desktop
        if ($LASTEXITCODE -ne 0) { throw 'Electron updater and storage checks failed' }
        & ./gradlew.bat check --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Kotlin checks failed' }
    }
    & ./scripts/create-icon.ps1 -OutputPath (Join-Path $projectRoot 'desktop/assets/aigram.ico') -PngPath (Join-Path $projectRoot 'desktop/assets/aigram.png')
    & ./gradlew.bat :backend:prepareDesktop :backend:contract:client:typescript:packageClient "-PappVersion=$Version" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Backend and web build failed' }
    $clientPackageDirectory=Join-Path $projectRoot 'backend/contract/client/typescript/build/package'
    $clientManifest=Get-Content -Raw -LiteralPath (Join-Path $clientPackageDirectory 'manifest.json') | ConvertFrom-Json
    if ($clientManifest.name -ne '@aigram/api-client' -or $clientManifest.version -ne $Version) { throw 'API client package version does not match the application' }
    $clientArchive=[System.IO.Path]::GetFullPath((Join-Path $clientPackageDirectory $clientManifest.filename))
    if ([System.IO.Path]::GetDirectoryName($clientArchive) -ne [System.IO.Path]::GetFullPath($clientPackageDirectory) -or -not (Test-Path -LiteralPath $clientArchive -PathType Leaf)) { throw 'API client archive is missing or outside its package directory' }
    New-Item -ItemType Directory -Force -Path $compileDirectory | Out-Null
    Copy-Item -LiteralPath $clientArchive -Destination (Join-Path $compileDirectory 'Aigram-API-Client.tgz')
    $runtime=Join-Path $projectRoot 'build/runtime'
    $runtimeModules=@('java.base','java.desktop','java.instrument','java.net.http','jdk.crypto.ec','java.logging','java.management','java.management.rmi','jdk.management','jdk.unsupported','java.naming','java.sql','java.security.jgss','jdk.charsets')
    $runtimeJava=Join-Path $runtime 'bin/java.exe'
    if (Test-Path -LiteralPath $runtimeJava) {
        $installedModules=@(& $runtimeJava --list-modules | ForEach-Object { $_.Split('@')[0] })
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect bundled Java runtime' }
        $missingModules=@($runtimeModules | Where-Object { $_ -notin $installedModules })
        if ($missingModules.Count -gt 0) {
            $runtimePath=[System.IO.Path]::GetFullPath($runtime)
            $expectedPath=[System.IO.Path]::GetFullPath((Join-Path $projectRoot 'build/runtime'))
            if ($runtimePath -ne $expectedPath) { throw 'Unexpected runtime directory' }
            Remove-Item -LiteralPath $runtimePath -Recurse -Force
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'bin/java.exe'))) {
        $jdkBin=Split-Path (Get-Command java.exe).Source
        & (Join-Path $jdkBin 'jlink.exe') --output $runtime --strip-debug --no-header-files --no-man-pages --add-modules ($runtimeModules -join ',')
        if ($LASTEXITCODE -ne 0) { throw 'Bundled Java runtime build failed' }
    }
    $unpackName="aigram-$Version-" + [guid]::NewGuid().ToString('N').Substring(0,12)
    npm.cmd run package --prefix desktop -- --config.extraMetadata.version=$Version "--config.portable.unpackDirName=$unpackName" "--config.directories.output=$compileDirectory" --publish never
    if ($LASTEXITCODE -ne 0) { throw 'Electron portable build failed' }
    $executable=Join-Path $compileDirectory 'Aigram.exe'
    @(
        (Get-FileHash -Algorithm SHA256 -LiteralPath $executable).Hash.ToLowerInvariant() + '  Aigram.exe'
        (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $compileDirectory 'Aigram-API-Client.tgz')).Hash.ToLowerInvariant() + '  Aigram-API-Client.tgz'
    ) | Set-Content -Encoding utf8 (Join-Path $compileDirectory 'SHA256.txt')
    if(-not $SkipShortcut) {
        $shortcutShell=New-Object -ComObject WScript.Shell
        $shortcut=$shortcutShell.CreateShortcut((Join-Path $projectRoot 'Aigram.lnk'))
        $shortcut.TargetPath=$executable
        $shortcut.WorkingDirectory=$compileDirectory
        $shortcut.IconLocation=$executable + ',0'
        $shortcut.Description='Aigram — Telegram'
        $shortcut.Save()
    }
    Write-Output "Application: $executable"
} finally { Pop-Location }
