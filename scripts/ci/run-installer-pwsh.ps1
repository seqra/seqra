# Run an opentaint PowerShell install script and propagate its
# OPENTAINT_BINARY_PATH line to $env:GITHUB_OUTPUT so a verify step can
# consume it.
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$Script
)

$ErrorActionPreference = 'Stop'

$output = pwsh -File $Script 2>&1 | Out-String
Write-Host $output

if ($LASTEXITCODE -ne 0) {
    throw "$Script exited with code $LASTEXITCODE"
}
if ($output -notmatch 'OPENTAINT_BINARY_PATH=(.+)') {
    throw "$Script did not emit OPENTAINT_BINARY_PATH"
}

$binaryPath = $Matches[1].Trim()
"OPENTAINT_BINARY_PATH=$binaryPath" | Out-File -FilePath $env:GITHUB_OUTPUT -Append -Encoding utf8
