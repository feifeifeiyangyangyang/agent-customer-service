$ErrorActionPreference = "Stop"

$root = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$envPath = Join-Path $root ".env"

if (Test-Path -LiteralPath $envPath) {
    Get-Content -LiteralPath $envPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }
        $parts = $line.Split("=", 2)
        if ($parts.Length -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
        }
    }
}

function Resolve-ProjectPathEnv($name) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return
    }
    if ([System.IO.Path]::IsPathRooted($value)) {
        return
    }
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $root $value))
    [Environment]::SetEnvironmentVariable($name, $resolved, "Process")
}

Resolve-ProjectPathEnv "DOCUMENT_STORAGE_PATH"
Resolve-ProjectPathEnv "EMBEDDING_MODEL_PATH"
Resolve-ProjectPathEnv "EMBEDDING_TOKENIZER_PATH"

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("SERVER_PORT", "Process"))) {
    [Environment]::SetEnvironmentVariable("SERVER_PORT", "18080", "Process")
}

Set-Location (Join-Path $root "server")
python -m uvicorn app.main:app --host 127.0.0.1 --port ([Environment]::GetEnvironmentVariable("SERVER_PORT", "Process"))
