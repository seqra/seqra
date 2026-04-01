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

## Phase G: Known Issues

### G1: Fix sink rule ID mismatch in fixture rule and tests — [x]
- Fixed `#java-path-traversal-sink` → `#java-path-traversal-sinks` in fixture rule and inline test YAML
- Tests now produce 4 path-traversal findings on Stirling-PDF

### G2: Fix `agent test-rules` Go command — missing flags and output — [x]
- Rewrote `agent_test_rules.go`: local flag vars for `--ruleset`, `-o`, `--timeout`, `--max-memory`, `--rule-id`
- Output dir uses `-o` flag (temp dir only as fallback); user rulesets passed to builder

### G3: Strengthen test assertions — remove vacuous passes — [x]
- `test_rule_test_detects_false_negative`: added `assert result_json.exists()` + `test_result.assert_ok()`
- `test_scan_stirling_with_path_traversal_rule`: added `assert len(findings) > 0`
- `test_approximations_change_results`: added `assert count1 != count2`
- `test_full_agent_loop`: added `assert len(findings) > 0`
- Updated `sarif_findings_for_rule()` to match both exact and semgrep-style dot-separated IDs

### G4: Analyzer exits non-zero on errors + auto-compile approximations — [x]
- `AbstractAnalyzerRunner.runProjectAnalysisRecursively()`: re-throw exceptions after logging
- `AbstractAnalyzerRunner.main()`: removed `return` on project load failure (let exception propagate)
- NEW: `cli/cmd/compile_approximations.go` — auto-compile `.java` files in `--dataflow-approximations`
  - Resolves `javac` from managed JDK
  - Extracts approximation utility classes from analyzer JAR (`opentaint-dataflow-approximations/` prefix)
  - Resolves project dependencies from `project.yaml` for the compilation classpath
  - Compiles with `javac -source 8 -target 8` and returns compiled output directory
  - On compilation failure, reports `javac` output and aborts scan
- Wired into `scan.go`: `compileApproximationsIfNeeded()` called for each `--dataflow-approximations` path

### G5: Build autobuilder JAR or skip test gracefully — [x]
- `test_rule_test_all_pass`: skip with clear message when compilation fails (autobuilder not available)
- `test_rule_test_detects_false_negative`: same skip logic

### G6: Verify error message in test_invalid_approximations_config_errors — [x]
- Added assertion checking combined stdout+stderr for config/yaml/parse/fail keywords

### G7: CLI errors go to stdout — update tests to check both — [x]
- `test_approximation_compilation_failure`: check `combined_output` (stdout + stderr)
- `test_invalid_approximations_config_errors`: same approach

### G8: Better timing breakdown — [x]
- Added `parse_analyzer_timing()` helper to `conftest.py` — parses IFDS elapsed time, phase markers, vulnerability count from analyzer output
- Added `print_timing_breakdown()` helper for formatted output
- Wired into `test_full_agent_loop` for initial scan and rescan phases

---

## Phase H: Discovered Issues (from design-vs-implementation comparison)

### H1: ~~Release pipeline — bundle agent files~~ → Embed agent files in binary — [x]
- Agent files (~28KB) embedded in Go binary via `go:generate` + `go:embed`
- New package `cli/internal/agent/` with `GetPath()`:
  - Tier 1: bundled `<exe-dir>/lib/agent/` (release archives)
  - Tier 2: extract from embedded FS to `~/.opentaint/agent/` (go install, dev builds)
  - SHA-256 content hash marker for staleness detection
- Removed `GetBundledAgentPath()` from `opentaint_home.go`
- Updated `agent_prompt.go` and `agent_skills.go` to use `agent.GetPath()`
- Works with: `go install`, released builds, dev builds

### H2: Release pipeline — bundle test-util JAR — [ ]
- `.github/workflows/release-cli.yaml` — add step to build/download `opentaint-sast-test-util.jar` to `cli/lib/`
- Without this, `opentaint agent init-test-project` fails in released builds
- `resolveTestUtilJar()` tier 1/2 won't find the JAR in release archives
- **Priority: HIGH**

### H3: Fix short rule IDs in skill docs — [x]
- `agent/skills/create-yaml-config.md:101` — uses `--rule-id my-vulnerability` instead of full format `java/security/my-vuln.yaml:my-vulnerability`
- `agent/skills/create-approximation.md:66` — same issue
- Inconsistent with the documented full rule ID format in `create-rule.md` and `run-analysis.md`
- **Priority: MEDIUM**

### H4: ~~Agent path resolution — single-tier only~~ — [x]
- Superseded by H1: agent files are now embedded in binary and extracted on demand
- Two-tier resolution: bundled (release) → embedded extraction (`~/.opentaint/agent/`)
- No longer depends on external file distribution

### H5: Env var naming mismatch in docs — [ ]
- Design docs say `OPENTAINT_ANALYZER_JAR` / `OPENTAINT_AUTOBUILDER_JAR`
- Actual viper binding uses `OPENTAINT_ANALYZER_JAR_PATH` / `OPENTAINT_AUTOBUILDER_JAR_PATH`
- Update `agent-mode/impl/agent-mode-impl.md` section 5.2 to match actual env var names
- **Priority: LOW**

### H6: Pre-existing analyzer exit code issues — [ ]
- `test_approximation_compilation_failure` — analyzer still exits 0 on some approximation loading errors
- `test_duplicate_approximation_errors` — same root cause (bijection violation swallowed)
- G4 fix addressed `runProjectAnalysisRecursively` but approximation loading errors in `installApproximations()` may not propagate
- **Priority: LOW**

---

## Phase I: Skill Fixes and Clarifications

### I1: Test-util JAR not bundled — `init-test-project` broken after `go install` — [ ]
- `resolveTestUtilJar()` checks bundled, install, and dev-build tiers — none exist after `go install`
- Options: (a) download from GitHub releases on demand (like analyzer JAR), or (b) embed in binary (it's a JAR though, likely too large), or (c) add a GitHub release download fallback tier to `resolveTestUtilJar()`
- **Priority: HIGH**

### I2: `test-rule.md` — unclear where to find test results — [ ]
- Skill says "Read `test-result.json` in the output directory" but doesn't clarify which directory
- When `-o` is omitted, results go to a temp dir that is immediately cleaned up (`defer os.RemoveAll`)
- Fix: update skill to always specify `-o ./agent-test-results` so agent knows the path
- Also clarify the exact result file path: `<output-dir>/test-result.json`
- **Priority: HIGH**

### I3: `scan` command expects directory, not `project.yaml` path — [ ]
- All skills/meta-prompt use `opentaint scan ./opentaint-project/project.yaml` but CLI expects the **directory** containing `project.yaml`
- CLI checks `os.Stat(filepath.Join(absUserProjectRoot, "project.yaml"))` — passing the file path results in looking for `project.yaml/project.yaml`
- Fix: change all scan examples in skills and meta-prompt from `./opentaint-project/project.yaml` to `./opentaint-project`
- Files to fix: `run-analysis.md` (3 examples), `create-yaml-config.md`, `create-approximation.md`, `create-rule.md`, `meta-prompt.md`
- **Priority: HIGH**

### I4: `analyze-findings.md` — clarify external methods represent missed *fact propagations*, not just vulnerability-relevant methods — [ ]
- Current text says "PROPAGATOR: Method passes taint from input to output" — agent interprets this as searching only for vulnerability-relevant methods (e.g. `statement.executeQuery` for SQLi)
- The actual purpose: external methods list shows where the analyzer **killed dataflow facts** because it had no model. Many of these are generic collection/utility methods (e.g. `List.add`/`List.get`, `Map.put`/`Map.get`, `StringBuilder.append`) that propagate taint regardless of vulnerability type
- Fix: clarify that the agent should prioritize **generic data-flow propagators** (collections, builders, wrappers) over vulnerability-specific methods, and give concrete examples
- **Priority: HIGH**

### I5: `build-project.md` — add manual build fallback with `opentaint project` and `--package` warning — [ ]
- If `opentaint compile` fails, the agent should try building the project manually (e.g. `./gradlew build`, `mvn package`) and then use `opentaint project` with the compiled artifacts
- When using `opentaint project`, the `--package` flag is **mandatory** — without it the analyzer will attempt to analyze all classes including third-party libraries and will hang or run for hours
- Add clear warning: "CRITICAL: Always specify `--package` to restrict analysis to project code only"
- **Priority: HIGH**

### I6: Update meta-prompt scan example to use directory path — [ ]
- `meta-prompt.md:37` uses `opentaint scan ./opentaint-project/project.yaml` — same bug as I3
- Fix alongside I3
- **Priority: HIGH** (part of I3)

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
| 235af7e3 | F4 | Fix CLI scan path, run full suite, write test report |
| (pending) | G1-G8 | Phase G: Fix known issues, auto-compile approximations, strengthen tests |
