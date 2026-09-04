# Usage

> **Run without installing:** every `opentaint <command>` below can be run install-free with `npx @seqra/opentaint <command>` (requires Node.js), e.g. `npx @seqra/opentaint scan`. See [Installation](installation.md#npm).

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

# Only error-level findings in a path, with up to 3 nesting levels of flow
opentaint summary results.sarif --severity error --path "src/main/**" --max-nesting-level 3 --show-findings

# Focus a single rule by its leaf name
opentaint summary results.sarif --rule-id sql-injection-in-spring-app --show-findings

# Group the listing by severity
opentaint summary results.sarif --group-by severity --show-findings

# Show all code flows for findings with multiple paths
opentaint summary results.sarif --show-findings --code-flow all

# Re-triage a single finding by its partial fingerprint (the abbrev shown as
# the finding header in the listing)
opentaint summary results.sarif --show-findings --partial-fingerprint deadbeefcafe
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
| `opentaint health` | Show resolved paths for the analyzer, autobuilder, rules, and Java runtime |
| `opentaint test rule` | Create, run, and debug detection-rule tests |
| `opentaint test approximation` | Create and run dataflow-approximation tests |
| `opentaint approximation` | Create dataflow approximation projects |
| `opentaint pull` | Download analyzer dependencies |
| `opentaint update` | Update to latest version |
| `opentaint prune` | Remove stale downloaded artifacts and cached models |

### opentaint scan

Automatically detects Maven/Gradle projects, builds them, and performs security analysis. The source path defaults to the current directory when omitted.

On the first run, the compiled project model is cached in `~/.opentaint/cache/`. Subsequent scans of the same project reuse the cached model, skipping compilation entirely.

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
| `--log-file` | Path to the log file (default: `<cache-dir>/logs/<timestamp>.log`) |

#### Rule-authoring flags

These flags are to work with custom approximations:

| Flag | Description |
|------|-------------|
| `--track-external-methods` | Write external-method coverage files next to the SARIF report |
| `--passthrough-approximations` | Apply pass-through approximation YAML files or directories (repeatable) |
| `--dataflow-approximations` | Apply dataflow approximation projects, build outputs, or class directories (repeatable) |

Use external-method tracking when a scan may miss flows through library methods. The dropped-methods file shows where taint was killed because no model was available; the approximated-methods file shows methods already covered by built-in or custom models.

### opentaint health

Show the on-disk paths OpenTaint uses for its dependencies:

```bash
opentaint health
opentaint health --rules
opentaint health --analyzer
```

With no flags, `health` shows the autobuilder, analyzer, built-in rules, and Java runtime. With a single component flag, it prints only the bare path, which is useful for scripts.

| Flag | Description |
|------|-------------|
| `--autobuilder` | Print only the autobuilder JAR path |
| `--analyzer` | Print only the analyzer JAR path |
| `--rules` | Print only the built-in rules path, downloading rules if needed |
| `--runtime` | Print only the Java runtime path |

### opentaint test

The `test` command group is tooling for rule and approximation development.

#### Rule tests

```bash
opentaint test rule init .opentaint/test-projects/my-rule
opentaint compile .opentaint/test-projects/my-rule/sinks -o .opentaint/test-compiled/my-rule/sinks
opentaint test rule run .opentaint/test-compiled/my-rule/sinks --ruleset .opentaint/rules --ruleset .opentaint/test-projects/my-rule/sinks/test-rules
opentaint test rule reachability java/security/my-rule.yaml:my-rule --project-model .opentaint/test-compiled/my-rule/sinks --ruleset builtin --ruleset .opentaint/rules
```

| Command | Description |
|---------|-------------|
| `opentaint test rule init <output-dir>` | Create source and sink test projects with annotated sample support |
| `opentaint test rule run <project-model>` | Run detection-rule tests on a compiled project model |
| `opentaint test rule reachability <rule-id> [source-path]` | Trace why a rule can or cannot reach its facts |

#### Approximation tests

```bash
opentaint test approximation init .opentaint/test-projects/my-approximation \
  --dependency "io.projectreactor:reactor-core:3.8.5"
opentaint compile .opentaint/test-projects/my-approximation -o .opentaint/test-compiled/my-approximation
opentaint test approximation run .opentaint/test-compiled/my-approximation \
  --dataflow-approximations .opentaint/dataflow/my-approximation
```

| Command | Description |
|---------|-------------|
| `opentaint test approximation init <output-dir>` | Create a test project with a fixed `Taint.source()` to `Taint.sink(...)` harness |
| `opentaint test approximation run <project-model>` | Run dataflow approximation tests on a compiled project model |

Rule and approximation test runs write `test-result.json` and `test-results.sarif` to the selected output directory.

### opentaint compile

Compiles Java and Kotlin projects and generates project models for analysis. Useful when you want to separate compilation from scanning or need to inspect the project model.

```bash
opentaint compile --output ./my-project-model /path/to/project
opentaint scan --project-model ./my-project-model
```

| Flag | Description |
|------|-------------|
| `--output`, `-o` | Path to the result project model (required) |
| `--dry-run` | Validate inputs and show what would run without compiling |
| `--log-file` | Path to the log file (default: `<cache-dir>/logs/<timestamp>.log`) |

### opentaint approximation

A dataflow approximation models how taint moves through a method the analyzer cannot see into. Because a model references the library type it models, it needs that library to compile — so the models live in their own project, which pins those versions itself:

```bash
opentaint approximation init .opentaint/dataflow/my-batch \
  --dependency "io.projectreactor:reactor-core:3.8.5"

# write the @Approximate classes under src/main/java/, then apply them
opentaint scan --project-model ./my-project-model \
  --dataflow-approximations .opentaint/dataflow/my-batch
```

Pin the versions the target application uses. Those pins are the models' compile environment: they, and nothing about the project under analysis, decide what the models compile against, so a model compiles identically wherever it is applied.

`--dataflow-approximations` accepts an approximation project, a build output, or a directory of compiled classes — and a directory holding any of those, so a tree with one project per batch can be passed as a single flag. A directory counts as compiled classes only when nothing below it still needs building; one that holds compiled classes of its own alongside a project is reported rather than guessed at. Projects are built on demand and rebuilt when their sources, their dependency pins, or the compiler change.

| Command | Description |
|---------|-------------|
| `opentaint approximation init <output-dir>` | Create a dataflow approximation project |
| `opentaint compile approximations <approximation-project>` | Compile a dataflow approximation project |

| Flag | Description |
|------|-------------|
| `--dependency` | Compile-only Maven dependency coordinates the models are written against (repeatable, `init` only) |
| `--output`, `-o` | Path to the compiled models (default: `<approximation-project>/.opentaint/build`, `compile approximations` only) |

Compiling ahead of time is optional — scanning and testing build the project themselves. Run it to see compilation errors on their own.

### opentaint summary

View findings from a SARIF report. By default it prints the Scan Summary; add
`--show-findings` for the detailed listing. The filter flags below narrow the
whole summary (both the counts and the listing); `Rules executed` always
reflects the full set the tool ran.

| Flag | Description |
|------|-------------|
| `--show-findings` | Show all findings |
| `--show-code-snippets` | Show code snippets for each finding |
| `--verbose-flow` | Show full code flow steps for each finding |
| `--path` | Show only findings whose file path matches this glob (`**` supported, repeatable) |
| `--severity` | Show only findings of this SARIF level: `error`, `warning`, `note`, `none` (repeatable) |
| `--rule-id` | Show only findings for this rule: full id, leaf name (after `:` or last `.`), or glob over the full id (repeatable) |
| `--partial-fingerprint` | Show only findings whose partial fingerprint starts with this value, git-hash style (repeatable). With `--show-findings`, each finding's header reads `Fingerprint: <abbrev>` — copy that value back into this flag to re-focus on it. |
| `--partial-fingerprint-key` | partialFingerprints key matched by `--partial-fingerprint` (default `vulnerabilityWithTraceHash/v1`) |
| `--max-nesting-level` | Collapse code-flow steps deeper than this call-nesting level (`-1` = no cap). Best-effort: depth is derived from step kinds and method names, so flows lacking method info may over-collapse |
| `--group-by` | Group the `--show-findings` listing by `severity`, `rule-id`, or `file-path` (default `file-path`) |
| `--code-flow` | Render code flows: `all`, a 1-based index, or unset (first flow only). On multi-flow findings the listing also shows a `Code flows: <N>` field. |

Filters combine as OR within a dimension and AND across dimensions.

### opentaint project

Create project models from precompiled JARs or classes when source code isn't available.

```bash
opentaint project --output ./project-model --source-root /path/to/source \
  --classpath /path/to/app.jar --package com.example

opentaint scan --project-model ./project-model
```

| Flag | Description |
|------|-------------|
| `--output`, `-o` | Output directory for project.yaml (required) |
| `--source-root` | Source root directory (required) |
| `--classpath` | Classpath entries — classes or JAR files (required) |
| `--package` | Project packages (required) |
| `--dependency` | Project dependencies — JAR files |
| `--dry-run` | Validate inputs and show what would run without generating project model |
| `--log-file` | Path to the log file (default: `<cache-dir>/logs/<timestamp>.log`) |

## Model Caching

When `opentaint scan` compiles a project, the resulting project model is cached in `~/.opentaint/cache/`. The cache directory name is derived from the project path (e.g. `my-project-a1b2c3d4`).

On subsequent scans of the same project, the cached model is reused automatically — compilation is skipped entirely. This makes repeated scans significantly faster.

```bash
# First scan: compiles and caches the model
opentaint scan /path/to/project

# Second scan: reuses the cached model (no compilation)
opentaint scan /path/to/project

# Force recompilation (e.g. after code changes)
opentaint scan --recompile /path/to/project
```

If another scan is actively compiling the same project, the scan aborts with an error instead of compiling concurrently. Multiple read-only scans against the same cached model can run in parallel.

To remove all cached models:

```bash
opentaint prune
```

When `--output` is not specified, the SARIF report is written next to the cached model at `<model-dir>/sources/opentaint.sarif`.

## Global Options

These options apply to all commands:

- `--config string` — Path to configuration file
- `--java-version int` — Java version for analyzer (default: 21)
- `--quiet` / `-q` — Suppress interactive output (spinners, progress bars, JAR streaming)
- `--debug` / `-d` — Enable debug output (stream JAR subprocess output, show debug fields)
- `--color string` — Color mode (`auto`, `always`, `never`); defaults to `auto` (detects terminal)

For persistent configuration using files or environment variables, see the [Configuration](configuration.md) documentation.
