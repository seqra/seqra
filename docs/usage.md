# Usage

## Scanning Projects

```bash
# Basic scan
seqra scan --output results.sarif /path/to/project

# With custom memory allocation
seqra scan --max-memory 16G --output results.sarif /path/to/project

# With specific severity levels
seqra scan --severity error --severity warning --output results.sarif /path/to/project

# With custom ruleset
seqra scan --ruleset /path/to/rules.yaml --output results.sarif /path/to/project

# With timeout
seqra scan --timeout 5m --output results.sarif /path/to/project
```

## Viewing Results

```bash
# Summary
seqra summary results.sarif

# With all findings
seqra summary --show-findings results.sarif
```

### IDE Integration

Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) VS Code extension for a rich, interactive experience.

### GitHub Integration

Use [seqra-action](https://github.com/seqra/seqra-action) for automated analysis and GitHub code scanning integration:

```yaml
- uses: seqra/seqra-action@v2
  with:
    path: ./
```

### CodeChecker

Use [CodeChecker](https://github.com/Ericsson/codechecker) for advanced result management, tracking, and team collaboration.

## Commands Reference

| Command | Description |
|---------|-------------|
| `seqra scan` | Analyze projects (auto-detects Maven/Gradle, builds, and scans) |
| `seqra compile` | Build project model separately from scanning |
| `seqra project` | Create project model from precompiled JARs/classes |
| `seqra summary` | View SARIF analysis results |
| `seqra pull` | Download analyzer dependencies |

### seqra scan

Automatically detects Maven/Gradle projects, builds them, and performs security analysis.

### seqra compile

Compiles Java and Kotlin projects and generates project models for analysis. Useful when you want to separate compilation from scanning or need to inspect the project model.

```bash
seqra compile --output ./my-project-model /path/to/project
seqra scan --output results.sarif ./my-project-model
```

### seqra project

Create project models from precompiled JARs or classes when source code isn't available.

```bash
seqra project --output ./project-model --source-root /path/to/source \
  --classpath /path/to/app.jar --package com.example

seqra scan --output results.sarif ./project-model
```

## Global Options

These options apply to all commands:

- `--config string` — Path to configuration file
- `--java-version int` — Java version for analyzer (default: 23)
- `--quiet` — Suppress interactive output
- `--verbosity string` — Log level (debug, info, warn, error, fatal, panic)

For persistent configuration using files or environment variables, see the [Configuration](configuration.md) documentation.
