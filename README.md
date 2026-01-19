[![GitHub release](https://img.shields.io/github/release/seqra/opentaint.svg)](https://github.com/seqra/opentaint/releases)

# Opentaint — security-focused static analyzer for Java and Kotlin

[Issues](https://github.com/seqra/opentaint/issues) | [Blog](https://opentaint.org/blog) | [FAQ](docs/faq.md) | [Discord](https://discord.gg/6BXDfbP4p9) | [seqradev@gmail.com](mailto:seqradev@gmail.com)


### Why Seqra?

* **CodeQL power + Semgrep simplicity**:
  - Write security rules using familiar patterns while getting cross-module dataflow analysis
* **Free and source-available**:
  - Use for any purpose except competing commercial offerings for free
* **Workflow ready**:
  - CLI tool with SARIF output for seamless CI/CD integration


### Demo

https://github.com/user-attachments/assets/aba3733b-2959-4470-be0c-605d259e97b6

### Table of Contents
- [License](#license)
- [Features](#features)
  * [Spring Boot Endpoint Extraction](docs/spring-boot-endpoints.md)
  * [Precompiled Classes and JARs Analysis](docs/classes-and-jars-analysis.md)
- [Install Opentaint](#install-seqra)
  * [Download and Install Precompiled Binaries](#download-and-install-precompiled-binaries)
  * [Install via Go (Linux/macOS/Windows)](#install-via-go-linuxmacoswindows)
  * [Compile from source (Linux/macOS)](#compile-from-source-linuxmacos)
- [Docker](#docker)
- [Usage](#usage)
  * [Basic Workflow](#basic-workflow)
  * [Commands Overview](#commands-overview)
  * [Project Type Examples](#project-type-examples)
  * [View and Analyze Results](#view-and-analyze-results)
  * [CI/CD Integration](#cicd-integration)
- [Configuration](#configuration)
  * [Configuration File](#configuration-file)
  * [Environment Variables](#environment-variables)
- [Troubleshooting](#troubleshooting)
  * [Build Issues](#build-issues)
  * [Dependency Download Issues](#dependency-download-issues)
  * [Java Runtime Issues](#java-runtime-issues)
  * [Memory and Performance](#memory-and-performance)
  * [Logs and Debugging](#logs-and-debugging)
- [Changelog](#changelog)

# License

This project is released under the MIT License.

# Features

## Spring Boot Endpoint Extraction

Opentaint automatically extracts URL path information from Spring Boot applications and includes controller-to-endpoint mappings in SARIF reports. This feature helps identify the web attack surface of your application by mapping security findings to specific HTTP endpoints.

[Learn more about Spring Boot endpoint extraction](docs/spring-boot-endpoints.md)

## Precompiled Classes and JARs Analysis

Opentaint supports analyzing precompiled classes and JARs through a `project.yaml` configuration file. This enables security analysis when you cannot compile the project due to missing sources or unavailable build environment.

[Learn more about precompiled classes and JARs analysis](docs/classes-and-jars-analysis.md)

# Install Opentaint

### Prerequisites:

- Same build requirements as your Java/Koltin project (Maven or Gradle, Java runtime, project dependencies)

## Download and Install Precompiled Binaries

Download the appropriate binary for your platform:

- **[Linux x64](https://github.com/seqra/opentaint/releases/latest/download/seqra_linux_amd64.tar.gz)**
- **[Linux ARM64](https://github.com/seqra/opentaint/releases/latest/download/seqra_linux_arm64.tar.gz)**
- **[macOS x64](https://github.com/seqra/opentaint/releases/latest/download/seqra_darwin_amd64.tar.gz)**
- **[macOS ARM64 (Apple Silicon)](https://github.com/seqra/opentaint/releases/latest/download/seqra_darwin_arm64.tar.gz)**
- **[Windows x64](https://github.com/seqra/opentaint/releases/latest/download/seqra_windows_amd64.zip)**
- **[Windows ARM64](https://github.com/seqra/opentaint/releases/latest/download/seqra_windows_arm64.zip)**

### Install Globally

**Linux/macOS:**

Replace the URL with your platform's download link from the list above.

```bash
mkdir opentaint
cd opentaint
curl -L https://github.com/seqra/opentaint/releases/latest/download/seqra_linux_amd64.tar.gz -o seqra.tar.gz
tar -xzf seqra.tar.gz opentaint
rm seqra.tar.gz
sudo ln -s $(pwd)/seqra /usr/local/bin/opentaint
```

**Windows:**
1. Download and extract the ZIP file
2. Add the extracted folder to your system PATH

### macOS Security Notice

On macOS, you may see: *"opentaint" cannot be opened because the developer cannot be verified.*

To resolve this:
1. Go to **System Preferences > Security & Privacy**
2. At the bottom, you'll see *"opentaint" was blocked*
3. Click **Open anyway** if you trust the installer

## Install via Go (Linux/macOS/Windows)

**Prerequisites:** Go 1.25+ is required. If you don't have Go installed:
- **Linux:** Follow the [official Go installation guide](https://golang.org/doc/install) or use your package manager (e.g., `sudo apt install golang-go` on Ubuntu)
- **macOS:** Install via [Homebrew](https://brew.sh/): `brew install go` or download from [golang.org](https://golang.org/dl/)
- **Windows:** Download and install from [golang.org](https://golang.org/dl/) or use [Chocolatey](https://chocolatey.org/): `choco install golang`

```bash
go install github.com/seqra/opentaint/v2@latest
```

> **Optional:** Add `GOPATH` to path

  * **bash (Linux):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.bashrc && source ~/.bashrc`
  * **zsh (macOS):** `echo 'export PATH=$PATH:$(go env GOPATH)/bin' >> ~/.zshrc && source ~/.zshrc`
  * **Windows:** Add `%USERPROFILE%\go\bin` to your system PATH environment variable

### Download Dependencies

After installation, download the required analyzer components:

```bash
opentaint pull
```

This command downloads:
- Opentaint autobuilder JAR
- Opentaint analyzer JAR
- Opentaint rules archive
- Java runtime (Temurin JDK)

This prepares your environment with all required dependencies for offline analysis.

## Compile from source (Linux/macOS/Windows)

  You can compile the project from source using the following commands:

  ```bash
  git clone https://github.com/seqra/opentaint.git
  cd opentaint
  go build
  ```

# Docker

Run Opentaint using Docker without installing any dependencies locally:

```bash
docker run --rm \
  -v /path/to/project:/project \
  -v $(pwd):/output \
  ghcr.io/seqra/opentaint:latest \
  opentaint scan --output /output/results.sarif /project
```

[Learn more about Docker usage](docs/docker.md)

# Usage

## Basic Workflow

Opentaint follows a simple workflow:

1. **Scan your project**:
   ```bash
   opentaint scan --output results.sarif /path/to/your/java/project
   ```

2. **Review results**:
   ```bash
   opentaint summary --show-findings results.sarif
   ```

## Commands Overview

### `opentaint scan` - Analyze Projects

Automatically detects Maven/Gradle projects, builds them, and performs security analysis:

**Basic scan:**
```bash
opentaint scan --output results.sarif /path/to/your/java/kotlin/project
```

**Scan with custom memory allocation:**
```bash
opentaint scan --max-memory 16G --output results.sarif /path/to/your/java/kotlin/project
```

**Scan with specific severity levels:**
```bash
opentaint scan --severity error --severity warning --output results.sarif /path/to/your/java/kotlin/project
```

**Scan with custom ruleset:**
```bash
opentaint scan --ruleset /path/to/custom/rules.yaml --output results.sarif /path/to/your/java/kotlin/project
```

**Scan with timeout:**
```bash
opentaint scan --timeout 5m --output results.sarif /path/to/your/java/kotlin/project
```

**First-time setup:** If you haven't run `opentaint pull` yet, Opentaint will automatically download required dependencies during the first scan.

### `opentaint compile` - Build Project Model

Compiles Java and Kotlin projects and generates project models for analysis. This is useful when you want to separate compilation from scanning or need to inspect the project model.

Compile project and generate model:
```bash
opentaint compile --output ./my-project-model /path/to/your/java/kotlin/project
```

Use the compiled project model for scanning:
```bash
opentaint scan --output results.sarif ./my-project-model
```

**Note:** The `opentaint scan` command includes compilation automatically, so `opentaint compile` is typically used when you need fine-grained control over the build process.

### `opentaint project` - Precompiled Classes Analysis

Create project models from precompiled JARs or classes when source code isn't available.

Create project model from JAR:
```bash
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath /path/to/app.jar --package com.example
```

Scan the project model:
```bash
opentaint scan --output results.sarif ./project-model
```

### `opentaint pull` - Download Dependencies

Downloads all required components for offline analysis:
- Opentaint autobuilder JAR
- Opentaint analyzer JAR
- Security rules
- Java runtime (Temurin JDK)

```bash
opentaint pull
```

### Global Options

These options apply to all commands:

- `--config string`: Path to configuration file
- `--java-version int`: Java version for analyzer (default: 23)
- `--quiet`: Suppress interactive output
- `--verbosity string`: Log level (debug, info, warn, error, fatal, panic)

For persistent configuration using files or environment variables, see the [Configuration](#configuration) section.

### `opentaint summary` - View Analysis Results

Quickly review SARIF analysis results from the command line:

```bash
# Basic summary
opentaint summary results.sarif

# Show all findings with details
opentaint summary --show-findings results.sarif
```

## View and Analyze Results

Opentaint generates results in the *SARIF* format, which can be explored in several ways:

* **Command-line preview**

  Quickly review the findings with:
  ```bash
  opentaint summary --show-findings results.sarif
  ```

* **VS Code**

  Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) extension for a rich, interactive experience.

* **GitHub**

  Use [opentaint-action](https://github.com/seqra/opentaint-action) for automated analysis and GitHub code scanning integration. The action performs analysis and uploads results automatically:

  ```yaml
  - uses: seqra/opentaint-action@v2
    with:
      path: ./
  ```

  Alternatively, you can manually upload SARIF files to [GitHub code scanning](https://docs.github.com/en/code-security/code-scanning/integrating-with-code-scanning/uploading-a-sarif-file-to-github) if needed.

* **CodeChecker**

  Use [CodeChecker](https://github.com/Ericsson/codechecker) for advanced result management, tracking, and team collaboration.


## CI/CD Integration

For seamless integration with your CI/CD pipelines, check out our dedicated integration repositories:

- **[opentaint-action](https://github.com/seqra/opentaint-action)** - GitHub Action for easy integration with GitHub workflows
- **[opentaint-gitlab](https://github.com/seqra/opentaint-gitlab)** - GitLab CI template for automated security scanning

# Configuration

Opentaint can be configured through a configuration file or environment variables. Command-line flags always take precedence over configuration file settings and environment variables.

## Configuration File

Create a YAML configuration file and specify it with the `--config` flag:

```bash
opentaint scan --config /path/to/config.yaml /path/to/project
```

**Example configuration file:**

```yaml
# Scan settings
scan:
  timeout: 15m
  max_memory: 16G

# Logging
log:
  verbosity: info  # debug, info, warn, error, fatal, panic

# Java runtime settings
java:
  version: 23

# Suppress interactive output
quiet: false
```

**Available configuration options:**

| Setting | Description | Default |
|---------|-------------|---------|
| `scan.timeout` | Analysis timeout duration | `15m` |
| `scan.max_memory` | Maximum memory for analyzer (e.g., `8G`, `1024m`) | `8G` |
| `log.verbosity` | Log level: debug, info, warn, error, fatal, panic | `info` |
| `java.version` | Java version for running the analyzer | `23` |
| `quiet` | Suppress interactive console output | `false` |

## Environment Variables

All configuration options can also be set via environment variables with the `OPENTAINT_` prefix. Use underscores to separate nested keys:

```bash
export OPENTAINT_SCAN_TIMEOUT=30m
export OPENTAINT_SCAN_MAX_MEMORY=16G
export OPENTAINT_LOG_VERBOSITY=debug
export OPENTAINT_JAVA_VERSION=23

opentaint scan --output results.sarif /path/to/project
```

The environment variables above set scan timeout, maximum memory, log verbosity, and Java version respectively.

**Priority order (highest to lowest):**
1. Command-line flags
2. Environment variables
3. Configuration file
4. Default values

# Troubleshooting

## Build Issues

> **Note:** Only Maven and Gradle projects are supported for automatic compilation.

**Project doesn't compile:**
* Check that all project dependencies are available in your local environment
* Ensure your `JAVA_HOME` is set to a compatible Java version for your project
* Verify your project builds successfully

**Missing dependencies or complex build:**

For projects with complex build configurations, use precompiled JARs instead. First, create a project model from the compiled artifacts:
```bash
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath target/myapp.jar --package com.example
```

Then scan the project model:
```bash
opentaint scan --output results.sarif ./project-model
```

## Dependency Download Issues

**Re-downloading dependencies:**
To force re-download of dependencies while keeping your configuration:
```bash
rm -rf ~/.opentaint/autobuilder ~/.opentaint/analyzer ~/.opentaint/rules ~/.opentaint/jdk
opentaint pull
```

**Download location:**
All dependencies are stored in `~/.opentaint/`:
* `~/.opentaint/autobuilder/` - Project compilation tools
* `~/.opentaint/analyzer/` - Security analyzer
* `~/.opentaint/rules/` - Security rules
* `~/.opentaint/jdk/` - Java runtime

## Java Runtime Issues

**Wrong Java version for your project:**

If compilation fails due to Java version mismatch, set `JAVA_HOME` to match your project's requirements. For example, to use Java 17 for compilation:
```bash
export JAVA_HOME=/path/to/java-17
opentaint scan --output results.sarif /path/to/project
```

## Memory and Performance

**Out of memory during analysis:**
For large projects, increase the analyzer memory:
```bash
opentaint scan --max-memory 16G --output results.sarif /path/to/project
```

**Analysis timeout:**
For complex projects that take longer to analyze:
```bash
opentaint scan --timeout 20m --output results.sarif /path/to/project
```

**Persistent memory settings:**
Add to your configuration file (`~/.opentaint/config.yaml`):
```yaml
scan:
  max_memory: 16G
  timeout: 20m
```

## Logs and Debugging

**Enable verbose logging:**
```bash
opentaint scan --verbosity debug --output results.sarif /path/to/project
```

**Common log locations to check:**

Temporary directory is shown in output.

# Changelog
See [CHANGELOG](CHANGELOG.md).
