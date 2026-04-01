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

```bash
opentaint scan ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --external-methods ./results/external-methods.yaml
```

### With custom passThrough config

```bash
opentaint scan ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --approximations-config ./agent-config/custom-propagators.yaml \
  --external-methods ./results/external-methods.yaml
```

### With code-based approximations

```bash
opentaint scan ./opentaint-project \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --dataflow-approximations ./agent-approximations/classes \
  --external-methods ./results/external-methods.yaml
```

### View results

```bash
opentaint summary ./results/report.sarif --show-findings
```

## Outputs

Three files to collect:

1. **`./results/report.sarif`** — Vulnerability findings with code flow traces
2. **`./results/external-methods-without-rules.yaml`** — Methods where no pass-through rules fired (**dataflow facts killed here — these cause false negatives**)
3. **`./results/external-methods-with-rules.yaml`** — Methods where pass-through rules were applied (already modeled, typically no action needed)

The `--external-methods` flag specifies the **base path**. The analyzer derives two filenames by appending `-without-rules` and `-with-rules` before the `.yaml` extension.

## Key Flags

| Flag | Purpose |
|------|---------|
| `--ruleset` | Rule directory (repeatable). Use `builtin` for built-in rules |
| `--rule-id` | Enable only specific rules by full ID `<path>:<id>` (repeatable) |
| `--approximations-config` | YAML passThrough config (OVERRIDE mode) |
| `--dataflow-approximations` | Directory of compiled approximation class files |
| `--external-methods` | Output path for skipped external methods YAML |
| `--severity` | Filter by severity (note, warning, error) |
| `--timeout` | Analysis timeout (default 900s) |

## Notes

- The scan path is the **directory** containing `project.yaml`, not the path to `project.yaml` itself (e.g. `./opentaint-project`, not `./opentaint-project/project.yaml`)
- `--rule-id` enables only the specified rules; library rules referenced via join-mode `refs` are auto-included
- `--approximations-config` uses OVERRIDE mode: custom rules replace (not extend) default config for matching methods
- `--dataflow-approximations` accepts a directory of compiled `.class` files
- Duplicate approximation targeting the same class as a built-in will cause an error
