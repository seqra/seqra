# Poll an HTTP URL until it responds; fail and dump the server log on timeout.
# Returns to the caller on success; throws on timeout or early server exit.
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Url,
    [int]$TimeoutSec = 30,
    [System.Diagnostics.Process]$Process,
    [string]$StdoutLog,
    [string]$StderrLog
)

$ErrorActionPreference = 'Stop'

for ($i = 1; $i -le $TimeoutSec; $i++) {
    if ($Process -and $Process.HasExited) { break }
    try {
        Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop | Out-Null
        Write-Host "Server ready after ${i}s"
        return
    } catch {
        Start-Sleep -Seconds 1
    }
}

Write-Host "Server did not become ready at $Url within ${TimeoutSec}s."
if ($StdoutLog) { Write-Host '--- stdout ---'; Get-Content $StdoutLog -ErrorAction SilentlyContinue }
if ($StderrLog) { Write-Host '--- stderr ---'; Get-Content $StderrLog -ErrorAction SilentlyContinue }
throw "Local HTTP server did not become ready at $Url"
