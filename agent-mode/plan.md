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

## Phase E: CLI Testing and Fixes

### E1: Revert ruleIdAllow to match full rule ID only — [x]
- `SemgrepRuleLoader.kt` — removed `shortRuleId` fallback, keep only `info.ruleId` match
- Full rule ID format is `<ruleSetRelativePath>:<ruleId>`, e.g. `java/security/path-traversal.yaml:path-traversal`

### E2: Skip external method tracking for static fact base — [x]
- `JIRMethodCallFlowFunction.kt` — added `startFactBase !is AccessPathBase.ClassStatic` guard
- External methods YAML reduced from ~10,643 to ~2,246 lines (no `<static>` entries)

### E3: Update skills with full rule ID format — [x]
- `agent/skills/create-rule.md` — documented full ID format `<path.yaml>:<id>`, how to discover IDs
- `agent/skills/run-analysis.md` — updated `--rule-id` examples with full IDs
- `agent/skills/test-rule.md` — clarified annotation `id` field vs full rule ID

### E4: Rebuild analyzer and CLI, retest — [x]
- Rebuilt `projectAnalyzerJar` + Go CLI binary
- CLI scan with `--rule-id java/security/path-traversal.yaml:path-traversal` → 20 findings
- External methods output confirmed clean (0 `<static>` entries)
- Updated test expectations: full rule IDs, fact position format (`<this>`, `arg(N)`, `ret`)
- All pytest tests pass (29 passed, 1 skipped, 5 pre-existing failures excluded)

---

## Phase F: Test Infrastructure and Missing Features

### F1: Refactor tests to use Go CLI only — [x]
- Removed dual-mode (Go CLI + direct JAR) from `conftest.py`
- All tests now require the Go CLI binary at `cli/bin/opentaint` (dev mode)
- Hidden `--analyzer-jar` / `--autobuilder-jar` flags auto-detected for local builds
- Removed `_find_java()`, `has_cli` branching, direct JAR invocation code paths
- Fixed CLI scan path: auto-strip `project.yaml` from file paths (CLI expects directory)

### F2: `opentaint agent init-test-project` command — [x]
- New file: `cli/cmd/agent_init_test_project.go`
- Creates directory structure, copies test-util JAR, generates `build.gradle.kts` and `settings.gradle.kts`
- Supports `--dependency` flag for Maven coordinates
- Resolves test-util JAR from bundled, install, or dev build tiers
- `test_init_test_project` now passes (was previously skipping)

### F3: Add timing instrumentation to all tests — [x]
- Added pytest hooks (`pytest_runtest_setup`/`pytest_runtest_teardown`) for per-test timing
- Added per-phase `time.time()` checkpoints to `test_full_agent_loop`
- All test output now includes `[timing]` lines with elapsed seconds

### F4: Run all tests via CLI, write test report — [x]
- Full suite: 31 passed, 3 failed (all pre-existing), 0 skipped
- Report written to `agent-mode/test-status.md`
- Pre-existing failures: analyzer exit code 0 on approximation errors (2 tests), autobuilder JAR not built (1 test)

---

## Git Commits

| Commit | Tasks | Description |
|--------|-------|-------------|
| e204e455 | A1-A7 | Phase A: Kotlin analyzer agent-mode features |
| e53f8c16 | Fix | Rename ExternalMethodResults -> SkippedExternalMethods, use kaml |
| 6d445b36 | B1-B4 | Phase B: Go CLI agent-mode features |
| 8734ae31 | C1-C2 | Phase C: Skills and meta-prompt |
| 7d094862 | D1 | Fix conftest JAR resolution order |
| 4e06427b | E-plan | Add Phase E tasks to plan |
| 67b9276f | E1-E4 | Phase E: Filter static facts, update rule ID format in skills/tests |
| 7c3f94ed | F-plan | Add Phase F to plan |
| 195d23a9 | F1 | Refactor tests to CLI-only mode |
| 592f2667 | F2 | Implement opentaint agent init-test-project command |
| 63c84b96 | F3 | Add timing instrumentation to all tests |
| (pending) | F4 | Fix CLI scan path, run full suite, write test report |
