# OpenTaint installer for Windows (PowerShell)
# Usage:
#   irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 | iex
#   & ([scriptblock]::Create((irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1))) -Version 1.2.3

param(
    [string]$Version = "latest"
)

$ErrorActionPreference = 'Stop'

function Test-Version {
    param([string]$Raw)

    if (-not $Raw -or $Raw -eq "latest") {
        return @{ PathSegment = "latest/download"; Tag = "latest" }
    }

    if ($Raw -match '^(v)?(?<ver>[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9._-]+)?)$') {
        $normalized = $Matches['ver']
        return @{ PathSegment = "download/v$normalized"; Tag = "v$normalized" }
    }

    [Console]::Error.WriteLine("Error: Invalid version '$Raw'. Expected 'latest' or 'X.Y.Z' (optionally prefixed with 'v').")
    exit 2
}

function Test-HomebrewInstall {
    $cmd = Get-Command opentaint -ErrorAction SilentlyContinue
    if (-not $cmd) {
        return $null
    }
    $path = $cmd.Source
    try {
        $resolved = (Resolve-Path -LiteralPath $path -ErrorAction Stop).Path
        $path = $resolved
    } catch { }

    $lower = $path.ToLower()
    if ($lower -match '/cellar/' -or $lower -match '/caskroom/' -or $lower -match '/homebrew/') {
        return $path
    }
    return $null
}

$Repo = if ($env:OPENTAINT_REPOSITORY) { $env:OPENTAINT_REPOSITORY } else { "seqra/opentaint" }

function Get-Architecture {
    $arch = $env:PROCESSOR_ARCHITECTURE
    switch ($arch) {
        "AMD64" { return "amd64" }
        "ARM64" { return "arm64" }
        default {
            [Console]::Error.WriteLine("Error: Unsupported architecture: $arch")
            [Console]::Error.WriteLine("See https://github.com/seqra/opentaint/blob/main/docs/installation.md for alternatives.")
            exit 1
        }
    }
}

function Invoke-Download {
    param(
        [string]$Url,
        [string]$OutFile,
        [bool]$ShowProgress = $false
    )

    $previous = $ProgressPreference
    if (-not $ShowProgress) {
        $ProgressPreference = 'SilentlyContinue'
    }
    try {
        Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing
    }
    finally {
        $ProgressPreference = $previous
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
        Invoke-Download -Url $checksumsUrl -OutFile $checksumsFile -ShowProgress $false
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
    if ($env:OPENTAINT_INSTALL_DIR) {
        return $env:OPENTAINT_INSTALL_DIR
    }
    return Join-Path $env:LOCALAPPDATA "opentaint"
}

function Main {
    $existingBrew = Test-HomebrewInstall
    if ($existingBrew -and $env:OPENTAINT_FORCE -ne "1") {
        [Console]::Error.WriteLine("Error: opentaint is already installed via Homebrew at $existingBrew.")
        [Console]::Error.WriteLine("Run 'brew upgrade --cask opentaint' to update, or set")
        [Console]::Error.WriteLine("`$env:OPENTAINT_FORCE='1' to install side-by-side anyway.")
        exit 3
    }

    $versionInfo = Test-Version -Raw $Version
    $BaseUrl = if ($env:OPENTAINT_DOWNLOAD_BASE_URL) {
        $env:OPENTAINT_DOWNLOAD_BASE_URL
    } else {
        "https://github.com/$Repo/releases/$($versionInfo.PathSegment)"
    }
    $script:BaseUrl = $BaseUrl

    $arch = Get-Architecture
    Write-Host "Architecture: $arch"

    $archiveName = "opentaint-full_windows_${arch}.zip"
    $url = "$BaseUrl/$archiveName"
    $installDir = Get-InstallDir
    Write-Host "Install directory: $installDir"

    $tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) "opentaint-install-$([System.Guid]::NewGuid())"
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

    try {
        $archivePath = Join-Path $tmpDir $archiveName
        Write-Host "Downloading $archiveName..."
        Invoke-Download -Url $url -OutFile $archivePath -ShowProgress $true

        Verify-Checksum -ArchivePath $archivePath -ArchiveName $archiveName

        Write-Host "Extracting..."
        Expand-Archive -Path $archivePath -DestinationPath $tmpDir -Force

        Write-Host "Installing to $installDir..."
        New-Item -ItemType Directory -Path $installDir -Force | Out-Null

        $binDir = Join-Path $installDir "install"
        New-Item -ItemType Directory -Path $binDir -Force | Out-Null
        Copy-Item -Path (Join-Path $tmpDir "opentaint.exe") -Destination (Join-Path $binDir "opentaint.exe") -Force

        $libSrc = Join-Path $tmpDir "lib"
        if (Test-Path $libSrc) {
            $libDst = Join-Path $binDir "lib"
            if (Test-Path $libDst) { Remove-Item -Recurse -Force $libDst }
            Copy-Item -Recurse -Path $libSrc -Destination $libDst
        }

        $jreSrc = Join-Path $tmpDir "jre"
        if (Test-Path $jreSrc) {
            $jreDst = Join-Path $binDir "jre"
            if (Test-Path $jreDst) { Remove-Item -Recurse -Force $jreDst }
            Copy-Item -Recurse -Path $jreSrc -Destination $jreDst
        }

        $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
        if ($userPath -notlike "*$binDir*") {
            [Environment]::SetEnvironmentVariable("Path", "$binDir;$userPath", "User")
            Write-Host ""
            Write-Host "Added $binDir to user PATH."
            Write-Host "Please restart your terminal for changes to take effect."
        }

        Write-Host ""
        Write-Host "opentaint installed successfully!"
        Write-Host "OPENTAINT_BINARY_PATH=$binDir\opentaint.exe"
        Write-Host "Run 'opentaint --version' to verify the installation."
    }
    finally {
        Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
    }
}

Main
