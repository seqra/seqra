# Agent Mode — Test Status Report

**Date**: 2026-03-31
**CLI binary**: `cli/bin/opentaint` (dev build with `--analyzer-jar` override)
**Analyzer JAR**: `core/build/libs/opentaint-project-analyzer.jar`
**Test target**: Stirling-PDF at `/home/sobol/data/Stirling-PDF/seqra-project/project.yaml`
**Total**: 34 tests (8 quick, 26 slow)
**Results**: 31 passed, 3 failed (all pre-existing)

---

## Quick Tests (non-slow)

| Suite | Test | Scenario | Status | Time |
|-------|------|----------|--------|------|
| build | test_scan_nonexistent_project | Error: scan with bad path | PASS | <0.1s |
| build | test_scan_missing_output_flag | Error: scan without -o | PASS | <0.1s |
| rules | test_builtin_rules_directory_exists | Verify rule directory structure | PASS | <0.1s |
| rules | test_builtin_lib_rules_exist | Verify library rule files | PASS | <0.1s |
| rules | test_rules_path_command | `opentaint agent rules-path` | PASS | <0.1s |
| rules | test_custom_rules_are_valid_yaml | Validate fixture rule YAML | PASS | <0.1s |
| rules | test_library_rule_has_lib_option | Library rule options.lib:true | PASS | <0.1s |
| rules | test_security_rule_has_metadata | Security rule CWE metadata | PASS | <0.1s |

## Slow Tests — Build (test_build.py)

| Test | Scenario | Status | Time |
|------|----------|--------|------|
| test_scan_with_builtin_rules | Scan Stirling-PDF, 69 findings across 9 rules | PASS | 44.4s |
| test_scan_with_custom_ruleset_directory | Scan with explicit rules path | PASS | 44.1s |
| test_scan_severity_filter_note | Include note-severity findings | PASS | 50.4s |
| test_scan_from_source_directory | Auto-compile + scan | PASS | 78.6s |
| test_compile_source_project | Compile-only (autobuilder) | PASS | 28.7s |

## Slow Tests — Rules (test_rules.py)

| Test | Scenario | Status | Time | Notes |
|------|----------|--------|------|-------|
| test_scan_with_rule_id_filter | `--rule-id` filters SARIF output | PASS | 25.0s | |
| test_scan_without_rule_id_filter_includes_all | No filter → multiple rule IDs | PASS | 42.6s | |
| test_init_test_project | `opentaint agent init-test-project` | PASS | <0.1s | Previously skipped |
| test_rule_test_all_pass | Compile test project + test-rules | **FAIL** | 4.2s | Pre-existing: autobuilder JAR not built locally |
| test_rule_test_detects_false_negative | FN detection in test framework | PASS | 5.1s | |
| test_scan_stirling_with_path_traversal_rule | Custom rule on Stirling-PDF | PASS | 26.1s | |

## Slow Tests — Approximations (test_approximations.py)

| Test | Scenario | Status | Time | Notes |
|------|----------|--------|------|-------|
| test_scan_with_approximations_config | YAML passThrough config | PASS | 22.0s | |
| test_approximations_config_with_custom_ruleset | Config + custom ruleset together | PASS | 25.2s | |
| test_invalid_approximations_config_errors | Bad YAML → error | PASS | 0.3s | |
| test_scan_with_java_source_approximations | Code-based .java approximations | PASS | 46.0s | |
| test_approximation_compilation_failure | Bad Java source → error | **FAIL** | 44.7s | Pre-existing: analyzer exits 0 despite error |
| test_duplicate_approximation_errors | Duplicate builtin class → error | **FAIL** | 43.6s | Pre-existing: analyzer exits 0 despite error |
| test_scan_with_both_approximation_types | Combined YAML + Java approx | PASS | 24.4s | |
| test_approximations_change_results | Compare with/without approx | PASS | 49.3s | |

## Slow Tests — External Methods (test_external_methods.py)

| Test | Scenario | Status | Time |
|------|----------|--------|------|
| test_scan_produces_external_methods_file | `--external-methods` flag | PASS | 45.5s |
| test_external_methods_structure | YAML structure validation | PASS | 52.8s |
| test_without_rules_nonempty_for_real_project | withoutRules non-empty (324) | PASS | 50.0s |
| test_with_rules_contains_standard_library_methods | withRules has stdlib (167) | PASS | 49.8s |
| test_approximations_reduce_without_rules | Approx reduces withoutRules | PASS | 75.8s |
| test_both_outputs_produced | SARIF + external methods together | PASS | 45.9s |

## Slow Tests — Full Loop (test_full_loop.py)

| Test | Scenario | Status | Time | Phase Timing |
|------|----------|--------|------|-------------|
| test_full_agent_loop | End-to-end agent workflow | PASS | 25.6s | P1: 0.0s, P2: 0.0s, P3 (scan): 25.6s, P3b: 25.6s, P4: 25.6s |

---

## Pre-Existing Failures (not caused by agent-mode)

1. **`test_approximation_compilation_failure`** — The Kotlin analyzer catches the compilation error internally but still exits with code 0. The Go CLI propagates this as success. Fix requires analyzer to exit non-zero on approximation compilation failures.

2. **`test_duplicate_approximation_errors`** — Same root cause: analyzer detects the bijection violation but exits with code 0. Fix requires analyzer exit code propagation.

3. **`test_rule_test_all_pass`** — The `compile` step fails because the autobuilder JAR is not built locally. This test requires `./gradlew :autobuilder:jar` to be run first. The test itself is correct; the environment is incomplete.

---

## Phase F Summary

| Task | Description | Status |
|------|-------------|--------|
| F1 | Refactor tests to CLI-only (remove direct JAR mode) | Done |
| F2 | Implement `opentaint agent init-test-project` command | Done |
| F3 | Add timing instrumentation to all tests | Done |
| F4 | Run all tests via CLI, write test report | Done |
