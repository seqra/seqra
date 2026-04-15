# Usage

## Scanning Projects

```bash
# Basic scan (current directory, SARIF written to the cached model directory)
opentaint scan

# Scan a specific project
opentaint scan /path/to/project

# With explicit output path
opentaint scan --output results.sarif /path/to/project

# With custom memory allocation
opentaint scan --max-memory 16G /path/to/project

# With specific severity levels
opentaint scan --severity error --severity warning /path/to/project

# With custom ruleset
opentaint scan --ruleset /path/to/rules.yaml /path/to/project

# With timeout
opentaint scan --timeout 5m /path/to/project
```

## Viewing Results

```bash
# Summary
opentaint summary results.sarif

# With all findings
opentaint summary --show-findings results.sarif

# With full code flow and code snippets
opentaint summary --show-findings --verbose-flow --show-code-snippets results.sarif
```

### IDE Integration

Open `results.sarif` with the [SARIF Viewer](https://marketplace.visualstudio.com/items?itemName=MS-SarifVSCode.sarif-viewer) VS Code extension for a rich, interactive experience.

### GitHub Integration

Use [GitHub Action](https://github.com/seqra/opentaint/tree/main/github) for automated analysis and GitHub code scanning integration:

```yaml
- uses: seqra/opentaint/github@v2
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
| `opentaint update` | Update to latest version |
| `opentaint prune` | Remove stale downloaded artifacts and cached models |

### opentaint scan

Automatically detects Maven/Gradle projects, builds them, and performs security analysis. The source path defaults to the current directory when omitted.

On the first run, the compiled project model is cached in `~/.opentaint/models/`. Subsequent scans of the same project reuse the cached model, skipping compilation entirely.

| Flag | Description |
|------|-------------|
| `--output`, `-o` | Path to the SARIF report (default: `<model-dir>/sources/opentaint.sarif`) |
| `--recompile` | Force recompilation even if a cached project model exists |
| `--project-model` | Path to a pre-compiled project model (skips compilation) |
| `--timeout`, `-t` | Timeout for analysis (default: `15m`) |
| `--max-memory` | Maximum memory for the analyzer (default: `8G`) |
| `--severity` | Severity levels to report (default: `warning`, `error`) |
| `--ruleset` | YAML rules file or directory (default: `builtin`) |
| `--dry-run` | Validate inputs and show what would run without compiling or scanning |

### opentaint compile

Compiles Java and Kotlin projects and generates project models for analysis. Useful when you want to separate compilation from scanning or need to inspect the project model.

```bash
opentaint compile --output ./my-project-model /path/to/project
opentaint scan --project-model ./my-project-model
```

### opentaint summary

View findings from a SARIF report.

| Flag | Description |
|------|-------------|
| `--show-findings` | Show all findings |
| `--show-code-snippets` | Show code snippets for each finding |
| `--verbose-flow` | Show full code flow steps for each finding |

### opentaint project

Create project models from precompiled JARs or classes when source code isn't available.

```bash
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath /path/to/app.jar --package com.example

opentaint scan --project-model ./project-model
```

## Model Caching

When `opentaint scan` compiles a project, the resulting project model is cached in `~/.opentaint/models/`. The cache directory name is derived from the project path (e.g. `my-project-a1b2c3d4`).

On subsequent scans of the same project, the cached model is reused automatically — compilation is skipped entirely. This makes repeated scans significantly faster.

```bash
# First scan: compiles and caches the model
opentaint scan /path/to/project

# Second scan: reuses the cached model (no compilation)
opentaint scan /path/to/project

# Force recompilation (e.g. after code changes)
opentaint scan --recompile /path/to/project
```

If a compilation is already in progress for the same project (detected via a staging directory), the scan aborts with an error instead of compiling concurrently.

To remove all cached models:

```bash
opentaint prune
```

When `--output` is not specified, the SARIF report is written next to the cached model at `<model-dir>/sources/opentaint.sarif`.

## Global Options

These options apply to all commands:

- `--config string` — Path to configuration file
- `--java-version int` — Java version for analyzer (default: 21)
- `--quiet` — Suppress interactive output
- `--verbosity string` — Verbosity level (`info`, `debug`)
- `--color string` — Color mode (`auto`, `always`, `never`); defaults to `auto` (detects terminal)

For persistent configuration using files or environment variables, see the [Configuration](configuration.md) documentation.
