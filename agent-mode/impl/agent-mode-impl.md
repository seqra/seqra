# Agent Mode — Implementation Plan

This document translates the design in `agent-mode/design/agent-mode-design.md` into a concrete, file-level implementation plan. It covers every module that needs modification, where skills and meta-prompt live, how they're distributed, and how to test without the CLI installed on PATH.

---

## Table of Contents

1. [Implementation Overview](#1-implementation-overview)
2. [Kotlin Analyzer Changes](#2-kotlin-analyzer-changes)
   - 2.1 [External Methods Tracker](#21-external-methods-tracker)
   - 2.2 [Rule ID Filter](#22-rule-id-filter)
   - 2.3 [Approximations Config + Semgrep Rules Together](#23-approximations-config--semgrep-rules-together)
   - 2.4 [Custom Dataflow Approximations Path](#24-custom-dataflow-approximations-path)
   - 2.5 [New CLI Flags Wiring](#25-new-cli-flags-wiring)
3. [Go CLI Changes](#3-go-cli-changes)
   - 3.1 [New Flags on `scan` Command](#31-new-flags-on-scan-command)
   - 3.2 [Approximation Auto-Compilation](#32-approximation-auto-compilation)
   - 3.3 [`test-rules` Command](#33-test-rules-command)
   - 3.4 [`rules-path` Command](#34-rules-path-command)
   - 3.5 [`init-test-project` Command](#35-init-test-project-command)
   - 3.6 [Hidden Dev Flags](#36-hidden-dev-flags)
   - 3.7 [AnalyzerBuilder Extensions](#37-analyzerbuilder-extensions)
4. [Skills and Meta-Prompt Location](#4-skills-and-meta-prompt-location)
   - 4.1 [File Layout](#41-file-layout)
   - 4.2 [Bundling Into CLI Distribution](#42-bundling-into-cli-distribution)
   - 4.3 [Runtime Access](#43-runtime-access)
   - 4.4 [Agent Integration Patterns](#44-agent-integration-patterns)
5. [Distribution](#5-distribution)
   - 5.1 [Packaging Skills and Meta-Prompt](#51-packaging-skills-and-meta-prompt)
   - 5.2 [Release Pipeline Changes](#52-release-pipeline-changes)
   - 5.3 [User-Facing Installation](#53-user-facing-installation)
6. [Testing Without CLI on PATH](#6-testing-without-cli-on-path)
   - 6.1 [Hidden `--analyzer-jar` / `--autobuilder-jar` Flags](#61-hidden---analyzer-jar----autobuilder-jar-flags)
   - 6.2 [Environment Variables](#62-environment-variables)
   - 6.3 [Python Test Infrastructure (conftest.py)](#63-python-test-infrastructure-conftestpy)
   - 6.4 [Local Dev Workflow](#64-local-dev-workflow)
7. [Implementation Order](#7-implementation-order)
8. [File Change Summary](#8-file-change-summary)

---

## 1. Implementation Overview

The implementation spans two main codebases (Kotlin analyzer, Go CLI) plus a new `agent-mode/skills/` directory for agent-consumable content. The work divides into:

| Area | Scope | Effort |
|------|-------|--------|
| Kotlin analyzer | 4 features: external methods tracker, rule ID filter, combined config+rules, custom approximations path | Medium-Large |
| Go CLI | 4 new flags on `scan`, 3 new commands, hidden dev flags, auto-compilation logic | Medium |
| Skills + Meta-prompt | 9 skill files + 1 meta-prompt, bundled into CLI distribution | Small |
| Distribution | Release pipeline changes to bundle `lib/skills/` | Small |
| Test infrastructure | Already built in Phase 3; needs hidden flag support | Small |

---

## 2. Kotlin Analyzer Changes

### 2.1 External Methods Tracker

**Goal**: Collect all external (unresolved) method calls during taint analysis and output them as YAML.

#### New files

**`core/opentaint-dataflow-core/opentaint-dataflow/src/main/kotlin/org/opentaint/dataflow/ap/ifds/taint/ExternalMethodTracker.kt`**

```kotlin
package org.opentaint.dataflow.ap.ifds.taint

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class ExternalMethodRecord(
    val method: String,           // "com.example.Foo#bar"
    val signature: String,        // JVM-style: "(Ljava/lang/String;)V"
    val factPositions: Set<String>, // "this", "arg(0)", "arg(1)", "result"
    val hasPassRules: Boolean,    // true if config has passThrough rules for this method
)

class ExternalMethodTracker {
    // Dedup key: method+signature+factPosition
    private val seen = ConcurrentHashMap.newKeySet<String>()
    
    // Per-method aggregation: method+signature → (factPositions, hasPassRules, callSiteCount)
    private val records = ConcurrentHashMap<String, ExternalMethodAggregation>()

    fun report(
        method: String,
        signature: String,
        factPosition: String,
        hasPassRules: Boolean,
    ) {
        val key = "$method|$signature|$factPosition"
        if (!seen.add(key)) return
        
        records.computeIfAbsent("$method|$signature") {
            ExternalMethodAggregation(method, signature, hasPassRules)
        }.addFactPosition(factPosition)
    }

    fun reportCallSite(method: String, signature: String) {
        records.computeIfAbsent("$method|$signature") {
            ExternalMethodAggregation(method, signature, false)
        }.incrementCallSites()
    }

    fun getResults(): ExternalMethodResults {
        val withoutRules = mutableListOf<ExternalMethodRecord>()
        val withRules = mutableListOf<ExternalMethodRecord>()
        
        for (agg in records.values) {
            val record = agg.toRecord()
            if (record.hasPassRules) withRules.add(record) else withoutRules.add(record)
        }
        
        return ExternalMethodResults(
            withoutRules.sortedByDescending { it.callSites },
            withRules.sortedByDescending { it.callSites },
        )
    }
}
```

**Pattern**: Modeled after `TaintSinkTracker` (same file location, same `ConcurrentHashMap` dedup pattern, same wiring through storage).

#### Modified files

| File | Change |
|------|--------|
| `core/opentaint-dataflow-core/opentaint-dataflow/.../taint/TaintAnalysisUnitStorage.kt` | Add `externalMethodTracker: ExternalMethodTracker` field |
| `core/opentaint-dataflow-core/opentaint-dataflow/.../taint/TaintAnalysisContext.kt` | Expose `externalMethodTracker` from storage |
| `core/opentaint-dataflow-core/opentaint-jvm-dataflow/.../JIRMethodCallFlowFunction.kt` | In `applyPassRulesOrCallSkip()` at line ~617: after resolving `callExpr.callee`, call `externalMethodTracker.report(...)` |
| `core/opentaint-jvm-sast-dataflow/.../TaintAnalysisUnitRunnerManager.kt` | Wire `ExternalMethodTracker` into unit storage creation (same pattern as `TaintSinkTracker`) |
| `core/src/main/kotlin/.../project/ProjectAnalyzer.kt` | After analysis completes, if `externalMethodsOutput` path is set, serialize tracker results to YAML |
| `core/src/main/kotlin/.../project/ProjectAnalysisOptions.kt` | Add `externalMethodsOutput: Path? = null` field |

#### Integration point in `JIRMethodCallFlowFunction`

The key insertion point is `applyPassRulesOrCallSkip()` at line 617:

```kotlin
// EXISTING: line 617
val method = callExpr.callee

// NEW: report external method to tracker
val tracker = analysisContext.taint.externalMethodTracker
if (tracker != null) {
    val methodName = "${method.declaringClass.name}#${method.name}"
    val signature = method.jvmSignature
    val factPosition = resolveFactPosition(factAp)  // "this", "arg(0)", etc.
    val hasPassRules = config.hasPassRulesFor(method)
    tracker.report(methodName, signature, factPosition, hasPassRules)
}
```

The `resolveFactPosition` helper maps `FinalFactAp` base to a human-readable position string. The `hasPassRulesFor` method checks if `TaintRulesProvider` has any passThrough rules matching this method (the same lookup `applyPassThrough` does, but returning boolean).

#### Output format

YAML file written by `ProjectAnalyzer` after analysis:

```yaml
withoutRules:
  - method: "org.apache.pdfbox.pdmodel.PDDocument#save"
    signature: "(Ljava/io/OutputStream;)V"
    factPositions: ["arg(0)", "this"]
    callSites: 12
  - method: "com.fasterxml.jackson.databind.ObjectMapper#readValue"
    signature: "(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;"
    factPositions: ["arg(0)", "result"]
    callSites: 7

withRules:
  - method: "java.lang.String#substring"
    signature: "(I)Ljava/lang/String;"
    factPositions: ["this", "result"]
    callSites: 45
```

Serialization uses `kaml` (already a dependency) or `snakeyaml` — consistent with `Project.kt` pattern.

---

### 2.2 Rule ID Filter

**Goal**: Filter loaded rules by ID, keeping only specified rules + their transitive `refs` dependencies.

#### Modified files

| File | Change |
|------|--------|
| `core/opentaint-java-querylang/.../SemgrepRuleLoader.kt` | Add `ruleIdFilter` parameter to `loadRules()`, implement ID-based filtering with recursive `refs` resolution |
| `core/src/main/kotlin/.../project/ProjectAnalysisOptions.kt` | Add `semgrepRuleId: List<String> = emptyList()` field |
| `core/src/main/kotlin/.../runner/ProjectAnalyzerRunner.kt` | Add `--semgrep-rule-id` Clikt option, wire to `ProjectAnalysisOptions` |
| `core/src/main/kotlin/.../runner/AbstractAnalyzerRunner.kt` | Alternative location if the flag should also affect `TestProjectAnalyzer` |

#### Implementation in `SemgrepRuleLoader.loadRules()`

Current signature (line 106):
```kotlin
fun loadRules(severity: List<Severity> = emptyList()): RuleLoadResult
```

New signature:
```kotlin
fun loadRules(
    severity: List<Severity> = emptyList(),
    ruleIdFilter: List<String> = emptyList(),
): RuleLoadResult
```

After loading all rules and applying severity filter, if `ruleIdFilter` is non-empty:

```kotlin
if (ruleIdFilter.isNotEmpty()) {
    val requestedIds = ruleIdFilter.toSet()
    // 1. Find directly requested rules
    val directMatches = allRules.filter { it.id in requestedIds }
    // 2. Recursively resolve refs (join-mode rules reference library rules)
    val resolvedIds = mutableSetOf<String>()
    fun resolveRefs(rule: Rule<*>) {
        if (!resolvedIds.add(rule.id)) return
        rule.refs?.forEach { ref ->
            allRules.find { it.ruleFile == ref }?.let { resolveRefs(it) }
        }
    }
    directMatches.forEach { resolveRefs(it) }
    // 3. Keep only resolved set
    allRules = allRules.filter { it.id in resolvedIds }
}
```

The `refs` field is already parsed by `SemgrepRuleLoader` — it references other YAML files relative to the ruleset root. Library rules (`options.lib: true`) are normally auto-included but with an ID filter they'd be dropped unless explicitly resolved.

---

### 2.3 Approximations Config + Semgrep Rules Together

**Goal**: Remove the mutual exclusion between `--config` (approximations) and `--semgrep-rule-set`.

#### Modified files

| File | Change |
|------|--------|
| `core/src/main/kotlin/.../project/ProjectAnalyzer.kt` | Modify `preloadRules()` at lines 54-80: add 4th variant, remove `check()` at line 62 |
| `core/src/main/kotlin/.../runner/ProjectAnalyzerRunner.kt` | Rename `--config` flag to `--approximations-config` |

#### Implementation in `ProjectAnalyzer.preloadRules()`

Current code (lines 54-80):
```kotlin
private sealed interface PreloadedRules {
    data class SemgrepRules(val rules: List<TaintRuleFromSemgrep>) : PreloadedRules
    data class Custom(val config: SerializedTaintConfig) : PreloadedRules
    data object DefaultRules : PreloadedRules
}

private fun preloadRules(): PreloadedRules {
    if (options.semgrepRuleSet.isNotEmpty()) {
        check(options.customConfig == null) { "Unsupported custom config" }  // ← REMOVE THIS
        val loadedRules = options.loadSemgrepRules()
        ruleMetadatas += loadedRules.rulesWithMeta.map { it.second }
        return PreloadedRules.SemgrepRules(loadedRules.rulesWithMeta.map { it.first })
    }
    // ...
}
```

New code:
```kotlin
private sealed interface PreloadedRules {
    data class SemgrepRules(val rules: List<TaintRuleFromSemgrep>) : PreloadedRules
    data class Custom(val config: SerializedTaintConfig) : PreloadedRules
    data class SemgrepRulesWithCustomConfig(
        val rules: List<TaintRuleFromSemgrep>,
        val config: SerializedTaintConfig,
    ) : PreloadedRules
    data object DefaultRules : PreloadedRules
}

private fun preloadRules(): PreloadedRules {
    val customConfig = options.customConfig?.let { cfg ->
        cfg.inputStream().use { loadSerializedTaintConfig(it) }
    }

    if (options.semgrepRuleSet.isNotEmpty()) {
        val loadedRules = options.loadSemgrepRules(ruleIdFilter = options.semgrepRuleId)
        ruleMetadatas += loadedRules.rulesWithMeta.map { it.second }
        val rules = loadedRules.rulesWithMeta.map { it.first }
        
        return if (customConfig != null) {
            PreloadedRules.SemgrepRulesWithCustomConfig(rules, customConfig)
        } else {
            PreloadedRules.SemgrepRules(rules)
        }
    }

    if (customConfig != null) {
        return PreloadedRules.Custom(customConfig)
    }

    return PreloadedRules.DefaultRules
}
```

Then in `loadTaintConfig()` (lines 82-103), add a branch for `SemgrepRulesWithCustomConfig`:

```kotlin
is PreloadedRules.SemgrepRulesWithCustomConfig -> {
    // Load default config, override with custom, then layer semgrep rules on top
    val defaultConfig = loadDefaultConfig()
    val mergedConfig = JIRCombinedTaintRulesProvider(defaultConfig, rules.config) // OVERRIDE mode
    // Then apply semgrep rules to mergedConfig
    // ... (same as SemgrepRules branch but with mergedConfig as base)
}
```

The OVERRIDE semantics means: custom config entries for the same method signature replace (not extend) the default config entries. This is already how `JIRCombinedTaintRulesProvider` works — later entries take precedence.

---

### 2.4 Custom Dataflow Approximations Path

**Goal**: Accept external directories of compiled approximation `.class` files via CLI flag.

#### Modified files

| File | Change |
|------|--------|
| `core/opentaint-jvm-sast-dataflow/.../DataFlowApproximationLoader.kt` | Add `customApproximationPaths: List<Path>` to `Options`, append in `approximationFiles()` |
| `core/src/main/kotlin/.../project/ProjectAnalysisOptions.kt` | Already has `approximationOptions: DataFlowApproximationLoader.Options` — new paths flow through this |
| `core/src/main/kotlin/.../runner/ProjectAnalyzerRunner.kt` | Add `--dataflow-approximations` Clikt option |

#### Implementation in `DataFlowApproximationLoader`

Current `Options` (line 20-23):
```kotlin
data class Options(
    val useDataflowApproximation: Boolean = true,
    val useOpentaintApproximations: Boolean = false,
)
```

New `Options`:
```kotlin
data class Options(
    val useDataflowApproximation: Boolean = true,
    val useOpentaintApproximations: Boolean = false,
    val customApproximationPaths: List<Path> = emptyList(),
)
```

Modified `approximationFiles()` (lines 52-63):
```kotlin
private fun approximationFiles(options: Options): List<File> {
    val result = mutableListOf<File>()
    if (options.useDataflowApproximation) {
        result += listOfNotNull(dataflowApproximationsPath?.toFile())
    }
    if (options.useOpentaintApproximations) {
        result += approximationPaths.presentPaths.map { File(it) }
    }
    // NEW: append custom paths AFTER built-in ones
    result += options.customApproximationPaths.map { it.toFile() }
    return result
}
```

Custom paths are appended **after** built-in ones. The `ApproximationIndexer` (which scans `@Approximate` annotations) maintains a bijection map from target class → approximation class. If a custom approximation targets the same class as a built-in one, the bijection's `require()` will throw — this is intentional (no silent override of built-in approximations).

---

### 2.5 New CLI Flags Wiring

**File**: `core/src/main/kotlin/org/opentaint/jvm/sast/runner/ProjectAnalyzerRunner.kt`

Current flag definitions (lines 23-52):

```kotlin
class ProjectAnalyzerRunner : AbstractAnalyzerRunner(name = "analyze") {
    // existing flags...
    private val config: Path? by option("--config").file(mustExist = true)
    private val semgrepRuleSet: List<Path> by option("--semgrep-rule-set").file(mustExist = true).multiple()
    private val semgrepSeverity: List<Severity> by option("--semgrep-rule-severity").enum<Severity>().multiple()
    // ...
}
```

New flags to add:

```kotlin
// Rename --config → --approximations-config (keep --config as hidden alias for backward compat)
private val approximationsConfig: Path? by option("--approximations-config", "--config")
    .file(mustExist = true)

// New: Rule ID filter
private val semgrepRuleId: List<String> by option("--semgrep-rule-id")
    .multiple()

// New: External methods output path
private val externalMethodsOutput: Path? by option("--external-methods-output")
    .newFile()

// New: Custom dataflow approximation directories
private val dataflowApproximations: List<Path> by option("--dataflow-approximations")
    .file(mustExist = true, canBeDir = true)
    .multiple()
```

In `analyzeProject()` (line ~80), wire to `ProjectAnalysisOptions`:

```kotlin
val options = ProjectAnalysisOptions(
    customConfig = approximationsConfig,     // was: config
    semgrepRuleSet = semgrepRuleSet,
    semgrepRuleId = semgrepRuleId,           // NEW
    externalMethodsOutput = externalMethodsOutput,  // NEW
    // ...
    approximationOptions = DataFlowApproximationLoader.Options(
        useDataflowApproximation = true,
        customApproximationPaths = dataflowApproximations,  // NEW
    ),
)
```

---

## 3. Go CLI Changes

### 3.1 New Flags on `scan` Command

**File**: `cli/cmd/scan.go`

Add to `init()`:

```go
// New flags
var RuleId []string
var ApproximationsConfig string
var DataflowApproximations string
var ExternalMethods string

func init() {
    rootCmd.AddCommand(scanCmd)
    // ... existing flags ...
    
    // NEW
    scanCmd.Flags().StringArrayVar(&RuleId, "rule-id", nil, 
        "Filter rules by ID (repeatable). Library rules referenced via 'refs' are auto-included")
    scanCmd.Flags().StringVar(&ApproximationsConfig, "approximations-config", "", 
        "Path to YAML passThrough approximations config (OVERRIDE mode)")
    scanCmd.Flags().StringVar(&DataflowApproximations, "dataflow-approximations", "", 
        "Directory of .java or .class approximation files")
    scanCmd.Flags().StringVar(&ExternalMethods, "external-methods", "", 
        "Output path for external methods YAML list")
}
```

In the `scan` command's `Run` function, before building the analyzer command:

```go
// Handle --dataflow-approximations auto-compilation
compiledApproxDir := ""
if DataflowApproximations != "" {
    compiledApproxDir = compileApproximationsIfNeeded(DataflowApproximations, projectPath)
}

// Build analyzer command
nativeBuilder := NewAnalyzerBuilder().
    // ... existing ...
    
// NEW: wire flags
for _, id := range RuleId {
    nativeBuilder.AddRuleIdFilter(id)
}
if ApproximationsConfig != "" {
    nativeBuilder.SetApproximationsConfig(ApproximationsConfig)
}
if compiledApproxDir != "" {
    nativeBuilder.AddDataflowApproximations(compiledApproxDir)
} else if DataflowApproximations != "" {
    nativeBuilder.AddDataflowApproximations(DataflowApproximations)
}
if ExternalMethods != "" {
    nativeBuilder.SetExternalMethodsOutput(ExternalMethods)
}
```

---

### 3.2 Approximation Auto-Compilation

**New file**: `cli/cmd/compile_approximations.go`

This function handles the `--dataflow-approximations` flag when the directory contains `.java` sources:

```go
package cmd

func compileApproximationsIfNeeded(approxDir string, projectPath string) string {
    // 1. Scan dir for .java files
    javaFiles := findJavaFiles(approxDir)
    if len(javaFiles) == 0 {
        return approxDir  // .class files only — pass directly
    }
    
    // 2. Resolve javac (from managed JRE or system)
    javaRunner := java.NewJavaRunner().TrySystem().TrySpecificVersion(globals.DefaultJavaVersion)
    javacPath := resolveJavac(javaRunner)
    
    // 3. Resolve analyzer JAR (for @Approximate annotation classes)
    analyzerJar := resolveAnalyzerJar()
    
    // 4. Resolve project dependencies (from project.yaml)
    projectDeps := resolveProjectDeps(projectPath)
    
    // 5. Create temp output dir
    outputDir := createTempDir("opentaint-approx-compiled-")
    
    // 6. Build classpath: analyzer.jar + project deps
    classpath := analyzerJar + string(os.PathListSeparator) + strings.Join(projectDeps, string(os.PathListSeparator))
    
    // 7. Run javac
    args := []string{
        "-source", "8", "-target", "8",
        "-cp", classpath,
        "-d", outputDir,
    }
    args = append(args, javaFiles...)
    
    err := exec.Command(javacPath, args...).Run()
    if err != nil {
        // Report javac stderr, abort
        out.Fatalf("Failed to compile approximations: %v\n%s", err, stderr)
    }
    
    return outputDir
}
```

**Dependencies**: Uses `java.NewJavaRunner()` (existing) for JDK resolution. Uses `utils.GetAnalyzerJarPath()` (existing) for analyzer JAR. Reads `project.yaml` via `utils/project/config.go` (existing) for dependency classpath.

**Key detail**: The `-source 8 -target 8` ensures compatibility with the analyzer's classloader. The analyzer JAR is needed on the compilation classpath because it contains `@Approximate`, `@ApproximateByName`, `@ArgumentTypeContext`, and `OpentaintNdUtil` classes.

---

### 3.3 `test-rules` Command

**New file**: `cli/cmd/test_rules.go`

```go
package cmd

import "github.com/spf13/cobra"

var TestRulesRuleset string
var TestRulesOutput string

var testRulesCmd = &cobra.Command{
    Use:   "test-rules <project-path-or-project.yaml>",
    Short: "Run rule tests against annotated test samples",
    Args:  cobra.ExactArgs(1),
    Annotations: map[string]string{"PrintConfig": "true"},
    Run: func(cmd *cobra.Command, args []string) {
        projectPath := args[0]
        
        // 1. If projectPath is a directory (not project.yaml), auto-compile
        if isDirectory(projectPath) {
            projectPath = autoCompile(projectPath, TestRulesOutput)
        }
        
        // 2. Build analyzer command with --debug-run-rule-tests
        builder := NewAnalyzerBuilder().
            SetProject(projectPath).
            SetOutputDir(TestRulesOutput).
            SetDebugRunRuleTests(true)
        
        if TestRulesRuleset != "" {
            builder.AddRuleSet(resolveRuleset(TestRulesRuleset))
        }
        
        // 3. Execute
        err := executeAnalyzer(builder)
        
        // 4. Parse test-result.json
        result := parseTestResult(filepath.Join(TestRulesOutput, "test-result.json"))
        
        // 5. Print summary table
        printTestSummary(result)
        
        // 6. Exit code: 0 if only success/disabled, 1 if any falsePositive/falseNegative/skipped
        if result.HasFailures() {
            os.Exit(1)
        }
    },
}

func init() {
    rootCmd.AddCommand(testRulesCmd)
    testRulesCmd.Flags().StringVar(&TestRulesRuleset, "ruleset", "", "Path to rules directory")
    testRulesCmd.Flags().StringVarP(&TestRulesOutput, "output", "o", "", "Output directory")
    _ = testRulesCmd.MarkFlagRequired("output")
}
```

---

### 3.4 `rules-path` Command

**New file**: `cli/cmd/rules_path.go`

```go
package cmd

import "github.com/spf13/cobra"

var rulesPathCmd = &cobra.Command{
    Use:   "rules-path",
    Short: "Print the resolved path to builtin rules",
    Args:  cobra.NoArgs,
    Run: func(cmd *cobra.Command, args []string) {
        version := globals.Config.Rules.Version
        if version == "" {
            version = globals.RulesBindVersion
        }
        
        rulesPath, err := utils.GetRulesPath(version)
        if err != nil {
            // Download if not found
            err = ensureArtifactAvailable("rules", version, rulesPath, downloadRules)
            if err != nil {
                out.Fatalf("Failed to resolve rules: %v", err)
            }
            rulesPath, _ = utils.GetRulesPath(version)
        }
        
        fmt.Println(rulesPath)
    },
}

func init() {
    rootCmd.AddCommand(rulesPathCmd)
}
```

**Pattern**: Reuses `utils.GetRulesPath()` and download logic already present in `scan.go:214-224`. The command downloads rules on demand (same 3-tier resolution: bundled > install > cache).

---

### 3.5 `init-test-project` Command

**New file**: `cli/cmd/init_test_project.go`

```go
package cmd

import "github.com/spf13/cobra"

var InitTestProjectDeps []string

var initTestProjectCmd = &cobra.Command{
    Use:   "init-test-project <output-dir>",
    Short: "Bootstrap a rule test project with build.gradle.kts and test utility JAR",
    Args:  cobra.ExactArgs(1),
    Run: func(cmd *cobra.Command, args []string) {
        outputDir := args[0]
        
        // 1. Create directory structure
        os.MkdirAll(filepath.Join(outputDir, "libs"), 0755)
        os.MkdirAll(filepath.Join(outputDir, "src", "main", "java", "test"), 0755)
        
        // 2. Resolve and copy opentaint-sast-test-util.jar
        testUtilJar := resolveTestUtilJar()
        copyFile(testUtilJar, filepath.Join(outputDir, "libs", "opentaint-sast-test-util.jar"))
        
        // 3. Generate build.gradle.kts
        generateBuildGradle(outputDir, InitTestProjectDeps)
        
        // 4. Generate settings.gradle.kts
        generateSettingsGradle(outputDir)
    },
}

func init() {
    rootCmd.AddCommand(initTestProjectCmd)
    initTestProjectCmd.Flags().StringArrayVar(&InitTestProjectDeps, "dependency", nil,
        "Maven dependency coordinates to add (e.g., 'javax.servlet:javax.servlet-api:4.0.1')")
}
```

The `opentaint-sast-test-util.jar` needs to be available. Options:

1. **Bundle in CLI distribution** alongside analyzer/autobuilder JARs (simplest — add to `lib/`)
2. **Download on demand** from a GitHub release (like other artifacts)
3. **Extract from analyzer JAR** (it's included as a dependency)

**Recommended**: Option 1 — bundle as `lib/opentaint-sast-test-util.jar`. It's tiny (just 2 annotation classes). Add it to the release workflow's "Download bundled artifacts" step.

Generated `build.gradle.kts`:
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
    compileOnly(files("libs/opentaint-sast-test-util.jar"))
    // User-specified dependencies:
    // compileOnly("javax.servlet:javax.servlet-api:4.0.1")
}
```

---

### 3.6 Hidden Dev Flags

**File**: `cli/cmd/root.go`

Add persistent flags (hidden):

```go
func init() {
    // ... existing PersistentFlags ...
    
    // Hidden: direct JAR paths for development
    rootCmd.PersistentFlags().StringVar(&globals.Config.Analyzer.JarPath, "analyzer-jar", "", 
        "Direct path to analyzer JAR (bypasses version resolution)")
    rootCmd.PersistentFlags().StringVar(&globals.Config.Autobuilder.JarPath, "autobuilder-jar", "",
        "Direct path to autobuilder JAR (bypasses version resolution)")
    
    rootCmd.PersistentFlags().MarkHidden("analyzer-jar")
    rootCmd.PersistentFlags().MarkHidden("autobuilder-jar")
    
    _ = viper.BindPFlag("analyzer.jar", rootCmd.PersistentFlags().Lookup("analyzer-jar"))
    _ = viper.BindPFlag("autobuilder.jar", rootCmd.PersistentFlags().Lookup("autobuilder-jar"))
}
```

**File**: `cli/internal/globals/global.go`

Add fields to `ConfigType`:

```go
type ConfigType struct {
    // ... existing ...
    Analyzer struct {
        Version string `mapstructure:"version"`
        JarPath string `mapstructure:"jar"`  // NEW
    }
    Autobuilder struct {
        Version string `mapstructure:"version"`
        JarPath string `mapstructure:"jar"`  // NEW
    }
}
```

**File**: `cli/cmd/artifacts.go` or `scan.go`

In `ensureAnalyzerAvailable()` or wherever the JAR path is resolved:

```go
func resolveAnalyzerJar() string {
    // NEW: check direct path first
    if globals.Config.Analyzer.JarPath != "" {
        if _, err := os.Stat(globals.Config.Analyzer.JarPath); err == nil {
            return globals.Config.Analyzer.JarPath
        }
        out.Fatalf("Analyzer JAR not found at specified path: %s", globals.Config.Analyzer.JarPath)
    }
    
    // Existing: 3-tier resolution
    return existingResolutionLogic()
}
```

**Environment variables**: Via viper's env binding, these are also settable as:
- `OPENTAINT_ANALYZER_JAR=/path/to/jar`
- `OPENTAINT_AUTOBUILDER_JAR=/path/to/jar`

---

### 3.7 AnalyzerBuilder Extensions

**File**: `cli/cmd/command_builder.go`

Add fields to `AnalyzerBuilder`:

```go
type AnalyzerBuilder struct {
    *BaseCommandBuilder
    // ... existing fields ...
    
    // NEW
    ruleIdFilters          []string
    approximationsConfig   string
    dataflowApproximations []string
    externalMethodsOutput  string
    debugRunRuleTests      bool
}
```

Add setter methods:

```go
func (a *AnalyzerBuilder) AddRuleIdFilter(ruleId string) *AnalyzerBuilder {
    a.ruleIdFilters = append(a.ruleIdFilters, ruleId)
    return a
}

func (a *AnalyzerBuilder) SetApproximationsConfig(path string) *AnalyzerBuilder {
    a.approximationsConfig = path
    return a
}

func (a *AnalyzerBuilder) AddDataflowApproximations(path string) *AnalyzerBuilder {
    a.dataflowApproximations = append(a.dataflowApproximations, path)
    return a
}

func (a *AnalyzerBuilder) SetExternalMethodsOutput(path string) *AnalyzerBuilder {
    a.externalMethodsOutput = path
    return a
}

func (a *AnalyzerBuilder) SetDebugRunRuleTests(enabled bool) *AnalyzerBuilder {
    a.debugRunRuleTests = enabled
    return a
}
```

Modify `BuildNativeCommand()`:

```go
func (a *AnalyzerBuilder) BuildNativeCommand() []string {
    flags := []string{...}  // existing
    
    // NEW: append new flags
    for _, id := range a.ruleIdFilters {
        flags = append(flags, "--semgrep-rule-id", id)
    }
    if a.approximationsConfig != "" {
        flags = append(flags, "--approximations-config", a.approximationsConfig)
    }
    for _, path := range a.dataflowApproximations {
        flags = append(flags, "--dataflow-approximations", path)
    }
    if a.externalMethodsOutput != "" {
        flags = append(flags, "--external-methods-output", a.externalMethodsOutput)
    }
    if a.debugRunRuleTests {
        flags = append(flags, "--debug-run-rule-tests")
    }
    
    return flags
}
```

---

## 4. Skills and Meta-Prompt Location

### 4.1 File Layout

Skills and meta-prompt are Markdown files placed in the source repository and bundled into the CLI distribution:

```
agent-mode/
├── skills/
│   ├── meta-prompt.md                  # The system prompt for the agent
│   ├── build-project.md                # Skill 3.1
│   ├── discover-entry-points.md        # Skill 3.2
│   ├── create-rule.md                  # Skill 3.3
│   ├── test-rule.md                    # Skill 3.4
│   ├── run-analysis.md                 # Skill 3.5
│   ├── analyze-findings.md             # Skill 3.6
│   ├── create-yaml-config.md           # Skill 3.7
│   ├── create-approximation.md         # Skill 3.8
│   └── generate-poc.md                 # Skill 3.9
├── design/
│   └── agent-mode-design.md            # (existing)
├── impl/
│   └── agent-mode-impl.md             # (this document)
├── info/
│   ├── pattern-rules.md               # (existing)
│   ├── approximations-config.md       # (existing)
│   └── agent-pipeline.md              # (existing)
└── test/
    └── ...                            # (existing test pipeline)
```

Each skill file is a self-contained Markdown document with:
- **Title and purpose** — what the skill does
- **Prerequisites** — what must be true before using this skill
- **Procedure** — step-by-step instructions with CLI commands
- **Examples** — concrete YAML/Java/command examples
- **Troubleshooting** — common errors and fixes

The meta-prompt (`meta-prompt.md`) is the top-level system prompt that references skills by name and defines the 4-phase agent workflow.

### 4.2 Bundling Into CLI Distribution

Skills are bundled like rules — as a directory in the CLI distribution archive.

**Option A (Recommended): Bundle as `lib/skills/`**

The GoReleaser `files` directive in `cli/.goreleaser.yaml` already includes `lib/**`. Skills would be placed at:

```
cli/lib/skills/
├── meta-prompt.md
├── build-project.md
├── ...
└── generate-poc.md
```

During the release workflow, skills are copied from `agent-mode/skills/` to `cli/lib/skills/` before GoReleaser runs — same pattern as rules (`opentaint-rules.tar.gz` → `cli/lib/rules/`).

**Release workflow change** (`.github/workflows/release-cli.yaml`):

```yaml
- name: Copy skills to lib
  run: |
    mkdir -p cli/lib/skills
    cp agent-mode/skills/*.md cli/lib/skills/
```

This is the simplest approach and requires no separate versioning or release pipeline for skills.

**Option B: Separate artifact (`opentaint-skills.tar.gz`)**

Like rules, skills could be versioned and released independently. This adds complexity (separate release pipeline, version tracking in `versions.yaml`, download-on-demand logic) but enables independent skill updates. 

**Recommendation**: Start with Option A. Skills are updated infrequently and coupling them to CLI releases is acceptable. If skill iteration velocity increases, migrate to Option B later.

### 4.3 Runtime Access

**New Go CLI command**: `opentaint skills-path`

**New file**: `cli/cmd/skills_path.go`

```go
package cmd

import (
    "fmt"
    "github.com/spf13/cobra"
)

var skillsPathCmd = &cobra.Command{
    Use:   "skills-path",
    Short: "Print the resolved path to bundled agent skills",
    Args:  cobra.NoArgs,
    Run: func(cmd *cobra.Command, args []string) {
        // Skills are always bundled — no download needed
        skillsDir := resolveSkillsDir()
        fmt.Println(skillsDir)
    },
}

func init() {
    rootCmd.AddCommand(skillsPathCmd)
}
```

**Resolution logic** (in `cli/internal/utils/opentaint_home.go`):

```go
func GetSkillsPath() (string, error) {
    // Only bundled tier — skills are always shipped with the CLI
    exeDir := getExeDir()
    bundled := filepath.Join(exeDir, "lib", "skills")
    if _, err := os.Stat(bundled); err == nil {
        return bundled, nil
    }
    
    // Install tier
    install := filepath.Join(OpentaintHome(), "install", "lib", "skills")
    if _, err := os.Stat(install); err == nil {
        return install, nil
    }
    
    return "", fmt.Errorf("skills not found; reinstall opentaint or run 'opentaint pull'")
}
```

### 4.4 Agent Integration Patterns

Agents consume skills in three ways:

#### Pattern 1: Direct file read

The agent reads skill files directly from the filesystem. The meta-prompt instructs:

```markdown
## Setup
1. Run `opentaint skills-path` to get the skills directory
2. Read `<skills-path>/meta-prompt.md` for the overall workflow
3. Read individual skill files as needed during each phase
```

This works with any agent framework (Cursor, Cline, Aider, custom).

#### Pattern 2: MCP tool integration

For agents using MCP (Model Context Protocol), skills can be exposed as MCP resources. The `.mcprules-*` files at the repo root already define agent modes — a new `.mcprules-agent` file could reference skills:

```yaml
mode: agent
instructions:
  general:
    - "Read skills from the opentaint skills directory"
    - "Follow the meta-prompt workflow"
skills_path: "opentaint skills-path"
```

This is a future extension — not in scope for initial implementation.

#### Pattern 3: Embedded in agent prompt

For one-shot integrations, the meta-prompt can be directly embedded as the agent's system prompt. The `opentaint skills-path` command lets the caller resolve the path programmatically:

```bash
SKILLS=$(opentaint skills-path)
META_PROMPT=$(cat "$SKILLS/meta-prompt.md")
# Pass $META_PROMPT as system prompt to the LLM agent
```

---

## 5. Distribution

### 5.1 Packaging Skills and Meta-Prompt

Skills ship as part of the CLI distribution in `lib/skills/`:

```
opentaint_linux_amd64.tar.gz
├── opentaint              # Go binary
└── lib/
    ├── opentaint-project-analyzer.jar
    ├── opentaint-project-auto-builder.jar
    ├── opentaint-sast-test-util.jar     # NEW
    ├── rules/                           # Extracted rules
    └── skills/                          # NEW
        ├── meta-prompt.md
        ├── build-project.md
        ├── create-rule.md
        ├── test-rule.md
        ├── run-analysis.md
        ├── analyze-findings.md
        ├── create-yaml-config.md
        ├── create-approximation.md
        ├── discover-entry-points.md
        └── generate-poc.md
```

All three archive variants (`cli`, `default`, `full`) include skills.

### 5.2 Release Pipeline Changes

**File**: `.github/workflows/release-cli.yaml`

Add step after "Download bundled artifacts":

```yaml
- name: Bundle skills
  run: |
    mkdir -p cli/lib/skills
    cp agent-mode/skills/*.md cli/lib/skills/

- name: Bundle test utility JAR
  run: |
    # Download from analyzer release (it's built alongside the analyzer)
    # Or build it: cd core && ./gradlew :opentaint-sast-test-util:jar
    cp core/opentaint-sast-test-util/build/libs/opentaint-sast-test-util.jar cli/lib/
```

**Alternative for test-util JAR**: Publish it as a separate GitHub release asset alongside the analyzer, then download it in the release workflow. This is cleaner but requires a change to `publish-analyzer.yaml`.

### 5.3 User-Facing Installation

No changes to installation scripts needed. The `install.sh`/`install.ps1` scripts download and extract the archive — skills and test-util JAR come along automatically.

Users access skills via:

```bash
# Print skills directory
opentaint skills-path

# Print rules directory
opentaint rules-path

# Both are resolved from the installation layout
```

---

## 6. Testing Without CLI on PATH

### 6.1 Hidden `--analyzer-jar` / `--autobuilder-jar` Flags

When `opentaint` IS on PATH but JARs haven't been downloaded (no `~/.opentaint`), the hidden flags allow pointing directly to locally-built JARs:

```bash
opentaint scan /path/to/project.yaml \
    --analyzer-jar ./core/build/libs/opentaint-jvm-sast.jar \
    -o report.sarif
```

This skips the 3-tier resolution entirely.

### 6.2 Environment Variables

Via viper's env binding (prefix `OPENTAINT_`, `_` separator):

```bash
export OPENTAINT_ANALYZER_JAR=/home/sobol/IdeaProjects/opentaint/core/build/libs/opentaint-jvm-sast.jar
export OPENTAINT_AUTOBUILDER_JAR=/home/sobol/IdeaProjects/opentaint/core/opentaint-jvm-autobuilder/build/libs/opentaint-project-auto-builder.jar

# Now scan just works
opentaint scan /path/to/project.yaml -o report.sarif
```

### 6.3 Python Test Infrastructure (`conftest.py`)

The test infrastructure already handles the "no CLI on PATH" case with a dual-mode strategy:

1. **`_find_opentaint_cli()`** — calls `shutil.which("opentaint")`. Returns `None` if not found.
2. **`OpenTaintCLI.has_cli`** — `True` if Go CLI found, `False` otherwise.
3. **Each command method** (`.scan()`, `.compile()`, etc.) branches:
   - CLI mode: `opentaint scan ...`
   - JAR mode: `java -jar analyzer.jar --project ... --output-dir ...`

**Flag translation** (Go CLI → Kotlin CLI):

| Go CLI | Kotlin CLI (JAR mode) |
|--------|----------------------|
| `--ruleset <path>` | `--semgrep-rule-set <path>` |
| `--rule-id <id>` | `--semgrep-rule-id <id>` |
| `--approximations-config <path>` | `--approximations-config <path>` (same name after rename) |
| `--dataflow-approximations <dir>` | `--dataflow-approximations <dir>` (same name) |
| `--external-methods <path>` | `--external-methods-output <path>` |
| `--severity <s>` | `--semgrep-rule-severity=<s>` |

**Limitation in JAR mode**: Commands with no JAR equivalent (`rules-path`, `init-test-project`, `skills-path`) return hardcoded results or skip:

```python
def rules_path(self) -> CLIResult:
    if self.has_cli:
        return self._run(["rules-path"])
    # Fallback: return known path in dev environment
    return CLIResult(0, str(BUILTIN_RULES_DIR), "", [])

def init_test_project(self, output_dir, dependencies=None) -> CLIResult:
    if self.has_cli:
        args = ["init-test-project", str(output_dir)]
        for dep in (dependencies or []):
            args += ["--dependency", dep]
        return self._run(args)
    # No JAR equivalent — skip
    return CLIResult(1, "", "init-test-project not available in direct JAR mode", [])
```

**For `--dataflow-approximations` in JAR mode**: The auto-compilation step (which the Go CLI does) must be done manually. The test infrastructure should detect `.java` files and compile them before passing the compiled directory to the JAR. This is already handled in the test fixture setup.

### 6.4 Local Dev Workflow

#### Option 1: Build Go CLI locally + use hidden flags

```bash
# Build CLI
cd cli && go build -o opentaint .

# Build analyzer (if needed)
cd core && ./gradlew :projectAnalyzerJar

# Run with direct JAR paths
./cli/opentaint scan /path/to/project.yaml \
    --analyzer-jar ./core/build/libs/opentaint-jvm-sast.jar \
    -o report.sarif
```

#### Option 2: Direct JAR mode (no Go CLI)

```bash
# Build analyzer
cd core && ./gradlew :projectAnalyzerJar

# Run directly
java -Xmx8G \
    -Dorg.opentaint.ir.impl.storage.defaultBatchSize=2000 \
    -Djdk.util.jar.enableMultiRelease=false \
    -jar core/build/libs/opentaint-jvm-sast.jar \
    --project /path/to/project.yaml \
    --output-dir ./output \
    --semgrep-rule-set ./rules/ruleset \
    --semgrep-rule-id my-rule-id \
    --approximations-config ./my-config.yaml \
    --external-methods-output ./external-methods.yaml
```

#### Option 3: Python tests with auto-detection

```bash
# Build analyzer
cd core && ./gradlew :projectAnalyzerJar

# Run tests — conftest.py auto-detects JAR, falls back from Go CLI
cd agent-mode/test
pytest test_build.py -v -k "not slow"
```

The `conftest.py` tries `shutil.which("opentaint")` first. If not found, it searches for the JAR at:
- `core/build/libs/opentaint-jvm-sast.jar`
- `core/build/libs/opentaint-project-analyzer.jar`

Both paths are relative to `OPENTAINT_ROOT` (3 levels up from `conftest.py`).

---

## 7. Implementation Order

Recommended sequence based on dependency analysis:

### Phase A: Kotlin Analyzer Core (can be parallelized internally)

| # | Task | Files | Depends On |
|---|------|-------|------------|
| A1 | Add `ExternalMethodTracker` class | `ExternalMethodTracker.kt` (new) | — |
| A2 | Wire tracker into analysis pipeline | `TaintAnalysisUnitStorage.kt`, `TaintAnalysisContext.kt`, `TaintAnalysisUnitRunnerManager.kt` | A1 |
| A3 | Report external methods from flow function | `JIRMethodCallFlowFunction.kt` | A2 |
| A4 | Add `--external-methods-output` flag + YAML serialization | `ProjectAnalyzerRunner.kt`, `ProjectAnalysisOptions.kt`, `ProjectAnalyzer.kt` | A3 |
| A5 | Add `--semgrep-rule-id` flag + filtering in loader | `SemgrepRuleLoader.kt`, `ProjectAnalyzerRunner.kt`, `ProjectAnalysisOptions.kt` | — |
| A6 | Rename `--config` → `--approximations-config`, remove mutual exclusion, add `SemgrepRulesWithCustomConfig` variant | `ProjectAnalyzerRunner.kt`, `ProjectAnalyzer.kt` | — |
| A7 | Add `customApproximationPaths` to `DataFlowApproximationLoader.Options`, add `--dataflow-approximations` flag | `DataFlowApproximationLoader.kt`, `ProjectAnalyzerRunner.kt`, `ProjectAnalysisOptions.kt` | — |

A1-A4 are sequential (pipeline). A5, A6, A7 are independent of each other and of A1-A4.

### Phase B: Go CLI (depends on Phase A for flag names)

| # | Task | Files | Depends On |
|---|------|-------|------------|
| B1 | Add hidden `--analyzer-jar`/`--autobuilder-jar` flags | `root.go`, `global.go`, `artifacts.go` | — |
| B2 | Add `AnalyzerBuilder` extensions | `command_builder.go` | — |
| B3 | Add new flags to `scan` command | `scan.go` | B2 |
| B4 | Implement approximation auto-compilation | `compile_approximations.go` (new) | B2 |
| B5 | Implement `rules-path` command | `rules_path.go` (new) | — |
| B6 | Implement `test-rules` command | `test_rules.go` (new) | B2 |
| B7 | Implement `init-test-project` command | `init_test_project.go` (new) | — |
| B8 | Implement `skills-path` command | `skills_path.go` (new) | — |

B1, B5, B7, B8 are independent. B2 must precede B3, B4, B6.

### Phase C: Skills and Meta-Prompt

| # | Task | Files | Depends On |
|---|------|-------|------------|
| C1 | Write 9 skill files | `agent-mode/skills/*.md` | A, B (need final CLI flag names) |
| C2 | Write meta-prompt | `agent-mode/skills/meta-prompt.md` | C1 |
| C3 | Update release workflow | `.github/workflows/release-cli.yaml` | C1 |
| C4 | Publish test-util JAR as release asset | `.github/workflows/publish-analyzer.yaml` | — |

### Phase D: Validation

| # | Task | Depends On |
|---|------|------------|
| D1 | Run existing Python test suite (6 passing tests) | A, B |
| D2 | Run `new_feature` tests (20 tests) | A, B |
| D3 | Run full agent loop test | A, B, C |

---

## 8. File Change Summary

### New Files (14)

| File | Purpose |
|------|---------|
| `core/.../taint/ExternalMethodTracker.kt` | External method collection during analysis |
| `cli/cmd/compile_approximations.go` | Auto-compile .java approximations to .class |
| `cli/cmd/test_rules.go` | `opentaint test-rules` command |
| `cli/cmd/rules_path.go` | `opentaint rules-path` command |
| `cli/cmd/init_test_project.go` | `opentaint init-test-project` command |
| `cli/cmd/skills_path.go` | `opentaint skills-path` command |
| `agent-mode/skills/meta-prompt.md` | Agent system prompt |
| `agent-mode/skills/build-project.md` | Skill: build project |
| `agent-mode/skills/discover-entry-points.md` | Skill: discover entry points |
| `agent-mode/skills/create-rule.md` | Skill: create pattern rules |
| `agent-mode/skills/test-rule.md` | Skill: test rules |
| `agent-mode/skills/run-analysis.md` | Skill: run analysis |
| `agent-mode/skills/analyze-findings.md` | Skill: analyze SARIF findings |
| `agent-mode/skills/create-yaml-config.md` | Skill: create YAML passThrough config |
| `agent-mode/skills/create-approximation.md` | Skill: create code-based approximations |
| `agent-mode/skills/generate-poc.md` | Skill: generate proof-of-concept |

### Modified Files (16)

| File | Change Summary |
|------|----------------|
| `core/.../taint/TaintAnalysisUnitStorage.kt` | Add `externalMethodTracker` field |
| `core/.../taint/TaintAnalysisContext.kt` | Expose tracker from storage |
| `core/.../TaintAnalysisUnitRunnerManager.kt` | Wire tracker into unit storage creation |
| `core/.../JIRMethodCallFlowFunction.kt` | Report to tracker in `applyPassRulesOrCallSkip()` |
| `core/.../project/ProjectAnalyzer.kt` | New `PreloadedRules` variant, YAML output, combined config+rules |
| `core/.../project/ProjectAnalysisOptions.kt` | New fields: `externalMethodsOutput`, `semgrepRuleId` |
| `core/.../runner/ProjectAnalyzerRunner.kt` | 4 new Clikt flags |
| `core/.../dataflow/DataFlowApproximationLoader.kt` | `customApproximationPaths` in `Options` |
| `core/.../semgrep/pattern/SemgrepRuleLoader.kt` | Rule ID filter in `loadRules()` |
| `cli/cmd/root.go` | Hidden `--analyzer-jar`, `--autobuilder-jar` flags |
| `cli/cmd/scan.go` | 4 new flags: `--rule-id`, `--approximations-config`, `--dataflow-approximations`, `--external-methods` |
| `cli/cmd/command_builder.go` | 5 new `AnalyzerBuilder` methods + fields |
| `cli/internal/globals/global.go` | `JarPath` fields in `Analyzer`/`Autobuilder` config structs |
| `cli/internal/utils/opentaint_home.go` | `GetSkillsPath()` function |
| `.github/workflows/release-cli.yaml` | Bundle skills + test-util JAR |
| `.github/workflows/publish-analyzer.yaml` | Publish test-util JAR as release asset |
