# Installation

**Prerequisites:** Same build requirements as your Java/Kotlin project (Maven or Gradle, project dependencies). Java runtime is bundled with release archives.

## Homebrew (macOS/Linux)

```bash
brew install seqra/tap/seqra
```

## Scoop (Windows)

```powershell
scoop bucket add seqra https://github.com/seqra/scoop-seqra
scoop install seqra
```

## Install Scripts

**Linux/macOS:**
```bash
curl -fsSL https://raw.githubusercontent.com/seqra/seqra/main/scripts/install/install.sh | bash
```

**Windows (PowerShell):**
```powershell
irm https://raw.githubusercontent.com/seqra/seqra/main/scripts/install/install.ps1 | iex
```

## Docker

No local installation required:

```bash
docker run --rm \
  -v /path/to/project:/project \
  -v $(pwd):/output \
  ghcr.io/seqra/seqra:latest \
  seqra scan --output /output/results.sarif /project
```

See [Docker documentation](docker.md) for advanced usage.

## Precompiled Binaries

Download for your platform:

| Platform | Download |
|----------|----------|
| Linux x64 | [seqra_linux_amd64.tar.gz](https://github.com/seqra/seqra/releases/latest/download/seqra_linux_amd64.tar.gz) |
| Linux ARM64 | [seqra_linux_arm64.tar.gz](https://github.com/seqra/seqra/releases/latest/download/seqra_linux_arm64.tar.gz) |
| macOS x64 | [seqra_darwin_amd64.tar.gz](https://github.com/seqra/seqra/releases/latest/download/seqra_darwin_amd64.tar.gz) |
| macOS ARM64 (Apple Silicon) | [seqra_darwin_arm64.tar.gz](https://github.com/seqra/seqra/releases/latest/download/seqra_darwin_arm64.tar.gz) |
| Windows x64 | [seqra_windows_amd64.zip](https://github.com/seqra/seqra/releases/latest/download/seqra_windows_amd64.zip) |
| Windows ARM64 | [seqra_windows_arm64.zip](https://github.com/seqra/seqra/releases/latest/download/seqra_windows_arm64.zip) |

Release archives include bundled JARs, rules, and JRE — no additional downloads needed.

### Linux/macOS Installation

```bash
curl -L https://github.com/seqra/seqra/releases/latest/download/seqra_linux_amd64.tar.gz | tar xz
sudo mv seqra /usr/local/bin/
```

Replace the URL with your platform's download link from the table above.

### macOS Security Note

If you see *"seqra" cannot be opened because the developer cannot be verified*, go to **System Preferences > Security & Privacy** and click **Open anyway**.

## Go Install

Requires Go 1.25+:

```bash
go install github.com/seqra/seqra/v2@latest
```

Note: `go install` builds only the binary without bundled artifacts. Run `seqra pull` after installation to download the analyzer components.

Add Go binaries to your PATH if needed:
- **bash (Linux):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
- **zsh (macOS):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`
- **Windows:** Add `%USERPROFILE%\go\bin` to your system PATH

## Updating

```bash
seqra update
```

For package manager installations, `seqra update` will show the appropriate command (e.g., `brew upgrade seqra`).

## Cleaning Up

Remove stale downloaded artifacts:

```bash
seqra prune              # Interactive confirmation
seqra prune --dry-run    # See what would be deleted
seqra prune --yes        # Skip confirmation
```

## Build from Source

```bash
git clone https://github.com/seqra/seqra.git
cd seqra
go build
```
