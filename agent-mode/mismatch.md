# Agent Mode — Design vs Implementation Mismatches

Scope of review:
- Design: `agent-mode/design/agent-mode-design.md`
- Agent prompt: `agent/meta-prompt.md`
- Agent skills: `agent/skills/*.md`
- Go CLI: `cli/cmd/*.go`
- Core analyzer CLI: `core/src/main/kotlin/org/opentaint/jvm/sast/runner/{AbstractAnalyzerRunner,ProjectAnalyzerRunner}.kt`

The classification below splits findings into:
- **CLI ↔ Core mismatches**: Go CLI passes a wrong flag name, wrong semantics, or wrong value to the Kotlin analyzer.
- **Skill ↔ CLI mismatches**: The skill / meta-prompt tells the agent to use a CLI surface that does not exist or works differently.
- **Skill ↔ Design mismatches**: The design promises a behavior that the skill contradicts (even if the skill happens to match the implementation).

Severities:
- **BLOCKER** — user command fails (non-zero exit) or produces no output.
- **MAJOR** — produces incorrect behavior or wrong output path.
- **MINOR** — misleading documentation; commands still work.

---

## 1. CLI ↔ Core analyzer mismatches

### 1.1 BLOCKER — `--external-methods-output` flag does not exist on the analyzer

**Go CLI** (`cli/cmd/command_builder.go:255`):
```go
if a.externalMethodsOutput != "" {
    flags = append(flags, "--external-methods-output", a.externalMethodsOutput)
}
```

**Kotlin analyzer** (`core/src/main/kotlin/org/opentaint/jvm/sast/runner/ProjectAnalyzerRunner.kt:51`):
```kotlin
private val trackExternalMethods: Boolean by option(help = "Track external methods, produce external methods YAML lists")
    .flag()
```

The analyzer exposes a **boolean flag** (`--track-external-methods`), not a path option. The
Go CLI sends `--external-methods-output <path>`, which Clikt will reject because no such
option is declared.

Additionally, the output **location is not configurable** in the analyzer — it always writes
into the analyzer output directory (`ProjectAnalyzer.writeExternalMethodsYaml`, lines 222–237):

```kotlin
val withoutRulesPath = resultDir / "external-methods-without-rules.yaml"
val withRulesPath  = resultDir / "external-methods-with-rules.yaml"
```

**Consequence**: the Go CLI flag `--external-methods` is broken end-to-end. Passing it fails
the scan (unknown option). Even if the flag name were fixed, the user-supplied base path
would be ignored.

**Fix options**:
- Change the Go CLI to pass `--track-external-methods` when `ExternalMethodsOutput != ""`, and
  surface the files from the analyzer output dir (`<outputDir>/external-methods-{without,with}-rules.yaml`);
- Or extend the analyzer to accept `--external-methods-output <basePath>` (matching the design).

> Historical note: `core/bin/main/.../ProjectAnalyzerRunner.kt:50–51` did declare
> `--external-methods-output` as `Path? by option(...).newFile()`. The current
> source has replaced it with a boolean `trackExternalMethods`. The Go CLI still
> targets the old contract.

### 1.2 MAJOR — Design / CLI disagreement on `--approximations-config` cardinality

**Design (1.2)**:
> **Kotlin CLI**: Rename `--config` to `--approximations-config`.

Design implies a **single** config path (consistent with pre-existing `customConfig: Path?`).

**Kotlin analyzer** (current source, `ProjectAnalyzerRunner.kt:37`):
```kotlin
private val approximationsConfig: List<Path> by option(help = "...")
    .file()
    .multiple()
```

So the real core API is now **repeatable** (`List<Path>`), but:

**Go CLI** (`cli/cmd/scan.go:35`):
```go
ApproximationsConfig          string
...
scanCmd.Flags().StringVar(&ApproximationsConfig, "approximations-config", "", "...")
```

and builder (`command_builder.go:60, 246–248`):
```go
approximationsConfig     string
...
if a.approximationsConfig != "" {
    flags = append(flags, "--approximations-config", a.approximationsConfig)
}
```

The Go CLI exposes a **single-valued** flag and passes at most one occurrence.

**Consequence**: agents relying on "OVERRIDE mode" semantics documented in the design can
only ever supply a single file. If the analyzer expects multiple (it accepts `.multiple()`)
there is no way to supply them through the Go CLI.

**Fix options**: make the Go CLI flag repeatable (`StringArrayVar`) and proxy every value,
or revert the Kotlin option to `Path?` and update the design to make the single-path
contract explicit.

### 1.3 MINOR — `--dataflow-approximations` accepts different path kinds

**Design (1.3/1.4)** and all skills: `--dataflow-approximations <dir>` — "Directory of compiled
approximation class files" (or sources which the Go CLI auto-compiles).

**Kotlin analyzer** current source (`ProjectAnalyzerRunner.kt:54`):
```kotlin
private val dataflowApproximations: List<Path> by option(help = "Directory of compiled approximation class files")
    .directory()
    .multiple()
```

The `core/bin/...` copy uses `.path()` instead of `.directory()`. Not a behavioural
mismatch between the Go CLI and the analyzer (the CLI does compile sources to a directory
and passes the directory path), but there is an inconsistency between the compiled artifact
and the source, which can silently bite integrators using the `bin` classpath.

---

## 2. Skill / meta-prompt ↔ Go CLI mismatches

### 2.1 BLOCKER — `opentaint rules-path` does not exist

**Design (1.8, 2.1)** and `agent/skills/create-rule.md:15` prescribe:
```bash
RULES_DIR=$(opentaint rules-path)
```

**Meta prompt** (`agent/meta-prompt.md:24`):
```
1. **Check built-in rules** -- read rules in `$(opentaint agent rules-path)`
```

**Actual CLI** (`cli/cmd/agent_rules_path.go`): the command is registered under
the `agent` command group, i.e. `opentaint agent rules-path`, not `opentaint rules-path`.

- The meta-prompt uses the correct form (`opentaint agent rules-path`).
- The `create-rule.md` skill uses the **wrong** form (`opentaint rules-path`),
  matching the design document verbatim.

**Fix**: change `create-rule.md:15` to `opentaint agent rules-path`.

### 2.2 BLOCKER — `opentaint test-rules` does not exist as a top-level command

**Design (1.5, 2.1)** and all design examples:
```bash
opentaint test-rules <test-project-path-or-project.yaml> --ruleset ... -o ...
```

**Actual CLI** (`cli/cmd/agent_test_rules.go:24`): the command is registered under the
`agent` group:
```go
agentCmd.AddCommand(agentTestRulesCmd)
```
Real invocation is `opentaint agent test-rules ...`.

- The `agent/skills/test-rule.md:64` uses the correct form (`opentaint agent test-rules`).
- The design document and every `run-analysis`/phased example in the design file use the
  incorrect top-level form.

### 2.3 BLOCKER — `opentaint init-test-project` does not exist as a top-level command

Same pattern as 2.2. Design says `opentaint init-test-project <dir>`; the implementation
registers it as `opentaint agent init-test-project` (`cli/cmd/agent_init_test_project.go:67`).

- `agent/skills/test-rule.md:16` uses the correct `opentaint agent init-test-project`.
- Design document uses `opentaint init-test-project` in Appendix A and §2.1 / §3.4.

### 2.4 MAJOR — `opentaint agent test-rules` argument is a directory, not `project.yaml`

**Design (1.5, 2.1)** and **skill `run-analysis.md`** repeatedly say:
```bash
opentaint test-rules ./agent-test-compiled/project.yaml --ruleset ... -o ...
```

**Actual CLI** (`cli/cmd/agent_test_rules.go:37-42`):
```go
projectPath := log.AbsPathOrExit(args[0], "project-model")
nativeProjectPath := filepath.Join(projectPath, "project.yaml")

if _, err := os.Stat(nativeProjectPath); os.IsNotExist(err) {
    out.Fatalf("Project model not found: %s", nativeProjectPath)
}
```

The CLI joins the argument with `project.yaml` and then stats it. If the user passes
`./agent-test-compiled/project.yaml`, the CLI stats `./agent-test-compiled/project.yaml/project.yaml`
and aborts.

Skill `test-rule.md:64-66` has the **correct** form (passes a directory). Design file
and the `analyze-findings` narrative in the design have the wrong form.

### 2.5 MAJOR — `opentaint scan` argument is **not** the directory containing `project.yaml` only

**Meta prompt** (`agent/meta-prompt.md:36`) and skills `run-analysis.md`,
`create-yaml-config.md`, `debug-rule-reachability.md`, `create-rule.md`,
`create-approximation.md` pass `./opentaint-project` (a directory) to `scan`.
`agent/skills/run-analysis.md:78` even states this as a "Note":
> The scan path is the **directory** containing `project.yaml`, not the path to `project.yaml` itself

**Actual CLI** (`cli/cmd/scan.go:158-167`):
- Checks `validation.ValidateSourceProject(absUserProjectRoot)` against source-project markers
  (`pom.xml`, `build.gradle*`, `mvnw`, `gradlew`, `.mvn`). A directory that contains only
  `project.yaml` and compiled classes has **none** of these markers.
- When validation fails it then tests `validation.IsProjectModel(absUserProjectRoot)` and, if
  true, **aborts with a suggestion** to use `--project-model`, exit code 1.

So `opentaint scan ./opentaint-project` (directory with `project.yaml`) **does not scan**;
it prints a suggestion and exits. The correct invocation is either `opentaint scan` on the
source directory (for compile+scan) or `opentaint scan --project-model ./opentaint-project`.

The design file is particularly bad about this — e.g. §3.5:
```bash
opentaint scan ./opentaint-project/project.yaml -o ./results/report.sarif ...
```
This passes a file to a command expecting a directory and fails validation in a different
way.

**Fix**: update every skill and design snippet to use
```bash
opentaint scan --project-model ./opentaint-project ...
```
(or pass the source directory if a fresh compile is desired).

### 2.6 MAJOR — `agent/skills/run-analysis.md` claims `--external-methods` produces two files whose base path is user-configurable

`run-analysis.md:59-60`:
> The `--external-methods` flag specifies the **base path**. The analyzer derives two
> filenames by appending `-without-rules` and `-with-rules` before the `.yaml` extension.

Both statements are wrong against the current analyzer:
1. Per 1.1 above, the analyzer does not accept a path at all — only a boolean
   `--track-external-methods`. The Go CLI itself currently passes an unsupported
   `--external-methods-output <path>` flag, so `--external-methods` in the Go CLI never
   actually drives the output path.
2. Output file names are hard-coded (`external-methods-{without,with}-rules.yaml`),
   written into the analyzer `resultDir`, not to a user-supplied base path.

The meta-prompt (`agent/meta-prompt.md:40,81`) and `analyze-findings.md:40-42` repeat the
"two files" expectation; the files do exist but at the fixed location above.

### 2.7 MINOR — `--rule-id` argument format

**Implementation** (`ruleIdAllow` in `SemgrepRuleLoader.kt:493-494`) compares the
`--semgrep-rule-id` value against `rule.info.ruleId`, which is built by
`SemgrepRuleUtils.getRuleId(ruleSetName, id)` as `"$ruleSetName:$id"` where `ruleSetName`
is the rule file path relative to the ruleset root (e.g. `java/security/my-vuln.yaml`).

Skills `run-analysis.md`, `create-rule.md`, `debug-rule-reachability.md`, and `test-rule.md`
all correctly document `--rule-id java/security/my-vuln.yaml:my-vulnerability`.

**Design file is wrong** — §1.6 and §3.3 examples give:
```bash
--rule-id my-vulnerability
```
and state "No need to list [refs] in `--rule-id`". With the implementation, the plain short
ID does not match any rule (`ruleIdAllow` will drop every rule), yielding zero findings.

The design's claim that referenced library rules are auto-included when a join rule is in
the filter is **not** visible in the current `SemgrepRuleLoader.loadRules`: the single filter
check is `ruleIdAllow(this, ruleIdFilter)` applied to every rule independently. If a library
rule's full ID is not in the filter, it is skipped (library rules are also skipped by
`info.isLibraryRule` anyway, regardless of refs).

The meta-prompt (`agent/meta-prompt.md:39`) writes `--rule-id <your-rule-ids>` without
specifying the format, which is less wrong but still misleading for an agent; the explicit
full-ID examples in the skills are correct.

### 2.8 MINOR — `create-rule.md` duplicates the design's wrong `RULES_DIR=$(opentaint rules-path)`

See 2.1 — `create-rule.md:15` needs the `agent` prefix.

---

## 3. Skill ↔ Design mismatches

### 3.1 Rule filter semantics re-refs

**Design (1.6)**: "Library rules (`options.lib: true`) referenced by active rules via `refs`
are automatically included — they don't need to be listed explicitly." and §3.3.

**Implementation** (`SemgrepRuleLoader.loadRules` and `ruleIdAllow`, lines 105-107, 493-494):
A rule is kept iff it is not disabled, not a library rule, passes severity, **and** passes
the `ruleIdFilter`. Library rules are always skipped (`info.isLibraryRule` skip), so the
"auto-include" only works to the extent that join rules physically carry their refs'
patterns internally. There is no code path that adds library rule IDs to the filter or
treats them as implicitly active via a join rule's `refs`.

This is a **design ↔ implementation** mismatch; it also explains why skills that repeat the
design's claim (`create-rule.md:127`, `meta-prompt.md:99`) are misleading.

### 3.2 `--approximations-config` OVERRIDE mode and scope

**Design (1.2, §3.7)** says the custom config **overrides** the default config and is used
**exclusively for passThrough**, because the analyzer "currently cannot use sanitizers from
the config".

**Implementation** (`ProjectAnalyzer.approximationConfigCombinationOptions`, lines 246-252):
```kotlin
private val approximationConfigCombinationOptions = CombinationOptions(
    entryPoint = CombinationMode.IGNORE,
    source     = CombinationMode.IGNORE,
    sink       = CombinationMode.IGNORE,
    cleaner    = CombinationMode.IGNORE,
    passThrough = CombinationMode.OVERRIDE,
)
```

Skills match the design (OVERRIDE, passThrough only), so no mismatch between skills and
implementation **for this category**. The design note in §3.7 says *"cleaner ignored"* but
describes it as `conditions` in §3.7 enumeration which includes `cleaner`-ish constructs
implicitly via custom configurations — that part is consistent.

### 3.3 `agent-approximations/` directory layout

- **Design § Working Directory Layout** says `agent-approximations/src/` contains Java
  sources that are auto-compiled by the CLI.
- **Meta prompt §Working Directory Layout** says `agent-approximations/classes/` (compiled
  classes).
- **Skill `create-approximation.md`** uses `agent-approximations/src/` for sources and
  `agent-approximations/classes/` for compiled output (compile manually with `javac`).
- **Go CLI** (`cli/cmd/compile_approximations.go`) auto-compiles `.java` files found in the
  given `--dataflow-approximations` directory (aligning with design).

The documentation is internally inconsistent: meta-prompt's layout omits the `src/`
directory and assumes the agent compiles manually, while design/skill direct the agent to
let the CLI auto-compile. Pick one convention; currently the agent may do both, depending
on which file it reads.

### 3.4 `agent-approximations/classes` vs `src` (skill text)

`agent/skills/create-approximation.md:52-56` tells the agent to compile manually and pass
`./agent-approximations/classes`, even though the CLI auto-compiles `.java` files. This is
not wrong (CLI accepts `.class` directories unchanged) but contradicts the design's
"one command" story and makes it awkward for agents that wrote only `.java` sources in
`src/`.

---

## 4. Summary of concrete fixes required

| # | Severity | File(s) | Change |
|---|---|---|---|
| 1 | BLOCKER | `cli/cmd/command_builder.go` | Replace `--external-methods-output <path>` with boolean `--track-external-methods` **or** add the corresponding option to `ProjectAnalyzerRunner.kt`. Align `ExternalMethodsOutput` semantics with whichever direction is chosen. |
| 2 | BLOCKER | `agent-mode/design/agent-mode-design.md`, `agent/skills/create-rule.md:15` | Use `opentaint agent rules-path`, `opentaint agent test-rules`, `opentaint agent init-test-project` consistently. |
| 3 | MAJOR | design doc, `agent/skills/run-analysis.md`, `agent/skills/create-rule.md`, `agent/skills/create-approximation.md`, `agent/skills/debug-rule-reachability.md`, `agent/skills/create-yaml-config.md`, `agent/meta-prompt.md` | When passing a pre-compiled model, use `opentaint scan --project-model ./opentaint-project ...`. Never pass `./opentaint-project` or `./opentaint-project/project.yaml` as the positional argument. |
| 4 | MAJOR | design doc | For `opentaint agent test-rules`, pass the **directory** (e.g. `./agent-test-compiled`), not `project.yaml`. |
| 5 | MAJOR | `cli/cmd/scan.go`, `cli/cmd/command_builder.go` | Decide single vs multiple `--approximations-config`; align Go CLI and Kotlin analyzer to one cardinality. |
| 6 | MAJOR | `agent/skills/run-analysis.md`, `agent/meta-prompt.md` | Document that the external-methods YAML files are emitted in the analyzer output directory with fixed names `external-methods-{without,with}-rules.yaml`, and that `--external-methods <path>` has no effect on the output location. |
| 7 | MINOR | `agent-mode/design/agent-mode-design.md` | Replace `--rule-id my-vulnerability` with `--rule-id java/security/my-vuln.yaml:my-vulnerability` everywhere, and remove the "refs are auto-included" claim unless `SemgrepRuleLoader` is updated to implement it. |
| 8 | MINOR | `agent/meta-prompt.md`, `agent/skills/create-approximation.md` | Standardise on `agent-approximations/src/` (auto-compile) and remove `classes/` references or vice versa. |
| 9 | MINOR | `core/bin/main/org/opentaint/jvm/sast/runner/ProjectAnalyzerRunner.kt` | Rebuild — the binary copy is out of sync with `core/src` on several option declarations (see diff in §1.1, §1.3). |
