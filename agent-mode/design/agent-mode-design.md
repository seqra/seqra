# Agent Mode Design

## Table of Contents

1. [Required Engine Changes](#1-required-engine-changes)
2. [Go CLI API Design](#2-go-cli-api-design)
3. [Agent Skills](#3-agent-skills)
4. [Meta Prompt](#4-meta-prompt)

---

## 1. Required Engine Changes

### 1.1 External Methods List Output

**Problem**: The engine currently performs call-to-return passthrough for unresolved external methods — taint is silently preserved. There is no reporting of which external methods were encountered, making it impossible for the agent to know where taint propagation models are missing.

**Current behavior** (in `JIRMethodCallFlowFunction.applyPassRulesOrCallSkip()`):
1. Taint fact arrives at a call to an unresolved method
2. `unresolvedCallDefaultFactPropagation()` copies the fact unchanged to the return site
3. If YAML pass-through rules exist for the method, those are also applied
4. No record is kept of this event

**Required change**: Collect external method call information during analysis and output it as a YAML file.

**Collection architecture**: Follow the `TaintSinkTracker` / `TaintAnalysisUnitStorage` pattern:

```
ExternalMethodTracker                      (like TaintSinkTracker)
  └── backed by per-unit storage in TaintAnalysisUnitStorage
        └── ConcurrentLinkedQueue<ExternalMethodRecord>

Wiring:
  TaintAnalysisContext                     (already carries TaintSinkTracker)
    └── + val externalMethodTracker: ExternalMethodTracker

  TaintAnalysisUnitRunnerManager
    └── spawnNewRunner()
          ├── creates ExternalMethodTracker(storage) per unit
          └── passes it into TaintAnalysisContext
    └── getExternalMethods()              (aggregates across all units, like getVulnerabilities())
```

**Collection point**: `JIRMethodCallFlowFunction.applyPassRulesOrCallSkip()` — this is called for every taint fact that encounters an unresolved method. At this point we know:
- The called method (class, name, signature)
- The taint fact position that was passthrough-ed (the `factReader`/`factAp` tells us `this`, `arg(N)`, etc.)
- Whether YAML pass-through rules were found for this method

The tracker records each encounter. Deduplication (by method identity) and aggregation (merging fact positions, counting call sites) happen at collection time via `ConcurrentHashMap`, same pattern as `TaintSinkTracker`'s `reportedVulnerabilities`.

**Output format** (`external-methods.yaml`):

Two separate lists — methods without rules (agent's priority list) and methods with rules (already modeled, for review):

```yaml
withoutRules:
  - method: com.example.lib.DataWrapper#getValue
    signature: "() java.lang.String"
    factPositions:
      - this
    callSites: 5

  - method: com.example.lib.Processor#transform
    signature: "(java.lang.Object) java.lang.Object"
    factPositions:
      - arg(0)
      - this
    callSites: 12

withRules:
  - method: java.lang.StringBuilder#append
    signature: "(java.lang.String) java.lang.StringBuilder"
    factPositions:
      - arg(0)
    callSites: 87
```

Fields:
- `method`: Fully qualified `Class#method` (class and method name are derivable from this, no need to store separately)
- `signature`: JVM-style `(paramTypes) returnType`
- `factPositions`: Deduplicated list of taint positions that were passthrough-ed at this method
- `callSites`: Number of distinct call sites where this method was encountered with taint

The split into `withoutRules` / `withRules` reduces the agent's effort — it can focus on `withoutRules` first (methods with no propagation model at all), and only review `withRules` if specific traces look suspicious.

**Kotlin CLI flag**: `--external-methods-output <path>` (optional, new flag on `ProjectAnalyzerRunner`)
**Go CLI flag**: `--external-methods <path>` (optional, new flag on `scan` command, proxied to Kotlin CLI)

### 1.2 Allow `--approximations-config` + `--semgrep-rule-set` Together

**Problem**: `--config` and `--semgrep-rule-set` are mutually exclusive (`check(options.customConfig == null)` in `ProjectAnalyzer.preloadRules()`). The agent needs both:
- `--semgrep-rule-set` for pattern rules (sources, sinks, vulnerability patterns)
- `--approximations-config` for YAML propagation rules (passThrough)

**Required change**: Rename the existing `--config` flag to `--approximations-config` to clarify its purpose. When both `--approximations-config` and `--semgrep-rule-set` are provided, load Semgrep rules as the pattern-matching layer and use the custom config to **override** the default propagation config.

**Implementation**: In `ProjectAnalyzer.preloadRules()`, add a fourth branch:

```kotlin
if (options.semgrepRuleSet.isNotEmpty() && options.approximationsConfig != null) {
    val semgrepRules = loadSemgrepRules(...)
    val customConfig = loadSerializedTaintConfig(options.approximationsConfig)
    return PreloadedRules.SemgrepRulesWithCustomConfig(semgrepRules, customConfig)
}
```

In `loadTaintConfig()`, the new `SemgrepRulesWithCustomConfig` case should:
1. Load default pass-through rules into a `TaintConfiguration`
2. Load the custom config into another `TaintConfiguration`
3. Merge via `JIRCombinedTaintRulesProvider(defaultRules, customRules)` with **OVERRIDE** mode for all categories

The agent's custom config intentionally overrides the default config — when the agent provides rules for a method, it means the agent has determined the correct behavior and the default should be replaced, not merged. Using EXTEND would mix the agent's corrections with the (possibly wrong) defaults, defeating the purpose.

**Note**: Despite the YAML config schema supporting a `cleaner` section, the analyzer currently cannot use sanitizers from the config. The `--approximations-config` is used exclusively for `passThrough` rules.

**Kotlin CLI**: Rename `--config` to `--approximations-config`.
**Go CLI**: Expose `--approximations-config <path>` flag on the `scan` command.

### 1.3 Custom Code-Based Approximations via CLI

**Problem**: There is no way to pass custom approximation source code via CLI. The agent needs to provide code-based approximations for complex methods (lambdas, async, callbacks).

**Required change**: The `--dataflow-approximations <dir>` flag on `scan` accepts a directory of Java source files. The CLI automatically compiles them during scan and passes the resulting `.class` files to the analyzer.

**Design**: Custom approximations are **dataflow approximations** — they go through the same `useDataflowApproximation` path as the built-in ones (Stream, CompletableFuture, etc.), not through the separate `useOpentaintApproximations` / environment variable mechanism.

**Implementation in `DataFlowApproximationLoader`**:

1. Add `customApproximationPaths: List<Path> = emptyList()` to `Options`
2. In `approximationFiles()`, append custom paths **after** built-in ones:

```kotlin
private fun approximationFiles(options: Options): List<File> {
    val result = mutableListOf<File>()
    if (options.useDataflowApproximation) {
        result += listOfNotNull(dataflowApproximationsPath?.toFile())
    }
    result += options.customApproximationPaths.map { it.toFile() }
    return result
}
```

No changes needed to `installApproximations()` or `createCpWithApproximations()` — they already consume whatever `approximationFiles()` returns. The `Approximations` feature indexes `@Approximate` annotations from all paths uniformly.

**Conflict behavior**: If a custom approximation targets the same class as a built-in one, the `ApproximationIndexer`'s bijection `require()` assertions will fire and **report an error**. This is intentional — the agent must not silently override built-in approximations. If the agent needs different behavior for a class that already has a built-in approximation, this indicates a design problem that should be escalated, not silently resolved.

**Kotlin CLI flag**: `--dataflow-approximations <path>` (repeatable, accepts directories of compiled `.class` files)
**Go CLI flag**: `--dataflow-approximations <dir>` on `scan`, accepts source directory, compiles automatically (see 1.4)

### 1.4 Automatic Approximation Compilation During Scan

**Problem**: The agent writes Java source files for approximations. These need to be compiled to `.class` files before the analyzer can use them. This should be seamless.

**Design**: The Go CLI's `--dataflow-approximations <dir>` flag:

1. Scans the directory for `.java` files
2. If `.java` files are found, compiles them automatically:
   - Resolves `opentaint-analyzer.jar` (same tier resolution as `scan`)
   - Resolves `javac` from managed JRE
   - Resolves additional classpath from the target project's dependencies (from `project.yaml`)
   - Runs: `javac -source 8 -target 8 -cp <analyzer.jar>:<project-deps> -d <temp-output-dir> <sources>`
3. If compilation fails, reports errors to the agent and aborts scan
4. If compilation succeeds, passes the compiled `.class` directory to the analyzer via `--dataflow-approximations`
5. If only `.class` files are found (no `.java`), passes them directly (pre-compiled)

**Why this is better than a separate command**: The agent writes source → runs scan → gets results. One command. No intermediate compile step to manage. If compilation fails, the error is reported in the context of the scan attempt.

**Error reporting**: The CLI captures `javac` stderr and presents compilation errors clearly:
```
Approximation compilation failed:
  agent-approximations/src/ReactiveProcessor.java:12: error: cannot find symbol
    com.example.lib.ReactiveProcessor self = ...
                                     ^
  symbol: class ReactiveProcessor
  
Hint: Ensure the library being approximated is in the project's dependencies.
```

### 1.5 Rule Test Command via Go CLI

**Problem**: Running rule tests currently requires invoking the Kotlin analyzer JAR directly with `--debug-run-rule-tests`. The Go CLI doesn't expose this capability.

**Required change**: Add a `test-rules` command to the Go CLI.

**Go CLI**:
```
opentaint test-rules <test-project-path-or-project.yaml> \
  --ruleset <path>                 # required, rule files to test
  --output <dir>                   # output directory for test-result.json
```

**Behavior**:
1. If input is a project directory (not project.yaml), auto-compile via autobuilder
2. Invoke analyzer JAR with `--debug-run-rule-tests --semgrep-rule-set <path>`
3. Parse and display `test-result.json` summary
4. Exit with non-zero code if any `falsePositive` or `falseNegative` entries exist

### 1.6 Rule ID Filter

**Problem**: The agent creates its own rules and may reference built-in library rules. When running analysis, the agent wants to execute **only its rules** (plus the referenced built-in library rules they depend on), without all other built-in security rules firing and producing noise.

**Current state**: The `--semgrep-rule-severity` flag filters rules by severity. There is no way to filter by rule ID. When `--ruleset builtin --ruleset ./agent-rules` is used, ALL rules from both rulesets are active.

**Required change**: Add a `--semgrep-rule-id` filter flag (repeatable) that restricts which rules are active. Only rules matching the provided IDs will execute. Library rules (`options.lib: true`) referenced by active rules via `refs` are automatically included — they don't need to be listed explicitly.

**Kotlin CLI flag**: `--semgrep-rule-id <id>` (repeatable)
**Go CLI flag**: `--rule-id <id>` (repeatable, on `scan` command)

**Example**:
```bash
# Only run the agent's custom rule (which refs built-in library rules)
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules \
  --rule-id my-vulnerability
```

**Implementation**: In `SemgrepRuleLoader`, after loading all rules, apply the ID filter:
1. Collect the set of active rule IDs from `--semgrep-rule-id` flags
2. If the set is non-empty, filter `rulesWithMeta` to keep only rules whose ID is in the set
3. For join-mode rules in the active set, recursively resolve `refs` and include all referenced library rules
4. All other rules are excluded (not loaded into the analyzer)

If `--semgrep-rule-id` is not provided, all loaded rules are active (current behavior preserved).

### 1.7 Builtin Rules Path Command

**Problem**: The agent needs to read built-in rules (to understand existing sources/sinks/patterns, to reference them via `refs`, and to decide whether custom rules are needed). Rules are a separate artifact (`opentaint-rules.tar.gz`) resolved via a 3-tier path system (bundled > install > cache) and downloaded lazily. The agent has no way to discover where the rules directory is on disk.

**Required change**: Add a `rules-path` command to the Go CLI that prints the resolved filesystem path to the built-in rules directory, downloading the rules if not already present.

**Go CLI**:
```
opentaint rules-path
```

**Behavior**:
1. Resolves the rules path using the same 3-tier logic as `scan --ruleset builtin`
2. If rules are not present on disk, downloads `opentaint-rules.tar.gz` from GitHub Releases and extracts
3. Prints the absolute path to stdout (e.g., `/home/user/.opentaint/install/lib/rules`)
4. Exit code 0 on success

**Usage by the agent**:
```bash
# Get the rules path
RULES_DIR=$(opentaint rules-path)

# Read builtin rules to understand available sources/sinks
ls $RULES_DIR/java/lib/generic/
cat $RULES_DIR/java/lib/generic/servlet-untrusted-data-source.yaml

# Read builtin security rules to check coverage
ls $RULES_DIR/java/security/
```

**Implementation**: New command in `cli/cmd/rules_path.go`. Reuses `utils.GetRulesPath()` and the existing download logic from `scan.go:214-224`.

### 1.8 Test Project Bootstrap Command

**Problem**: Creating a test project for rule testing requires setting up a Gradle project with the correct `opentaint-sast-test-util` dependency. The agent needs to know how to obtain this JAR and wire it into the build script. This is error-prone.

**Required change**: Add an `init-test-project` command to the Go CLI that bootstraps a ready-to-use test project.

**Go CLI**:
```
opentaint init-test-project <output-dir> \
  [--dependency <maven-coord>] ...    # additional maven dependencies for test code
```

**Behavior**:
1. Creates the directory structure:
   ```
   <output-dir>/
   ├── build.gradle.kts
   ├── settings.gradle.kts
   ├── libs/
   │   └── opentaint-sast-test-util.jar
   └── src/main/java/test/
       └── .gitkeep
   ```
2. Downloads `opentaint-sast-test-util.jar` from the same artifact source as the analyzer (GitHub releases, tiered resolution: bundled > install > cache). Alternatively, extracts it from the `opentaint-analyzer.jar` if bundled inside.
3. Generates `build.gradle.kts` referencing the local JAR:
   ```kotlin
   plugins { java }
   java {
       sourceCompatibility = JavaVersion.VERSION_1_8
       targetCompatibility = JavaVersion.VERSION_1_8
   }
   repositories { mavenCentral() }
   dependencies {
       compileOnly(files("libs/opentaint-sast-test-util.jar"))
       // User-requested dependencies:
       compileOnly("javax.servlet:javax.servlet-api:4.0.1")
   }
   ```
4. Generates `settings.gradle.kts` with a project name derived from the directory.
5. Prints next steps:
   ```
   Test project created at ./agent-test-project
   
   Next steps:
     1. Add test samples in src/main/java/test/
     2. Build: opentaint compile ./agent-test-project -o ./agent-test-compiled
     3. Test: opentaint test-rules ./agent-test-compiled/project.yaml --ruleset <rules> -o ./test-output
   ```

---

## 2. Go CLI API Design

All agent operations flow through the Go CLI (`opentaint`). The design adds 4 new commands and 4 new flags to existing commands.

### 2.1 Complete Command Reference (Existing + New)

#### `opentaint compile` (existing)
Build project and create project model.
```
opentaint compile <project-path> -o <output-dir> [--dry-run]
```

#### `opentaint project` (existing)
Create project model from precompiled artifacts.
```
opentaint project \
  --output <dir> \
  --source-root <dir> \
  --classpath <path> ...  \
  --package <pkg> ... \
  [--dependency <jar> ...]
```

#### `opentaint scan` (existing, extended)
Run analysis. **New flags** marked with ★.
```
opentaint scan <project-path-or-project.yaml> \
  -o <report.sarif> \
  [--ruleset builtin] \
  [--ruleset <path>] \
  [--rule-id <id>]                       ★ filter: only run these rule IDs
  [--approximations-config <path>]       ★ YAML passThrough config (overrides defaults)
  [--dataflow-approximations <dir>]      ★ approximation source/class dir (auto-compiles .java)
  [--external-methods <path>]            ★ output external methods list
  [--timeout <duration>] \
  [--max-memory <size>] \
  [--severity <levels>] \
  [--code-flow-limit <n>]
```

Flag interactions:
- `--ruleset` and `--approximations-config` can be used together (engine change 1.2)
- `--dataflow-approximations` accepts `.java` source dir (auto-compiled) or `.class` dir (passed directly)
- `--external-methods` requires an output path; produces the YAML file alongside SARIF
- `--rule-id` restricts which rules are active; referenced library rules are included automatically

#### `opentaint test-rules` ★ NEW
Run rule tests against a test project.
```
opentaint test-rules <test-project-path-or-project.yaml> \
  --ruleset <path> \
  -o <output-dir> \
  [--timeout <duration>] \
  [--max-memory <size>]
```

Output: `<output-dir>/test-result.json` with verdicts per test sample.

Exit codes:
- `0`: All tests pass (only `success` and `disabled` entries)
- `1`: Test failures exist (`falsePositive`, `falseNegative`, or `skipped` entries)

Prints a summary table:
```
Rule Tests Summary:
  ✓ success:        12
  ✗ false positive:  1
  ✗ false negative:  2
  - skipped:         0
  - disabled:        1
```

#### `opentaint rules-path` ★ NEW
Print the resolved filesystem path to built-in rules (downloads if needed).
```
opentaint rules-path
```

Prints absolute path to stdout. The agent uses this to read built-in rule YAML files.

#### `opentaint init-test-project` ★ NEW
Bootstrap a test project for rule testing.
```
opentaint init-test-project <output-dir> \
  [--dependency <maven-coord>] ...
```

Downloads `opentaint-sast-test-util.jar`, generates `build.gradle.kts` and directory structure.

#### `opentaint summary` (existing)
Print SARIF results.
```
opentaint summary <report.sarif> \
  [--show-findings] \
  [--show-code-snippets] \
  [--verbose-flow]
```

### 2.2 Command Builder Changes

The `AnalyzerBuilder` in `command_builder.go` needs new methods for the new flags:

```go
func (b *AnalyzerBuilder) SetApproximationsConfig(configPath string) *AnalyzerBuilder
func (b *AnalyzerBuilder) AddDataflowApproximations(approxPath string) *AnalyzerBuilder
func (b *AnalyzerBuilder) SetExternalMethodsOutput(path string) *AnalyzerBuilder
func (b *AnalyzerBuilder) SetDebugRunRuleTests(enabled bool) *AnalyzerBuilder
func (b *AnalyzerBuilder) AddRuleIdFilter(ruleId string) *AnalyzerBuilder
```

These translate to:
| Go CLI flag | Analyzer CLI flag |
|---|---|
| `--approximations-config <path>` | `--approximations-config <path>` |
| `--dataflow-approximations <path>` | `--dataflow-approximations <path>` (compiled classes dir) |
| `--external-methods <path>` | `--external-methods-output <path>` |
| `--rule-id <id>` | `--semgrep-rule-id <id>` |
| (test-rules command) | `--debug-run-rule-tests` |

---

## 3. Agent Skills

Skills are self-contained instruction sets the agent loads to perform specific operations. Each skill contains: purpose, prerequisites, step-by-step instructions, CLI commands with examples, expected outputs, and error handling.

### 3.1 Skill: `build-project`

**Purpose**: Build a target project and prepare it for analysis.

**Instructions**:

1. Determine the project type by examining the project directory:
   - Look for `build.gradle`, `build.gradle.kts` → Gradle project
   - Look for `pom.xml` → Maven project
   - Look for pre-compiled JARs → classpath mode

2. For Gradle/Maven projects, use the autobuilder:
   ```bash
   opentaint compile /path/to/project -o ./opentaint-project
   ```

3. For pre-compiled artifacts, use the project command:
   ```bash
   opentaint project \
     --output ./opentaint-project \
     --source-root /path/to/src \
     --classpath /path/to/app.jar \
     --package com.example.app
   ```

4. Verify `./opentaint-project/project.yaml` was created.

5. If compilation fails:
   - Check build tool is installed and project builds independently
   - Check Java version compatibility (OpenTaint uses Java 21)
   - Examine the autobuilder log for specific errors
   - Fall back to `opentaint project` with pre-compiled artifacts

**Expected output**: A directory containing `project.yaml` and compiled class files.

### 3.2 Skill: `discover-entry-points`

**Purpose**: Identify entry points and attack surface of the target project by reading source code and analyzing project structure.

**Instructions**:

The agent discovers entry points itself — no special CLI command is needed. The analysis engine automatically selects entry points (all public/protected methods for generic projects, Spring endpoints for Spring projects). The agent's role is to **understand** the attack surface to plan rules effectively.

1. Read the project's source code and identify:
   - **Spring controllers**: Search for `@RestController`, `@Controller` annotations. Read `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` to understand routes and parameters.
   - **Servlet handlers**: Search for classes extending `HttpServlet` with `doGet`, `doPost`, `doPut`, `doDelete` methods.
   - **JAX-RS endpoints**: Search for `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE` annotations.
   - **Message handlers**: Search for `@JmsListener`, `@KafkaListener`, `@RabbitListener` annotations.
   - **CLI entry points**: Find `main(String[])` methods that process external input (command-line args, stdin, files).
   - **Scheduled tasks**: Search for `@Scheduled` methods that read external state (files, DB, network).

2. For each entry point, determine:
   - What external data it receives (HTTP params, headers, body, path variables, message payloads)
   - What operations it performs (DB queries, file I/O, command execution, HTTP responses, serialization)
   - Which vulnerability classes are relevant (SQLi, XSS, command injection, path traversal, SSRF, XXE, etc.)

3. Examine the project's dependencies (from `build.gradle`, `pom.xml`, or `project.yaml`) to understand:
   - Which frameworks are used (Spring, Servlets, JAX-RS, etc.)
   - Which database libraries (JDBC, JPA, MyBatis, etc.)
   - Which template engines (Thymeleaf, JSP, Freemarker)
   - Which HTTP clients (OkHttp, Apache HttpClient, RestTemplate)

4. Record findings in `opentaint-analysis-plan.md`.

**Note**: The engine handles entry point selection automatically during analysis:
- For `--project-kind spring-web`: Uses Spring endpoint discovery (`SpringWebProject.kt`)
- For `--project-kind unknown` (default): Uses all public/protected methods from public project classes
- For targeted analysis: Agent can use `--debug-run-analysis-on-selected-entry-points "com.example.Class#method"` via the Kotlin CLI directly

### 3.3 Skill: `create-rule`

**Purpose**: Create a pattern rule for detecting a specific vulnerability class.

**Instructions**:

1. Determine the rule architecture:
   - **Source**: Where does untrusted data enter? (HTTP params, headers, body, etc.)
   - **Sink**: Where is the data dangerous? (SQL query, command exec, file path, HTML output, etc.)
   - **Sanitizers**: What makes the data safe? (encoding, escaping, parameterized queries, etc.)

2. Read built-in rules to check existing coverage:
   ```bash
   RULES_DIR=$(opentaint rules-path)
   # List available source/sink library rules
   ls $RULES_DIR/java/lib/generic/
   ls $RULES_DIR/java/lib/spring/
   # Read specific rules to understand their patterns and IDs
   cat $RULES_DIR/java/lib/generic/servlet-untrusted-data-source.yaml
   cat $RULES_DIR/java/lib/generic/jdbc-sql-sink.yaml
   # List existing security rules to check what's already covered
   ls $RULES_DIR/java/security/
   ```
   - Sources: `$RULES_DIR/java/lib/generic/` and `$RULES_DIR/java/lib/spring/`
   - Sinks: Same directories
   - If existing rules cover the needed source/sink, skip to step 4 (join-mode composition referencing built-in rules)

3. If new source/sink patterns are needed, create library rules:

   **Source library rule** (`agent-rules/java/lib/my-source.yaml`):
   ```yaml
   rules:
     - id: my-custom-source
       options:
         lib: true
       severity: NOTE
       message: Custom untrusted data source
       languages: [java]
       patterns:
         - pattern-either:
             - patterns:
                 - pattern: |
                     $RETURNTYPE $METHOD(HttpServletRequest $UNTRUSTED, ...) { ... }
                 - metavariable-pattern:
                     metavariable: $METHOD
                     pattern-either:
                       - pattern: doGet
                       - pattern: doPost
   ```

   **Sink library rule** (`agent-rules/java/lib/my-sink.yaml`):
   ```yaml
   rules:
     - id: my-custom-sink
       options:
         lib: true
       severity: NOTE
       message: Custom dangerous operation
       languages: [java]
       mode: taint
       pattern-sinks:
         - patterns:
             - pattern-either:
                 - pattern: (java.sql.Statement $S).executeQuery($UNTRUSTED)
                 - pattern: (java.sql.Statement $S).execute($UNTRUSTED)
             - focus-metavariable: $UNTRUSTED
   ```

4. Create the join-mode security rule (`agent-rules/java/security/my-vuln.yaml`):
   ```yaml
   rules:
     - id: my-vulnerability
       severity: ERROR
       message: >-
         Untrusted data flows to dangerous operation
       metadata:
         cwe: CWE-89
         short-description: SQL Injection via untrusted input
       languages: [java]
       mode: join
       join:
         refs:
           - rule: java/lib/my-source.yaml#my-custom-source
             as: source
           - rule: java/lib/my-sink.yaml#my-custom-sink
             as: sink
         on:
           - 'source.$UNTRUSTED -> sink.$UNTRUSTED'
   ```

   You can reference built-in library rules — they will be auto-included when the agent's rule is active:
   ```yaml
   refs:
     - rule: java/lib/generic/servlet-untrusted-data-source.yaml#java-servlet-untrusted-data-source
       as: servlet-source
     - rule: java/lib/spring/untrusted-data-source.yaml#spring-untrusted-data-source
       as: spring-source
   ```

5. For simple structural patterns (no dataflow), use default mode:
   ```yaml
   rules:
     - id: weak-crypto
       severity: WARNING
       message: Use of weak cryptographic algorithm
       metadata:
         cwe: CWE-327
         short-description: Weak cryptography
       languages: [java]
       patterns:
         - pattern: Cipher.getInstance("DES")
   ```

6. When running analysis, use `--rule-id` to activate only the agent's rules:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --rule-id weak-crypto
   ```

   Library rules referenced via `refs` in join-mode rules are auto-included. No need to list them in `--rule-id`.

**Constraints**:
- Rule IDs must be globally unique
- Library rules must have `options.lib: true` and `severity: NOTE`
- Security rules must have `metadata.cwe` and `metadata.short-description`
- Source/sink metavariable names must match across `refs` + `on` clauses (convention: `$UNTRUSTED`)
- The `rule:` path in `refs` is relative to the ruleset root; when using `--ruleset`, the root is the ruleset directory

### 3.4 Skill: `test-rule`

**Purpose**: Create test samples for a rule and verify it works correctly.

**Instructions**:

1. Bootstrap a test project:
   ```bash
   opentaint init-test-project ./agent-test-project \
     --dependency "javax.servlet:javax.servlet-api:4.0.1"
   ```

   This creates the directory structure with `build.gradle.kts`, `settings.gradle.kts`, and the `opentaint-sast-test-util.jar` in `libs/`. Add more `--dependency` flags for additional libraries your test code needs (e.g., Spring, JDBC drivers).

2. Create test samples in `src/main/java/test/MyVulnTest.java`:
   ```java
   package test;

   import org.opentaint.sast.test.util.PositiveRuleSample;
   import org.opentaint.sast.test.util.NegativeRuleSample;
   import javax.servlet.http.HttpServletRequest;
   import java.sql.Connection;
   import java.sql.Statement;

   public class MyVulnTest {

       private Connection db;

       @PositiveRuleSample(value = "java/security/my-vuln.yaml", id = "my-vulnerability")
       public void vulnerable(HttpServletRequest req) throws Exception {
           String input = req.getParameter("id");
           Statement stmt = db.createStatement();
           stmt.executeQuery("SELECT * FROM users WHERE id = " + input);
       }

       @NegativeRuleSample(value = "java/security/my-vuln.yaml", id = "my-vulnerability")
       public void safe(HttpServletRequest req) throws Exception {
           String input = req.getParameter("id");
           var pstmt = db.prepareStatement("SELECT * FROM users WHERE id = ?");
           pstmt.setString(1, input);
           pstmt.executeQuery();
       }
   }
   ```

   Annotation fields:
   - `value`: Path to the rule YAML file, relative to the ruleset root
   - `id`: The rule ID within that file

3. Build the test project:
   ```bash
   opentaint compile ./agent-test-project -o ./agent-test-compiled
   ```

4. Run rule tests:
   ```bash
   opentaint test-rules ./agent-test-compiled/project.yaml \
     --ruleset ./agent-rules \
     -o ./test-output
   ```

5. Check results in `./test-output/test-result.json`:
   ```json
   {
     "success": [
       {"className": "test.MyVulnTest", "methodName": "vulnerable",
        "rule": {"rulePath": "java/security/my-vuln.yaml", "ruleId": "my-vulnerability"}},
       {"className": "test.MyVulnTest", "methodName": "safe",
        "rule": {"rulePath": "java/security/my-vuln.yaml", "ruleId": "my-vulnerability"}}
     ],
     "falsePositive": [],
     "falseNegative": [],
     "skipped": [],
     "disabled": []
   }
   ```

6. If tests fail:
   - `falseNegative` (positive sample didn't trigger): Rule patterns too narrow, or missing source/sink patterns
   - `falsePositive` (negative sample triggered): Rule patterns too broad, need `pattern-not` or sanitizer exclusion
   - `skipped` (rule not found): Check that `value` path and `id` in annotations match the rule file

7. Fix the rule or test samples and repeat from step 3.

### 3.5 Skill: `run-analysis`

**Purpose**: Run OpenTaint analysis on the target project and collect results.

**Instructions**:

1. Run analysis with the agent's rules:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin \
     --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --external-methods ./results/external-methods.yaml \
     --timeout 900s \
     --severity warning,error
   ```

   If you have custom passThrough config:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --approximations-config ./agent-config/custom-propagators.yaml \
     --external-methods ./results/external-methods.yaml
   ```

   If you have approximation source files:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --approximations-config ./agent-config/custom-propagators.yaml \
     --dataflow-approximations ./agent-approximations/src \
     --external-methods ./results/external-methods.yaml
   ```

   The `--dataflow-approximations` flag accepts a directory. If it contains `.java` files, the CLI auto-compiles them using `opentaint-analyzer.jar` as the classpath (which contains `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext`) plus the target project's dependencies. Compilation errors are reported before analysis starts.

2. View results summary:
   ```bash
   opentaint summary ./results/report.sarif --show-findings --verbose-flow
   ```

3. Collect both outputs for the decision loop:
   - `./results/report.sarif` — vulnerability findings with traces
   - `./results/external-methods.yaml` — split into `withoutRules` (priority) and `withRules` (for review)

### 3.6 Skill: `analyze-findings`

**Purpose**: Interpret SARIF findings and decide on TP/FP/FN actions.

**Instructions**:

For each finding in the SARIF report:

1. **Read the trace** (codeFlows in SARIF):
   - First location = source (where tainted data enters)
   - Last location = sink (where tainted data is used dangerously)
   - Intermediate locations = dataflow path

2. **Classify the finding**:

   **TRUE POSITIVE (TP)**: The trace represents a real vulnerability.
   - The source genuinely provides attacker-controlled data
   - The sink genuinely performs a dangerous operation with that data
   - No sanitization occurs between source and sink
   - Action: Generate a proof-of-concept, document in `vulnerabilities.md`

   **FALSE POSITIVE (FP) — fixable via Rule**: The trace is invalid due to over-broad pattern matching.
   - The sink pattern is too broad (matches safe methods)
   - A sanitizer is not recognized by the pattern
   - The source pattern matches non-attacker-controlled data
   - Action: Add `pattern-not`, `pattern-not-inside`, `pattern-sanitizers`, or narrow `metavariable-regex`. Update tests. Re-run.

   **FALSE POSITIVE (FP) — fixable via Approximation** (non-preferred): The trace is invalid due to imprecise taint propagation modeling.
   - A library method is modeled as propagating taint when it actually transforms data in a way that neutralizes the threat
   - Action: Override the passThrough approximation to remove the incorrect propagation. Re-run.

3. **For external methods list** (FN discovery):

   Focus on the `withoutRules` section first — these methods have no propagation model at all. Classify each:

   **PROPAGATOR**: The method passes taint from input to output.
   - Example: `DataWrapper#getValue()` — taint on `this` flows to `result`
   - Action: Create a `passThrough` YAML rule via `--approximations-config`

   **TRANSFORMER with lambdas**: The method invokes callbacks/lambdas.
   - Example: `ReactiveStream#map(Function)` — taint flows through the function
   - Action: Create a code-based approximation via `--dataflow-approximations`

   **NEUTRAL**: The method is irrelevant to taint flow (logging, metrics, sanitizers, etc.)
   - Action: Skip — the default call-to-return passthrough is correct

   The `withRules` section can be reviewed if specific traces look suspicious (existing rules may be incorrect or incomplete).

### 3.7 Skill: `create-yaml-config`

**Purpose**: Create YAML propagation rules (passThrough) for library methods.

**Instructions**:

1. Create a YAML config file (`agent-config/custom-propagators.yaml`):

   **Simple getter propagation** (taint on `this` → `result`):
   ```yaml
   passThrough:
     - function: com.example.lib.DataWrapper#getValue
       copy:
         - from: this
           to: result
   ```

   **Argument-to-result propagation**:
   ```yaml
   passThrough:
     - function: com.example.lib.Converter#convert
       copy:
         - from: arg(0)
           to: result
   ```

   **Builder pattern** (taint flows through builder chain):
   ```yaml
   passThrough:
     - function: com.example.lib.Builder#withName
       copy:
         - from: arg(0)
           to: this
         - from: arg(0)
           to: result
         - from: this
           to: result
   ```

   **Object with internal state** (using `<rule-storage>`):
   ```yaml
   passThrough:
     # Store taint
     - function: com.example.lib.Container#put
       copy:
         - from: arg(0)
           to:
             - this
             - .com.example.lib.Container#<rule-storage>#java.lang.Object
     # Retrieve taint
     - function: com.example.lib.Container#get
       copy:
         - from:
             - this
             - .com.example.lib.Container#<rule-storage>#java.lang.Object
           to: result
   ```

   **Package-wide getter pattern** (all getters in a package):
   ```yaml
   passThrough:
     - function:
         package: com.example.dto
         class:
           pattern: .*
         name:
           pattern: get.*
       copy:
         - from: this
           to: result
   ```

   **Conditional propagation**:
   ```yaml
   passThrough:
     - function: com.example.lib.Parser#parse
       condition:
         typeIs:
           position: arg(0)
           type: java.lang.String
       copy:
         - from: arg(0)
           to: result
   ```

2. Use with analysis:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --approximations-config ./agent-config/custom-propagators.yaml
   ```

**Constraints**:
- The `function` field format is `package.Class#method` (simple) or `{package, class, name}` (complex with patterns)
- Position values: `this`, `result`, `arg(0)`, `arg(1)`, ..., `arg(*)`, `any(classifier)`
- Position modifiers (YAML list): `.[*]` (array element), `.ClassName#fieldName#fieldType` (field access), `.<rule-storage>` (synthetic internal state)
- `overrides: true` (default) means the rule applies to subclasses too
- Custom config rules **override** the default config when passed via `--approximations-config`
- Only `passThrough` rules are supported; the analyzer cannot use sanitizers from the config

### 3.8 Skill: `create-approximation`

**Purpose**: Create code-based approximations for complex library methods (lambdas, async, callbacks).

**Instructions**:

1. Create a Java source file for the approximation in `agent-approximations/src/`:

   ```java
   package agent.approximations;

   import org.opentaint.ir.approximation.annotation.Approximate;
   // For methods with lambda parameters:
   import org.opentaint.jvm.dataflow.approximations.ArgumentTypeContext;
   // For non-deterministic branching:
   import org.opentaint.jvm.dataflow.approximations.OpentaintNdUtil;

   import java.util.function.Function;

   @Approximate(com.example.lib.ReactiveProcessor.class)
   public class ReactiveProcessor {

       // Model: taint on this flows through the function to the result
       public Object transform(@ArgumentTypeContext Function fn) throws Throwable {
           com.example.lib.ReactiveProcessor self =
               (com.example.lib.ReactiveProcessor) (Object) this;
           if (OpentaintNdUtil.nextBool()) return null;  // async failure path
           Object input = self.getValue();
           return fn.apply(input);
       }

       // Model: taint on this flows to the consumer argument
       public void subscribe(@ArgumentTypeContext java.util.function.Consumer consumer) {
           com.example.lib.ReactiveProcessor self =
               (com.example.lib.ReactiveProcessor) (Object) this;
           if (OpentaintNdUtil.nextBool()) {
               consumer.accept(self.getValue());
           }
       }
   }
   ```

   **Key patterns**:
   - `@Approximate(TargetClass.class)` or `@ApproximateByName("fqn")` on the class
   - `(TargetClass) (Object) this` cast to access the real object's methods
   - `@ArgumentTypeContext` on lambda/functional interface parameters
   - `OpentaintNdUtil.nextBool()` for non-deterministic branching (models both success and failure paths)
   - Java 8 source compatibility
   - One approximation class per target class (strict bijection)
   - Must NOT target a class that already has a built-in approximation (will error)

2. Use with analysis — compilation is automatic:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --dataflow-approximations ./agent-approximations/src
   ```

   The `--dataflow-approximations` flag detects `.java` files and auto-compiles them using:
   - `opentaint-analyzer.jar` as classpath (contains `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext`)
   - Target project's dependencies from `project.yaml` (so `javac` can resolve the library being approximated)

   If compilation fails, errors are reported before analysis starts.
   If a custom approximation targets a class that already has a built-in approximation, the analyzer reports an error and aborts.

**When to use code-based approximations vs YAML config**:
- Lambda/callback invocation → code-based (YAML cannot model lambda calls)
- Non-deterministic branching (async paths) → code-based (`OpentaintNdUtil.nextBool()`)
- Complex internal state with multiple method interactions → code-based (more expressive)
- Simple from→to propagation → YAML passThrough (simpler, faster to write)

### 3.9 Skill: `generate-poc`

**Purpose**: Generate a proof-of-concept exploit for a confirmed true positive vulnerability.

**Instructions**:

1. Extract the vulnerability trace from SARIF:
   - Source: entry point method and parameter (e.g., HTTP request parameter `id`)
   - Path: sequence of method calls through which taint flows
   - Sink: dangerous operation (e.g., SQL query execution)

2. Construct a PoC:
   - For HTTP-based sources: a `curl` command or HTTP request demonstrating the attack
   - For command injection: the payload that achieves command execution
   - For SQL injection: the payload that demonstrates data extraction
   - For path traversal: the payload that reads/writes unauthorized files
   - For XSS: the payload that executes JavaScript in the browser

3. Document in `vulnerabilities.md`:
   ```markdown
   ## VULN-001: SQL Injection in UserController.getUser

   **Severity**: Critical (CWE-89)
   **Location**: `src/main/java/com/example/controller/UserController.java:45`
   **Rule**: `my-vulnerability`

   ### Description
   User-controlled input from HTTP parameter `id` flows unsanitized into
   a SQL query via `Statement.executeQuery()`.

   ### Trace
   1. **Source**: `UserController.getUser()` — `request.getParameter("id")` (line 42)
   2. **Flow**: String concatenation `"SELECT * FROM users WHERE id = " + input` (line 44)
   3. **Sink**: `Statement.executeQuery(query)` (line 45)

   ### Proof of Concept
   ```
   curl "http://target:8080/api/users/1' OR '1'='1"
   ```

   ### Remediation
   Use parameterized queries:
   ```java
   PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
   pstmt.setString(1, input);
   ```
   ```

---

## 4. Meta Prompt

The meta prompt orchestrates the agent through the complete workflow. It references skills and implements the decision loop from task.md steps 1-10.

```
You are a security analysis agent using OpenTaint, a dataflow-based SAST analyzer for JVM projects.
OpenTaint is available on PATH as `opentaint`.

Your goal: Perform comprehensive security analysis of a target project, discovering all vulnerabilities
and minimizing false positives and false negatives.

## Your Capabilities

You can:
- Generate pattern rules (YAML) defining vulnerability patterns (sources, sinks, sanitizers)
- Generate YAML passThrough config for library methods
- Generate code-based approximations (Java stubs) for complex methods with lambdas/callbacks
- Test rules against sample code
- Run analysis and interpret results
- Override existing passThrough rules via --approximations-config

You cannot:
- Modify framework support (Spring detection is automatic)
- Change the analysis algorithm itself
- Add sanitizers via YAML config (sanitizers are handled via pattern rules only)
- Override built-in code-based approximations (will error on conflict)

## Available Skills

Load these skills as needed during your workflow:
- `build-project` — Build and prepare the target project
- `discover-entry-points` — Analyze source code to find entry points and attack surface
- `create-rule` — Create pattern rules for vulnerability detection
- `test-rule` — Test rules with annotated samples
- `run-analysis` — Run OpenTaint and collect results
- `analyze-findings` — Interpret SARIF findings and external methods list
- `create-yaml-config` — Create YAML passThrough rules
- `create-approximation` — Create code-based approximations for complex methods
- `generate-poc` — Generate proof-of-concept for confirmed vulnerabilities

## Workflow

### Phase 1: Project Setup

1. Load `build-project` skill. Build the target project:
   ```bash
   opentaint compile <project-path> -o ./opentaint-project
   ```

2. Load `discover-entry-points` skill. Read source code, analyze project structure, identify:
   - Framework in use (Spring, Servlets, JAX-RS, etc.)
   - Entry points (controllers, servlets, listeners, CLI entry points)
   - Attack surface (what external data enters, what dangerous operations are performed)
   - Relevant vulnerability classes to test

3. Create `opentaint-analysis-plan.md` with:
   - Project description and technology stack
   - Identified entry points and attack surface
   - Relevant vulnerability classes to test
   - Plan for rule creation

### Phase 2: Rule Creation

4. For each relevant vulnerability class (SQLi, XSS, command injection, path traversal, etc.):

   a. Load `create-rule` skill. Read built-in rules to check coverage:
      ```bash
      RULES_DIR=$(opentaint rules-path)
      ls $RULES_DIR/java/security/       # existing security rules
      ls $RULES_DIR/java/lib/generic/    # available source/sink libraries
      ```

   b. Create rules in `./agent-rules/`:
      - Library rules in `./agent-rules/java/lib/`
      - Security rules in `./agent-rules/java/security/`
      - Reference built-in library rules where applicable

   c. Load `test-rule` skill. Bootstrap and test:
      ```bash
      opentaint init-test-project ./agent-test-project --dependency "javax.servlet:javax.servlet-api:4.0.1"
      ```
      - Add `@PositiveRuleSample` and `@NegativeRuleSample` test methods
      - Run: `opentaint test-rules ./agent-test-compiled/project.yaml --ruleset ./agent-rules -o ./test-output`
      - Fix until `test-result.json` shows zero failures

### Phase 3: Analysis Loop

5. Load `run-analysis` skill. Run initial analysis:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --rule-id my-vulnerability \
     --external-methods ./results/external-methods.yaml
   ```

6. Load `analyze-findings` skill. For each SARIF finding:

   **If TRUE POSITIVE**:
   - Load `generate-poc` skill
   - Generate proof-of-concept exploit
   - Document in `vulnerabilities.md`

   **If FALSE POSITIVE (fixable via rule)**:
   - Load `create-rule` skill
   - Add `pattern-not`, `pattern-sanitizers`, or narrow patterns
   - Load `test-rule` skill — add `@NegativeRuleSample` for the FP case
   - Re-run tests, then goto step 5

   **If FALSE POSITIVE (fixable via approximation)** (non-preferred):
   - Load `create-yaml-config` skill
   - Override the passThrough approximation to remove incorrect propagation
   - Goto step 5

7. For each entry in `external-methods.yaml` (focus on `withoutRules` section):

   Classify the method (propagator / transformer / neutral):

   **If PROPAGATOR** (simple taint flow):
   - Load `create-yaml-config` skill
   - Create passThrough rule
   - Goto step 5

   **If TRANSFORMER** (involves lambdas/callbacks):
   - Load `create-approximation` skill
   - Create approximation source file in `./agent-approximations/src/`
   - Goto step 5

   **If NEUTRAL** (logging, metrics, sanitizers, irrelevant):
   - Skip — default passthrough is correct

### Phase 4: Finalization

8. When the agent determines analysis is complete:
   - All traces have been reviewed and classified
   - All identified FP have been fixed
   - All relevant external methods have been addressed
   - Remaining external methods are classified as NEUTRAL

9. Update `opentaint-analysis-plan.md` with final status.

10. Deliver:
    - `vulnerabilities.md` — confirmed vulnerabilities with PoCs
    - `opentaint-analysis-plan.md` — analysis log
    - `./agent-rules/` — custom pattern rules
    - `./agent-config/` — custom YAML passThrough rules (if any)
    - `./agent-approximations/src/` — custom code-based approximation sources (if any)

## Working Directory Layout

```
<project-root>/
├── opentaint-analysis-plan.md     # Analysis progress tracking
├── vulnerabilities.md             # Confirmed vulnerabilities
├── opentaint-project/             # Compiled project model
│   └── project.yaml
├── agent-rules/                   # Agent-created pattern rules
│   └── java/
│       ├── security/              # Executable security rules
│       └── lib/                   # Reusable library rules
├── agent-config/                  # Agent-created YAML passThrough config
│   └── custom-propagators.yaml
├── agent-approximations/          # Agent-created code-based approximations
│   └── src/                       # Java source files (auto-compiled by CLI)
├── agent-test-project/            # Test project (bootstrapped via init-test-project)
│   ├── build.gradle.kts
│   ├── libs/opentaint-sast-test-util.jar
│   └── src/main/java/test/
└── results/                       # Analysis outputs
    ├── report.sarif
    └── external-methods.yaml
```

## Decision Priorities

When fixing FN:
1. YAML passThrough rule (simplest, covers most cases)
2. Code-based approximation (for lambdas/callbacks only)
3. Rule pattern fix (only if FN is due to missing source/sink pattern, not missing propagation)

When fixing FP:
1. Rule fix via `pattern-not` / `pattern-sanitizers` (preferred, scoped to one rule)
2. PassThrough override (non-preferred, affects all rules globally)

## Iteration Strategy

- Process findings batch by batch (don't try to fix everything at once)
- After each batch of fixes, re-run analysis and check for regressions
- Group external methods by library/package for efficient batch processing
- Stop when the external methods list stabilizes (no new entries between iterations)
  and all SARIF findings are classified
```

---

## Appendix A: Sample Test Project Bootstrap

```bash
# Bootstrap test project with servlet API dependency
opentaint init-test-project ./agent-test-project \
  --dependency "javax.servlet:javax.servlet-api:4.0.1"

# Add test samples
cat > ./agent-test-project/src/main/java/test/SampleTest.java << 'EOF'
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;

public class SampleTest {

    @PositiveRuleSample(value = "java/security/my-rule.yaml", id = "my-rule-id")
    public void vulnerableMethod() {
        // Write code that demonstrates the vulnerability pattern
    }

    @NegativeRuleSample(value = "java/security/my-rule.yaml", id = "my-rule-id")
    public void safeMethod() {
        // Write code that is safe (sanitized, parameterized, etc.)
    }
}
EOF

# Build and test
opentaint compile ./agent-test-project -o ./agent-test-compiled
opentaint test-rules ./agent-test-compiled/project.yaml \
  --ruleset ./agent-rules -o ./test-output
cat ./test-output/test-result.json
```

---

## Appendix B: SARIF Output Structure (Quick Reference)

```json
{
  "$schema": "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/sarif-2.1/schema/sarif-schema-2.1.0.json",
  "version": "2.1.0",
  "runs": [{
    "tool": { "driver": { "name": "OpenTaint", "rules": [...] } },
    "results": [{
      "ruleId": "my-vulnerability",
      "level": "error",
      "message": { "text": "Untrusted data flows to SQL query" },
      "locations": [{
        "physicalLocation": {
          "artifactLocation": { "uri": "src/main/java/com/example/UserController.java" },
          "region": { "startLine": 45, "startColumn": 9 }
        }
      }],
      "codeFlows": [{
        "threadFlows": [{
          "locations": [
            { "location": { "physicalLocation": { "region": { "startLine": 42 } }, "message": { "text": "source" } } },
            { "location": { "physicalLocation": { "region": { "startLine": 44 } }, "message": { "text": "flow" } } },
            { "location": { "physicalLocation": { "region": { "startLine": 45 } }, "message": { "text": "sink" } } }
          ]
        }]
      }],
      "relatedLocations": [...]
    }]
  }]
}
```

---

## Appendix C: External Methods Output Structure (Quick Reference)

```yaml
withoutRules:
  - method: com.example.lib.DataWrapper#getValue
    signature: "() java.lang.String"
    factPositions:
      - this
    callSites: 5

  - method: com.example.lib.Processor#transform
    signature: "(java.lang.Object) java.lang.Object"
    factPositions:
      - arg(0)
      - this
    callSites: 12

withRules:
  - method: java.lang.StringBuilder#append
    signature: "(java.lang.String) java.lang.StringBuilder"
    factPositions:
      - arg(0)
    callSites: 87
```
