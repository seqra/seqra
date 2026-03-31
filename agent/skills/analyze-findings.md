# Skill: Analyze Findings

Interpret SARIF findings and the external methods list to classify results and plan next actions.

## Prerequisites

- Analysis run complete (run-analysis skill)
- SARIF report and external methods YAML available

## Procedure

### 1. Read SARIF findings

For each finding in `runs[0].results[]`:
- `ruleId`: Which rule triggered
- `locations[]`: Sink location (file, line)
- `codeFlows[]`: Taint trace from source to sink

Read the trace:
- First location = **source** (where tainted data enters)
- Last location = **sink** (where tainted data is used dangerously)
- Intermediate locations = dataflow path

### 2. Classify each finding

**TRUE POSITIVE (TP)**: Real vulnerability.
- Source genuinely provides attacker-controlled data
- Sink genuinely performs a dangerous operation
- No sanitization between source and sink
- **Action**: Generate PoC (generate-poc skill), document in `vulnerabilities.md`

**FALSE POSITIVE -- fixable via Rule**: Over-broad pattern matching.
- Sink pattern too broad, sanitizer not recognized, source matches non-attacker data
- **Action**: Add `pattern-not`, `pattern-not-inside`, `pattern-sanitizers`, or narrow `metavariable-regex`. Update tests. Re-run.

**FALSE POSITIVE -- fixable via Approximation** (non-preferred): Imprecise taint propagation through a library method.
- Library method modeled as propagating taint when it actually neutralizes the threat
- **Action**: Override passThrough approximation. Re-run.

### 3. Process external methods list (FN discovery)

Focus on `withoutRules` section first. For each method:

**PROPAGATOR**: Method passes taint from input to output.
- Example: `DataWrapper#getValue()` -- taint on `this` flows to `result`
- **Action**: Create `passThrough` YAML rule (create-yaml-config skill)

**TRANSFORMER with lambdas**: Method invokes callbacks/lambdas.
- Example: `ReactiveStream#map(Function)` -- taint flows through the function
- **Action**: Create code-based approximation (create-approximation skill)

**NEUTRAL**: Irrelevant to taint flow (logging, metrics, sanitizers).
- **Action**: Skip -- default call-to-return passthrough is correct

### 4. Batch processing

- Group external methods by package/library
- Check built-in coverage first
- Generate comprehensive rules per library
- Re-run after each batch, check for regressions

## Decision Priorities

- **FN fixes**: (1) YAML passThrough rule, (2) Code-based approximation (lambdas only), (3) Rule pattern fix
- **FP fixes**: (1) Rule fix via `pattern-not`/`pattern-sanitizers` (preferred), (2) PassThrough override (non-preferred)

## Stop Condition

Stop iterating when:
- External methods list stabilizes (no new methods appear)
- All SARIF findings are classified as TP or resolved FP
- High-priority vulnerabilities have PoCs
