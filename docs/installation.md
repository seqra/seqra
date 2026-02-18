# Installation

**Prerequisites:** Same build requirements as your Java/Kotlin project (Maven or Gradle, project dependencies). Java runtime is bundled with release archives.

## Homebrew (Linux/macOS)

```bash
brew install --cask seqra/tap/opentaint
```

## Install Scripts

**Linux/macOS:**
```bash
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash
```

**Windows (PowerShell):**
```powershell
irm https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.ps1 | iex
```

**Windows (CMD):**
```cmd
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.cmd -o install.cmd && install.cmd && del install.cmd
```

## Docker

No local installation required:

```bash
docker run --rm \
  -v /path/to/project:/project \
  -v $(pwd):/output \
  ghcr.io/seqra/opentaint:latest \
  opentaint scan --output /output/results.sarif /project
```

See [Docker documentation](docker.md) for advanced usage.

## Precompiled Binaries

Each release provides three archive variants:

| Variant | Contents | Use case |
|---------|----------|----------|
| **`opentaint-full`** | Binary + JARs + rules + JRE | Recommended — everything included, no additional downloads |
| **`opentaint`** | Binary + JARs + rules | Use your own JRE (Java 17+) |
| **`opentaint-cli`** | Binary only | Minimal install; run `opentaint pull` to download components |

Download `opentaint-full` for your platform (recommended):

| Platform | Download |
|----------|----------|
| Linux x64 | [opentaint-full_linux_amd64.tar.gz](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_linux_amd64.tar.gz) |
| Linux ARM64 | [opentaint-full_linux_arm64.tar.gz](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_linux_arm64.tar.gz) |
| macOS x64 | [opentaint-full_darwin_amd64.tar.gz](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_darwin_amd64.tar.gz) |
| macOS ARM64 (Apple Silicon) | [opentaint-full_darwin_arm64.tar.gz](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_darwin_arm64.tar.gz) |
| Windows x64 | [opentaint-full_windows_amd64.zip](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_windows_amd64.zip) |
| Windows ARM64 | [opentaint-full_windows_arm64.zip](https://github.com/seqra/opentaint/releases/latest/download/opentaint-full_windows_arm64.zip) |

The `opentaint-full` archives include bundled JARs, rules, and JRE — no additional downloads needed. Replace `opentaint-full` with `opentaint` or `opentaint-cli` in the URLs above for other variants.

### Linux/macOS Installation

Use the install script (recommended) or download and extract manually:

```bash
# Install script (recommended — handles placement automatically)
curl -fsSL https://raw.githubusercontent.com/seqra/opentaint/main/scripts/install/install.sh | bash

# Or for opentaint-cli variant (binary only):
curl -L https://github.com/seqra/opentaint/releases/latest/download/opentaint-cli_linux_amd64.tar.gz | tar xz
sudo mv opentaint /usr/local/bin/
```

Replace the URL with your platform's download link from the table above.

### macOS Security Note

If you see *"opentaint" cannot be opened because the developer cannot be verified*, go to **System Preferences > Security & Privacy** and click **Open anyway**.

## Go Install

Requires Go 1.25+:

```bash
go install github.com/seqra/opentaint/v2@latest
```

Note: `go install` builds only the binary without bundled artifacts. Run `opentaint pull` after installation to download the analyzer components.

Add Go binaries to your PATH if needed:
- **bash (Linux):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
- **zsh (macOS):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`
- **Windows:** Add `%USERPROFILE%\go\bin` to your system PATH

## Updating

```bash
opentaint update
```

For package manager installations, `opentaint update` will show the appropriate command (e.g., `brew upgrade --cask opentaint`).

## Cleaning Up

Remove stale downloaded artifacts:

```bash
opentaint prune              # Interactive confirmation
opentaint prune --dry-run    # See what would be deleted
opentaint prune --yes        # Skip confirmation
```

## Build from Source

```bash
git clone https://github.com/seqra/opentaint.git
cd opentaint
go build
```
