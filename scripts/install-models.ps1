param([ValidateSet("0.6b", "1.7b", "4b", "4b-instruct")][string[]]$Models = @("0.6b", "4b-instruct"))
$ErrorActionPreference = 'Stop'
$modelRoot = Join-Path $env:USERPROFILE '.ollama/models'
$blobRoot = Join-Path $modelRoot 'blobs'
$manifestRoot = Join-Path $modelRoot 'manifests/registry.ollama.ai/library/qwen3'
New-Item -ItemType Directory -Force $blobRoot, $manifestRoot | Out-Null
$Models | ForEach-Object {
    $tag = $_
    $response = Invoke-WebRequest -UseBasicParsing "https://registry.ollama.ai/v2/library/qwen3/manifests/$tag"
    $raw = if ($response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($response.Content) } else { $response.Content }
    $manifest = $raw | ConvertFrom-Json
    @($manifest.config) + @($manifest.layers) | ForEach-Object {
        $digest = $_.digest
        $target = Join-Path $blobRoot ($digest.Replace(':', '-'))
        if (-not (Test-Path -LiteralPath $target)) {
            Write-Output "Downloading qwen3:$tag layer $digest"
            Invoke-WebRequest -UseBasicParsing "https://registry.ollama.ai/v2/library/qwen3/blobs/$digest" -OutFile "$target.partial"
            $actual = (Get-FileHash -LiteralPath "$target.partial" -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actual -ne $digest.Substring(7)) { throw "Checksum mismatch: $digest" }
            Move-Item -LiteralPath "$target.partial" -Destination $target
        }
    }
    [IO.File]::WriteAllText((Join-Path $manifestRoot $tag), $raw, [Text.UTF8Encoding]::new($false))
    Write-Output "Installed qwen3:$tag"
}
