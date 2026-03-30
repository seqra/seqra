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

### 1.2 Allow `--config` + `--semgrep-rule-set` Together

**Problem**: These flags are mutually exclusive (`check(options.customConfig == null)` in `ProjectAnalyzer.preloadRules()`). The agent needs both:
- `--semgrep-rule-set` for pattern rules (sources, sinks, vulnerability patterns)
- `--config` for YAML propagation rules (passThrough, cleaner, custom sources/sinks)

**Required change**: When both are provided, load Semgrep rules as the pattern-matching layer and use the custom config to **override** the default propagation config.

**Implementation**: In `ProjectAnalyzer.preloadRules()`, add a fourth branch:

```kotlin
if (options.semgrepRuleSet.isNotEmpty() && options.customConfig != null) {
    val semgrepRules = loadSemgrepRules(...)
    val customConfig = loadSerializedTaintConfig(options.customConfig)
    return PreloadedRules.SemgrepRulesWithCustomConfig(semgrepRules, customConfig)
}
```

In `loadTaintConfig()`, the new `SemgrepRulesWithCustomConfig` case should:
1. Load default pass-through rules into a `TaintConfiguration`
2. Load the custom config into another `TaintConfiguration`
3. Merge via `JIRCombinedTaintRulesProvider(defaultRules, customRules)` with **OVERRIDE** mode for all categories

The agent's custom config intentionally overrides the default config — when the agent provides rules for a method, it means the agent has determined the correct behavior and the default should be replaced, not merged. Using EXTEND would mix the agent's corrections with the (possibly wrong) defaults, defeating the purpose.

**Go CLI**: Expose `--config <path>` flag on the `scan` command, proxy to `--config` on the Kotlin CLI. Remove the mutual exclusion.

### 1.3 Custom Code-Based Approximations via CLI

**Problem**: There is no way to pass custom approximation source code via CLI. The agent needs to provide code-based approximations for complex methods (lambdas, async, callbacks).

**Required change**: The `--approximations <dir>` flag on `scan` accepts a directory of Java source files. The CLI automatically compiles them during scan and passes the resulting `.class` files to the analyzer.

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
    // Custom approximations loaded AFTER built-in, so they override
    result += options.customApproximationPaths.map { it.toFile() }
    return result
}
```

No changes needed to `installApproximations()` or `createCpWithApproximations()` — they already consume whatever `approximationFiles()` returns. The `Approximations` feature indexes `@Approximate` annotations from all paths uniformly.

Note: If a custom approximation targets the same class as a built-in one, the `ApproximationIndexer`'s bijection `require()` assertions need to be relaxed to allow replacement (custom wins, with a warning log).

**Kotlin CLI flag**: `--approximations <path>` (repeatable, accepts directories of compiled `.class` files)
**Go CLI flag**: `--approximations <dir>` on `scan`, accepts source directory, compiles automatically (see 1.4)

### 1.4 Automatic Approximation Compilation During Scan

**Problem**: The agent writes Java source files for approximations. These need to be compiled to `.class` files before the analyzer can use them. This should be seamless.

**Design**: The Go CLI's `--approximations <dir>` flag:

1. Scans the directory for `.java` files
2. If `.java` files are found, compiles them automatically:
   - Resolves `opentaint-analyzer.jar` (same tier resolution as `scan`)
   - Resolves `javac` from managed JRE
   - Resolves additional classpath from the target project's dependencies (from `project.yaml`)
   - Runs: `javac -source 8 -target 8 -cp <analyzer.jar>:<project-deps> -d <temp-output-dir> <sources>`
3. If compilation fails, reports errors to the agent and aborts scan
4. If compilation succeeds, passes the compiled `.class` directory to the analyzer via `--approximations`
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

---

## 2. Go CLI API Design

All agent operations flow through the Go CLI (`opentaint`). The design adds 1 new command and 3 new flags to existing commands.

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
  [--config <path>]               ★ YAML propagation config (overrides defaults)
  [--approximations <dir>]        ★ approximation source/class dir (auto-compiles .java)
  [--external-methods <path>]     ★ output external methods list
  [--timeout <duration>] \
  [--max-memory <size>] \
  [--severity <levels>] \
  [--code-flow-limit <n>]
```

Flag interactions:
- `--ruleset` and `--config` can be used together (engine change 1.2)
- `--approximations` accepts `.java` source dir (auto-compiled) or `.class` dir (passed directly)
- `--external-methods` requires an output path; produces the YAML file alongside SARIF

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
func (b *AnalyzerBuilder) SetConfig(configPath string) *AnalyzerBuilder
func (b *AnalyzerBuilder) AddApproximations(approxPath string) *AnalyzerBuilder
func (b *AnalyzerBuilder) SetExternalMethodsOutput(path string) *AnalyzerBuilder
func (b *AnalyzerBuilder) SetDebugRunRuleTests(enabled bool) *AnalyzerBuilder
```

These translate to:
| Go CLI flag | Analyzer CLI flag |
|---|---|
| `--config <path>` | `--config <path>` |
| `--approximations <path>` | `--approximations <path>` (compiled classes dir) |
| `--external-methods <path>` | `--external-methods-output <path>` |
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

2. Check if existing library rules cover the source/sink:
   - Sources: `rules/ruleset/java/lib/generic/` and `rules/ruleset/java/lib/spring/`
   - Sinks: Same directories
   - If covered, skip to step 4 (join-mode composition)

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

   You can also reference built-in library rules:
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

**Constraints**:
- Rule IDs must be globally unique
- Library rules must have `options.lib: true` and `severity: NOTE`
- Security rules must have `metadata.cwe` and `metadata.short-description`
- Source/sink metavariable names must match across `refs` + `on` clauses (convention: `$UNTRUSTED`)
- The `rule:` path in `refs` is relative to the ruleset root; when using `--ruleset`, the root is the ruleset directory

### 3.4 Skill: `test-rule`

**Purpose**: Create test samples for a rule and verify it works correctly.

**Instructions**:

1. Create a test project directory with this structure:
   ```
   agent-test-project/
   ├── build.gradle.kts
   └── src/main/java/
       └── test/
           └── MyVulnTest.java
   ```

2. Create `build.gradle.kts`:
   ```kotlin
   plugins {
       java
   }

   java {
       sourceCompatibility = JavaVersion.VERSION_1_8
       targetCompatibility = JavaVersion.VERSION_1_8
   }

   repositories {
       mavenCentral()
   }

   dependencies {
       // The test annotation library — extract from opentaint-analyzer.jar
       // or reference the published artifact
       compileOnly(files("libs/opentaint-sast-test-util.jar"))

       // Dependencies needed by your test samples
       compileOnly("javax.servlet:javax.servlet-api:4.0.1")
       // Add more as needed for your test code
   }
   ```

   Note: The `opentaint-sast-test-util.jar` containing `@PositiveRuleSample` and `@NegativeRuleSample` annotations is bundled inside `opentaint-analyzer.jar`. Extract it or use the published Maven artifact `org.opentaint:opentaint-sast-test-util`.

3. Create test samples (`src/main/java/test/MyVulnTest.java`):
   ```java
   package test;

   import org.opentaint.sast.test.util.PositiveRuleSample;
   import org.opentaint.sast.test.util.NegativeRuleSample;
   import javax.servlet.http.HttpServletRequest;
   import javax.servlet.http.HttpServletResponse;
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

4. Build the test project:
   ```bash
   opentaint compile ./agent-test-project -o ./agent-test-compiled
   ```

5. Run rule tests:
   ```bash
   opentaint test-rules ./agent-test-compiled/project.yaml \
     --ruleset ./agent-rules \
     -o ./test-output
   ```

6. Check results in `./test-output/test-result.json`:
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

7. If tests fail:
   - `falseNegative` (positive sample didn't trigger): Rule patterns too narrow, or missing source/sink patterns
   - `falsePositive` (negative sample triggered): Rule patterns too broad, need `pattern-not` or sanitizer exclusion
   - `skipped` (rule not found): Check that `value` path and `id` in annotations match the rule file

8. Fix the rule or test samples and repeat from step 4.

### 3.5 Skill: `run-analysis`

**Purpose**: Run OpenTaint analysis on the target project and collect results.

**Instructions**:

1. Run analysis with pattern rules and optional YAML config:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin \
     --ruleset ./agent-rules \
     --external-methods ./results/external-methods.yaml \
     --config ./agent-config/custom-propagators.yaml \
     --timeout 900s \
     --severity warning,error
   ```

   If you have approximation source files:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin \
     --ruleset ./agent-rules \
     --external-methods ./results/external-methods.yaml \
     --config ./agent-config/custom-propagators.yaml \
     --approximations ./agent-approximations/src \
     --timeout 900s
   ```

   The `--approximations` flag accepts a directory. If it contains `.java` files, the CLI auto-compiles them using `opentaint-analyzer.jar` as the classpath (which contains `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext`) plus the target project's dependencies. Compilation errors are reported before analysis starts.

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
   - A sanitizer is not recognized
   - The source pattern matches non-attacker-controlled data
   - Action: Add `pattern-not`, `pattern-not-inside`, `pattern-sanitizers`, or narrow `metavariable-regex`. Update tests. Re-run.

   **FALSE POSITIVE (FP) — fixable via Approximation** (non-preferred): The trace is invalid due to imprecise taint propagation modeling.
   - A library method is modeled as propagating taint when it actually sanitizes or transforms data in a safe way
   - Action: Override the approximation or add a cleaner rule. Re-run.

3. **For external methods list** (FN discovery):

   Focus on the `withoutRules` section first — these methods have no propagation model at all. Classify each:

   **PROPAGATOR**: The method passes taint from input to output.
   - Example: `DataWrapper#getValue()` — taint on `this` flows to `result`
   - Action: Create a `passThrough` YAML rule

   **TRANSFORMER with lambdas**: The method invokes callbacks/lambdas.
   - Example: `ReactiveStream#map(Function)` — taint flows through the function
   - Action: Create a code-based approximation

   **SANITIZER**: The method sanitizes taint.
   - Example: `HtmlEncoder#encode(String)` — result is safe
   - Action: Create a `cleaner` YAML rule

   **NEUTRAL**: The method is irrelevant to taint flow (logging, metrics, etc.)
   - Action: Skip — the default call-to-return passthrough is correct

   The `withRules` section can be reviewed if specific traces look suspicious (existing rules may be incorrect or incomplete).

### 3.7 Skill: `create-yaml-config`

**Purpose**: Create YAML propagation rules (passThrough, cleaner) for library methods.

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

   **Cleaner rule** (sanitizer):
   ```yaml
   cleaner:
     - function: com.example.security.Sanitizer#sanitize
       cleans:
         - pos: result
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
     --config ./agent-config/custom-propagators.yaml
   ```

**Constraints**:
- The `function` field format is `package.Class#method` (simple) or `{package, class, name}` (complex with patterns)
- Position values: `this`, `result`, `arg(0)`, `arg(1)`, ..., `arg(*)`, `any(classifier)`
- Position modifiers (YAML list): `.[*]` (array element), `.ClassName#fieldName#fieldType` (field access), `.<rule-storage>` (synthetic internal state)
- `overrides: true` (default) means the rule applies to subclasses too
- Custom config rules **override** the default config when passed via `--config`

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

2. Use with analysis — compilation is automatic:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
     --approximations ./agent-approximations/src
   ```

   The `--approximations` flag detects `.java` files and auto-compiles them using:
   - `opentaint-analyzer.jar` as classpath (contains `@Approximate`, `OpentaintNdUtil`, `ArgumentTypeContext`)
   - Target project's dependencies from `project.yaml` (so `javac` can resolve the library being approximated)

   If compilation fails, errors are reported before analysis starts.

**When to use code-based approximations vs YAML config**:
- Lambda/callback invocation → code-based (YAML cannot model lambda calls)
- Non-deterministic branching (async paths) → code-based (`OpentaintNdUtil.nextBool()`)
- Complex internal state with multiple method interactions → code-based (more expressive)
- Simple from→to propagation → YAML passThrough (simpler, faster to write)
- Sanitizer/cleaner → YAML cleaner (no need for code)

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
- Generate YAML propagation config (passThrough, cleaner) for library methods
- Generate code-based approximations (Java stubs) for complex methods with lambdas/callbacks
- Test rules against sample code
- Run analysis and interpret results
- Override any existing approximation or propagation rule

You cannot:
- Modify framework support (Spring detection is automatic)
- Change the analysis algorithm itself

## Available Skills

Load these skills as needed during your workflow:
- `build-project` — Build and prepare the target project
- `discover-entry-points` — Analyze source code to find entry points and attack surface
- `create-rule` — Create pattern rules for vulnerability detection
- `test-rule` — Test rules with annotated samples
- `run-analysis` — Run OpenTaint and collect results
- `analyze-findings` — Interpret SARIF findings and external methods list
- `create-yaml-config` — Create YAML propagation/cleaner rules
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

   a. Load `create-rule` skill. Check if built-in rules cover it (`--ruleset builtin`).

   b. If custom rules are needed, create them in `./agent-rules/`:
      - Library rules in `./agent-rules/java/lib/`
      - Security rules in `./agent-rules/java/security/`

   c. Load `test-rule` skill. Create test samples and verify:
      - Create `./agent-test-project/` with annotated test methods
      - Both `@PositiveRuleSample` (must fire) and `@NegativeRuleSample` (must not fire)
      - Run: `opentaint test-rules ./agent-test-compiled/project.yaml --ruleset ./agent-rules -o ./test-output`
      - Fix until `test-result.json` shows zero failures

### Phase 3: Analysis Loop

5. Load `run-analysis` skill. Run initial analysis:
   ```bash
   opentaint scan ./opentaint-project/project.yaml \
     -o ./results/report.sarif \
     --ruleset builtin --ruleset ./agent-rules \
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
   - Load `create-yaml-config` skill or `create-approximation` skill
   - Override the imprecise approximation
   - Goto step 5

7. For each entry in `external-methods.yaml` (focus on `withoutRules` section):

   Classify the method (propagator / transformer / sanitizer / neutral):

   **If PROPAGATOR** (simple taint flow):
   - Load `create-yaml-config` skill
   - Create passThrough rule
   - Goto step 5

   **If TRANSFORMER** (involves lambdas/callbacks):
   - Load `create-approximation` skill
   - Create approximation source file in `./agent-approximations/src/`
   - Goto step 5

   **If SANITIZER**:
   - Load `create-yaml-config` skill
   - Create cleaner rule
   - Goto step 5

   **If NEUTRAL** (logging, metrics, irrelevant):
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
    - `./agent-config/` — custom YAML propagation rules (if any)
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
├── agent-config/                  # Agent-created YAML propagation config
│   └── custom-propagators.yaml
├── agent-approximations/          # Agent-created code-based approximations
│   └── src/                       # Java source files (auto-compiled by CLI)
├── agent-test-project/            # Test project for rule validation
│   ├── build.gradle.kts
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
2. Approximation override (non-preferred, affects all rules globally)

## Iteration Strategy

- Process findings batch by batch (don't try to fix everything at once)
- After each batch of fixes, re-run analysis and check for regressions
- Group external methods by library/package for efficient batch processing
- Stop when the external methods list stabilizes (no new entries between iterations)
  and all SARIF findings are classified
```

---

## Appendix A: Sample Test Project Template

A minimal test project the agent can copy and adapt for testing custom rules.

### Directory Structure
```
agent-test-project/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    └── main/
        └── java/
            └── test/
                └── SampleTest.java
```

### `build.gradle.kts`
```kotlin
plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

repositories {
    mavenCentral()
}

dependencies {
    // Test annotations — bundled in opentaint-analyzer.jar
    // Extract opentaint-sast-test-util classes or use published artifact
    compileOnly(files("libs/opentaint-sast-test-util.jar"))

    // Common dependencies for test samples
    compileOnly("javax.servlet:javax.servlet-api:4.0.1")
    compileOnly("org.springframework:spring-web:6.1.0")
    compileOnly("org.springframework:spring-webmvc:6.1.0")
}
```

### `settings.gradle.kts`
```kotlin
rootProject.name = "agent-test-project"
```

### `src/main/java/test/SampleTest.java`
```java
package test;

import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;

public class SampleTest {

    // This method MUST trigger the rule (positive = vulnerability exists)
    @PositiveRuleSample(value = "java/security/my-rule.yaml", id = "my-rule-id")
    public void vulnerableMethod() {
        // Write code that demonstrates the vulnerability pattern
    }

    // This method MUST NOT trigger the rule (negative = safe code)
    @NegativeRuleSample(value = "java/security/my-rule.yaml", id = "my-rule-id")
    public void safeMethod() {
        // Write code that is safe (sanitized, parameterized, etc.)
    }
}
```

### Build & Test Commands
```bash
# 1. Build the test project
opentaint compile ./agent-test-project -o ./agent-test-compiled

# 2. Run rule tests
opentaint test-rules ./agent-test-compiled/project.yaml \
  --ruleset ./agent-rules \
  -o ./test-output

# 3. Check results
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
