# Seqra installer for Windows (PowerShell)
# Usage: irm https://raw.githubusercontent.com/seqra/seqra/main/scripts/install/install.ps1 | iex

$ErrorActionPreference = 'Stop'

$Repo = "seqra/seqra"
$BaseUrl = if ($env:SEQRA_DOWNLOAD_BASE_URL) { $env:SEQRA_DOWNLOAD_BASE_URL } else { "https://github.com/$Repo/releases/latest/download" }

function Get-Architecture {
    $arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
    switch ($arch) {
        "X64"   { return "amd64" }
        "Arm64" { return "arm64" }
        default {
            Write-Error "Unsupported architecture: $arch"
            exit 1
        }
    }
}

function Verify-Checksum {
    param(
        [string]$ArchivePath,
        [string]$ArchiveName
    )

    $checksumsUrl = "$BaseUrl/checksums.txt"
    $checksumsFile = Join-Path ([System.IO.Path]::GetDirectoryName($ArchivePath)) "checksums.txt"

    Write-Host "Verifying checksum..."
    try {
        Invoke-WebRequest -Uri $checksumsUrl -OutFile $checksumsFile -UseBasicParsing
    } catch {
        Write-Warning "Could not download checksums.txt, skipping verification."
        return
    }

    $line = Get-Content $checksumsFile | Where-Object { $_ -match "  $([regex]::Escape($ArchiveName))$" } | Select-Object -First 1
    if (-not $line) {
        Write-Warning "No checksum found for $ArchiveName, skipping verification."
        return
    }

    $expected = ($line -split "  ")[0].ToLower()
    $actual = (Get-FileHash -Path $ArchivePath -Algorithm SHA256).Hash.ToLower()

    if ($expected -ne $actual) {
        Write-Error "Checksum verification failed!`n  Expected: $expected`n  Actual:   $actual"
        exit 1
    }
    Write-Host "Checksum verified."
}

function Get-InstallDir {
    if ($env:SEQRA_INSTALL_DIR) {
        return $env:SEQRA_INSTALL_DIR
    }
    return Join-Path $env:LOCALAPPDATA "seqra"
}

function Main {
    $arch = Get-Architecture
    Write-Host "Architecture: $arch"

    $archiveName = "seqra_windows_${arch}.zip"
    $url = "$BaseUrl/$archiveName"
    $installDir = Get-InstallDir
    Write-Host "Install directory: $installDir"

    $tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) "seqra-install-$([System.Guid]::NewGuid())"
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

    try {
        $archivePath = Join-Path $tmpDir $archiveName
        Write-Host "Downloading $archiveName..."
        Invoke-WebRequest -Uri $url -OutFile $archivePath -UseBasicParsing

        Verify-Checksum -ArchivePath $archivePath -ArchiveName $archiveName

        Write-Host "Extracting..."
        Expand-Archive -Path $archivePath -DestinationPath $tmpDir -Force

        Write-Host "Installing to $installDir..."
        New-Item -ItemType Directory -Path $installDir -Force | Out-Null

        $binDir = Join-Path $installDir "bin"
        New-Item -ItemType Directory -Path $binDir -Force | Out-Null
        Copy-Item -Path (Join-Path $tmpDir "seqra.exe") -Destination (Join-Path $binDir "seqra.exe") -Force

        # Install bundled lib and jre if present
        $libSrc = Join-Path $tmpDir "lib"
        if (Test-Path $libSrc) {
            $libDst = Join-Path $installDir "lib"
            if (Test-Path $libDst) { Remove-Item -Recurse -Force $libDst }
            Copy-Item -Recurse -Path $libSrc -Destination $libDst
        }

        $jreSrc = Join-Path $tmpDir "jre"
        if (Test-Path $jreSrc) {
            $jreDst = Join-Path $installDir "jre"
            if (Test-Path $jreDst) { Remove-Item -Recurse -Force $jreDst }
            Copy-Item -Recurse -Path $jreSrc -Destination $jreDst
        }

        # Add to PATH if not already there
        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($userPath -notlike "*$binDir*") {
            [Environment]::SetEnvironmentVariable("Path", "$binDir;$userPath", "User")
            Write-Host ""
            Write-Host "Added $binDir to user PATH."
            Write-Host "Please restart your terminal for changes to take effect."
        }

        Write-Host ""
        Write-Host "seqra installed successfully!"
        Write-Host "Run 'seqra --version' to verify the installation."
    }
    finally {
        Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
    }
}

Main
