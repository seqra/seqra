# Usage

## Scanning Projects

```bash
# Basic scan
opentaint scan --output results.sarif /path/to/project

# With custom memory allocation
opentaint scan --max-memory 16G --output results.sarif /path/to/project

# With specific severity levels
opentaint scan --severity error --severity warning --output results.sarif /path/to/project

# With custom ruleset
opentaint scan --ruleset /path/to/rules.yaml --output results.sarif /path/to/project

# With timeout
opentaint scan --timeout 5m --output results.sarif /path/to/project
```

## Viewing Results

```bash
# Summary
opentaint summary results.sarif

# With all findings
opentaint summary --show-findings results.sarif
```

### IDE Integration

Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) VS Code extension for a rich, interactive experience.

### GitHub Integration

Use [opentaint-action](https://github.com/seqra/opentaint-action) for automated analysis and GitHub code scanning integration:

```yaml
- uses: seqra/opentaint-action@v2
  with:
    path: ./
```

### CodeChecker

Use [CodeChecker](https://github.com/Ericsson/codechecker) for advanced result management, tracking, and team collaboration.

## Commands Reference

| Command | Description |
|---------|-------------|
| `opentaint scan` | Analyze projects (auto-detects Maven/Gradle, builds, and scans) |
| `opentaint compile` | Build project model separately from scanning |
| `opentaint project` | Create project model from precompiled JARs/classes |
| `opentaint summary` | View SARIF analysis results |
| `opentaint pull` | Download analyzer dependencies |

### opentaint scan

Automatically detects Maven/Gradle projects, builds them, and performs security analysis.

### opentaint compile

Compiles Java and Kotlin projects and generates project models for analysis. Useful when you want to separate compilation from scanning or need to inspect the project model.

```bash
opentaint compile --output ./my-project-model /path/to/project
opentaint scan --output results.sarif ./my-project-model
```

### opentaint project

Create project models from precompiled JARs or classes when source code isn't available.

```bash
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath /path/to/app.jar --package com.example

opentaint scan --output results.sarif ./project-model
```

## Global Options

These options apply to all commands:

- `--config string` — Path to configuration file
- `--java-version int` — Java version for analyzer (default: 23)
- `--quiet` — Suppress interactive output
- `--verbosity string` — Log level (debug, info, warn, error, fatal, panic)

For persistent configuration using files or environment variables, see the [Configuration](configuration.md) documentation.
