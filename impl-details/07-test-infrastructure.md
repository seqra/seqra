# 7. Test Infrastructure Changes

---

## 7.1 `AnalysisTest` Enhancements

> **File**: `core/src/test/kotlin/org/opentaint/go/sast/dataflow/AnalysisTest.kt`

### 7.1.1 Multi-Package Build Support

Change `createClasspath()` to pass `"./..."` pattern for building all sub-packages:

```kotlin
private fun createClasspath(): GoIRProgram {
    return client.buildFromDir(sourcesDir, "./...")
}
```

This ensures sub-packages like `crosspkg/` are included in the IR.

### 7.1.2 Convenience Methods

Add shorthand methods for the standard source/sink pair:

```kotlin
abstract class AnalysisTest {

    // Standard rules (used by most tests)
    val stdSource = TaintRules.Source("test.source", "taint", Result)
    val stdSink = TaintRules.Sink("test.sink", "taint", Argument(0), "test-id")

    // Typed source variants
    val intSource = TaintRules.Source("test.sourceInt", "taint", Result)
    val floatSource = TaintRules.Source("test.sourceFloat", "taint", Result)
    val anySource = TaintRules.Source("test.sourceAny", "taint", Result)
    val boolSource = TaintRules.Source("test.sourceBool", "taint", Result)

    // Typed sink variants
    val anySink = TaintRules.Sink("test.sinkAny", "taint", Argument(0), "test-id")
    val intSink = TaintRules.Sink("test.sinkInt", "taint", Argument(0), "test-id")

    // Convenience: assert with default source/sink
    fun assertReachable(fn: String) = assertSinkReachable(stdSource, stdSink, fn)
    fun assertNotReachable(fn: String) = assertSinkNotReachable(stdSource, stdSink, fn)

    // Convenience: assert with typed source + any sink
    fun assertReachableAny(source: TaintRules.Source, fn: String) =
        assertSinkReachable(source, anySink, fn)
    fun assertNotReachableAny(source: TaintRules.Source, fn: String) =
        assertSinkNotReachable(source, anySink, fn)
```

### 7.1.3 `runAnalysis` with Pass Rules

Each test uses exactly **one source rule and one sink rule**. Multiple source/sink rules per test are not needed. The only extension over the existing `runAnalysis` is support for pass-through rules.

```kotlin
    // Existing (unchanged): single source/sink
    fun runAnalysis(
        source: TaintRules.Source,
        sink: TaintRules.Sink,
        entryPointFunction: String,
    ): List<TaintSinkTracker.TaintVulnerability> {
        // ... same as current implementation (see AnalysisTest.kt)
    }

    // New: with extra pass rules
    fun runAnalysis(
        source: TaintRules.Source,
        sink: TaintRules.Sink,
        entryPointFunction: String,
        passRules: List<TaintRules.Pass>,
    ): List<TaintSinkTracker.TaintVulnerability> {
        val entryPoint = cp.findFunctionByFullName(entryPointFunction)
            ?: error("Entry point not found: $entryPointFunction")

        val config = GoTaintConfig(listOf(source), listOf(sink), commonPassRules + passRules)
        val ifdsGraph = GoApplicationGraph(cp)

        @Suppress("UNCHECKED_CAST")
        val engine = TaintAnalysisUnitRunnerManager(
            GoAnalysisManager(cp),
            ifdsGraph as ApplicationGraph<CommonMethod, CommonInst>,
            unitResolver = { SingletonUnit },
            apManager = TreeApManager(anyAccessorUnrollStrategy = AnyAccessorUnrollStrategy.AnyAccessorDisabled),
            summarySerializationContext = DummySerializationContext,
            taintConfig = config,
            taintRulesStatsSamplingPeriod = null,
        )

        return engine.use { eng ->
            eng.runAnalysis(listOf(entryPoint), timeout = 1.minutes, cancellationTimeout = 10.seconds)
            eng.getVulnerabilities()
        }
    }

    // Assert helpers with pass rules
    fun assertReachableWithPass(fn: String, passRules: List<TaintRules.Pass>) {
        val vulns = runAnalysis(stdSource, stdSink, fn, passRules)
        assertTrue(vulns.isNotEmpty(), "Sink was not reached in $fn")
    }
    fun assertNotReachableWithPass(fn: String, passRules: List<TaintRules.Pass>) {
        val vulns = runAnalysis(stdSource, stdSink, fn, passRules)
        assertTrue(vulns.isEmpty(), "Sink should not be reached in $fn")
    }
```

---

## 7.2 `util.go` Updates

> **File**: `core/samples/src/main/go/util.go`

Extend with typed source/sink variants and collection sources:

```go
package test

// String source/sink (primary)
func source() string          { return "tainted" }
func sink(data string)        { consume(data) }
func consume(str string)      { _ = str }

// Typed sources for specific test categories
func sourceInt() int          { return 42 }
func sourceFloat() float64    { return 3.14 }
func sourceAny() interface{}  { return "tainted" }
func sourceBool() bool        { return true }

// Typed sinks
func sinkAny(data interface{})  { _ = data }
func sinkInt(data int)          { _ = data }

// Collection sources (whole container tainted via Result position)
func sourceSlice() []string       { return []string{"tainted"} }
func sourceMap() map[string]string { return map[string]string{"k": "tainted"} }

// Pass-through stubs (behavior configured via TaintRules.Pass in Kotlin)
func passthrough(data string) string           { return data }
func sanitize(data string) string              { return "clean" }
func transform(in1 string, in2 string) string  { return in2 }

// Multiple taint mark sources/sinks
func sourceA() string         { return "a" }
func sourceB() string         { return "b" }
func sinkA(data string)       { _ = data }
func sinkB(data string)       { _ = data }
```

### TaintRules for each:

| Go function | Rule Type | Configuration |
|-------------|-----------|---------------|
| `source` | Source | `("test.source", "taint", Result)` |
| `sourceInt` | Source | `("test.sourceInt", "taint", Result)` |
| `sourceFloat` | Source | `("test.sourceFloat", "taint", Result)` |
| `sourceAny` | Source | `("test.sourceAny", "taint", Result)` |
| `sourceBool` | Source | `("test.sourceBool", "taint", Result)` |
| `sourceSlice` | Source | `("test.sourceSlice", "taint", Result)` |
| `sourceMap` | Source | `("test.sourceMap", "taint", Result)` |
| `sink` | Sink | `("test.sink", "taint", Argument(0), "test-id")` |
| `sinkAny` | Sink | `("test.sinkAny", "taint", Argument(0), "test-id")` |
| `sinkInt` | Sink | `("test.sinkInt", "taint", Argument(0), "test-id")` |
| `sinkA` | Sink | `("test.sinkA", "markA", Argument(0), "test-markA")` |
| `sinkB` | Sink | `("test.sinkB", "markB", Argument(0), "test-markB")` |
| `sourceA` | Source | `("test.sourceA", "markA", Result)` |
| `sourceB` | Source | `("test.sourceB", "markB", Result)` |
| `passthrough` | Pass | `("test.passthrough", Argument(0) → Result)` |
| `transform` | Pass | `("test.transform", Argument(0) → Result)` |
| `sanitize` | — | No rule → call kills taint (unresolved call) |

---

## 7.3 Cross-Package Test Infrastructure

### Directory Layout

```
core/samples/src/main/go/
├── go.mod                    # module test
├── util.go                   # source(), sink(), typed variants
├── sample.go                 # existing tests
├── crosspkg/
│   └── helpers.go            # package crosspkg
└── crosspkg2/
    └── other.go              # package crosspkg2
```

### `go.mod` — No changes needed

The existing `module test` works for multi-package. Go's module system automatically resolves `import "test/crosspkg"` to the local `crosspkg/` directory.

### `crosspkg/helpers.go`

```go
package crosspkg

func PassThrough(data string) string { return data }
func DropTaint(data string) string   { return "safe" }

type Processor struct{ Data string }
func (p Processor) GetData() string  { return p.Data }
```

### Cross-package entry points

Cross-package test functions live in the root `package test` and import sub-packages:

```go
// cross_package.go
package test

import "test/crosspkg"

func crossPkg001T() {
    data := source()
    result := crosspkg.PassThrough(data)
    sink(result)
}
```

In Kotlin: `assertReachable("test.crossPkg001T")`.

The cross-package function `crosspkg.PassThrough` must either:
1. Be analyzed interprocedurally (body is available in the IR), OR
2. Have a pass-through rule configured

Since `"./..."` includes all sub-packages, option (1) works automatically.

---

## 7.4 JAR Packaging

The existing `build.gradle.kts` already includes `**/*.go` and `**/*.mod` from the Go source set. Subdirectories like `crosspkg/` are included automatically since they're under `src/main/go/`.

The `extractGoSourcesFromJar` method in `AnalysisTest` preserves directory structure:
```kotlin
val targetFile = targetDir.resolve(entry.name)
targetFile.parent.createDirectories()
```

So `crosspkg/helpers.go` in the JAR becomes `<tempDir>/crosspkg/helpers.go` on extraction.

---

## 7.5 Sample File Organization

All Go sample files are organized by test category as described in `go-dataflow-test-system.md` Section 7.1:

| File | Category | Phase |
|------|----------|-------|
| `util.go` | Infrastructure | — |
| `sample.go` | Existing (A) | 1 |
| `basic_flow.go` | Category A | 1 |
| `interprocedural.go` | Category B | 1 |
| `control_flow.go` | Category C | 1 |
| `expressions.go` | Category D | 1 |
| `defer_tests.go` | Category E | 1 |
| `cross_package.go` | Category F | 1 |
| `generics.go` | Category G | 1 |
| `pass_through.go` | Generated | 1 |
| `taint_marks.go` | Generated | 1 |
| `deep_calls.go` | Generated | 1 |
| `arg_position.go` | Generated | 1 |
| `multi_return.go` | Generated | 1 |
| `sanitization.go` | Generated | 1 |
| `interface_dispatch.go` | Category H | 2 |
| `field_sensitive.go` | Category I | 2 |
| `collections.go` | Category J | 2 |
| `pointers_aliases.go` | Category K | 2 |
| `struct_fields.go` | Generated | 2 |
| `method_receiver.go` | Generated | 2 |
| `closures.go` | Category L | 3 |
| `higher_order.go` | Category M | 3 |
| `goroutines_channels.go` | Category N | 3 |

---

## 7.6 Kotlin Test Class Organization

One class per category, all in `org.opentaint.go.sast.dataflow`:

```kotlin
// Phase 1
class BasicFlowTest : AnalysisTest()
class InterproceduralTest : AnalysisTest()
class ControlFlowTest : AnalysisTest()
class ExpressionTest : AnalysisTest()
class DeferTest : AnalysisTest()
class CrossPackageTest : AnalysisTest()
class GenericsTest : AnalysisTest()
class PassThroughTest : AnalysisTest()
class TaintMarkTest : AnalysisTest()
class SanitizationTest : AnalysisTest()

// Phase 2
class InterfaceDispatchTest : AnalysisTest()
class FieldSensitiveTest : AnalysisTest()
class CollectionTest : AnalysisTest()
class PointerAliasTest : AnalysisTest()

// Phase 3
class ClosureTest : AnalysisTest()
class HigherOrderTest : AnalysisTest()
class GoroutineChannelTest : AnalysisTest()
```

Each uses `@TestInstance(TestInstance.Lifecycle.PER_CLASS)` to share the Go IR program across tests in the same class.

### Example: `PassThroughTest`

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassThroughTest : AnalysisTest() {

    private val passRule = TaintRules.Pass(
        "test.passthrough",
        PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
        PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    )
    private val transformRule = TaintRules.Pass(
        "test.transform",
        PositionBaseWithModifiers.BaseOnly(PositionBase.Argument(0)),
        PositionBaseWithModifiers.BaseOnly(PositionBase.Result)
    )

    @Test fun passThrough001T() = assertReachableWithPass("test.passThrough001T", listOf(passRule))
    @Test fun passThrough002F() = assertNotReachable("test.passThrough002F")  // no rule → kills taint
    @Test fun passThrough003T() = assertReachableWithPass("test.passThrough003T", listOf(transformRule))
    @Test fun passThrough004F() {
        // Arg1 is tainted, but rule says Arg0→Result. Arg0 is clean.
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough004F", listOf(transformRule))
        assertTrue(vulns.isEmpty())
    }
}
```
