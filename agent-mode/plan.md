# Agent Mode — Implementation Progress

Tracking document for the implementation of agent mode features.
Refer to `agent-mode/impl/agent-mode-impl.md` for the full design.

---

## Phase A: Kotlin Analyzer Changes

### A1: ExternalMethodTracker class — [ ]
- New file: `core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/ap/ifds/taint/ExternalMethodTracker.kt`
- Data classes: `ExternalMethodRecord`, `ExternalMethodResults`, `ExternalMethodAggregation`
- Thread-safe via ConcurrentHashMap (same pattern as TaintSinkTracker)

### A2: Wire tracker into analysis pipeline — [ ]
- `TaintAnalysisUnitStorage.kt` — add `externalMethodTracker` field
- `TaintAnalysisContext.kt` — expose tracker
- `TaintAnalysisUnitRunnerManager.kt` — wire into unit storage creation

### A3: Report external methods from flow function — [ ]
- `JIRMethodCallFlowFunction.kt` — report to tracker in `applyPassRulesOrCallSkip()`
- Use `passThroughFacts.isSome` to determine `passRulesApplied`

### A4: External methods output flag + YAML serialization — [ ]
- `ProjectAnalysisOptions.kt` — add `externalMethodsOutput: Path?`
- `ProjectAnalyzerRunner.kt` — add `--external-methods-output` Clikt flag
- `ProjectAnalyzer.kt` — serialize tracker results to YAML after analysis

### A5: Rule ID filter — [ ]
- `SemgrepRuleLoader.kt` — add `ruleIdFilter` parameter, add `ruleIdAllow()` to `skip()`
- `ProjectAnalysisOptions.kt` — add `semgrepRuleId: List<String>`
- `ProjectAnalyzerRunner.kt` — add `--semgrep-rule-id` Clikt flag

### A6: Combined config+rules — [ ]
- `ProjectAnalyzer.kt` — remove `check()` at line 62, add `SemgrepRulesWithCustomConfig` variant
- `ProjectAnalyzerRunner.kt` — rename `--config` → `--approximations-config` (keep `--config` as alias)

### A7: Custom dataflow approximations path — [ ]
- `DataFlowApproximationLoader.kt` — add `customApproximationPaths: List<Path>` to `Options`
- `ProjectAnalyzerRunner.kt` — add `--dataflow-approximations` Clikt flag
- `ProjectAnalysisOptions.kt` — wire through `approximationOptions`

---

## Phase B: Go CLI Changes

### B1: Hidden dev flags — [ ]
- `root.go` — add `--analyzer-jar`, `--autobuilder-jar` persistent hidden flags
- `global.go` — add jar path fields to `Analyzer`/`Autobuilder` structs

### B2: AnalyzerBuilder extensions — [ ]
- `command_builder.go` — new fields + setters + BuildNativeCommand entries for all new flags

### B3: New scan flags — [ ]
- `scan.go` — `--rule-id`, `--approximations-config`, `--dataflow-approximations`, `--external-methods`

### B4: Approximation auto-compilation — [ ]
- New file: `compile_approximations.go`

### B5-B9: Agent command group — [ ]
- New files: `agent.go`, `agent_skills.go`, `agent_prompt.go`, `agent_rules_path.go`, `agent_test_rules.go`, `agent_init_test_project.go`
- `opentaint_home.go` — `GetAgentPath()` function

---

## Phase C: Skills and Meta-Prompt

### C1: Write skill files — [ ]
- 9 skill files in `agent/skills/`

### C2: Write meta-prompt — [ ]
- `agent/meta-prompt.md`

### C3-C4: Release pipeline changes — [ ]
- Bundle agent files + test-util JAR in release

---

## Phase D: Validation

### D1: Run existing tests — [ ]
### D2: Run new_feature tests — [ ]
### D3: Run full agent loop — [ ]

---

## Git Commits

| Commit | Tasks | Status |
|--------|-------|--------|
| | | |
