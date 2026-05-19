$ErrorActionPreference = "Stop"

$localEnvPath = Join-Path $PSScriptRoot "local.env"

if (Test-Path $localEnvPath) {
    Get-Content $localEnvPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line.Split("=", 2)
        if ($parts.Length -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
        }
    }
}

.\gradlew.bat bootRun
