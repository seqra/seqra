---
name: run-scan
description: Run an OpenTaint scan on project and produces the SARIF report. Use whenever the user asks to scan or re-scan a project
license: Apache-2.0
metadata:
  author: opentaint
  version: "0.3.0"
---

# Skill: Run Scan

Run an OpenTaint scan over the project model and collect its findings

## Inputs

Provided by the caller, fall back to the default value when omitted. Ask back only when a required input is missing and has no sensible default

- `project-root` (optional) — root of the target project. Opentaint keeps all analysis artifacts under the fixed `<project-root>/.opentaint/` directory, so every `.opentaint/...` path below resolves there. Default: current directory
- `rule-ids` (optional) — full rule IDs to restrict the scan to
- `max-memory` (optional) — a `--max-memory` value to run scan with. Default: unset (engine default `8G`)

## Workflow

### 1. Run the scan

Scan the pre-built model at `.opentaint/project`. Write the report to `.opentaint/results/report.sarif` and load both the built-in ruleset and the project's own rules under `.opentaint/rules`:

```bash
opentaint scan --project-model .opentaint/project \
  -o .opentaint/results/report.sarif \
  --ruleset builtin --ruleset .opentaint/rules \
  --track-external-methods
```

- `--rule-id <full-id>` — restrict to specific executable rules (repeatable, one per input rule ID); referenced library rules remain available without listing their IDs separately. Omit to run all loaded rules
- `--passthrough-approximations .opentaint/pass-through` — add when that directory exists: passThrough configs override built-ins at the rule level, a provided rule overriding a built-in only when it matches one
- `--dataflow-approximations .opentaint/dataflow` — add when that directory exists: code-based approximations, one project per batch

Both approximation-dir flags walk their trees recursively; pass each parent directory once, not every package or batch separately.

The scan is long — run it in the background and wait for it to finish. Leave `--timeout` at the engine default (900s); the CLI ends the analysis itself and writes whatever SARIF it has.

### 2. Retry once on out-of-memory

Start at the 8G default. An out-of-memory failure at 8G — even when the engine still wrote a partial SARIF — is not an acceptable result: retry once at `--max-memory 16G` before collecting anything. Never higher (more RAM won't improve results), one bump only. When the caller passed `max-memory`, run at it from the first attempt instead

### 3. Collect the report, or escalate

A SARIF is complete enough to use even alongside the two normal scan errors — a timeout (the CLI ended the analysis and wrote what it had), or an out-of-memory at 16G (after §2's bump), take it as-is. The other two outcomes are not results to accept. A config-load failure on a malformed approximation is fixed, not bumped: report the engine error and the offending file under `.opentaint/pass-through`/`.opentaint/dataflow` per Output so it can be repaired and rescanned. No SARIF at all, even at 16G, is a plain failure: report it per Output with the scan setup.

## Output

Short and concise report of what was done

### Artifacts:

All three sit next to the report under `.opentaint/results/`:

- `.opentaint/results/report.sarif` — findings with code-flow traces
- `.opentaint/results/dropped-external-methods.yaml` — external methods where dataflow facts were killed for lack of a model
- `.opentaint/results/approximated-external-methods.yaml` — external methods already modeled

### Summary:

- finding count, and dropped-vs-approximated external-method counts
- whether memory was bumped to 16G
- if the scan failed at config-load on a malformed approximation: the engine error and the offending file under `.opentaint/pass-through` or `.opentaint/dataflow` (located from the error), so it can be fixed and rescanned — this is not an out-of-memory failure and 16G won't help
- if no SARIF came out even at 16G: that the scan is left failed, with the setup used (full scan command with ruleset, approximation dirs, model)

## Constraints

- Never hand-edit the project model to change scan results — this skill only reads it. If the model is wrong, rebuild it at the model stage rather than patching `project.yaml` or anything under it
