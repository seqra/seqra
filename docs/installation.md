# Installation

**Prerequisites:** Same build requirements as your Java/Kotlin project (Maven or Gradle, Java runtime, project dependencies).

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

### Linux/macOS Installation

```bash
mkdir seqra && cd seqra
curl -L https://github.com/seqra/seqra/releases/latest/download/seqra_linux_amd64.tar.gz -o seqra.tar.gz
tar -xzf seqra.tar.gz seqra && rm seqra.tar.gz
sudo ln -s $(pwd)/seqra /usr/local/bin/seqra
```

Replace the URL with your platform's download link from the table above.

### macOS Security Note

If you see *"seqra" cannot be opened because the developer cannot be verified*, go to **System Preferences > Security & Privacy** and click **Open anyway**.

## Go Install

Requires Go 1.25+:

```bash
go install github.com/seqra/seqra/v2@latest
```

Add Go binaries to your PATH if needed:
- **bash (Linux):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
- **zsh (macOS):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`
- **Windows:** Add `%USERPROFILE%\go\bin` to your system PATH

## Download Dependencies

After installation, download the analyzer components:

```bash
seqra pull
```

This downloads:
- Seqra autobuilder JAR
- Seqra analyzer JAR
- Security rules
- Java runtime (Temurin JDK)

First-time `seqra scan` will also download these automatically.

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

## Build from Source

```bash
git clone https://github.com/seqra/seqra.git
cd seqra
go build
```
