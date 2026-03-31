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

### F1: Refactor tests to use Go CLI only — [ ]
- Currently `conftest.py` has two modes: Go CLI and direct JAR invocation
- Remove direct JAR mode entirely — all tests must go through `opentaint` CLI
- `conftest.py` should find the local CLI binary at `cli/bin/opentaint` (dev mode)
- Pass `--analyzer-jar` and `--autobuilder-jar` hidden flags when using local builds
- Remove `_find_analyzer_jar()`, `_find_autobuilder_jar()`, `_find_java()` — CLI handles all of this
- Simplify `OpenTaintCLI`: single code path per method, no `if self.has_cli` branching

### F2: `opentaint agent init-test-project` command — [ ]
- Designed in impl doc (B8) but never implemented
- Currently tests that depend on it skip with `pytest.skip("init-test-project not available")`
- Affected tests: `test_init_test_project`, `test_rule_test_all_pass`, `test_rule_test_detects_false_negative`
- Implementation: `cli/cmd/agent_init_test_project.go` — bootstrap Gradle project with test-util JAR

### F3: Add timing instrumentation to full loop test — [ ]
- `test_full_loop.py::test_full_agent_loop` runs 2 scans + rule creation + approximation creation
- Currently no per-step timing — hard to tell which phase is slow when test times out
- Add `time.time()` checkpoints and print elapsed time after each phase
- Helps investigate performance bottlenecks without requiring an actual agent

### F4: Run all tests via CLI, write test report — [ ]
- Run full pytest suite using the Go CLI binary (after F1)
- Write results to `agent-mode/test-status.md` with table: feature | test name | scenario | status
- Must cover: build, rules, approximations, external methods, full loop

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
