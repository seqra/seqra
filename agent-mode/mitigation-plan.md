# Agent Mode — Mismatch Mitigation Plan

## Priority Rules

1. **Core analyzer API is frozen.** Its current surface (`AbstractAnalyzerRunner` +
   `ProjectAnalyzerRunner`) is the source of truth. We do **not** add, rename, or change
   the semantics of any Kotlin option.
2. **CLI follows Core.** Any Go CLI flag that does not correctly map onto a Core option is
   changed until it does.
3. **Skills and design docs follow CLI.** Anything still documented incorrectly is rewritten
   in skills / meta-prompt / design.

Every fix below is locked to this hierarchy.

---

## 1. Frozen Core API (reference)

From `core/src/main/kotlin/org/opentaint/jvm/sast/runner/ProjectAnalyzerRunner.kt`
(+ `AbstractAnalyzerRunner.kt`). These are the options the CLI is allowed to use:

| Kotlin name | CLI flag (Clikt-derived) | Kind | Notes |
|---|---|---|---|
| `approximationsConfig` | `--approximations-config` | `List<Path>` (repeatable) | Custom YAML passThrough; OVERRIDE mode. |
| `semgrepRuleSet` | `--semgrep-rule-set` | `List<Path>` | Ruleset roots. |
| `semgrepRuleSeverity` | `--semgrep-rule-severity` | `List<Severity>` | |
| `semgrepRuleId` | `--semgrep-rule-id` | `List<String>` | Full ID `<path>.yaml:<id>`. |
| `trackExternalMethods` | `--track-external-methods` | Boolean flag | Writes fixed-name YAMLs into `outputDir`. |
| `dataflowApproximations` | `--dataflow-approximations` | `List<Path>` (directories) | Compiled class dirs. |
| `semgrepRuleLoadTrace` | `--semgrep-rule-load-trace` | `Path?` | |
| `sarifFileName`, `sarifCodeFlowLimit`, `sarifSemgrepStyleId`, `sarifToolVersion`, `sarifToolSemanticVersion`, `sarifGenerateFingerprint`, `sarifUriBase` | corresponding `--sarif-*` flags | … | |
| `debugFactReachabilitySarif` | `--debug-fact-reachability-sarif` | Flag | Output: `outputDir/debug-ifds-fact-reachability.sarif`. |
| `debugRunRuleTests` | `--debug-run-rule-tests` | Flag | Output: `outputDir/test-result.json`. |
| `--project`, `--output-dir`, `--project-kind`, `--ifds-analysis-timeout`, `--ifds-ap-mode`, `--verbosity`, `--logs-file` | inherited | | |

**Hard consequences** locked in by this surface:
- External-methods output: fixed filenames (`external-methods-{without,with}-rules.yaml`) in
  `outputDir`. Users cannot choose a path.
- `--approximations-config` is repeatable.
- No "refs auto-include" for `--semgrep-rule-id`: filtering is purely
  `rule.info.ruleId in filter` (`SemgrepRuleLoader.kt:493`).

Anything in CLI / skills / design that contradicts the table above must yield.

---

## 2. CLI changes (to match Core)

### 2.1 External methods — decision: boolean `--track-external-methods`

**File**: `cli/cmd/scan.go`, `cli/cmd/command_builder.go`.

We considered two CLI shapes:

| Option | UX | Code | Failure modes |
|---|---|---|---|
| **A. Boolean** `--track-external-methods` | Files always in `<outputDir>/external-methods-{without,with}-rules.yaml` (next to SARIF). | 1:1 with Core; emitter is three lines. | None introduced. |
| B. String `--external-methods <base>` with post-scan rename | User picks base path; CLI renames/copies after the analyzer exits. | Extra I/O code; partial-failure semantics if analyzer crashes mid-write or user path is on a different volume; must handle two files atomically. | CLI must also decide whether to leave the originals; rename logic has to run even when analyzer returns non-zero (for partial output). |

**Decision: Option A — boolean `--track-external-methods`.** It matches the frozen Core API 1:1 (Priority 1 rule), removes a class of failure modes, and the agent workflow doesn't need a custom base path — it already knows the output directory because it controls `-o`.

Concrete changes:

- Remove from `cli/cmd/command_builder.go`: the `externalMethodsOutput string` field, the `SetExternalMethodsOutput(...)` method, and the `--external-methods-output` emitter (`command_builder.go:254-256`). Add `trackExternalMethods bool` + `SetTrackExternalMethods(bool)`; emit only `"--track-external-methods"` (no value).
- Remove from `cli/cmd/scan.go`: the `ExternalMethodsOutput string` var, the `--external-methods` flag registration, and the absolute-path resolution block that calls `SetExternalMethodsOutput(...)`. Add:
  ```go
  var TrackExternalMethods bool
  scanCmd.Flags().BoolVar(&TrackExternalMethods, "track-external-methods", false,
      "Write external-methods-{without,with}-rules.yaml next to the SARIF report")
  ...
  if TrackExternalMethods {
      nativeBuilder.SetTrackExternalMethods(true)
  }
  ```
- Document the fixed output location in the flag help text and in the updated skill (`run-analysis.md`): `<sarif-directory>/external-methods-{without,with}-rules.yaml`.

### 2.2 `--approximations-config` — make repeatable

**File**: `cli/cmd/scan.go`, `cli/cmd/command_builder.go`.

Core takes `List<Path>`; CLI currently takes `string`. Promote to `[]string`:

```go
var ApproximationsConfig []string
scanCmd.Flags().StringArrayVar(&ApproximationsConfig, "approximations-config", nil,
    "YAML passThrough approximations config (OVERRIDE mode, repeatable)")
```

Builder gains `AddApproximationsConfig(path string)`; the emitter loops and appends
`--approximations-config <p>` per entry. Same treatment for the absolute-path resolution
loop in `scan.go`.

### 2.3 Verify `--dataflow-approximations` stays compatible

The current `directory()` typing in Core rejects files. The CLI already produces a
directory (auto-compile branch in `compile_approximations.go`). Keep as-is; add a
defensive check in the CLI that `info.IsDir()` before proxying (we do already).
No changes needed.

### 2.4 Other flags reviewed — keep as-is

- `--rule-id` → `--semgrep-rule-id` proxy: correct.
- `--ruleset` → `--semgrep-rule-set`: correct.
- `--severity` → `--semgrep-rule-severity`: correct.
- `--timeout` → `--ifds-analysis-timeout`: correct.
- `--debug-fact-reachability-sarif`: correct.
- `--debug-run-rule-tests` (used via `EnableRunRuleTests`): correct.

### 2.5 `opentaint agent test-rules` — clarify argument

The CLI already requires a project-model **directory** (it joins `project.yaml`). No code
change; just ensure the `Short`/`Long` help strings are unambiguous:

```
Usage: opentaint agent test-rules <project-model-directory>
```

(`project-model` is already used but is easy to misread as a file.)

### 2.6 `opentaint scan` with compiled model — keep `--project-model`

No CLI change required. Validation already tells users the right command via `suggest(...)`.
All misuse is documentation-side; fixed in §3.

### 2.7 Sync `core/bin/main/...` to `core/src/main/...`

Clean-rebuild the `core/bin` tree (or delete it if it's a stale IDE artefact). It diverges
from `core/src` (still declares the old `--external-methods-output`, the `--config` alias
on `approximationsConfig`, etc.), which will confuse anyone who runs the binary copy.

Action: add a pre-commit / CI check or just `./gradlew clean build` + commit the result; if
`core/bin` is in `.gitignore`, drop the stale directory from the workspace.

---

## 3. Skill & design changes (to match CLI)

For each file, exhaustive edits:

### 3.1 `agent/meta-prompt.md`

- Line 36 example `opentaint scan ./opentaint-project \...` → replace with
  `opentaint scan --project-model ./opentaint-project \...`.
- Line 40 `--external-methods ./results/external-methods.yaml` → replace with
  `--track-external-methods` and add note that files are emitted next to the SARIF in
  the analyzer output directory.
- Line 42 "Collect `report.sarif`, `external-methods-without-rules.yaml`..." → update paths
  to `<sarif-dir>/external-methods-{without,with}-rules.yaml`.
- Line 39 `--rule-id <your-rule-ids>` → add a note that IDs are `<path>.yaml:<id>`.
- Layout block (§Working Directory Layout): drop `agent-approximations/classes/`; use
  `agent-approximations/src/` only (the CLI auto-compiles).
- Line 99 remove the "library rules auto-included via join-mode refs" claim; replace with:
  "every rule ID you want active (including library rules referenced by join rules) must be
  listed explicitly in `--rule-id`".

### 3.2 `agent/skills/run-analysis.md`

- Every `opentaint scan ./opentaint-project \` → `opentaint scan --project-model ./opentaint-project \`.
- Replace all `--external-methods ./results/external-methods.yaml` with
  `--track-external-methods`.
- Rewrite the "base path" note: the analyzer writes
  `<outputDir>/external-methods-{without,with}-rules.yaml`, where `<outputDir>` is the
  directory that contains the SARIF file. The user does **not** choose filenames.
- Update the "Outputs" section accordingly.
- "Key Flags" table: replace `--external-methods` row with `--track-external-methods`;
  mark `--approximations-config` as repeatable.

### 3.3 `agent/skills/create-rule.md`

- Line 15 `RULES_DIR=$(opentaint rules-path)` → `RULES_DIR=$(opentaint agent rules-path)`.
- §6 example: `opentaint scan ./opentaint-project \...` → `opentaint scan --project-model ./opentaint-project \...`.
- Keep the `--rule-id <path>.yaml:<id>` guidance (already correct).

### 3.4 `agent/skills/create-approximation.md`

- Remove the manual `javac` block (§2). The Go CLI auto-compiles `.java` from the
  `--dataflow-approximations` directory.
- Replace §3 example `opentaint scan ./opentaint-project` with
  `opentaint scan --project-model ./opentaint-project`.
- Change `--dataflow-approximations ./agent-approximations/classes` to
  `--dataflow-approximations ./agent-approximations/src` (CLI handles compilation).

### 3.5 `agent/skills/create-yaml-config.md`

- Replace `opentaint scan ./opentaint-project` with
  `opentaint scan --project-model ./opentaint-project`.

### 3.6 `agent/skills/debug-rule-reachability.md`

- Same `--project-model` replacement.
- Keep the single `--rule-id` warning.

### 3.7 `agent/skills/test-rule.md`

- Already uses `opentaint agent test-rules` and `opentaint agent init-test-project`. No
  change needed beyond one pass to verify the project-model argument is a directory
  (already is).

### 3.8 `agent/skills/analyze-findings.md`

- Under §3 ("Process external methods"), update to fixed filenames in the SARIF output
  directory; remove the "base path" wording.

### 3.9 `agent/skills/build-project.md`, `discover-entry-points.md`, `generate-poc.md`

- No CLI usages to fix (these are about source reading and documentation).
- Spot-check: `discover-entry-points.md` mentions
  `--debug-run-analysis-on-selected-entry-points`; verify it is still in
  `AbstractAnalyzerRunner.kt` (it is, line 45). Keep.

### 3.10 `agent-mode/design/agent-mode-design.md`

This is the biggest offender; edit inline, do **not** re-open the design question:

- §1.1 "Kotlin CLI flag: `--external-methods-output <path>`" → replace with
  "Kotlin CLI flag: `--track-external-methods` (boolean). Output filenames are fixed
  (`external-methods-{without,with}-rules.yaml` in `--output-dir`)."
- §1.1 "Go CLI flag: `--external-methods <path>`" → "Go CLI flag:
  `--track-external-methods`."
- §1.2 Kotlin CLI rename note: keep but mark `approximationsConfig` as `List<Path>`.
- §1.6 rewrite: remove "referenced library rules are automatically included"; state
  explicitly: "Each rule whose ID is not in the filter is dropped, including library rules
  referenced via `refs`. Callers must list every rule they want active."
- §2.1 Complete Command Reference:
  - Every `opentaint scan ./opentaint-project/project.yaml` → either `opentaint scan <source-path>`
    (for compile+scan) or `opentaint scan --project-model ./opentaint-project`.
  - `opentaint test-rules` → `opentaint agent test-rules`, and argument is a directory.
  - `opentaint rules-path` → `opentaint agent rules-path`.
  - `opentaint init-test-project` → `opentaint agent init-test-project`.
  - `--external-methods <path>` row → `--track-external-methods`.
  - `--rule-id my-vulnerability` → `--rule-id java/security/my-vuln.yaml:my-vulnerability`.
- §2.2 Command Builder mapping:
  - `--external-methods <path>` → `--external-methods-output <path>` row → delete; replace
    with `--track-external-methods` → `--track-external-methods`.
- §3.3 / §3.5 / §3.7 / §3.8 / §3.9 examples: propagate the same three changes
  (`--project-model`, `--track-external-methods`, full `--rule-id`).
- Appendix A: `opentaint init-test-project` → `opentaint agent init-test-project`;
  `opentaint test-rules ./agent-test-compiled/project.yaml` →
  `opentaint agent test-rules ./agent-test-compiled`.
- Appendix C: keep (output format itself is correct, only the path control prose in body is
  wrong).

---

## 4. Execution order

Pick the order to minimise churn re-testing:

1. **CLI code changes** (§2.1, §2.2, §2.5 help text, §2.7 bin cleanup).
   After this step, `opentaint scan --track-external-methods` and repeatable
   `--approximations-config` work end-to-end against the unchanged Core.
2. **Test suite sync** (§4.5). Update `conftest.py` + the five `test_*.py` files
   so the CLI changes can be validated. Running the suite after step 2 is the
   primary regression gate for the whole mitigation.
3. **Skill updates** (§3.1–§3.9). These only touch `.md`; do in one pass with a single
   `edit_file` per skill, guided by this plan.
4. **Design doc rewrite** (§3.10). Largest text change; do last so the design reflects
   settled CLI + skill wording.
5. **Verification pass** — for each updated file, grep for the banned tokens and fail the
   build if any survive:
   ```
   opentaint rules-path        -> must be absent (except after "agent ")
   opentaint test-rules        -> must be absent
   opentaint init-test-project -> must be absent
   --external-methods          -> must be absent (replaced by --track-external-methods)
   --external-methods-output   -> must be absent (Core doesn't have it)
   opentaint scan ./opentaint-project  -> must be absent
   scan .*project\.yaml        -> must be absent
   --rule-id my-vulnerability  -> must be absent (must be full ID)
   ```
   Add this grep as a `scripts/check-docs.sh` and run it in CI.

## 4.5 `agent-mode/test/` — verification suite fixes

The pytest suite under `agent-mode/test/` is currently wired to the old/wrong
CLI surface. After Core freezes and CLI is corrected, every test file needs targeted
edits; otherwise **no test can pass** against the new CLI (scan rejects the model path,
`--external-methods` no longer exists, etc.). Changes below are keyed to the decisions
made in §1–§3.

### 4.5.1 `agent-mode/test/conftest.py`

- `OpenTaintCLI.scan(...)` (lines ~190–230):
  - Signature: replace `external_methods: Optional[str] = None` with
    `track_external_methods: bool = False`.
  - Body: when the incoming `project_path` points at a pre-compiled model (directory
    containing `project.yaml` or the file itself), pass `--project-model <dir>` instead of
    a positional argument. Pseudocode:
    ```python
    p = Path(project_path)
    if p.name == "project.yaml" and p.is_file():
        p = p.parent
    if (p / "project.yaml").is_file():
        cmd = self._base_cmd() + ["scan", "-o", output, "--project-model", str(p)]
    else:
        cmd = self._base_cmd() + ["scan", str(p), "-o", output]
    ```
  - Flag emission: drop `--external-methods`; when `track_external_methods=True`, append
    `--track-external-methods` (no value).
- `OpenTaintCLI.test_rules(...)` (lines ~230–245): the current code already passes a
  directory — just verify the swap from `project.yaml` file to parent directory stays
  (it does). No semantic change required; keep as-is.
- Helper `_derive_external_methods_paths(base_path)` and `load_external_methods(base_path)`
  (lines ~335–380): switch them to take the **SARIF path** (or its parent directory) and
  return the two fixed filenames in that directory:
  ```python
  def _derive_external_methods_paths(sarif_path: Path) -> tuple[Path, Path]:
      parent = sarif_path.parent
      return (
          parent / "external-methods-without-rules.yaml",
          parent / "external-methods-with-rules.yaml",
      )

  def load_external_methods(sarif_path: Path) -> dict: ...
  def external_methods_exist(sarif_path: Path) -> bool: ...
  ```
- No other helper changes needed; `count_external_methods`, `sarif_*`, and timing helpers
  are agnostic to CLI wiring.

### 4.5.2 `agent-mode/test/test_external_methods.py`

Every test in this file threads the `ext_methods_path = tmp_output / "external-methods.yaml"`
variable through `external_methods=str(ext_methods_path)` and then loads the pair via
`load_external_methods(ext_methods_path)`. After the CLI change:

- Drop the `ext_methods_path` computation entirely.
- Call `cli.scan(..., track_external_methods=True)`.
- Pass the SARIF path to `load_external_methods` / `external_methods_exist` (they are the
  fixed files next to SARIF).

All three test classes (`TestExternalMethodsBasic`, `TestExternalMethodsContent`,
`TestExternalMethodsWithApproximations`, `TestExternalMethodsAlongsideSarif`) are updated
the same way; the `run1`/`run2` subdirectory pattern in
`test_approximations_reduce_without_rules` is kept so the two runs don't collide.

### 4.5.3 `agent-mode/test/test_full_loop.py`

- `test_full_agent_loop` sets `ext_methods_path = ws["results"] / "external-methods-1.yaml"`
  and `-2.yaml`, passes them to `cli.scan(external_methods=...)`, and inspects them.
- The two scan runs share `ws["results"]`, which means the fixed filenames would
  collide. Fix: give each scan its own subdir (`ws["results"] / "run-1"` and `run-2`),
  and write SARIF into that subdir. Then `load_external_methods(sarif_path)` picks up
  the two files next to it.
- Replace `external_methods=str(...)` with `track_external_methods=True`.
- Replace every `cli.scan(project_path=str(stirling_project), ...)` call's argument
  handling via conftest (no per-test change needed once conftest uses `--project-model`).

### 4.5.4 `agent-mode/test/test_approximations.py`

- No `external_methods=` usage — only scan-with-approx. The fix is entirely indirect via
  conftest (project-model routing). The test `test_approximations_change_results` keeps its
  separate `run1` / `run2` output dirs; no collision.
- `test_approximations_config_with_custom_ruleset` currently passes a single
  `approximations_config` value; this continues to work since CLI accepts the flag once.
  If we also promote `cli.scan`'s signature to accept a list (optional), add a follow-up.
- Verify `test_approximation_compilation_failure` against the current
  `compile_approximations.go` — keep assertions.

### 4.5.5 `agent-mode/test/test_rules.py`

- No `external_methods=` usage. Indirect fix via conftest (`--project-model`).
- `test_rules_path_command`: keep — already uses `cli.rules_path()` which is
  `opentaint agent rules-path`.
- `test_init_test_project`, `test_rule_test_all_pass`,
  `test_rule_test_detects_false_negative`: keep — already use
  `cli.init_test_project` / `cli.test_rules` (both under `agent` subcommand).
- `test_rule_test_all_pass` still passes `project_path=str(compiled_dir / "project.yaml")`
  to `cli.test_rules`. Conftest already strips `project.yaml` for that method; keep.

### 4.5.6 `agent-mode/test/test_build.py`

- No external-methods or rule-id references that need changing.
- `test_scan_nonexistent_project`, `test_scan_missing_output_flag`: keep as-is (exit-code
  checks).
- All other tests pass `stirling_project` which resolves via conftest — will work once
  conftest routes pre-compiled models through `--project-model`.

### 4.5.7 Run/verify procedure

After CLI and test edits:

1. `cd cli && go build -o ./bin/opentaint .`
2. Build local JARs once (`cd core && ./gradlew build`) so the hidden `--analyzer-jar` /
   `--autobuilder-jar` resolution finds them.
3. `cd agent-mode/test && pytest -m "not slow" -q` for the fast smoke set, then
   `pytest -q` for the full (slow) set.
4. Expected outcomes:
   - `test_external_methods.py::TestExternalMethodsBasic::test_scan_produces_external_methods_file` passes because the CLI now enables tracking and the helper looks at the fixed file names.
   - `test_full_loop.py::test_full_agent_loop` passes after the per-run subdir split.
   - `test_rules.py::test_rules_path_command` passes (already correct).
   - `test_build.py::test_scan_with_builtin_rules` passes because conftest now uses
     `--project-model`.
5. If a test still fails, inspect the CLI's stderr (captured in `CLIResult.stderr`) for
   the real Clikt / analyzer error; do not reintroduce the old flag names.

---

## 5. Out of scope / explicitly NOT changed

- **Core option rename** (e.g. bringing `--external-methods-output` back). Design's §1.1
  lost out to the frozen-Core rule; the external-methods output path is not configurable.
- **"Auto-include library refs" in `SemgrepRuleLoader`**. Implementation requires
  explicit IDs; design doc gets rewritten, not the loader.
- **`agent-approximations/classes/` as the canonical compiled directory**. The CLI
  auto-compiles; we commit to `src/`.
- **Changing `opentaint agent ...` subcommands to top-level**. Current grouping stays;
  docs follow the grouping.
