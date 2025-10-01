[![GitHub release](https://img.shields.io/github/release/seqra/seqra.svg)](https://github.com/seqra/seqra/releases)

# Seqra — security-focused static analyzer for Java

[Issues](https://github.com/seqra/seqra/issues) | [FAQ](docs/faq.md) | [Discord](https://discord.gg/FtKRPv8n) | [seqradev@gmail.com](mailto:seqradev@gmail.com)

### Why Seqra?

* **CodeQL power + Semgrep simplicity**:
  - Write security rules using familiar patterns while getting cross-module dataflow analysis
* **Free and source-available**:
  - Use for any purpose except competing commercial offerings for free
* **Workflow ready**:
  - CLI tool with SARIF output for seamless CI/CD integration



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

The [core analysis engine](https://github.com/seqra/seqra-jvm-sast) is source-available under the [Functional Source License (FSL-1.1-ALv2)](https://fsl.software/), which converts to Apache 2.0 two years after each release. You can use Seqra for free, including for commercial use, except for competing products or services.

# Install Seqra CLI

### Prerequisites:

- [Install Docker](https://docs.docker.com/get-started/get-docker/)
- *For Apple Silicon Mac*: [Enable x86_64/amd64 emulation in Docker Desktop](https://docs.docker.com/desktop/settings/mac/#general)

## Download and Install Precompiled Binaries (Linux)

- #### [Download Linux binary](https://github.com/seqra/seqra/releases/latest/download/seqra_linux_amd64.tar.gz)

### Install  Globally

Install seqra globally on your machine by placing the compiled binary on your path.

```bash
mkdir seqra
cd seqra
curl -L https://github.com/seqra/seqra/releases/latest/download/seqra_linux_amd64.tar.gz -o seqra.tar.gz
tar -xzf seqra.tar.gz seqra
rm seqra.tar.gz
sudo ln -s $(pwd)/seqra /usr/local/bin/seqra
```

## Install via Go (Linux/macOS)

> **Note:** **Support Apple Silicon Mac is experimental** you need [Enable x86_64/amd64 emulation in Docker Desktop](https://docs.docker.com/desktop/settings/mac/#general)

```bash
go install github.com/seqra/seqra@latest
```

> **Optional:** Add `GOPATH` to path

  * bash `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
  * zsh (macOS) `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`

## Compile from source (Linux/macOS)

  You can compile the project from source using the following commands:
  
  ```bash
  git clone https://github.com/seqra/seqra.git
  cd seqra
  go build
  ./seqra --version
  ```

## Scan

  Scan a Java project and generate SARIF report

  ```bash
  seqra scan --output results.sarif /path/to/your/java/project
  ```

## View and Analyze Results

Seqra generates results in the *SARIF* format, which can be explored in several ways:

* **VS Code**

  Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) extension for a rich, interactive experience.

* **GitHub**

  Upload results to [GitHub code scanning](https://docs.github.com/en/code-security/code-scanning/integrating-with-code-scanning/uploading-a-sarif-file-to-github) by [seqra-action](https://github.com/seqra/seqra-action) for security alerts and code quality feedback.

* **Command-line preview**

  Quickly review the findings with:
  ```bash
  seqra summary --show-findings results.sarif
  ```

* **CodeChecker**

  Use [CodeChecker](https://github.com/Ericsson/codechecker) for advanced result management, tracking, and team collaboration.


## CI/CD Integration

For seamless integration with your CI/CD pipelines, check out our dedicated integration repositories:

- **[seqra-action](https://github.com/seqra/seqra-action)** - GitHub Action for easy integration with GitHub workflows
- **[seqra-gitlab](https://github.com/seqra/seqra-gitlab)** - GitLab CI template for automated security scanning


# Troubleshooting

### Docker not running

  * Make sure Docker is installed on your system.
  * Run `docker info` to confirm that Docker is up and accessible.

### Build Issues

  > **Note:** **only Maven and Gradle projects are supported**
  * Verify that your project builds successfully with `Maven` or `Gradle`
  * If the Docker image is missing required dependencies, try scanning the project with a native compilation:
    ```bash
    seqra scan --compile-type native --output results.sarif /path/to/your/java/project
    ```

### Logs and Debugging

  * Add the `--verbosity debug` flag to enable detailed logging
  * Check logs in: `~/.seqra/logs/`

# Changelog
See [CHANGELOG](CHANGELOG.md).


