# Skill: Run Analysis

Run OpenTaint analysis on the target project and collect results.

## Prerequisites

- Project built (build-project skill)
- Rules created and tested (create-rule, test-rule skills)
- Optionally: YAML config (create-yaml-config skill) and/or approximations (create-approximation skill)

## Procedure

### Basic analysis

```bash
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules \
  --rule-id my-vulnerability \
  --external-methods ./results/external-methods.yaml
```

### With custom passThrough config

```bash
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id my-vulnerability \
  --approximations-config ./agent-config/custom-propagators.yaml \
  --external-methods ./results/external-methods.yaml
```

### With code-based approximations

```bash
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id my-vulnerability \
  --dataflow-approximations ./agent-approximations/classes \
  --external-methods ./results/external-methods.yaml
```

### View results

```bash
opentaint summary ./results/report.sarif --show-findings
```

## Outputs

Two files to collect:

1. **`./results/report.sarif`** -- Vulnerability findings with code flow traces
2. **`./results/external-methods.yaml`** -- External methods split into:
   - `withoutRules`: Methods where no pass-through rules fired (dataflow killed)
   - `withRules`: Methods where pass-through rules were applied

## Key Flags

| Flag | Purpose |
|------|---------|
| `--ruleset` | Rule directory (repeatable). Use `builtin` for built-in rules |
| `--rule-id` | Enable only specific rules by ID (repeatable) |
| `--approximations-config` | YAML passThrough config (OVERRIDE mode) |
| `--dataflow-approximations` | Directory of compiled approximation class files |
| `--external-methods` | Output path for skipped external methods YAML |
| `--severity` | Filter by severity (note, warning, error) |
| `--timeout` | Analysis timeout (default 900s) |

## Notes

- `--rule-id` enables only the specified rules; library rules referenced via join-mode `refs` are auto-included
- `--approximations-config` uses OVERRIDE mode: custom rules replace (not extend) default config for matching methods
- `--dataflow-approximations` accepts a directory of compiled `.class` files
- Duplicate approximation targeting the same class as a built-in will cause an error
