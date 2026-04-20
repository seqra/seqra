# Skill: Debug Rule Reachability

Generate a fact reachability SARIF report to debug why a specific rule does (or doesn't) reach certain taint sinks.

## Prerequisites

- Project built (build-project skill)
- Rule created and tested (create-rule, test-rule skills)

## ⚠️ CRITICAL: Single Rule Only

**You MUST run the analyzer with exactly ONE rule** via a single `--rule-id` flag. Running fact reachability across multiple rules will produce an enormously huge SARIF report that is effectively unusable.

## Procedure

### Run analysis with fact reachability debugging

```bash
opentaint scan --project-model ./opentaint-project \
  -o ./results/fact-reachability.sarif \
  --ruleset builtin --ruleset ./agent-rules \
  --rule-id java/security/my-vuln.yaml:my-vulnerability \
  --debug-fact-reachability-sarif
```

The `--rule-id` flag requires the **full rule ID** in the format `<ruleSetRelativePath>:<shortId>`.
Example: for a rule file at `agent-rules/java/security/my-vuln.yaml` with `id: my-vulnerability`,
the full ID is `java/security/my-vuln.yaml:my-vulnerability`.

### View results

```bash
opentaint summary ./results/fact-reachability.sarif --show-findings
```

## Key Flags

| Flag | Purpose |
|------|---------|
| `--debug-fact-reachability-sarif` | Enable fact reachability SARIF output |
| `--rule-id` | **Exactly one** rule ID (format: `<path>:<id>`) |
| `--ruleset` | Rule directory (repeatable). Use `builtin` for built-in rules |
| `--timeout` | Analysis timeout (default 900s) |

## Outputs

The debug fact reachability report is **not** the main SARIF file specified by `-o`. The analyzer writes it as a **separate file** named `debug-ifds-fact-reachability.sarif` in the same output directory as the main report.

For example, with `-o ./results/report.sarif`:

- **`./results/report.sarif`** — Main vulnerability findings
- **`./results/debug-ifds-fact-reachability.sarif`** — Debug fact reachability report

Always check the output directory (`-o` parent) for this file.

## Notes

- This is a debug-only option intended for troubleshooting rule coverage
- Pre-compiled project models are passed via `--project-model <dir>`, not as a positional argument
- `--rule-id` drops every rule whose full ID is not listed, **including** library rules referenced via join-mode `refs`; list each library rule explicitly if you need refs resolved
