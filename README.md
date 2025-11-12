[![GitHub release](https://img.shields.io/github/release/seqra/opentaint.svg)](https://github.com/seqra/opentaint/releases)

# Opentaint — security-focused static analyzer for Java

[Issues](https://github.com/seqra/opentaint/issues) | [Blog](https://opentaint.org/blog) | [FAQ](docs/faq.md) | [Discord](https://discord.gg/6BXDfbP4p9) | [seqradev@gmail.com](mailto:seqradev@gmail.com)


### Why Seqra?

* **CodeQL power + Semgrep simplicity**:
  - Write security rules using familiar patterns while getting cross-module dataflow analysis
* **Free and source-available**:
  - Use for any purpose except competing commercial offerings for free
* **Workflow ready**:
  - CLI tool with SARIF output for seamless CI/CD integration


### Demo

https://github.com/user-attachments/assets/ddaa55de-8623-4f1a-be3e-f66d34b7336d


### Table of Contents
- [License](#license)
- [Quick Start](#quick-start)
  * [Download and Install Precompiled Binaries (Linux)](#download-and-install-precompiled-binaries-linux)
  * [Install via Go (Linux/macOS)](#install-via-go-linuxmacos)
  * [Compile from source (Linux/macOS)](#compile-from-source-linuxmacos)
  * [Scan](#scan)
  * [View and Analyze Results](#view-and-analyze-results)
  * [CI/CD Integration](#cicd-integration)
- [Troubleshooting](#troubleshooting)
  * [Docker not running](#docker-not-running)
  * [Build Issues](#build-issues)
  * [Logs and Debugging](#logs-and-debugging)
- [Changelog](#changelog)

# License

This project is released under the MIT License.

# Install Opentaint CLI

### Prerequisites:

**For Docker-based scanning (default):**
- [Install Docker](https://docs.docker.com/get-started/get-docker/)
- **For Apple Silicon Mac**: you need [Enable x86_64/amd64 emulation in Docker Desktop](https://docs.docker.com/desktop/settings/mac/#general)

**For native compiling (optional):**
- Java untime environment and Maven or Gradle installed and configured
- Project dependencies available in local environment

**For native scanning (optional):**
- Java 17+ runtime environment

## Download and Install Precompiled Binaries (Linux)

- #### [Download Linux binary](https://github.com/seqra/opentaint/releases/latest/download/seqra_linux_amd64.tar.gz)

### Install  Globally

Install opentaint globally on your machine by placing the compiled binary on your path.

```bash
mkdir opentaint
cd opentaint
curl -L https://github.com/seqra/opentaint/releases/latest/download/seqra_linux_amd64.tar.gz -o seqra.tar.gz
tar -xzf seqra.tar.gz opentaint
rm seqra.tar.gz
sudo ln -s $(pwd)/seqra /usr/local/bin/opentaint
```

## Install via Go (Linux/macOS)

**Prerequisites:** Go 1.19+ is required. If you don't have Go installed:
- **Linux:** Follow the [official Go installation guide](https://golang.org/doc/install) or use your package manager (e.g., `sudo apt install golang-go` on Ubuntu)
- **macOS:** Install via [Homebrew](https://brew.sh/): `brew install go` or download from [golang.org](https://golang.org/dl/)

```bash
go install github.com/seqra/opentaint@latest
```

> **Optional:** Add `GOPATH` to path

  * bash `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
  * zsh (macOS) `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`

## Compile from source (Linux/macOS)

  You can compile the project from source using the following commands:

  ```bash
  git clone https://github.com/seqra/opentaint.git
  cd opentaint
  go build
  ./seqra --version
  ```

## Scan

  Scan a Java project and generate SARIF report

  ```bash
  opentaint scan --output results.sarif /path/to/your/java/project
  ```

### Native Environment Scanning

  For environments where Docker is not available or when you prefer to use your local Java toolchain, Opentaint supports native execution:

#### Use native compilation (requires local Maven/Gradle)
```bash
  opentaint scan --compile-type native --output results.sarif /path/to/your/java/project
```

#### Use native scanning (requires local Java 17+ runtime)
```bash
  opentaint scan --scan-type native --output results.sarif /path/to/your/java/project
```

#### Use both native compilation and scanning
```bash
  opentaint scan --compile-type native --scan-type native --output results.sarif /path/to/your/java/project
```

## View and Analyze Results

Opentaint generates results in the *SARIF* format, which can be explored in several ways:

* **VS Code**

  Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) extension for a rich, interactive experience.

* **GitHub**

  Upload results to [GitHub code scanning](https://docs.github.com/en/code-security/code-scanning/integrating-with-code-scanning/uploading-a-sarif-file-to-github) by [opentaint-action](https://github.com/seqra/opentaint-action) for security alerts and code quality feedback.

* **Command-line preview**

  Quickly review the findings with:
  ```bash
  opentaint summary --show-findings results.sarif
  ```

* **CodeChecker**

  Use [CodeChecker](https://github.com/Ericsson/codechecker) for advanced result management, tracking, and team collaboration.


## CI/CD Integration

For seamless integration with your CI/CD pipelines, check out our dedicated integration repositories:

- **[opentaint-action](https://github.com/seqra/opentaint-action)** - GitHub Action for easy integration with GitHub workflows
- **[opentaint-gitlab](https://github.com/seqra/opentaint-gitlab)** - GitLab CI template for automated security scanning


# Troubleshooting

### Docker not running

  * Make sure Docker is installed on your system.
  * Run `docker info` to confirm that Docker is up and accessible.

### Build Issues

  > **Note:** **only Maven and Gradle projects are supported**
  * Verify that your project builds successfully with `Maven` or `Gradle`
  * If the Docker image is missing required dependencies, try native compilation:
    ```bash
    opentaint scan --compile-type native --output results.sarif /path/to/your/java/project
    ```
  * For native compilation issues:
    - Ensure Java runtime is installed and accessible via `java -version`
    - Verify Maven/Gradle can build your project locally
    - Check that all project dependencies are available in your local environment

### Logs and Debugging

  * Add the `--verbosity debug` flag to enable detailed logging
  * Check logs in: `~/.opentaint/logs/`

# Changelog
See [CHANGELOG](CHANGELOG.md).
