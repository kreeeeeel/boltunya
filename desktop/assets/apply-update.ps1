param(
    [Parameter(Mandatory = $true)][string]$Plan,
    [ValidateSet('Install', 'Recover')][string]$Mode = 'Install'
)
$ErrorActionPreference = 'Stop'
$configuration = $null
$backup = $null
$replaced = $false
$accepted = $false
$updatedProcess = $null
$startupProcessId = $null

function Write-AtomicJson($Path, $Value) {
    $temporary = $Path + '.tmp'
    [System.IO.File]::WriteAllText($temporary, ($Value | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}
function Read-Marker($Path, $Nonce) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $null }
    $value = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($value.nonce -ne $Nonce) { throw 'Update marker session differs' }
    return $value
}
function Stop-UpdatedProcess($ProcessId) {
    if (-not $ProcessId -or -not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) { return }
    & "$env:SystemRoot\System32\taskkill.exe" /PID $ProcessId /T /F 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) { throw 'Could not stop the failed update process' }
}
try {
    $configuration = Get-Content -LiteralPath $Plan -Raw -Encoding UTF8 | ConvertFrom-Json
    $target = [System.IO.Path]::GetFullPath($configuration.target)
    $download = [System.IO.Path]::GetFullPath($configuration.download)
    $updateDirectory = [System.IO.Path]::GetFullPath((Split-Path -Parent $Plan))
    $parsedNonce = [Guid]::Empty
    if ($configuration.protocol -ne 2 -or -not [Guid]::TryParseExact($configuration.nonce, 'D', [ref]$parsedNonce)) { throw 'Invalid update session' }
    $nonce = $parsedNonce.ToString('D')
    if ($configuration.nonce -cne $nonce) { throw 'Noncanonical update session' }
    if (-not $download.StartsWith($updateDirectory + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Download is outside update directory' }
    if ([System.IO.Path]::GetExtension($target) -ne '.exe' -or $target -eq $download) { throw 'Invalid executable target' }
    if (-not (Test-Path -LiteralPath $target -PathType Leaf) -or ((Get-Item -LiteralPath $target).Attributes -band [System.IO.FileAttributes]::ReparsePoint)) { throw 'Invalid executable file' }
    if ([string]$configuration.checksum -ne ([string]$configuration.checksum).ToLowerInvariant() -or ([string]$configuration.checksum).Length -ne 64) { throw 'Invalid update checksum' }
    $backup = $target + '.update-backup'
    $ready = Join-Path $updateDirectory ('ready-' + $nonce + '.json')
    $cancelled = Join-Path $updateDirectory ('cancelled-' + $nonce + '.json')
    $started = Join-Path $updateDirectory ('started-' + $nonce + '.json')
    $healthy = Join-Path $updateDirectory ('healthy-' + $nonce + '.json')
    if ($Mode -eq 'Install') {
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $configuration.checksum) { throw 'Update checksum mismatch' }
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $configuration.originalChecksum) { throw 'Installed application changed before update' }
    } else {
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) { throw 'Previous application backup is missing' }
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $configuration.checksum) { throw 'Recovery executable differs from update' }
    }
    Write-AtomicJson $ready @{nonce = $nonce; processId = $PID}
    $accepted = $true
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        if (Read-Marker $cancelled $nonce) { exit 2 }
        $application = Get-Process -Id $configuration.processId -ErrorAction SilentlyContinue
        $launcher = Get-CimInstance Win32_Process -Filter "Name LIKE '%.exe'" | Where-Object { $_.ExecutablePath -and [string]::Equals($_.ExecutablePath, $target, [System.StringComparison]::OrdinalIgnoreCase) }
        if (-not $application -and -not $launcher) { break }
        Start-Sleep -Milliseconds 300
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($application -or $launcher) { throw 'Application did not exit for update' }
    if (Read-Marker $cancelled $nonce) { exit 2 }
    if ($Mode -eq 'Recover') {
        Move-Item -LiteralPath $backup -Destination $target -Force
        throw 'Recovered a legacy update after failed application startup'
    }
    Move-Item -LiteralPath $target -Destination $backup -Force
    try {
        Copy-Item -LiteralPath $download -Destination $target -Force
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $configuration.checksum) { throw 'Installed update checksum mismatch' }
        $replaced = $true
        $arguments = @('--updated', ('--update-session=' + $nonce))
        if ($configuration.startup) { $arguments += '--startup' }
        $updatedProcess = Start-Process -FilePath $target -ArgumentList $arguments -WorkingDirectory (Split-Path -Parent $target) -WindowStyle Hidden -PassThru
        $deadline = [DateTime]::UtcNow.AddSeconds(120)
        $launcherGrace = [DateTime]::UtcNow.AddSeconds(10)
        do {
            if (Read-Marker $healthy $nonce) {
                try {
                    Remove-Item -LiteralPath $backup -Force
                    Remove-Item -LiteralPath $Plan -Force
                } catch {
                    Write-Warning 'Could not clean the confirmed update backup.'
                }
                @($download, $ready, $started, $healthy) | ForEach-Object {
                    try { if (Test-Path -LiteralPath $_ -PathType Leaf) { Remove-Item -LiteralPath $_ -Force } }
                    catch { Write-Warning 'Could not remove a completed update temporary file.' }
                }
                exit 0
            }
            $startupMarker = Read-Marker $started $nonce
            if ($startupMarker) {
                $candidate = [int]$startupMarker.processId
                if ($candidate -le 0) { throw 'Updated application startup process is invalid' }
                $application = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + $candidate)
                if (-not $application) { throw 'Updated application exited before becoming ready' }
                if (-not $application.CommandLine.Contains('--update-session=' + $nonce)) { throw 'Updated application startup process is invalid' }
                $startupProcessId = $candidate
            } elseif ($updatedProcess.HasExited -and [DateTime]::UtcNow -gt $launcherGrace) { throw 'Updated launcher exited before application startup' }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $deadline)
        throw 'Updated application did not become ready'
    } catch {
        if ($startupProcessId) { Stop-UpdatedProcess $startupProcessId }
        if ($updatedProcess) { Stop-UpdatedProcess $updatedProcess.Id }
        if (Test-Path -LiteralPath $backup) {
            if ($configuration.originalChecksum -and (Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $configuration.originalChecksum) { throw 'Previous application backup checksum mismatch' }
            Move-Item -LiteralPath $backup -Destination $target -Force
        }
        $replaced = $false
        throw
    }
} catch {
    if ($accepted -and $configuration -and -not $replaced -and (Test-Path -LiteralPath $configuration.target -PathType Leaf)) {
        $arguments = @('--update-failed')
        if ($configuration.startup) { $arguments += '--startup' }
        Start-Process -FilePath $configuration.target -ArgumentList $arguments -WorkingDirectory (Split-Path -Parent $configuration.target) -WindowStyle Hidden
    }
    exit 1
}
