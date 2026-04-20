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

### 3. Process external methods (FN discovery)

The `--track-external-methods` flag produces two files next to the SARIF report:
- **`<sarif-dir>/external-methods-without-rules.yaml`** — Methods where the analyzer **killed dataflow facts** (no approximation model). **This is the only list worth approximating.** Every false negative caused by a missing library model is rooted here.
- **`<sarif-dir>/external-methods-with-rules.yaml`** — Methods that already have an approximation model. Do NOT target these with custom approximations or YAML `passThrough` rules — you would OVERRIDE an existing model, which is usually a regression.

Filenames and directory are fixed; the flag is a boolean.

**Approximation scope — hard rules**:
- Only methods listed in `external-methods-without-rules.yaml` are candidates for a new YAML `passThrough` rule or a code-based approximation.
- Methods not listed in either file were never reached on a tainted path during the scan; approximating them is a no-op until that changes (different sources/rules/entry points).
- Application-internal methods are never in these lists — approximations don't apply to them. Fix those via rule patterns, not approximations.

Read `external-methods-without-rules.yaml`. **Prioritize generic data-flow propagators** over
vulnerability-specific methods. The most common cause of killed facts is mundane collection/utility
methods, not the vulnerability-relevant operations themselves.

**HIGH PRIORITY — Generic propagators** (affect ALL vulnerability types):
- Collection operations: `List.add`/`List.get`, `Map.put`/`Map.get`, `Set.add`/`Set.iterator`
- String operations: `StringBuilder.append`/`toString`, `StringBuffer.append`
- Wrapper/DTO getters/setters: `Container.getValue`, `Pair.getFirst`
- Stream/iterator methods: `Iterator.next`, `Stream.collect`
- **Action**: Create `passThrough` YAML rules (create-yaml-config skill)

**MEDIUM PRIORITY — Lambda/callback methods**:
- Example: `ReactiveStream#map(Function)` — taint flows through the function
- Example: `CompletableFuture#thenApply(Function)` — async propagation
- **Action**: Create code-based approximation (create-approximation skill)

**LOW PRIORITY — Vulnerability-specific methods**:
- These are usually already modeled in built-in rules. Only add if missing.
- **Action**: Check `external-methods-with-rules.yaml` first; if present, skip.

**NEUTRAL**: Irrelevant to taint flow (logging, metrics, sanitizers).
- **Action**: Skip — default call-to-return passthrough is correct

### 4. Batch processing

- Filter `external-methods-without-rules.yaml` to methods on a plausible source→sink path for the current vulnerability class; approximating methods that sit outside that path wastes iteration time.
- Group the filtered methods by package/library
- **Start with generic propagators** (collections, strings, wrappers) — they affect all rules
- Check built-in coverage first (many common libraries already have approximations — cross-check against `external-methods-with-rules.yaml`)
- Generate comprehensive rules per library
- Re-run with `--track-external-methods` after each batch; verify the approximated methods actually moved from `without-rules` to `with-rules`, and check for finding regressions

## Decision Priorities

- **FN fixes**: (1) YAML passThrough rule, (2) Code-based approximation (lambdas only), (3) Rule pattern fix
- **FP fixes**: (1) Rule fix via `pattern-not`/`pattern-sanitizers` (preferred), (2) PassThrough override (non-preferred)

## Stop Condition

Stop iterating when:
- External methods list stabilizes (no new methods appear)
- All SARIF findings are classified as TP or resolved FP
- High-priority vulnerabilities have PoCs
