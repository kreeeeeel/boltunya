param(
    [string]$DataDirectory = $(if ($env:BOLTUNYA_DATA_DIR) { $env:BOLTUNYA_DATA_DIR } elseif ($env:TIHO_DATA_DIR) { $env:TIHO_DATA_DIR } else { Join-Path $env:LOCALAPPDATA 'Tiho' }),
    [string]$OutputPath = (Join-Path ([Environment]::GetFolderPath('Desktop')) 'boltunya-diagnostics.json'),
    [switch]$TestModel
)
$ErrorActionPreference = 'Stop'

function Invoke-DiagnosticRequest {
    param([string]$Url, [hashtable]$Headers = @{}, [string]$Body = '', [int]$Timeout = 5)
    try {
        $arguments = @{ Uri = $Url; Headers = $Headers; TimeoutSec = $Timeout; UseBasicParsing = $true; MaximumRedirection = 0 }
        if ($Headers.ContainsKey('Cookie')) {
            $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
            $session.Cookies.SetCookies([Uri]$Url, $Headers.Cookie)
            $arguments.WebSession = $session
            $arguments.Headers = @{}
        }
        if ($Body) { $arguments.Method = 'POST'; $arguments.Body = $Body; $arguments.ContentType = 'application/json; charset=utf-8' }
        $response = Invoke-WebRequest @arguments
        return @{ ok = $true; status = [int]$response.StatusCode; data = ($response.Content | ConvertFrom-Json) }
    } catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $code = 'UNREACHABLE'
        if ($status) { $code = 'HTTP_ERROR' }
        if ($_.ErrorDetails.Message) {
            try {
                $errorBody = $_.ErrorDetails.Message | ConvertFrom-Json
                if ($errorBody.code -cmatch '^[A-Z_]{1,80}$') { $code = $errorBody.code }
            } catch { $code = 'INVALID_ERROR_RESPONSE' }
        }
        return @{ ok = $false; status = $status; code = $code }
    }
}

$report = [ordered]@{
    formatVersion = 1
    collectedAt = [DateTime]::UtcNow.ToString('o')
    system = [ordered]@{ os = [Environment]::OSVersion.Version.ToString(); processors = [Environment]::ProcessorCount; memoryMb = $null; freeMemoryMb = $null; gpu = @(); hardwareAvailable = $false }
    application = [ordered]@{ reachable = $false; status = 'NOT_RUNNING'; version = 'unknown' }
    ollama = [ordered]@{ reachable = $false; installed = $false; localEndpoint = $false; version = ''; models = @(); serverModels = @() }
    generation = [ordered]@{ requested = [bool]$TestModel; status = 'NOT_REQUESTED' }
    findings = @()
}
try {
    $os = Get-CimInstance Win32_OperatingSystem
    $report.system.memoryMb = [int][Math]::Round($os.TotalVisibleMemorySize / 1024)
    $report.system.freeMemoryMb = [int][Math]::Round($os.FreePhysicalMemory / 1024)
    $report.system.gpu = @(Get-CimInstance Win32_VideoController | ForEach-Object { $_.Name })
    $report.system.hardwareAvailable = $true
    $backendProcess = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like '*ru.boltunya.MainKt*' } | Select-Object -First 1
    if ($backendProcess.ExecutablePath -match 'boltunya-(\d+\.\d+\.\d+)') { $report.application.version = $Matches[1] }
} catch { $report.findings += 'HARDWARE_INFO_UNAVAILABLE' }

$base = ''
$headers = @{}
$state = $null
$descriptor = Join-Path $DataDirectory 'launch.url'
if (Test-Path -LiteralPath $descriptor) {
    try {
        $uri = [Uri]([IO.File]::ReadAllText($descriptor).Trim())
        if ($uri.Scheme -ne 'http' -or $uri.Host -ne '127.0.0.1' -or $uri.UserInfo) { throw 'Invalid local descriptor' }
        $keyParameter = @($uri.Query.TrimStart('?').Split('&') | Where-Object { $_.StartsWith('key=') })
        if ($keyParameter.Count -ne 1) { throw 'Invalid local descriptor' }
        $key = [Uri]::UnescapeDataString($keyParameter[0].Substring(4))
        if ($key -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Invalid local descriptor' }
        $base = $uri.GetLeftPart([UriPartial]::Authority)
        $headers = @{ Cookie = "tiho=$key" }
        $result = Invoke-DiagnosticRequest "$base/api/state" $headers
        $report.application.reachable = $result.ok
        $report.application.status = $(if ($result.ok) { 'READY' } else { $result.code })
        if ($result.ok) { $state = $result.data }
    } catch { $report.application.status = 'INVALID_DESCRIPTOR' }
}
if ($null -ne $state) {
    $selected = $state.settings.defaultModel
    $selectedModel = @($state.engine.models | Where-Object { $_.id -eq $selected }) | Select-Object -First 1
    $rules = @($state.settings.rules.PSObject.Properties | ForEach-Object { $_.Value })
    $report.application.telegramState = $state.telegram.state
    $report.application.aiPaused = [bool]$state.settings.paused
    $report.application.queuePaused = [bool]$state.replyQueue.paused
    $report.application.queued = [int]$state.replyQueue.queued
    $report.application.running = [int]$state.replyQueue.running
    $report.application.defaultModel = $selected
    $report.application.enabledChats = @($rules | Where-Object { $_.enabled }).Count
    $report.application.customModelOverrides = @($rules | Where-Object { $_.enabled -and $_.useDefaultModel -eq $false }).Count
    $report.application.groupReplies = $state.settings.groupReplies
    $report.application.ignoreAssistantTasks = [bool]$state.settings.ignoreAssistantTasks
    $report.application.recentActivity = @($state.activity | Select-Object -First 10 | ForEach-Object {
        [ordered]@{ status = $_.status; phase = $_.phase; durationMs = $_.durationMs; attempts = $_.attempts; inputTokens = $_.inputTokens; outputTokens = $_.outputTokens }
    })
    $report.ollama.installed = [bool]($state.engine.installed -or $state.engine.online)
    $report.ollama.reachable = [bool]$state.engine.online
    $report.ollama.models = @($state.engine.models | Where-Object { $_.installed } | ForEach-Object { $_.id })
    if ($state.telegram.state -ne 'ready') { $report.findings += 'TELEGRAM_NOT_CONNECTED' }
    if ($state.settings.paused) { $report.findings += 'AI_PAUSED' }
    if ($state.replyQueue.paused) { $report.findings += 'QUEUE_PAUSED' }
    if (-not $report.application.enabledChats) { $report.findings += 'NO_ENABLED_CHATS' }
    if (-not $state.engine.online) { $report.findings += 'OLLAMA_OFFLINE' }
    if (-not $selectedModel.installed) { $report.findings += 'DEFAULT_MODEL_NOT_INSTALLED' }
    if ($selectedModel -and $selectedModel.supportsAutomation -eq $false) { $report.findings += 'DEFAULT_MODEL_DRAFTS_ONLY' }
    if ($selectedModel -and $report.system.memoryMb -and $selectedModel.memoryGb * 1024 -gt $report.system.memoryMb) { $report.findings += 'MODEL_EXCEEDS_SYSTEM_MEMORY_ESTIMATE' }
    if ($TestModel) {
        $body = @{ text = 'Say hello in one short sentence.'; rule = @{ model = $selected; useDefaultModel = $false; internetAccess = $false; matchMyStyle = $false; useMemory = $false } } | ConvertTo-Json -Depth 4
        $generationTimer = [Diagnostics.Stopwatch]::StartNew()
        $test = Invoke-DiagnosticRequest "$base/api/preview" $headers $body 190
        $report.generation.requestDurationMs = $generationTimer.ElapsedMilliseconds
        $report.generation.status = $(if ($test.ok -and $test.data.text) { 'OK' } elseif ($test.ok) { 'EMPTY_REPLY' } else { $test.code })
        $report.generation.httpStatus = $test.status
        if ($test.ok) { $report.generation.durationMs = $test.data.durationMs; $report.generation.tokensPerSecond = $test.data.tokensPerSecond }
    }
} else {
    $report.findings += 'APPLICATION_UNREACHABLE'
    if ($TestModel) { $report.generation.status = 'APPLICATION_UNREACHABLE' }
}

$ollamaUrl = $(if ($env:BOLTUNYA_OLLAMA_URL) { $env:BOLTUNYA_OLLAMA_URL.TrimEnd('/') } else { 'http://127.0.0.1:11434' })
try {
    $ollamaUri = [Uri]$ollamaUrl
    $report.ollama.localEndpoint = $ollamaUri.Scheme -eq 'http' -and $ollamaUri.Host -in @('127.0.0.1', 'localhost', '[::1]') -and -not $ollamaUri.UserInfo
    if ($report.ollama.localEndpoint) {
        $version = Invoke-DiagnosticRequest "$ollamaUrl/api/version"
        if ($version.ok -and $version.data.version -match '^\d+\.\d+\.\d+([-+][a-zA-Z0-9.-]+)?$') { $report.ollama.version = $version.data.version }
        $tags = Invoke-DiagnosticRequest "$ollamaUrl/api/tags"
        if ($tags.ok) {
            $report.ollama.serverModels = @($tags.data.models | ForEach-Object { if ($_.name) { $_.name } elseif ($_.model) { $_.model } } | Sort-Object -Unique)
        }
    }
} catch { $report.findings += 'INVALID_OLLAMA_ENDPOINT' }

[IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath), ($report | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
Write-Output "Diagnostics saved: $OutputPath"
