# Agent Mode — Implementation Progress

Tracking document for the implementation of agent mode features.
Refer to `agent-mode/impl/agent-mode-impl.md` for the full design.

---

## Phase A: Kotlin Analyzer Changes

### A1: ExternalMethodTracker class — [x]
- New file: `ExternalMethodTracker.kt`
- Data classes: `ExternalMethodRecord`, `SkippedExternalMethods`, `ExternalMethodAggregation`
- Thread-safe via ConcurrentHashMap (same pattern as TaintSinkTracker)

### A2: Wire tracker into analysis pipeline — [x]
- `TaintAnalysisContext.kt` — added `externalMethodTracker: ExternalMethodTracker?`
- `TaintAnalysisUnitRunnerManager.kt` — constructor param, pass through `TaintAnalysisManagerWithContext`
- `JIRTaintAnalyzer.kt` — constructor param, pass to engine, expose `getSkippedExternalMethods()`

### A3: Report external methods from flow function — [x]
- `JIRMethodCallFlowFunction.kt` — report to tracker in `applyPassRulesOrCallSkip()`
- Uses `passThroughFacts.isSome` to determine `passRulesApplied`

### A4: External methods output flag + YAML serialization — [x]
- `ProjectAnalysisOptions.kt` — `externalMethodsOutput: Path?`
- `ProjectAnalyzerRunner.kt` — `--external-methods-output` Clikt flag
- `ProjectAnalyzer.kt` — `@Serializable` data classes + kaml `encodeToStream`

### A5: Rule ID filter — [x]
- `SemgrepRuleLoader.kt` — `ruleIdFilter` parameter, `ruleIdAllow()` in `skip()`
- `ProjectAnalysisOptions.kt` — `semgrepRuleId: List<String>`
- `ProjectAnalyzerRunner.kt` — `--semgrep-rule-id` Clikt flag
- `LoadSemgrepRules.kt` — pass filter through

### A6: Combined config+rules — [x]
- `ProjectAnalyzer.kt` — removed `check()`, added `SemgrepRulesWithCustomConfig` variant
- `ProjectAnalyzerRunner.kt` — renamed `--config` → `--approximations-config` (with `--config` alias)

### A7: Custom dataflow approximations path — [x]
- `DataFlowApproximationLoader.kt` — `customApproximationPaths: List<Path>` in `Options`
- `ProjectAnalyzerRunner.kt` — `--dataflow-approximations` Clikt flag

---

## Phase B: Go CLI Changes

### B1: Hidden dev flags — [x]
- `root.go` — `--analyzer-jar`, `--autobuilder-jar` persistent hidden flags
- `global.go` — `JarPath` fields on `Analyzer`/`Autobuilder` structs

### B2: AnalyzerBuilder extensions — [x]
- `command_builder.go` — new fields, setters, `BuildNativeCommand` entries

### B3: New scan flags — [x]
- `scan.go` — `--rule-id`, `--approximations-config`, `--dataflow-approximations`, `--external-methods`

### B4: Agent command group — [x]
- `agent.go`, `agent_skills.go`, `agent_prompt.go`, `agent_rules_path.go`, `agent_test_rules.go`
- `opentaint_home.go` — `GetBundledAgentPath()`
- `compile.go` — autobuilder jar override support

---

## Phase C: Skills and Meta-Prompt

### C1: Write skill files — [x]
- 9 skill files in `agent/skills/`

### C2: Write meta-prompt — [x]
- `agent/meta-prompt.md`

### C3-C4: Release pipeline changes — [ ]
- Bundle agent files + test-util JAR in release (deferred to release work)

---

## Phase D: Validation

### D1: Run existing tests — [x]
- 6 passed, 1 skipped (quick tests)
- Fixed conftest JAR resolution order

### D2: Run slow tests — [x]
- 6 passed, 1 failed (autobuilder JAR not built locally — expected)
- Scan tests against Stirling-PDF all pass

### D3: Run new_feature tests — [x]
- 1 passed (rules-path command)

---

## Git Commits

| Commit | Tasks | Description |
|--------|-------|-------------|
| e204e455 | A1-A7 | Phase A: Kotlin analyzer agent-mode features |
| e53f8c16 | Fix | Rename ExternalMethodResults -> SkippedExternalMethods, use kaml |
| 6d445b36 | B1-B4 | Phase B: Go CLI agent-mode features |
| 8734ae31 | C1-C2 | Phase C: Skills and meta-prompt |
| 7d094862 | D1 | Fix conftest JAR resolution order |
