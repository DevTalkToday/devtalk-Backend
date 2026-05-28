$ErrorActionPreference = "Stop"

Push-Location $PSScriptRoot

try {
    $tcpClient = [System.Net.Sockets.TcpClient]::new()
    try {
        $connection = $tcpClient.BeginConnect("127.0.0.1", 4000, $null, $null)
        if ($connection.AsyncWaitHandle.WaitOne(500)) {
            $tcpClient.EndConnect($connection)
            Write-Host "Devtalk backend is already running on port 4000."
            return
        }
    }
    catch {
    }
    finally {
        $tcpClient.Close()
    }

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

    $jar = Get-ChildItem (Join-Path $PSScriptRoot "build\libs") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-plain.jar" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($jar) {
        & java -jar $jar.FullName
    }
    else {
        .\gradlew.bat bootRun
    }
}
finally {
    Pop-Location
}
