# Agent Pipeline Design Document

## Overview

This document describes the end-to-end pipeline for an LLM agent to perform security analysis of a JVM project using OpenTaint. The agent builds the project, creates rules, tests them, runs analysis, interprets results (SARIF + external methods list), and iterates to fix FP/FN until coverage is satisfactory.

## Agent Capabilities Summary

| Capability | Artifact Type | Reference |
|-----------|---------------|-----------|
| Generate vulnerability detection rules | Pattern rules (YAML) | `pattern-rules.md` |
| Debug/fix rules (FP/FN) | Pattern rules (YAML) | `pattern-rules.md` |
| Generate taint propagation rules | YAML config rules | `approximations-config.md` |
| Generate complex propagators | Code-based approximations (Java) | `approximations-config.md` |
| Override existing propagation | Either YAML or Java stubs | `approximations-config.md` |
| Framework support | Not configurable | Provided as-is |

## CLI Interfaces

OpenTaint provides two CLI interfaces. The agent uses them at different pipeline stages.

### Go CLI (`opentaint`)

High-level wrapper. Manages Java runtime, downloads artifacts, invokes the analyzer JAR.

| Command | Purpose |
|---------|---------|
| `opentaint compile <project-path> -o <output-dir>` | Build project and create `project.yaml` |
| `opentaint project --output <dir> --source-root <dir> --classpath <jar> --package <pkg>` | Create `project.yaml` from precompiled JARs/classes |
| `opentaint scan <project-path-or-project.yaml> -o <report.sarif> [--ruleset builtin] [--ruleset <path>]` | Run full analysis (optionally compile first) |
| `opentaint summary <report.sarif> [--show-findings] [--show-code-snippets] [--verbose-flow]` | Print SARIF results summary |
| `opentaint pull` | Download all artifacts + JRE |

**Key flags for `opentaint scan`:**
- `--output <path>` — SARIF output file (required)
- `--ruleset builtin` — use built-in rules (default)
- `--ruleset <path>` — custom Semgrep rule file/directory (can specify multiple times; combinable with `builtin`)
- `--timeout <seconds>` — analysis timeout (default: 900)
- `--max-memory <size>` — JVM memory limit (default: `8G`)
- `--severity <levels>` — severity filter (default: `warning,error`)
- `--code-flow-limit <n>` — max code flows per finding

### Kotlin CLI (`opentaint-project-analyzer.jar`)

Low-level analyzer JAR. Invoked by the Go CLI, but can be used directly for advanced features.

| Flag | Purpose |
|------|---------|
| `--project <project.yaml>` | Project model (required) |
| `--output-dir <dir>` | Output directory (required) |
| `--semgrep-rule-set <path>` | Semgrep rule files/directories (multiple) |
| `--config <path>` | Custom passThrough/approximation YAML (**mutually exclusive** with `--semgrep-rule-set`) |
| `--debug-run-rule-tests` | Run rule tests instead of project analysis |
| `--debug-run-analysis-on-selected-entry-points <spec>` | `*` for all methods or `com.example.Class#method` |
| `--semgrep-rule-load-trace <path>` | Output rule loader diagnostics |
| `--sarif-file-name <name>` | SARIF filename (default: `report-ifds.sarif`) |
| `--ifds-analysis-timeout <seconds>` | IFDS timeout (default: 10000) |
| `--project-kind <kind>` | `unknown` or `spring-web` |

**Important**: `--config` and `--semgrep-rule-set` are **mutually exclusive**. The `--config` flag is the only way to pass custom passThrough/cleaner YAML rules directly. The Go CLI does not expose `--config` — it only passes `--semgrep-rule-set` via `--ruleset`.

### Autobuilder (`opentaint-project-auto-builder.jar`)

| Flag | Purpose |
|------|---------|
| `--project-root-dir <path>` | Project root (required) |
| `--build portable` | Build + create self-contained project directory |
| `--result-dir <path>` | Output directory for portable build |
| `--build simple` | Just dump `project.yaml` |
| `--result <path>` | Output path for simple build |

## Full Agent Workflow

### Step 1: Project Setup

Agent takes the path to the target project and prepares it for analysis.

**Option A: Use Go CLI (recommended)**
```bash
# Build and create project model
opentaint compile /path/to/project -o ./opentaint-project

# Result: ./opentaint-project/project.yaml
```

**Option B: Use Autobuilder directly**
```bash
java -jar opentaint-project-auto-builder.jar \
  --project-root-dir /path/to/project \
  --build portable \
  --result-dir ./opentaint-project \
  --logs-file autobuild.log \
  --verbosity debug
```

**Option C: Create project.yaml manually**

For projects that don't use standard Gradle/Maven builds, or for pre-compiled artifacts:

```bash
opentaint project \
  --output ./opentaint-project \
  --source-root /path/to/sources \
  --classpath /path/to/classes.jar \
  --classpath /path/to/dependency.jar \
  --package com.example.app
```

The generated `project.yaml` follows this schema:
```yaml
sourceRoot: sources
javaToolchain: toolchain/jdk-17
modules:
  - moduleSourceRoot: sources/src/main/java
    packages: [com.example.app]
    moduleClasses:
      - classes/c0_main
dependencies:
  - dependencies/spring-web-5.3.39.jar
  - dependencies/javax.servlet-api-4.0.1.jar
```

### Step 2: Entry Point Discovery

Agent searches for entry points and potentially vulnerable places. This is a code-level analysis step.

The agent should examine:
- **Spring controllers**: `@RestController`/`@Controller` classes with `@RequestMapping`/`@GetMapping` etc.
- **Servlet handlers**: Classes extending `HttpServlet` with `doGet`/`doPost`/etc.
- **JAX-RS endpoints**: Classes with `@Path` and `@GET`/`@POST` annotations
- **Message handlers**: JMS/Kafka/RabbitMQ listeners
- **CLI entry points**: `main()` methods that process external input

The engine automatically discovers Spring entry points (via `SpringWebProject.kt`) and for unknown projects selects all public/protected methods from public project classes. The agent can also use `--debug-run-analysis-on-selected-entry-points "com.example.Class#method"` to target specific methods.

### Step 3: Analysis Planning

Agent creates `opentaint-analysis-plan.md` to track progress. This document records:
- Target project description
- Identified entry points and attack surface
- Rules to create/apply
- Analysis iterations with findings
- FP/FN tracking and resolution status
- Final vulnerability inventory

### Step 4: Rule Creation

Agent creates pattern rules for the vulnerability classes relevant to the target project. See `pattern-rules.md` for the full rule language.

**Typical rule structure:**

```
rules/
├── agent-rules/                    # Agent-created rules
│   ├── java/
│   │   ├── security/
│   │   │   └── custom-sqli.yaml   # Security rule (join mode)
│   │   └── lib/
│   │       └── custom-sinks.yaml  # Sink library rule
│   └── test/
│       └── CustomSqliTest.java    # Test samples
```

The agent composes rules using the three modes:
1. **Simple patterns** — for structural issues (no dataflow)
2. **Taint mode** — for defining sinks with `focus-metavariable`
3. **Join mode** — for composing source + sink library rules via `refs` and `on` clauses

### Step 5: Rule Testing

Agent creates test samples and validates rules work before running on the real project.

**5a. Create a simple test project:**

A minimal Gradle project with Java source files containing annotated test samples:

```java
import org.opentaint.sast.test.util.PositiveRuleSample;
import org.opentaint.sast.test.util.NegativeRuleSample;

public class CustomSqliTest {

    @PositiveRuleSample(value = "java/security/custom-sqli.yaml", id = "custom-sql-injection")
    public void vulnerable(HttpServletRequest req) {
        String input = req.getParameter("id");
        db.execute("SELECT * FROM users WHERE id = " + input);
    }

    @NegativeRuleSample(value = "java/security/custom-sqli.yaml", id = "custom-sql-injection")
    public void safe(HttpServletRequest req) {
        String input = req.getParameter("id");
        db.execute("SELECT * FROM users WHERE id = ?", input);
    }
}
```

**5b. Build the test project:**
```bash
opentaint compile ./test-project -o ./test-opentaint-project
```

**5c. Run rule tests (via Kotlin CLI directly):**
```bash
java -Xmx8G -jar opentaint-project-analyzer.jar \
  --project ./test-opentaint-project/project.yaml \
  --output-dir ./test-result \
  --semgrep-rule-set ./agent-rules \
  --debug-run-rule-tests \
  --verbosity debug
```

This produces `test-result/test-result.json` with per-sample verdicts:
```json
{
  "success": [...],
  "falsePositive": [...],
  "falseNegative": [...],
  "skipped": [...],
  "disabled": [...]
}
```

**5d. Fix and repeat** until all tests pass (no falsePositive/falseNegative entries).

### Step 6: Run Analysis on Target Project

```bash
# Option A: Go CLI
opentaint scan ./opentaint-project/project.yaml \
  -o ./results/report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules

# Option B: Kotlin CLI (if custom --config needed)
java -Xmx8G -jar opentaint-project-analyzer.jar \
  --project ./opentaint-project/project.yaml \
  --output-dir ./results \
  --semgrep-rule-set ./agent-rules \
  --ifds-analysis-timeout 900 \
  --verbosity info
```

### Step 7: Interpret Results

The analyzer produces two output files:

**7a. SARIF report** (`results/report-ifds.sarif`)

Standard SARIF 2.1.0 format containing:
- `runs[0].results[]` — each result is a vulnerability finding with:
  - `ruleId` — which rule triggered
  - `message.text` — human-readable description
  - `level` — severity (error/warning/note)
  - `locations[]` — sink location (file, line, column)
  - `codeFlows[]` — taint traces from source to sink
  - `relatedLocations[]` — HTTP endpoints, parameter info

View results:
```bash
opentaint summary ./results/report.sarif --show-findings --show-code-snippets --verbose-flow
```

**7b. External methods list** (`results/external-methods.json`)

JSON list of external methods where a dataflow fact was killed during analysis. Each entry contains:
- Method signature (class, name, parameter types)
- Fact position information (the taint flow position from the passThrough rule perspective)

This is the primary signal for fixing FN caused by missing taint propagation models.

### Step 8: Decision Loop

For each analysis result, the agent decides between the following actions:

```
For each finding in SARIF:
│
├── Analyze the trace (codeFlow)
│   │
│   ├── Trace is a TRUE POSITIVE (TP)
│   │   → Generate POC exploit
│   │   → Save to vulnerabilities.md
│   │
│   ├── Trace contains FALSE POSITIVE (FP) — fixable via Rule
│   │   → Add pattern-not / pattern-not-inside to exclude the safe pattern
│   │   → Update tests (add @NegativeRuleSample)
│   │   → Re-run analysis (go to Step 6)
│   │
│   └── Trace contains FALSE POSITIVE (FP) — fixable via Approximation (non-preferred)
│       → Override approximation to remove impossible dataflow path
│       → Re-run analysis (go to Step 6)
│
For each entry in external methods list:
│
├── Method is a taint PROPAGATOR
│   → Generate passThrough YAML rule (preferred)
│   → Re-run analysis (go to Step 6)
│
├── Method is a complex TRANSFORMER (lambdas/callbacks)
│   → Generate code-based approximation (Java stub)
│   → Re-run analysis (go to Step 6)
│
├── Method is a SANITIZER
│   → Generate cleaner YAML rule
│   → Re-run analysis (go to Step 6)
│
└── Method is NEUTRAL (logging, metrics)
    → Skip (default call-to-return passthrough is correct)
```

**FN fix via Rule** (non-preferred): If the FN is due to a missing source/sink pattern (not a missing approximation), the agent can add more patterns and tests to the rule. This is less common since most FN stem from taint being lost at external method calls.

### Step 9: Iteration

Steps 6-8 repeat until the agent determines:
- All traces have been reviewed
- All identified FP have been fixed
- All relevant external methods have been addressed
- Remaining external methods are classified as NEUTRAL
- All TPs have been documented with POCs in `vulnerabilities.md`

## Detailed Sub-Scenarios

### Fixing FN via External Methods List

This is the most common and impactful iteration. Each external methods list entry provides:

```json
{
  "method": "com.example.lib.DataWrapper#getValue",
  "signature": "() java.lang.String",
  "factPosition": "this"
}
```

The `factPosition` tells the agent **from where** taint should propagate. The agent uses this to generate the correct `copy.from` in the passThrough rule:

```yaml
# factPosition: "this" means taint is on the receiver → should flow to result
passThrough:
  - function: com.example.lib.DataWrapper#getValue
    copy:
      - from: this
        to: result
```

```yaml
# factPosition: "arg(0)" means taint is on first argument → should flow to result/this
passThrough:
  - function: com.example.lib.DataWrapper#process
    copy:
      - from: arg(0)
        to: result
```

#### Decision Tree: YAML Config vs Code-Based Approximation

```
Does the method involve lambdas/callbacks/functional interfaces?
├── YES → Code-based approximation required
│         (YAML cannot model lambda invocation)
└── NO
    Does the method involve complex internal state?
    ├── YES → YAML with <rule-storage> pattern
    │         (model internal state with synthetic fields)
    └── NO
        Is it a simple from→to propagation?
        ├── YES → YAML passThrough rule (simplest, preferred)
        └── NO
            Does it require non-deterministic branching?
            ├── YES → Code-based approximation
            │         (use OpentaintNdUtil.nextBool())
            └── NO → YAML passThrough rule with conditions
```

#### Batch Processing Strategy

When the external methods list is large, process library-by-library:

```
1. Group by package/library
   com.fasterxml.jackson.* → 47 methods
   org.springframework.* → 23 methods
   org.apache.commons.* → 15 methods

2. For each library:
   ├── Check if built-in config already covers it (jar-split/ configs)
   ├── Look up library documentation / source
   ├── Generate comprehensive passThrough rules
   └── Save as agent-config/<library-name>.yaml

3. Re-run analysis after each library batch
```

### Fixing FP via Rule

When a SARIF trace shows a false positive:

**Common causes and fixes:**

| Cause | Fix | Example |
|-------|-----|---------|
| Sanitization not recognized | Add `pattern-not-inside` or `pattern-sanitizers` | `Encoder.htmlEncode()` not recognized |
| Safe type not excluded | Add `metavariable-regex` with negative lookahead | Primitive types flowing to sink |
| Context makes it safe | Add `pattern-not-inside` for the safe context | Inside validation block |
| Wrong method matched | Narrow `metavariable-regex` or pattern | Too broad `$OBJ.$METHOD(...)` |

**Example: Adding a sanitizer exclusion**
```yaml
# Before: sink matches all calls
pattern-sinks:
  - pattern: response.getWriter().write($UNTRUSTED)

# After: exclude sanitized paths
pattern-sinks:
  - patterns:
      - pattern: response.getWriter().write($UNTRUSTED)
      - pattern-not-inside: |
          $X = Encoder.htmlEncode(...);
          ...
          response.getWriter().write($X);
```

### Fixing FP via Approximation (Non-Preferred)

Sometimes a false dataflow path exists because an approximation is too permissive (e.g., models a method as propagating taint when it actually transforms data in a way that neutralizes it).

**Fix**: Override with a more precise approximation or add a cleaner rule:

```yaml
# Add a cleaner rule to kill taint at the sanitizing method
cleaner:
  - function: com.example.security.Sanitizer#clean
    clean:
      - position: result
        mark: tainted
```

**Note**: This is non-preferred because approximation changes affect all rules globally, not just the specific FP case.

### Overriding Existing Approximations

The agent can override built-in approximations at two levels:

**Override YAML config rules**: Provide a custom config via `--config` flag (Kotlin CLI only). PassThrough and cleaner rules are **extended** (merged with built-in), while source/sink/entryPoint rules are **overridden** (replace built-in).

**Override with code-based approximations**: Create a Java stub class with `@Approximate`. Code-based approximations always take priority over YAML config for the same method. However, custom code-based approximations are **not currently passable via CLI flags** — they require building a custom approximations JAR and setting environment variables (`opentaint.jvm.api.jar.path`, `opentaint.jvm.approximations.jar.path`).

### Priority chain:

```
Code-based approximation    ← Highest (analyzed as actual code)
    overrides
YAML config rules           ← Agent CAN provide via --config
    merged with
Auto-generated defaults     ← Engine auto-generates for get* on non-project classes
    fall back to
Intra-procedural analysis   ← Engine analyzes callee body if available
    fall back to
Call-to-return passthrough  ← Taint preserved, method treated as no-op
```

## Passing Custom Approximations to the Analyzer

### Via `--semgrep-rule-set` (Go CLI `--ruleset`)

Pattern rules (source/sink/sanitizer patterns). This is the primary path for agent-generated rules.

```bash
opentaint scan project.yaml -o report.sarif \
  --ruleset builtin \
  --ruleset ./agent-rules/
```

### Via `--config` (Kotlin CLI only, not exposed in Go CLI)

PassThrough/cleaner/source/sink YAML in `SerializedTaintConfig` format. Use this when the agent needs to add custom taint propagation models.

```bash
java -jar opentaint-project-analyzer.jar \
  --project project.yaml \
  --output-dir ./results \
  --config ./agent-config/custom-propagators.yaml
```

**Limitation**: `--config` and `--semgrep-rule-set` are **mutually exclusive**. If the agent needs both custom pattern rules and custom propagation config, it must either:
1. Use `--semgrep-rule-set` — pattern rules include only passThrough from the default built-in config (agent cannot add extra passThrough rules this way)
2. Use `--config` — loses the ability to provide Semgrep-format pattern rules

This is a current limitation that may need to be addressed (see "Required Engine Enhancements" below).

### Via Environment Variables (Code-Based Approximations)

Custom compiled Java stub JARs. Requires `useOpentaintApproximations=true` which is not exposed via CLI.

```bash
export opentaint.jvm.api.jar.path=/path/to/api.jar
export opentaint.jvm.approximations.jar.path=/path/to/approximations.jar
```

**Current status**: Not practically usable via CLI. Requires programmatic API access.

## Common Templates

### Template: PassThrough for Simple Getter

```yaml
passThrough:
  - function: com.example.Type#getField
    copy:
      - from: this
        to: result
```

### Template: PassThrough for Builder Pattern

```yaml
passThrough:
  - function: com.example.Builder#withField
    copy:
      - from: arg(0)
        to: this
      - from: arg(0)
        to: result
      - from: this
        to: result
```

### Template: PassThrough for Collection Wrapper

```yaml
passThrough:
  - function: com.example.Collection#add
    copy:
      - from: arg(0)
        to:
          - this
          - .com.example.Collection#<rule-storage>#java.lang.Object
  - function: com.example.Collection#get
    copy:
      - from:
          - this
          - .com.example.Collection#<rule-storage>#java.lang.Object
        to: result
```

### Template: PassThrough for Generic Pattern (All Getters in a Package)

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

### Template: Code-Based Approximation for Functional API

```java
@Approximate(com.example.FunctionalApi.class)
public class FunctionalApi {
    public Object transform(@ArgumentTypeContext Function<Object, Object> fn) throws Throwable {
        FunctionalApi self = (FunctionalApi) (Object) this;
        if (OpentaintNdUtil.nextBool()) return null;
        Object input = self.getValue();
        return fn.apply(input);
    }

    public void consume(@ArgumentTypeContext Consumer<Object> consumer) {
        FunctionalApi self = (FunctionalApi) (Object) this;
        if (OpentaintNdUtil.nextBool()) {
            consumer.accept(self.getValue());
        }
    }
}
```

### Template: Cleaner Rule (Sanitizer)

```yaml
cleaner:
  - function: com.example.security.HtmlEncoder#encode
    clean:
      - position: result
        mark: tainted
```

## Required Engine Enhancements

Based on the requirements in the task specification, the following features need to be implemented:

### 1. External Methods List Output (JSON)

**Requirement**: "Engine will return a list of external methods, where dataflow fact was killed" (task.md line 13).

**Current state**: The engine does not produce this output. When a method is unresolvable (external), the fact is preserved via call-to-return passthrough, not killed. The engine needs a new mechanism to:
- Track which external methods were encountered during analysis
- Record the fact position (from which taint was propagating) at each external call
- Output this as a JSON file alongside the SARIF report

**Proposed format**:
```json
[
  {
    "method": "com.example.lib.Wrapper#getValue",
    "signature": "() java.lang.String",
    "factPosition": "this",
    "callCount": 5
  }
]
```

A CLI flag (e.g., `--external-methods-output <path>`) should be added to both CLIs.

### 2. Combined `--config` + `--semgrep-rule-set`

**Current state**: These flags are mutually exclusive.

**Requirement**: The agent needs to provide both custom pattern rules (`--semgrep-rule-set`) and custom passThrough/approximation YAML (`--config`) simultaneously.

**Proposed fix**: Allow both flags. When both are provided, load Semgrep rules as the pattern-matching layer and merge the custom config's passThrough/cleaner rules with the default config.

### 3. Custom Code-Based Approximations via CLI

**Current state**: No CLI flag to pass custom approximation JARs. The `useOpentaintApproximations` flag is hardcoded to `false`.

**Requirement**: Agent must be able to provide code-based approximations for complex methods.

**Proposed fix**: Add a CLI flag (e.g., `--approximations-jar <path>`) that enables `useOpentaintApproximations` and sets the JAR paths. Expose this in both CLIs.

## Integration Constraints

### What the Agent CAN Do

1. Create/modify pattern rules (YAML) in custom rule directories
2. Create/modify YAML config rules (passThrough, source, sink, cleaner) via `--config`
3. Create code-based approximation Java stubs (pending CLI support)
4. Generate test cases for rules
5. Override YAML config rules with more specific rules
6. Override YAML config rules with code-based approximations (pending CLI support)
7. Use `--ruleset` with multiple custom rule directories alongside `builtin`

### What the Agent CANNOT Do

1. Modify framework support (Spring, etc.) — provided as-is
2. Change the IFDS analysis algorithm
3. Change the access path abstraction mode
4. Change how the call graph is constructed
5. Modify the pattern matching engine semantics
6. Currently: combine `--config` and `--semgrep-rule-set` in a single run
7. Currently: pass custom code-based approximations via CLI

### Validation Checklist

Before submitting any artifact, the agent should verify:

- [ ] YAML is valid and parseable
- [ ] Rule IDs are globally unique
- [ ] Library rules have `options.lib: true`
- [ ] Security rules have `metadata.cwe` and `metadata.short-description`
- [ ] Source/sink rules use consistent metavariable names (`$UNTRUSTED`)
- [ ] Join-mode `on` clauses reference valid aliases defined in `refs`
- [ ] Test cases exist for all enabled non-library rules
- [ ] passThrough `from`/`to` positions are valid (`this`, `arg(N)`, `result`, etc.)
- [ ] Code-based approximations compile and use `@Approximate` annotation
- [ ] No regressions: existing test cases still pass
- [ ] `opentaint-analysis-plan.md` is updated with current iteration status
