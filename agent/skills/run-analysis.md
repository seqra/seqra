# Skill: Run Analysis

Run OpenTaint analysis on the target project and collect results.

## Prerequisites

- Project built (build-project skill)
- Rules created and tested (create-rule, test-rule skills)
- Optionally: YAML config (create-yaml-config skill) and/or approximations (create-approximation skill)

## Procedure

### Basic analysis

The `--rule-id` flag requires the **full rule ID** in the format `<ruleSetRelativePath>:<shortId>`.
Example: for a rule file at `agent-rules/java/security/my-vuln.yaml` with `id: my-vulnerability`,
the full ID is `java/security/my-vuln.yaml:my-vulnerability`.

Pass the pre-compiled project model via `--project-model`. The positional `scan <path>`
argument is reserved for source projects that the CLI will compile itself.

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --track-external-methods
```

### With custom passThrough config

`--approximations-config` is repeatable; every occurrence is OVERRIDE-merged.

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --approximations-config ./agent-config/custom-propagators.yaml \
  --track-external-methods
```

### With code-based approximations

Point `--dataflow-approximations` at a directory of Java sources. The CLI auto-compiles
`.java` files into a temp directory and forwards that to the analyzer.

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --dataflow-approximations ./agent-approximations/src \
  --track-external-methods
```

### View results

```bash
opentaint summary ./results/report.sarif --show-findings
```

## Outputs

Three files to collect — all next to the SARIF report:

1. **`./results/report.sarif`** — Vulnerability findings with code flow traces
2. **`./results/external-methods-without-rules.yaml`** — Methods where no pass-through rules fired (**dataflow facts killed here — these cause false negatives**)
3. **`./results/external-methods-with-rules.yaml`** — Methods where pass-through rules were applied (already modeled, typically no action needed)

The `--track-external-methods` flag is a boolean. Filenames and location are fixed: the
two YAMLs are written into the same directory as the SARIF file, using the names above.

## Key Flags

| Flag | Purpose |
|------|---------|
| `--project-model` | Pre-compiled project model directory (contains `project.yaml`) |
| `--ruleset` | Rule directory (repeatable). Use `builtin` for built-in rules |
| `--rule-id` | Enable only specific rules by full ID `<path>.yaml:<id>` (repeatable) |
| `--approximations-config` | YAML passThrough config (OVERRIDE mode, repeatable) |
| `--dataflow-approximations` | Directory of Java sources or compiled class files (repeatable) |
| `--track-external-methods` | Emit `external-methods-{without,with}-rules.yaml` next to the SARIF |
| `--severity` | Filter by severity (note, warning, error) |
| `--timeout` | Analysis timeout (default 900s) |

## Notes

- For a pre-compiled model, always use `--project-model <dir>`. The positional argument is only for source projects that will be compiled by the CLI.
- `--rule-id` drops every rule whose full ID is not in the filter, **including library rules referenced via join-mode `refs`**. List every rule you want active explicitly.
- `--approximations-config` uses OVERRIDE mode: custom rules replace (not extend) default config for matching methods.
- `--dataflow-approximations` accepts a directory. `.java` files are auto-compiled by the CLI; already-compiled `.class` directories are passed through as-is.
- Duplicate approximation targeting the same class as a built-in will cause an error.
