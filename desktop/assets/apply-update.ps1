param([Parameter(Mandatory = $true)][string]$Plan)
$ErrorActionPreference = 'Stop'
$configuration = $null
$backup = $null
$replaced = $false
try {
    $configuration = Get-Content -LiteralPath $Plan -Raw -Encoding UTF8 | ConvertFrom-Json
    $target = [System.IO.Path]::GetFullPath($configuration.target)
    $download = [System.IO.Path]::GetFullPath($configuration.download)
    $updateDirectory = [System.IO.Path]::GetFullPath((Split-Path -Parent $Plan))
    if (-not $download.StartsWith($updateDirectory + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Download is outside update directory' }
    if ([System.IO.Path]::GetExtension($target) -ne '.exe' -or $target -eq $download) { throw 'Invalid executable target' }
    $actual = (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash
    if ($actual -ne $configuration.checksum) { throw 'Update checksum mismatch' }
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        $application = Get-Process -Id $configuration.processId -ErrorAction SilentlyContinue
        $launcher = Get-CimInstance Win32_Process -Filter "Name LIKE '%.exe'" | Where-Object { $_.ExecutablePath -and [string]::Equals($_.ExecutablePath, $target, [System.StringComparison]::OrdinalIgnoreCase) }
        if (-not $application -and -not $launcher) { break }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($application -or $launcher) { throw 'Application did not exit for update' }
    $backup = $target + '.update-backup'
    Move-Item -LiteralPath $target -Destination $backup -Force
    try {
        Copy-Item -LiteralPath $download -Destination $target -Force
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $configuration.checksum) { throw 'Installed update checksum mismatch' }
        $replaced = $true
        $arguments = @('--updated')
        if ($configuration.startup) { $arguments += '--startup' }
        Start-Process -FilePath $target -ArgumentList $arguments -WorkingDirectory (Split-Path -Parent $target) -WindowStyle Hidden
    } catch {
        if (Test-Path -LiteralPath $backup) { Move-Item -LiteralPath $backup -Destination $target -Force }
        $replaced = $false
        throw
    }
    @($download) | ForEach-Object {
        try { Remove-Item -LiteralPath $_ -Force }
        catch { Write-Warning 'Could not remove a completed update temporary file.' }
    }
} catch {
    try {
        @{failedAt = [DateTime]::UtcNow.ToString('o'); stage = 'install'; code = 'UPDATE_INSTALL_FAILED'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path (Split-Path -Parent $Plan) 'last-error.json') -Encoding UTF8
    } catch { Write-Warning 'Could not record the update installation failure.' }
    if ($configuration -and -not $replaced -and (Test-Path -LiteralPath $configuration.target)) {
        $arguments = @('--update-failed')
        if ($configuration.startup) { $arguments += '--startup' }
        Start-Process -FilePath $configuration.target -ArgumentList $arguments -WorkingDirectory (Split-Path -Parent $configuration.target) -WindowStyle Hidden
    }
    exit 1
}
