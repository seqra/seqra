# Go Dataflow Engine — Test System Design

> This document describes the test system design for the Go taint dataflow analysis engine.
> It covers: existing test infrastructure, benchmark adaptation strategy, generated test cases,
> and Kotlin test class organization.

---

## Table of Contents

1. [Current Test Infrastructure](#1-current-test-infrastructure)
2. [Benchmark Overview](#2-benchmark-overview)
3. [Adaptation Strategy](#3-adaptation-strategy)
4. [Test Categories & Inventory](#4-test-categories--inventory)
5. [Adapted Benchmark Tests](#5-adapted-benchmark-tests)
6. [Generated Test Cases](#6-generated-test-cases)
7. [Go Sample File Organization](#7-go-sample-file-organization)
8. [Kotlin Test Class Organization](#8-kotlin-test-class-organization)
9. [Implementation Plan](#9-implementation-plan)

---

## 1. Current Test Infrastructure

### 1.1 How Tests Work

The test system has three layers:

1. **Go sample files** (`core/samples/src/main/go/*.go`) — Go source code containing test functions
2. **Kotlin test base** (`AnalysisTest.kt`) — Builds Go IR from samples, runs the analysis engine
3. **Kotlin test classes** (`SampleTest.kt`) — Individual test methods asserting sink reachability

**Execution flow:**
```
Go source files → JAR (via Gradle) → GoIRClient.buildFromDir() → GoIRProgram
    → findFunctionByFullName(entryPoint) → TaintAnalysisUnitRunnerManager.runAnalysis()
    → getVulnerabilities() → assert reachable/not-reachable
```

### 1.2 Existing Test Base: `AnalysisTest`

```kotlin
abstract class AnalysisTest {
    // Setup: extracts .go files from JAR, builds GoIRProgram via GoIRClient
    // Provides:
    fun assertSinkReachable(source, sink, entryPointFunction)
    fun assertSinkNotReachable(source, sink, entryPointFunction)
    fun runAnalysis(source, sink, entryPointFunction): List<TaintVulnerability>
}
```

Key parameters per test:
- `source: TaintRules.Source` — function name, taint mark, position (e.g., `Result`)
- `sink: TaintRules.Sink` — function name, taint mark, position (e.g., `Argument(0)`), vulnerability ID
- `entryPointFunction: String` — fully qualified Go function name (e.g., `"test.sample"`)

### 1.3 Existing Samples

**`util.go`** — Source/sink stubs:
```go
package test

func source() string { return "tainted" }
func sink(data string) { consume(data) }
func consume(str string) { _ = str }
```

**`sample.go`** — Two test functions:
```go
package test

func sample() {              // _T: source() → data → other → sink(other) ✓
    var data = source()
    var other = data
    sink(other)
}

func sampleNonReachable() {  // _F: source() → data, but "safe" → other → sink(other) ✗
    var data = source()
    var other = "safe"
    sink(other)
    consume(data)
}
```

### 1.4 Existing Kotlin Tests

```kotlin
class SampleTest : AnalysisTest() {
    @Test fun sample() = assertSinkReachable(
        TaintRules.Source("test.source", "taint", Result),
        TaintRules.Sink("test.sink", "taint", Argument(0), "test-id"),
        "test.sample"
    )
    @Test fun sampleNonReachable() = assertSinkNotReachable(
        TaintRules.Source("test.source", "taint", Result),
        TaintRules.Sink("test.sink", "taint", Argument(0), "test-id"),
        "test.sampleNonReachable"
    )
}
```

### 1.5 Constraints

- All Go files must be in `package test` (module name is `test` in `go.mod`)
- Functions are referenced as `"test.<functionName>"` in Kotlin tests
- All Go files in the same directory → same package → type/function names must be unique across files
- The existing `source()` returns `string`; we may need additional typed source variants

---

## 2. Benchmark Overview

### 2.1 Source

The benchmark at `~/data/ant-application-security-testing-benchmark/sast-go/cases/` contains **344 test cases** (170 _T, 174 _F) in 362 `.go` files.

### 2.2 Benchmark Pattern

Every benchmark test follows this pattern:

```go
package main
import "os/exec"

func testCase(__taint_src string) {
    // ... data flow logic ...
    __taint_sink(result)
}

func __taint_sink(o interface{}) {
    _ = exec.Command("sh", "-c", o.(string)).Run()
}

func main() {
    __taint_src := "taint_src_value"
    testCase(__taint_src)
}
```

- **Source**: `__taint_src` parameter passed into the test function (entry-point style)
- **Sink**: `__taint_sink()` function with `exec.Command` (command injection)
- **`_T` suffix**: True positive — taint reaches sink (vulnerability exists)
- **`_F` suffix**: False positive — taint does NOT reach sink (no vulnerability)

### 2.3 Category Inventory

| Category | _T | _F | Total | Priority |
|----------|----|----|-------|----------|
| **accuracy/context_sensitive/argument_return_value_passing** | 11 | 11 | 22 | Phase 1 |
| **accuracy/context_sensitive/multi_invoke** | 1 | 1 | 2 | Phase 1 |
| **accuracy/context_sensitive/polymorphism** | 2 | 2 | 4 | Phase 1 |
| **accuracy/field_sensitive/struct** | 6 | 6 | 12 | Phase 2 |
| **accuracy/field_sensitive/one_dimensional_collection** | 8 | 9 | 17 | Phase 2 |
| **accuracy/field_sensitive/multidimensional_collection** | 2 | 2 | 4 | Phase 3 |
| **accuracy/flow_sensitive/loop_stmt** | 2 | 2 | 4 | Phase 1 |
| **accuracy/flow_sensitive/asynchronous** | 1 | 1 | 2 | Phase 3 |
| **accuracy/flow_sensitive/defer_exectution** | 1 | 1 | 2 | Phase 1 |
| **accuracy/object_sensitive/collection** | 8 | 7 | 15 | Phase 2 |
| **accuracy/object_sensitive/interface_class** | 6 | 6 | 12 | Phase 2 |
| **accuracy/object_sensitive/struct** | 6 | 6 | 12 | Phase 2 |
| **accuracy/path_sensitive/explicit_jump_control** | 6 | 6 | 12 | Phase 1 |
| **accuracy/path_sensitive/loop_conditional_stmt** | 5 | 9 | 14 | Phase 2 |
| completeness/single_app_tracing/datatype/* | 26 | 26 | 52 | Phase 1-2 |
| completeness/single_app_tracing/expression/* | 18 | 18 | 36 | Phase 1 |
| completeness/single_app_tracing/control_flow/* | 10 | 10 | 20 | Phase 1 |
| completeness/single_app_tracing/function_call/* | 31 | 31 | 62 | Phase 1-2 |
| completeness/single_app_tracing/alias | 1 | 1 | 2 | Phase 2 |
| completeness/single_app_tracing/cross_file_package_namespace | 8 | 8 | 16 | Phase 1 |
| completeness/single_app_tracing/asynchronous_tracing | 6 | 6 | 12 | Phase 3 |
| completeness/single_app_tracing/interface_class | 1 | 1 | 2 | Phase 2 |
| completeness/single_app_tracing/exception_error | 1 | 1 | 2 | Phase 3 |
| completeness/single_app_tracing/datatype/generics | 1 | 1 | 2 | Phase 1 |
| completeness/dynamic_tracing/reflect_call | 1 | 1 | 2 | Phase 3 |

**Total: 170 _T + 174 _F = 344 test cases**

---

## 3. Adaptation Strategy

### 3.1 Key Differences: Benchmark vs Our System

| Aspect | Benchmark | Our System |
|--------|-----------|------------|
| Source mechanism | `__taint_src` parameter (entry-point) | `source()` function call (call-site source rule) |
| Sink mechanism | `__taint_sink(x)` inline function | `sink(x)` function call (call-site sink rule) |
| Package | `package main` per file | `package test` shared |
| Entry point | `main()` calling test func | Test func itself is the entry point |
| Imports | `os/exec`, etc. | None needed |
| Types | May conflict (`type A struct`) | Must be unique across all files |

### 3.2 Rewriting Rules

Each benchmark test case requires the following transformations:

#### Rule 1: Replace entry-point source with `source()` call

**Before (benchmark):**
```go
func testCase(__taint_src string) {
    // uses __taint_src
}
```

**After (our system):**
```go
func testCase() {
    taintSrc := source()
    // uses taintSrc
}
```

- Remove `__taint_src` parameter from test function signature
- Add `taintSrc := source()` at the beginning of the function body
- Replace all occurrences of `__taint_src` with `taintSrc` in the function body
- For tests where `__taint_src` is passed to helper functions, the local variable works the same way

#### Rule 2: Replace `__taint_sink` with `sink()`

**Before:** `__taint_sink(result)`
**After:** `sink(result)`

- Remove the `__taint_sink` function definition
- Replace all `__taint_sink(x)` calls with `sink(x)`
- Note: some tests use `___taint_sink` (triple underscore) — normalize these

#### Rule 3: Change package and remove unnecessary code

- Change `package main` to `package test`
- Remove `import "os/exec"` and other unused imports
- Remove `func main() { ... }`
- Remove metadata comments (optional, can keep for reference)

#### Rule 4: Make type names unique

Many benchmark tests define types like `type A struct`. Since all files share `package test`, we must prefix type names:

**Before:** `type A struct { data string }`
**After:** `type StructField001A struct { data string }` (prefixed with test case name)

#### Rule 5: Handle typed sources

The benchmark uses `__taint_src` with various types: `string`, `interface{}`, `int`.

Our `source()` returns `string`. We need typed source variants:

```go
func source() string          { return "tainted" }
func sourceInt() int          { return 42 }
func sourceAny() interface{}  { return "tainted" }
```

And corresponding taint rules:
```kotlin
TaintRules.Source("test.source", "taint", Result)
TaintRules.Source("test.sourceInt", "taint", Result)
TaintRules.Source("test.sourceAny", "taint", Result)
```

### 3.3 Rewriting Example

**Benchmark (`struct_field_001_T.go`):**
```go
package main
import "os/exec"

type A struct {
    data string
    sani string
}

func struct_field_001_T(__taint_src string) {
    p := A{
        data: __taint_src,
        sani: "_",
    }
    __taint_sink(p.data)
}

func __taint_sink(o interface{}) {
    _ = exec.Command("sh", "-c", o.(string)).Run()
}

func main() {
    __taint_src := "taint_src_value"
    struct_field_001_T(__taint_src)
}
```

**Adapted (`field_sensitive.go`):**
```go
package test

type StructField001 struct {
    data string
    sani string
}

func structField001T() {
    taintSrc := source()
    p := StructField001{
        data: taintSrc,
        sani: "_",
    }
    sink(p.data)
}

func structField002F() {
    taintSrc := source()
    p := StructField001{
        data: taintSrc,
        sani: "_",
    }
    sink(p.sani)
}
```

### 3.4 Tests That Need Special Handling

Some benchmark categories need special handling or adaptation beyond the standard rewriting rules:

| Category | Issue | Approach |
|----------|-------|----------|
| **cross_file / cross_directory / cross_module** | Multi-file, multi-package tests | **Include** — cross-package analysis works. Use subdirectories under `core/samples/src/main/go/` with separate packages. The JAR extraction preserves directory structure, and `GoIRProgram.packages` is a multi-package map. `findFunctionByFullName` searches across all packages. May need to pass `"./..."` pattern to `buildFromDir`. |
| **reflect_call** | Reflection-based dispatch | Skip — not in MVP scope |
| **generics** | Go 1.18+ generics (type parameters) | **Include** — generics must be tested. Go IR supports instantiated generics. Adapt generic type tests with standard rewriting rules. |
| **library_function** | Requires stdlib propagation rules (`append`, `strconv.Itoa`, etc.) | Adapt using `TaintRules.Pass` rules to model stdlib propagation |
| **asynchronous / goroutine / channel** | Requires goroutine analysis | Skip for Phase 1; include in Phase 3 |
| **defer / panic-recover** | Requires defer analysis | **Include in Phase 1** — defer support is required. Defer tests verify correct ordering (deferred code runs after surrounding code). |

### 3.5 Cross-Package Test Adaptation

Cross-package tests require a different adaptation strategy than single-file tests, because they involve multiple Go packages in separate directories.

**Directory layout for cross-package tests:**
```
core/samples/src/main/go/
├── go.mod                    # module test
├── util.go                   # source(), sink() in package test
├── sample.go                 # existing tests
├── crosspkg/                 # sub-package for cross-package tests
│   └── helpers.go            # package crosspkg — helper functions called from test
└── crosspkg2/
    └── other.go              # package crosspkg2 — another sub-package
```

**Rewriting rules for cross-package tests:**
1. The main test function stays in `package test` (root), calling functions from sub-packages
2. Helper functions go in sub-packages (e.g., `package crosspkg`)
3. The main package imports the sub-package: `import "test/crosspkg"`
4. Function names in sub-packages must be exported (capitalized)

**Example adaptation of `cross_directory_001_T`:**

```go
// core/samples/src/main/go/crosspkg/helpers.go
package crosspkg

func PassThrough(data string) string {
    return data
}

func DropTaint(data string) string {
    return "safe"
}
```

```go
// core/samples/src/main/go/cross_package.go
package test

import "test/crosspkg"

func crossPkg001T() {
    data := source()
    result := crosspkg.PassThrough(data)
    sink(result)
}

func crossPkg002F() {
    data := source()
    result := crosspkg.DropTaint(data)
    sink(result)
}
```

**Kotlin test entry point** uses fully-qualified names: `"test.crossPkg001T"`.

**Note**: `AnalysisTest.createClasspath()` currently calls `client.buildFromDir(sourcesDir)` with no patterns. For multi-package support, this should be changed to `client.buildFromDir(sourcesDir, "./...")` to ensure all sub-packages are built.

---

## 4. Test Categories & Inventory

### 4.1 Phase 1: MVP Tests (must pass with initial implementation)

These test basic dataflow features that the Phase 1 engine implementation supports.

#### Category A: Basic Intraprocedural Flow
Tests that source → variable → sink flow works within a single function.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `sample` | `test.sample` | Reachable | Basic: `source() → var → copy → sink()` |
| `sampleNonReachable` | `test.sampleNonReachable` | Not Reachable | Overwrite: `source() → var`, `"safe" → other → sink()` |
| `stringDirect` | `test.stringDirect` | Reachable | Direct: `source() → sink()` |
| `assignExpression001T` | `test.assignExpression001T` | Reachable | Assignment expression |
| `assignExpression002F` | `test.assignExpression002F` | Not Reachable | Assignment overwrite |

#### Category B: Interprocedural Flow (Direct Calls)
Tests that taint flows through function calls and return values.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `returnValuePassing001F` | `test.returnValuePassing001F` | Not Reachable | Return value overwritten in callee |
| `returnValuePassing002T` | `test.returnValuePassing002T` | Reachable | Return value passes taint |
| `argPassing002T` | `test.argPassing002T` | Reachable | Argument passes taint to callee |
| `argPassing001F` | `test.argPassing001F` | Not Reachable | Argument overwritten in callee |
| `argPassing006T` | `test.argPassing006T` | Reachable | Correct arg (arg1) reaches sink |
| `argPassing005F` | `test.argPassing005F` | Not Reachable | Wrong arg (arg2) reaches sink |
| `multiReturn002T` | `test.multiReturn002T` | Reachable | Multiple return: tainted value used |
| `multiReturn001F` | `test.multiReturn001F` | Not Reachable | Multiple return: clean value used |
| `namedReturn002T` | `test.namedReturn002T` | Reachable | Named return value |
| `namedReturn001F` | `test.namedReturn001F` | Not Reachable | Named return, taint not assigned |
| `multiInvoke001F` | `test.multiInvoke001F` | Not Reachable | Same function called twice; clean call's result at sink |
| `multiInvoke002T` | `test.multiInvoke002T` | Reachable | Same function called twice; tainted call's result at sink |

#### Category C: Control Flow
Tests that taint propagates correctly through branches and loops.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `conditionalIf001T` | `test.conditionalIf001T` | Reachable | if-true branch |
| `conditionalIf002F` | `test.conditionalIf002F` | Not Reachable | if-false branch |
| `conditionalSwitch001T` | `test.conditionalSwitch001T` | Reachable | switch-case |
| `conditionalSwitch002F` | `test.conditionalSwitch002F` | Not Reachable | switch-case (clean path) |
| `forBody001T` | `test.forBody001T` | Reachable | for loop body |
| `forBody002F` | `test.forBody002F` | Not Reachable | for loop body (overwrite) |
| `break001T` | `test.break001T` | Reachable | break in loop |
| `break002F` | `test.break002F` | Not Reachable | break before taint |

#### Category D: Expressions & Type Casts
Tests taint propagation through various expressions.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `typeCast001T` | `test.typeCast001T` | Reachable | `float64(taintedInt)` preserves taint |
| `typeCast002F` | `test.typeCast002F` | Not Reachable | Type cast on clean value |
| `multipleAssignment001T` | `test.multipleAssignment001T` | Reachable | `_, _, result := ...` |
| `blankIdentifier001T` | `test.blankIdentifier001T` | Reachable | `a, _ := getData()` |

#### Category E: Defer

Tests that defer execution order is handled correctly.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `defer001T` | `test.defer001T` | Reachable | Taint read before deferred overwrite runs |
| `defer002F` | `test.defer002F` | Not Reachable | Taint written in defer, but sink executes before defer |
| `panicRecover001T` | `test.panicRecover001T` | Reachable | `panic(tainted)` → `recover()` in defer |
| `panicRecover002F` | `test.panicRecover002F` | Not Reachable | `panic("safe")` → `recover()` in defer |

#### Category F: Cross-Package

Tests taint flow across package boundaries.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `crossFile001T` | `test.crossFile001T` | Reachable | Same package, different files |
| `crossFile002F` | `test.crossFile002F` | Not Reachable | Same package, callee ignores tainted arg |
| `crossPkg001T` | `test.crossPkg001T` | Reachable | Different package, taint flows through |
| `crossPkg002F` | `test.crossPkg002F` | Not Reachable | Different package, callee drops taint |
| `crossPkgDeep001T` | `test.crossPkgDeep001T` | Reachable | Nested package (pkg/sub), taint flows |
| `crossPkgDeep002F` | `test.crossPkgDeep002F` | Not Reachable | Nested package, taint dropped |

#### Category G: Generics

Tests taint flow through generic types and functions.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `generics001T` | `test.generics001T` | Reachable | Generic slice `Slice[string]` with tainted element |
| `generics002F` | `test.generics002F` | Not Reachable | Generic slice, tainted element overwritten |
| `genericFunc001T` | `test.genericFunc001T` | Reachable | Generic function `Identity[T]()` preserves taint |
| `genericFunc002F` | `test.genericFunc002F` | Not Reachable | Generic function returns clean value |

### 4.2 Phase 2: Enhanced Tests

#### Category H: Interface Dispatch (INVOKE)
Tests taint flow through interface method calls.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `polymorphism001T` | `test.polymorphism001T` | Reachable | Interface call → Sub1 returns src |
| `polymorphism002F` | `test.polymorphism002F` | Not Reachable | Interface call → Sub2 returns "_" |
| `polymorphism003T` | `test.polymorphism003T` | Reachable | Different Sub1 assigned |
| `polymorphism004F` | `test.polymorphism004F` | Not Reachable | Different Sub2 assigned |
| `interfaceClass001F` | `test.interfaceClass001F` | Not Reachable | Interface: process2 returns "_" |
| `interfaceClass002T` | `test.interfaceClass002T` | Reachable | Interface: process returns data |

#### Category I: Field Sensitivity (Structs)
Tests that the engine distinguishes struct fields.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `structField001T` | `test.structField001T` | Reachable | `p.data` tainted → `sink(p.data)` |
| `structField002F` | `test.structField002F` | Not Reachable | `p.data` tainted → `sink(p.sani)` |
| `structField003T` | `test.structField003T` | Reachable | Tainted via setter method |
| `structField004F` | `test.structField004F` | Not Reachable | Clean field via setter method |
| `structDeep3_001T` | `test.structDeep3_001T` | Reachable | Nested struct: `obj.a.b.c` tainted |
| `structDeep3_002F` | `test.structDeep3_002F` | Not Reachable | Different object, same struct type |
| `structNormal001T` | `test.structNormal001T` | Reachable | Whole struct tainted |
| `structNormal002F` | `test.structNormal002F` | Not Reachable | Different object is clean |

#### Category J: Collections (Slices, Arrays, Maps)

**Important: Collection sensitivity model.** The analysis is **not** sensitive to specific map keys or array indices. However, the analysis **must** distinguish between a tainted collection element and the collection container itself. For example:
- `a[0] = source()` → `a[0]` is tainted (element), but `a` as a whole is not tainted
- `sink(a[0])` → reachable (reading tainted element)
- `sink(a)` → **not** reachable (the container itself is not tainted, only an element is)
- `sink(a[1])` → reachable (key-insensitive: any element access on a container with a tainted element is tainted)

This means: writing to `a["key1"]` taints the element-level of `a` (via `ElementAccessor`), and reading any `a[<key>]` reads the element-level. But `a` itself (without element access) is the container base and is NOT tainted.

Tests for collections must verify this model:

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `sliceElem001T` | `test.sliceElem001T` | Reachable | `s[0] = tainted`, `sink(s[0])` — element tainted |
| `sliceElem002T` | `test.sliceElem002T` | Reachable | `s[0] = tainted`, `sink(s[1])` — key-insensitive, still tainted |
| `sliceContainer001F` | `test.sliceContainer001F` | Not Reachable | `s[0] = tainted`, `sink(s)` — container not tainted |
| `sliceWholeAssign001T` | `test.sliceWholeAssign001T` | Reachable | `s = sourceSlice()`, `sink(s)` — whole slice tainted |
| `mapElem001T` | `test.mapElem001T` | Reachable | `m["k1"] = tainted`, `sink(m["k1"])` — element tainted |
| `mapElem002T` | `test.mapElem002T` | Reachable | `m["k1"] = tainted`, `sink(m["k2"])` — key-insensitive |
| `mapContainer001F` | `test.mapContainer001F` | Not Reachable | `m["k1"] = tainted`, `sink(m)` — container not tainted |
| `arrayElem001T` | `test.arrayElem001T` | Reachable | `a[0] = tainted`, `sink(a[0])` — element tainted |
| `arrayContainer001F` | `test.arrayContainer001F` | Not Reachable | `a[0] = tainted`, `sink(a)` — container not tainted |

#### Category K: Pointers & Aliases
Tests pointer-based taint flow and aliasing.

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `pointer001T` | `test.pointer001T` | Reachable | `&x` → `*p` preserves taint |
| `pointer002F` | `test.pointer002F` | Not Reachable | Pointer to clean value |
| `alias001F` | `test.alias001F` | Not Reachable | Alias overwrite: `b.Value = "_"` clears `a.Value` |
| `alias002T` | `test.alias002T` | Reachable | Alias write: `b.Value = tainted` taints `a.Value` |
| `argPassingRef001F` | `test.argPassingRef001F` | Not Reachable | Map ref: callee overwrites |
| `argPassingRef002T` | `test.argPassingRef002T` | Reachable | Map ref: callee writes taint |

### 4.3 Phase 3: Advanced Tests

#### Category L: Closures & Anonymous Functions

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `anonFunc002T` | `test.anonFunc002T` | Reachable | Anonymous func: returns `a + b` |
| `anonFunc001F` | `test.anonFunc001F` | Not Reachable | Anonymous func: returns "safe" |
| `closure002T` | `test.closure002T` | Reachable | Closure captures tainted var |
| `closure001F` | `test.closure001F` | Not Reachable | Closure captures overwritten var |
| `closure004T` | `test.closure004T` | Reachable | Second-order closure |
| `closure003F` | `test.closure003F` | Not Reachable | Second-order closure, overwritten |

#### Category M: Higher-Order Functions

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `higherOrder002T` | `test.higherOrder002T` | Reachable | HOF: taint flows through callback |
| `higherOrder001F` | `test.higherOrder001F` | Not Reachable | HOF: callback ignores taint |
| `higherOrder008T` | `test.higherOrder008T` | Reachable | Anonymous func as param |
| `higherOrder007F` | `test.higherOrder007F` | Not Reachable | Anonymous func returns "safe" |

#### Category N: Goroutines & Channels

| Test | Go Function | Expected | What It Tests |
|------|-------------|----------|---------------|
| `goroutineChannel001T` | `test.goroutineChannel001T` | Reachable | goroutine + channel send/receive |
| `goroutineChannel002F` | `test.goroutineChannel002F` | Not Reachable | Channel with clean data |
| `channel001T` | `test.channel001T` | Reachable | Buffered channel |
| `asyncExec001T` | `test.asyncExec001T` | Reachable | Goroutine worker via channel |

---

## 5. Adapted Benchmark Tests

### 5.1 Full Adaptation List (Phase 1 Priority)

From the benchmark, the following test cases should be adapted first (they test features the MVP engine should support):

**Interprocedural / Return Value Passing (22 tests):**
- `argument_passing_reference_001_F` through `_008_T` → 8 tests
- `argument_passing_value_return_001_F` through `_006_T` → 6 tests
- `multiple_return_value_passing_001_F` through `_004_T` → 4 tests
- `named_return_value_passing_001_F`, `_002_T` → 2 tests
- `return_normal_value_passing_001_F`, `_002_T` → 2 tests

**Context Sensitivity (6 tests):**
- `multi_invoke_001_F`, `_002_T` → 2 tests
- `polymorphism_001_T` through `_004_F` → 4 tests

**Control Flow (20 tests):**
- `conditional_if_001_T` through `_004_F` → 4 tests
- `conditional_switch_001_T` through `_006_F` → 6 tests
- `for_body_001_T`, `_002_F` → 2 tests
- `for_init_001_T`, `_002_F` → 2 tests
- `for_range_001_T`, `_002_F` → 2 tests
- `for_update_001_T`, `_002_F` → 2 tests
- `select_001_T`, `_002_F` → 2 tests

**Expressions (36 tests):**
- `assign_expression_001_T`, `_002_F` → 2 tests
- `binary_expression_add_001_T`, `_002_F` → 2 tests
- `binary_expression_add_assignment_001_T`, `_002_F` → 2 tests
- All bitwise, logic, relation expressions → 18 tests
- `type_cast_001_T` through `_004_F` → 4 tests
- `multiple_assignment_001_T`, `_002_F` → 2 tests
- `blank_identifier_001_T`, `_002_F` → 2 tests

**Basic Data Types (36 tests):**
- `string_001_T` through `_004_F` → 4 tests
- `primitives_int_001_T`, `_002_F` → 2 tests (+ bool, float, complex)
- `pointer_001_T` through `pointer_new_002_F` → 4 tests
- `struct_001_T` through `struct_cross_004_F` → 12 tests
- `array_001_T` through `_008_F` → 8 tests
- `slice_001_T` through `_008_F` → 8 tests
- `map_001_T` through `_004_F` → 4 tests

**Defer (4 tests):**
- `defer_exectution_001_T`, `_002_F` → 2 tests
- `exception_throw_001_T`, `_002_F` → 2 tests (panic/recover through defer)

**Cross-Package (16 tests):**
- `cross_file_001_T`, `_002_F` → 2 tests (same package, different files)
- `cross_directory_001_T` through `_010_F` → 10 tests (different packages)
- `cross_module_001_T` through `_004_F` → 4 tests (different Go modules)

**Generics (2 tests):**
- `generics_001_T`, `_002_F` → 2 tests (generic type `Slice[T]`)

**Total Phase 1 adapted: ~142 test cases**

### 5.2 Phase 2 Adaptation List

**Field Sensitivity (33 tests):**
- `struct_field_001_T` through `_006_F` → 6 tests
- `field_len_001_T` through `_006_F` → 6 tests
- All slice/array/map index sensitivity tests → 21 tests

**Object Sensitivity (39 tests):**
- `interface_class_001_F` through `_012_T` → 12 tests
- `struct_normal/deep3/deep5/deep10` tests → 12 tests
- Collection object sensitivity → 15 tests

**Path Sensitivity (26 tests):**
- Explicit jump control → 12 tests
- Conditional stmt (no solver) → 8 tests
- Conditional stmt (solver) → 6 tests

**Alias (2 tests):**
- `alias_001_F`, `alias_002_T`

**Total Phase 2 adapted: ~100 test cases**

### 5.3 Phase 3 Adaptation List

**Closures / Anonymous Functions (18 tests)**
**Higher-Order Functions (8 tests)**
**Chained Calls (4 tests)**
**Goroutines / Channels (12 tests)**
**Reflection (2 tests)** — requires reflect analysis

**Total Phase 3 adapted: ~44 test cases**

> **Note**: Defer tests (4) and cross-package tests (16) moved to Phase 1. Generics tests (2) added to Phase 1.

---

## 6. Generated Test Cases

Beyond the benchmark, we need test cases for features that the benchmark doesn't cover or covers insufficiently. These are generated from scratch, tailored to our engine's design.

### 6.1 Pass-Through Rules (TaintRules.Pass)

The benchmark doesn't test configurable pass-through rules. We need to verify that `TaintRules.Pass` works correctly.

```go
// util_passthrough.go
package test

func passthrough(data string) string { return data }      // configured as pass-through
func sanitize(data string) string { return "clean" }       // NOT configured as pass-through
func transform(in string, out string) string { return out } // pass-through from arg1 to result
```

| Test | Expected | Pass Rule | What It Tests |
|------|----------|-----------|---------------|
| `passThrough001T` | Reachable | `Pass("test.passthrough", Argument(0), Result)` | Basic pass-through |
| `passThrough002F` | Not Reachable | (no rule for `sanitize`) | Unresolved call kills taint |
| `passThrough003T` | Reachable | `Pass("test.transform", Argument(0), Result)` | Pass from specific arg |
| `passThrough004F` | Not Reachable | `Pass("test.transform", Argument(0), Result)` | Arg1 not tainted, only arg0 |

```go
// pass_through.go
package test

func passThrough001T() {
    data := source()
    result := passthrough(data)
    sink(result)
}

func passThrough002F() {
    data := source()
    result := sanitize(data)
    sink(result)
}

func passThrough003T() {
    data := source()
    result := transform(data, "other")
    sink(result)
}

func passThrough004F() {
    result := transform("clean", source())
    sink(result)
}
```

### 6.2 Multiple Taint Marks

Test that the engine correctly matches taint marks between sources and sinks.

```go
// taint_marks.go
package test

func sourceA() string { return "a" }  // configured with mark "markA"
func sourceB() string { return "b" }  // configured with mark "markB"
func sinkA(data string) { _ = data }  // configured to check mark "markA"
func sinkB(data string) { _ = data }  // configured to check mark "markB"

func taintMarkMatch001T() {
    data := sourceA()
    sinkA(data)          // markA → sinkA checks markA → match
}

func taintMarkMismatch001F() {
    data := sourceA()
    sinkB(data)          // markA → sinkB checks markB → no match
}

func taintMarkMatch002T() {
    data := sourceB()
    sinkB(data)          // markB → sinkB checks markB → match
}
```

**Kotlin test:**
```kotlin
@Test fun taintMarkMatch001T() = assertSinkReachable(
    TaintRules.Source("test.sourceA", "markA", Result),
    TaintRules.Sink("test.sinkA", "markA", Argument(0), "test-id"),
    "test.taintMarkMatch001T"
)

@Test fun taintMarkMismatch001F() = assertSinkNotReachable(
    TaintRules.Source("test.sourceA", "markA", Result),
    TaintRules.Sink("test.sinkB", "markB", Argument(0), "test-id"),
    "test.taintMarkMismatch001F"
)
```

### 6.3 Interprocedural: Deep Call Chains

Test taint propagation through multiple levels of function calls.

```go
// deep_calls.go
package test

func identity(x string) string { return x }
func identityChain(x string) string { return identity(x) }
func identityChainDeep(x string) string { return identityChain(x) }

func deepCall001T() {
    data := source()
    result := identity(data)
    sink(result)
}

func deepCall002T() {
    data := source()
    result := identityChain(data)
    sink(result)
}

func deepCall003T() {
    data := source()
    result := identityChainDeep(data)
    sink(result)
}

func deepCallClean001F() {
    data := source()
    _ = data
    result := identity("safe")
    sink(result)
}
```

### 6.4 Interprocedural: Argument Position Sensitivity

Test that taint tracks the correct argument through functions with multiple parameters.

```go
// arg_position.go
package test

func selectFirst(a string, b string) string { return a }
func selectSecond(a string, b string) string { return b }
func swap(a string, b string) (string, string) { return b, a }

func argPosition001T() {
    data := source()
    result := selectFirst(data, "clean")
    sink(result)
}

func argPosition002F() {
    data := source()
    result := selectSecond(data, "clean")
    sink(result)
}

func argPosition003T() {
    data := source()
    _, second := swap(data, "clean")  // swap: second = a = tainted
    sink(second)
}

func argPosition004F() {
    data := source()
    first, _ := swap(data, "clean")   // swap: first = b = clean
    sink(first)
}
```

### 6.5 Interface Dispatch: Structural Subtyping

Test Go's structural (duck-typing) interface dispatch.

```go
// interface_dispatch.go
package test

type IDReader interface {
    Read() string
}

type TaintedReader struct{ data string }
func (r TaintedReader) Read() string { return r.data }

type CleanReader struct{}
func (r CleanReader) Read() string { return "clean" }

func readFromInterface(r IDReader) string {
    return r.Read()
}

func interfaceDispatch001T() {
    data := source()
    var r IDReader = TaintedReader{data: data}
    result := readFromInterface(r)
    sink(result)
}

func interfaceDispatch002F() {
    data := source()
    _ = data
    var r IDReader = CleanReader{}
    result := readFromInterface(r)
    sink(result)
}
```

### 6.6 Struct Field Sensitivity: Read & Write

```go
// struct_fields.go
package test

type SFPair struct {
    tainted string
    clean   string
}

func structFieldRead001T() {
    data := source()
    p := SFPair{tainted: data, clean: "safe"}
    sink(p.tainted)
}

func structFieldRead002F() {
    data := source()
    p := SFPair{tainted: data, clean: "safe"}
    sink(p.clean)
}

func structFieldWrite001T() {
    data := source()
    p := SFPair{}
    p.tainted = data
    sink(p.tainted)
}

func structFieldWrite002F() {
    data := source()
    p := SFPair{}
    p.tainted = data
    sink(p.clean)
}

// Test field sensitivity through function calls
func getPairTainted(p SFPair) string { return p.tainted }
func getPairClean(p SFPair) string { return p.clean }

func structFieldInterproc001T() {
    data := source()
    p := SFPair{tainted: data, clean: "safe"}
    result := getPairTainted(p)
    sink(result)
}

func structFieldInterproc002F() {
    data := source()
    p := SFPair{tainted: data, clean: "safe"}
    result := getPairClean(p)
    sink(result)
}
```

### 6.7 Collections: Element vs Container Sensitivity

**Key principle**: The analysis is NOT sensitive to specific map keys or array/slice indices.
All element-level operations use a single `ElementAccessor`, so writing to `a[0]` taints the
same abstract location as `a[1]`. However, the analysis MUST distinguish between the
element-level (`a[*]`) and the container itself (`a`). Writing to an element does NOT taint the
container; it only taints the element-level. Reading the container does NOT read the elements.

```go
// collections.go
package test

// --- Slice: element vs container ---

func sliceElem001T() {
    data := source()
    s := make([]string, 3)
    s[0] = data
    sink(s[0])          // Reachable: reading element from container with tainted element
}

func sliceElem002T() {
    data := source()
    s := make([]string, 3)
    s[0] = data
    sink(s[1])          // Reachable: key-insensitive, any element access reads the tainted element-level
}

func sliceContainer001F() {
    data := source()
    s := make([]string, 3)
    s[0] = data
    sinkAny(s)          // NOT Reachable: the container `s` itself is not tainted, only its elements
}

func sliceWholeAssign001T() {
    s := sourceSlice()   // whole slice returned as tainted
    sinkAny(s)           // Reachable: the container itself is tainted
}

// --- Map: element vs container ---

func mapElem001T() {
    data := source()
    m := make(map[string]string)
    m["key1"] = data
    sink(m["key1"])     // Reachable: element tainted
}

func mapElem002T() {
    data := source()
    m := make(map[string]string)
    m["key1"] = data
    sink(m["key2"])     // Reachable: key-insensitive, any element read hits the tainted element-level
}

func mapContainer001F() {
    data := source()
    m := make(map[string]string)
    m["key1"] = data
    sinkAny(m)          // NOT Reachable: the container `m` itself is not tainted
}

// --- Array: element vs container ---

func arrayElem001T() {
    data := source()
    var a [3]string
    a[0] = data
    sink(a[0])          // Reachable: element tainted
}

func arrayContainer001F() {
    data := source()
    var a [3]string
    a[0] = data
    sinkAny(a)          // NOT Reachable: the array container is not tainted
}

// --- Append propagation (requires TaintRules.Pass for append) ---

func sliceAppend001T() {
    data := source()
    s := []string{"clean"}
    s = append(s, data)
    sink(s[0])          // Reachable: append merges taint to element-level of the result slice
}
```

**Note on `sourceSlice()`**: Needs a new util function that returns a slice where the whole
container is tainted (via `Result` position), not just an element:

```go
func sourceSlice() []string { return []string{"tainted"} }
```

### 6.8 Method Receivers

Test taint propagation through method receivers (value vs pointer).

```go
// method_receiver.go
package test

type MRContainer struct {
    value string
}

func (c MRContainer) getValue() string { return c.value }
func (c *MRContainer) setValue(v string) { c.value = v }

func methodReceiver001T() {
    data := source()
    c := MRContainer{value: data}
    result := c.getValue()
    sink(result)
}

func methodReceiverPtr001T() {
    data := source()
    c := &MRContainer{}
    c.setValue(data)
    result := c.getValue()
    sink(result)
}
```

### 6.9 Multiple Return Values

```go
// multi_return.go
package test

func twoReturns(a string, b string) (string, string) {
    return a, b
}

func multiReturn001T() {
    data := source()
    first, _ := twoReturns(data, "clean")
    sink(first)
}

func multiReturn002F() {
    data := source()
    _, second := twoReturns(data, "clean")
    sink(second)
}

func multiReturnErr001T() {
    data := source()
    result, err := twoReturns(data, "")
    _ = err
    sink(result)
}
```

### 6.10 Generics

Test taint propagation through Go 1.18+ generic types and functions.

```go
// generics.go
package test

// Generic identity function
func Identity[T any](x T) T { return x }

// Generic container
type Box[T any] struct {
    value T
}

func (b Box[T]) Get() T { return b.value }

func genericFunc001T() {
    data := source()
    result := Identity(data)
    sink(result)
}

func genericFunc002F() {
    data := source()
    _ = data
    result := Identity("safe")
    sink(result)
}

func genericBox001T() {
    data := source()
    b := Box[string]{value: data}
    sink(b.Get())
}

func genericBox002F() {
    data := source()
    b := Box[string]{value: "safe"}
    _ = data
    sink(b.Get())
}
```

### 6.11 Defer Execution Order

Test that analysis correctly models defer execution semantics: deferred calls execute
after surrounding code but before the function returns.

```go
// defer_tests.go
package test

func defer001T() {
    data := source()
    defer func() {
        data = "safe"       // runs after sink, before function returns
    }()
    sink(data)              // Reachable: sink executes BEFORE defer
}

func defer002F() {
    data := "safe"
    defer func() {
        data = source()     // taint written in defer
    }()
    sink(data)              // NOT Reachable: sink executes BEFORE defer, `data` is still "safe"
}

func deferArg001T() {
    data := source()
    defer sink(data)        // defer evaluates args immediately: `data` is tainted NOW
}

func deferArg002F() {
    data := source()
    data = "safe"
    defer sink(data)        // defer evaluates args immediately: `data` is already "safe"
}
```

### 6.12 Cross-Package Tests

See section 3.5 for the detailed adaptation strategy. Generated cross-package tests:

```go
// core/samples/src/main/go/crosspkg/helpers.go
package crosspkg

func PassThrough(data string) string { return data }
func DropTaint(data string) string { return "safe" }

type Processor struct{ Data string }
func (p Processor) GetData() string { return p.Data }
```

```go
// core/samples/src/main/go/cross_package.go
package test

import "test/crosspkg"

func crossPkg001T() {
    data := source()
    result := crosspkg.PassThrough(data)
    sink(result)
}

func crossPkg002F() {
    data := source()
    result := crosspkg.DropTaint(data)
    sink(result)
}

func crossPkgStruct001T() {
    data := source()
    p := crosspkg.Processor{Data: data}
    sink(p.GetData())
}
```

### 6.13 Taint Killing / Sanitization

```go
// sanitization.go
package test

func killByOverwrite001F() {
    data := source()
    data = "safe"
    sink(data)
}

func killByReassign001F() {
    data := source()
    other := data
    other = "safe"
    sink(other)
}

func noKill001T() {
    data := source()
    other := data
    data = "safe"  // kills data but not other
    sink(other)
}
```

---

## 7. Go Sample File Organization

All Go sample files live in `core/samples/src/main/go/` under `package test`.

### 7.1 File Structure

```
core/samples/src/main/go/
├── go.mod                          # module test
├── util.go                         # source(), sink(), consume() + typed variants
├── sample.go                       # Existing: sample(), sampleNonReachable()
│
│   # Adapted benchmark tests (organized by category):
├── basic_flow.go                   # Category A: basic intraprocedural
├── interprocedural.go              # Category B: argument/return passing
├── control_flow.go                 # Category C: if/switch/for/range
├── expressions.go                  # Category D: type casts, binary ops
├── defer_tests.go                  # Category E: defer execution order
├── cross_package.go                # Category F: cross-package (calls into crosspkg/)
├── generics.go                     # Category G: generic types and functions
├── interface_dispatch.go           # Category H: polymorphism, INVOKE
├── field_sensitive.go              # Category I: struct fields
├── collections.go                  # Category J: slices, arrays, maps (element vs container)
├── pointers_aliases.go             # Category K: pointers, aliasing
├── closures.go                     # Category L: closures, anonymous funcs
├── higher_order.go                 # Category M: higher-order functions
├── goroutines_channels.go          # Category N: goroutines, channels
│
│   # Sub-packages for cross-package tests:
├── crosspkg/
│   └── helpers.go                  # package crosspkg — PassThrough, DropTaint, Processor
├── crosspkg2/
│   └── other.go                    # package crosspkg2 — additional cross-package targets
│
│   # Generated tests (our own, not from benchmark):
├── pass_through.go                 # Pass-through rules tests
├── taint_marks.go                  # Multiple taint mark tests
├── deep_calls.go                   # Deep call chain tests
├── arg_position.go                 # Argument position sensitivity
├── struct_fields.go                # Struct field sensitivity (detailed)
├── method_receiver.go              # Method receiver tests
├── multi_return.go                 # Multiple return value tests
└── sanitization.go                 # Taint killing / sanitization tests
```

### 7.2 Updated `util.go`

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

// Pass-through stubs (behavior configured via TaintRules.Pass)
func passthrough(data string) string       { return data }
func sanitize(data string) string          { return "clean" }
func transform(in string, out string) string { return out }

// Multiple taint mark sources/sinks
func sourceA() string         { return "a" }
func sourceB() string         { return "b" }
func sinkA(data string)       { _ = data }
func sinkB(data string)       { _ = data }
```

### 7.3 Naming Conventions

- **Go function names**: camelCase, matching the Kotlin test method name (e.g., `structField001T`)
- **Go type names**: PascalCase, prefixed to avoid conflicts (e.g., `SFPair` for struct_fields, `IDReader` for interface_dispatch)
- **Type prefix convention**: Use 2-3 letter acronym of the category + descriptive name
  - `SF` = Struct Fields, `MR` = Method Receiver, `ID` = Interface Dispatch, etc.

---

## 8. Kotlin Test Class Organization

### 8.1 Test Class Structure

One Kotlin test class per category, all extending `AnalysisTest`:

```
core/src/test/kotlin/org/opentaint/go/sast/dataflow/
├── AnalysisTest.kt              # Base class (existing)
├── SampleTest.kt                # Existing 2 tests
│
│   # Phase 1 tests:
├── BasicFlowTest.kt             # Category A
├── InterproceduralTest.kt       # Category B
├── ControlFlowTest.kt           # Category C
├── ExpressionTest.kt            # Category D
├── DeferTest.kt                 # Category E: defer execution order
├── CrossPackageTest.kt          # Category F: cross-package analysis
├── GenericsTest.kt              # Category G: generic types/functions
├── PassThroughTest.kt           # Pass-through rules
├── TaintMarkTest.kt             # Multiple taint marks
├── SanitizationTest.kt          # Taint killing
│
│   # Phase 2 tests:
├── InterfaceDispatchTest.kt     # Category H
├── FieldSensitiveTest.kt        # Category I
├── CollectionTest.kt            # Category J: element vs container sensitivity
├── PointerAliasTest.kt          # Category K
│
│   # Phase 3 tests:
├── ClosureTest.kt               # Category L
├── HigherOrderTest.kt           # Category M
└── GoroutineChannelTest.kt      # Category N
```

### 8.2 Shared Rules

Most tests use the same source/sink rules. Define common rules in `AnalysisTest`:

```kotlin
abstract class AnalysisTest {
    // Existing common rules
    val commonPathRules = listOf<TaintRules.Pass>()

    // Standard source/sink rules (used by most tests)
    val stdSource = TaintRules.Source("test.source", "taint", Result)
    val stdSink = TaintRules.Sink("test.sink", "taint", Argument(0), "test-id")

    // Typed variants
    val intSource = TaintRules.Source("test.sourceInt", "taint", Result)
    val anySource = TaintRules.Source("test.sourceAny", "taint", Result)
    val anySink = TaintRules.Sink("test.sinkAny", "taint", Argument(0), "test-id")

    // Convenience: standard assertion with default rules
    fun assertReachable(fn: String) = assertSinkReachable(stdSource, stdSink, fn)
    fun assertNotReachable(fn: String) = assertSinkNotReachable(stdSource, stdSink, fn)
}
```

### 8.3 Example Test Class

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterproceduralTest : AnalysisTest() {

    // Return value passing
    @Test fun returnValuePassing001F() = assertNotReachable("test.returnValuePassing001F")
    @Test fun returnValuePassing002T() = assertReachable("test.returnValuePassing002T")

    // Argument passing
    @Test fun argPassing001F() = assertNotReachable("test.argPassing001F")
    @Test fun argPassing002T() = assertReachable("test.argPassing002T")
    @Test fun argPassing005F() = assertNotReachable("test.argPassing005F")
    @Test fun argPassing006T() = assertReachable("test.argPassing006T")

    // Multiple return values
    @Test fun multiReturn001F() = assertNotReachable("test.multiReturn001F")
    @Test fun multiReturn002T() = assertReachable("test.multiReturn002T")

    // Named return values
    @Test fun namedReturn001F() = assertNotReachable("test.namedReturn001F")
    @Test fun namedReturn002T() = assertReachable("test.namedReturn002T")

    // Multi-invoke (context sensitivity)
    @Test fun multiInvoke001F() = assertNotReachable("test.multiInvoke001F")
    @Test fun multiInvoke002T() = assertReachable("test.multiInvoke002T")

    // Deep call chains
    @Test fun deepCall001T() = assertReachable("test.deepCall001T")
    @Test fun deepCall002T() = assertReachable("test.deepCall002T")
    @Test fun deepCall003T() = assertReachable("test.deepCall003T")
    @Test fun deepCallClean001F() = assertNotReachable("test.deepCallClean001F")

    // Argument position sensitivity
    @Test fun argPosition001T() = assertReachable("test.argPosition001T")
    @Test fun argPosition002F() = assertNotReachable("test.argPosition002F")
    @Test fun argPosition003T() = assertReachable("test.argPosition003T")
    @Test fun argPosition004F() = assertNotReachable("test.argPosition004F")
}
```

### 8.4 Tests Requiring Custom Rules

Some tests need non-standard source/sink/pass rules:

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PassThroughTest : AnalysisTest() {

    private val passRule = TaintRules.Pass("test.passthrough", "taint", Argument(0), Result)
    private val transformRule = TaintRules.Pass("test.transform", "taint", Argument(0), Result)

    @Test fun passThrough001T() {
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough001T",
            extraPassRules = listOf(passRule))
        assertTrue(vulns.isNotEmpty())
    }

    @Test fun passThrough002F() {
        // No pass rule for sanitize() → call kills taint
        val vulns = runAnalysis(stdSource, stdSink, "test.passThrough002F")
        assertTrue(vulns.isEmpty())
    }
}
```

This requires extending `AnalysisTest.runAnalysis` to accept extra pass rules:

```kotlin
fun runAnalysis(
    source: TaintRules.Source,
    sink: TaintRules.Sink,
    entryPointFunction: String,
    extraPassRules: List<TaintRules.Pass> = emptyList(),
): List<TaintSinkTracker.TaintVulnerability> {
    val config = GoTaintConfig(
        listOf(source),
        listOf(sink),
        commonPathRules + extraPassRules
    )
    // ... rest of analysis
}
```

### 8.5 `runAnalysis` Enhancements

The `runAnalysis` method should be extended to support multiple sources and sinks:

```kotlin
fun runAnalysis(
    sources: List<TaintRules.Source> = emptyList(),
    sinks: List<TaintRules.Sink> = emptyList(),
    entryPointFunction: String,
    passRules: List<TaintRules.Pass> = commonPathRules,
): List<TaintSinkTracker.TaintVulnerability> {
    val config = GoTaintConfig(sources, sinks, passRules)
    // ...
}

// Backward-compatible single source/sink version
fun runAnalysis(
    source: TaintRules.Source,
    sink: TaintRules.Sink,
    entryPointFunction: String,
): List<TaintSinkTracker.TaintVulnerability> {
    return runAnalysis(listOf(source), listOf(sink), entryPointFunction)
}
```

---

## 9. Implementation Plan

### 9.1 Phase 1: Foundation (with MVP engine)

**Goal**: Get ~60 tests passing (basic intraprocedural + interprocedural + defer + cross-package + generics)

1. **Extend `util.go`** with typed source/sink variants, collection sources
2. **Create `basic_flow.go`** — adapt ~10 basic tests from benchmark
3. **Create `interprocedural.go`** — adapt ~20 interprocedural tests
4. **Create `control_flow.go`** — adapt ~10 control flow tests
5. **Create `expressions.go`** — adapt ~10 expression tests
6. **Create `defer_tests.go`** — adapt 4 defer tests + generate 4 more
7. **Create `cross_package.go` + `crosspkg/` subdirectory** — adapt 16 cross-package tests + generate 3 more
8. **Create `generics.go`** — adapt 2 benchmark tests + generate 4 more
9. **Update `AnalysisTest.createClasspath()`** to pass `"./..."` for multi-package builds
10. **Create generated tests**: `pass_through.go`, `sanitization.go`, `deep_calls.go`, `arg_position.go`, `multi_return.go`
11. **Create Kotlin test classes**: `BasicFlowTest`, `InterproceduralTest`, `ControlFlowTest`, `ExpressionTest`, `DeferTest`, `CrossPackageTest`, `GenericsTest`, `PassThroughTest`, `SanitizationTest`
12. **Extend `AnalysisTest`** with convenience methods and enhanced `runAnalysis`

### 9.2 Phase 2: Enhanced Analysis

**Goal**: Get ~80 more tests passing (field sensitivity, interfaces, collections)

1. **Create `interface_dispatch.go`** — adapt polymorphism + interface tests
2. **Create `field_sensitive.go`** — adapt struct field tests
3. **Create `collections.go`** — adapt slice/array/map tests with element-vs-container model
4. **Create `pointers_aliases.go`** — adapt pointer + alias tests
5. **Create generated tests**: `struct_fields.go`, `method_receiver.go`
6. **Create Kotlin test classes**: `InterfaceDispatchTest`, `FieldSensitiveTest`, `CollectionTest`, `PointerAliasTest`

### 9.3 Phase 3: Advanced Features

**Goal**: Get ~40 more tests passing (closures, goroutines)

1. **Create `closures.go`** — adapt closure tests
2. **Create `higher_order.go`** — adapt higher-order function tests
3. **Create `goroutines_channels.go`** — adapt goroutine/channel tests
4. **Create Kotlin test classes**: `ClosureTest`, `HigherOrderTest`, `GoroutineChannelTest`

### 9.4 Test Execution

All tests run via:
```bash
gradle :test --tests "org.opentaint.go.sast.dataflow.*"
```

Individual category:
```bash
gradle :test --tests "org.opentaint.go.sast.dataflow.InterproceduralTest"
```

### 9.5 Expected Test Counts

| Phase | Adapted | Generated | Total | Cumulative |
|-------|---------|-----------|-------|------------|
| Phase 1 | ~82 | ~40 | ~122 | ~122 |
| Phase 2 | ~80 | ~20 | ~100 | ~222 |
| Phase 3 | ~30 | ~10 | ~40 | ~262 |

Plus the 2 existing `SampleTest` tests → **~264 total test cases**.
