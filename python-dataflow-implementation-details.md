# Python Dataflow Analysis — Implementation Details

## 1. Executive Summary

This document translates the high-level designs from Phase 1 (`python-dataflow-design.md`) and Phase 2
(`python-dataflow-test-system.md`) into concrete implementation instructions, organized by module.
It identifies interface mismatches between the original design and the actual codebase, specifies
every file that must be created or modified, and defines the implementation order.

### Scope

Make the two existing tests pass (`testSimpleSample`, `testSimpleNonReachableSample`) and lay the
groundwork for the test system expansion described in Phase 2.

### Key Discovery: IR Bridge Gaps

The original design assumed certain PIR↔Common bridges exist. They do not:

| Bridge | Status | Impact | Resolution |
|--------|--------|--------|------------|
| `PIRInstruction.location` | `TODO()` — throws at runtime | Blocks `methodOf()`, `getInstIndex()` | Add `location` as mutable field, wire post-construction (like `enclosingClass`) |
| `PIRFunction.flowGraph()` | `error("Unsupported operation")` | Not usable | Use `PIRFunction.cfg: PIRCFG` directly |
| `PIRValue : CommonValue` | **Not implemented** — separate hierarchies | Blocks `CommonCallExpr.args` bridge | Make `PIRValue` extend `CommonValue` |
| `PIRCall : CommonCallInst` | **Not implemented** | Need adapter | Create `PIRCallExprAdapter` |
| `PIRCallExprAdapter` | Design-doc only, not coded | — | Create in `adapter` package |
| `PIRLocation` | Interface exists, no concrete impl | — | Create `PIRLocationImpl` with method + index |

---

## 2. Module Map

Four modules require changes:

```
Module 1: opentaint-ir-api-python           (PIR API interfaces)
Module 2: opentaint-ir-impl-python          (PIR implementations + builders)
Module 3: opentaint-python-dataflow         (analysis engine — the main work)
Module 4: core (tests + samples)            (tests and Python sample files)
```

Dependencies flow: Module 4 → Module 3 → Modules 1 & 2.

Implementation order must respect these dependencies — IR fixes (Modules 1-2) come first,
then the dataflow engine (Module 3), then tests (Module 4).

---

## 3. Module 1: `opentaint-ir-api-python` — PIR API Fixes

**Path**: `core/opentaint-ir/python/opentaint-ir-api-python/src/main/kotlin/org/opentaint/ir/api/python/`

### 3.1 Change: Make `PIRValue` extend `CommonValue`

**File**: `Values.kt`

**Current**:
```kotlin
sealed interface PIRValue : PIRExpr {
    val type: PIRType
}
```

**Required**:
```kotlin
sealed interface PIRValue : PIRExpr, CommonValue {
    val type: PIRType
    override val typeName: String get() = type.typeName
}
```

**Why**: The dataflow engine's `CommonCallExpr.args` returns `List<CommonValue>`. Without this
bridge, we cannot pass PIR values through the common interface without wrapper adapters for every
value. Making `PIRValue` extend `CommonValue` directly is cleaner than creating `PIRValueAdapter`
wrappers.

**Side effect**: `PIRExpr` must also extend `CommonExpr` (since `CommonValue extends CommonExpr`
which requires `typeName: String`):

```kotlin
sealed interface PIRExpr : CommonExpr {
    override val typeName: String get() = "expr"  // default; values override with type info
}
```

**Imports to add**: `org.opentaint.ir.api.common.cfg.CommonValue`, `org.opentaint.ir.api.common.cfg.CommonExpr`

### 3.2 Change: Add `location` to every `PIRInstruction`

**File**: `Instructions.kt`

The `PIRInstruction.location` is currently a `TODO()` default. We need real location on every
instruction — carrying both the owning `PIRFunction` and the instruction's flat index. This enables
`inst.location.method` and `inst.location.index` throughout the engine without lookup tables.

**Step 1**: Extend `PIRLocation` to include the instruction index:

```kotlin
interface PIRLocation : CommonInstLocation {
    override val method: PIRFunction
    val index: Int  // flat instruction index within the method
}
```

**Step 2**: Change `PIRInstruction` to declare `location` as a late-init mutable property:

```kotlin
sealed interface PIRInstruction : CommonInst {
    val lineNumber: Int
    val colOffset: Int
    fun <T> accept(visitor: PIRInstVisitor<T>): T

    override var location: PIRLocation  // mutable — set post-construction
}
```

**Step 3**: Add `override var location: PIRLocation` to every instruction data class. Since all 21
data classes (excluding `PIRUnreachable` data object) already follow the same pattern with
`lineNumber` and `colOffset` as trailing params, add `location` as a mutable property with a
lateinit-like default. Because `data class` properties cannot be `lateinit`, use a `var` with a
sentinel or a backing property:

```kotlin
data class PIRAssign(
    val target: PIRValue,
    val expr: PIRExpr,
    override val lineNumber: Int = -1,
    override val colOffset: Int = -1,
) : PIRInstruction {
    override lateinit var location: PIRLocation  // set after construction
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitAssign(this)
}
```

Note: `lateinit var` on an interface property override in a data class is valid in Kotlin — the
property is not part of the data class's `equals`/`hashCode`/`copy` (since it's declared in the body,
not the constructor). This is exactly the pattern used for `PIRFunctionImpl.enclosingClass`.

For `PIRUnreachable` (data object), assign a dummy location:

```kotlin
data object PIRUnreachable : PIRInstruction {
    override val lineNumber: Int = -1
    override val colOffset: Int = -1
    override var location: PIRLocation = DummyPIRLocation
    override fun <T> accept(visitor: PIRInstVisitor<T>): T = visitor.visitUnreachable(this)
}
```

**All 21 instruction types** must get `override lateinit var location: PIRLocation`:
`PIRAssign`, `PIRStoreAttr`, `PIRStoreSubscript`, `PIRStoreGlobal`, `PIRStoreClosure`,
`PIRCall`, `PIRNextIter`, `PIRUnpack`, `PIRGoto`, `PIRBranch`, `PIRReturn`, `PIRRaise`,
`PIRExceptHandler`, `PIRYield`, `PIRYieldFrom`, `PIRAwait`, `PIRDeleteLocal`, `PIRDeleteAttr`,
`PIRDeleteSubscript`, `PIRDeleteGlobal`, `PIRUnreachable`.

**Why not a lookup table?** A lookup table (IdentityHashMap) duplicates the responsibility of
location tracking into every consumer. Embedding location directly on the instruction is the
standard pattern (JIR does `inst.location.index`), keeps the codebase clean, and enables any
future code to call `inst.location.method` without needing a reference to the graph.

---

## 4. Module 2: `opentaint-ir-impl-python` — PIR Implementation Fixes

**Path**: `core/opentaint-ir/python/opentaint-ir-impl-python/src/main/kotlin/org/opentaint/ir/impl/python/`

### 4.1 New class: `PIRLocationImpl`

**File**: `PIRLocationImpl.kt` (new)

```kotlin
package org.opentaint.ir.impl.python

import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRLocation

data class PIRLocationImpl(
    override val method: PIRFunction,
    override val index: Int,
) : PIRLocation
```

Also add a sentinel for `PIRUnreachable`:
```kotlin
object DummyPIRLocation : PIRLocation {
    override val method: PIRFunction get() = error("Dummy location has no method")
    override val index: Int get() = -1
}
```

### 4.2 Wire location post-construction in `MypyModuleBuilder`

**File**: `builder/MypyModuleBuilder.kt`

After each `PIRFunctionImpl(...)` construction, add a location-wiring pass. The pattern
follows the existing `enclosingClass` wiring:

```kotlin
val pirCfg = ic.convertCFG(cfgProto)
// ... params, returnType ...
val function = PIRFunctionImpl(name, qualifiedName, params, returnType, pirCfg, ...)

// Wire instruction locations (method + flat index)
wireInstructionLocations(function)
```

**New helper function** in `MypyModuleBuilder`:

```kotlin
private fun wireInstructionLocations(function: PIRFunction) {
    var index = 0
    for (block in function.cfg.blocks.sortedBy { it.label }) {
        for (inst in block.instructions) {
            inst.location = PIRLocationImpl(function, index)
            index++
        }
    }
}
```

This must be called in all 3 places where `PIRFunctionImpl` is created:
1. Top-level function definitions (~line 275)
2. Method definitions inside classes (~line 360)
3. Module `__init__` function (~line 475)

**Block sorting order**: `sortedBy { it.label }` — same order used throughout the dataflow engine
for CFG flattening. This ensures `inst.location.index` matches the flat instruction list index
used by `PIRLanguageManager`.

### 4.3 `InstructionConverter` — No changes needed

`InstructionConverter.convertCFG()` returns a `PIRCFG` with instructions already constructed.
The location wiring happens in `MypyModuleBuilder` after the function is fully assembled,
because that's when both the `PIRFunction` reference and the flat instruction order are known.

---

## 5. Module 3: `opentaint-python-dataflow` — Analysis Engine Implementation

**Path**: `core/opentaint-dataflow-core/opentaint-python-dataflow/src/main/kotlin/org/opentaint/dataflow/python/`

This is where the bulk of the work happens. The current module has 4 files (3 stubs + 1 complete).
We need to implement all stubs and add ~10 new files.

### 5.1 Interface Alignment: Design Doc vs Actual Interfaces

The original design (`python-dataflow-design.md`) has several signature mismatches with the actual
interfaces discovered during exploration. This section documents each correction.

#### 5.1.1 `TaintAnalysisManager.getMethodAnalysisContext`

**Design doc** (Section 5):
```kotlin
fun getMethodAnalysisContext(
    methodEntryPoint: MethodEntryPoint,
    graph: ApplicationGraph<CommonMethod, CommonInst>,
    callResolver: MethodCallResolver,
    taintAnalysisContext: TaintAnalysisContext,
    contextForEmptyMethod: MethodAnalysisContext?,
): MethodAnalysisContext
```

**Actual interface**: Same. ✅ No correction needed.

#### 5.1.2 `AnalysisManager.getMethodCallResolver`

**Design doc** (Section 5):
```kotlin
fun getMethodCallResolver(
    graph: ApplicationGraph<CommonMethod, CommonInst>,
    runner: TaintAnalysisUnitRunner,
): MethodCallResolver
```

**Actual interface**:
```kotlin
fun getMethodCallResolver(
    graph: ApplicationGraph<CommonMethod, CommonInst>,
    unitResolver: UnitResolver<CommonMethod>,  // ← EXTRA PARAMETER
    runner: TaintAnalysisUnitRunner,
): MethodCallResolver
```

**Correction**: Add `unitResolver` parameter. For the minimal prototype, ignore it (we use `SingletonUnit`).

#### 5.1.3 `AnalysisManager.getMethodStartFlowFunction`

**Design doc**: `fun getMethodStartFlowFunction(methodAnalysisContext: MethodAnalysisContext)`

**Actual interface**: `fun getMethodStartFlowFunction(apManager: ApManager, analysisContext: MethodAnalysisContext)`

**Correction**: Add `apManager: ApManager` parameter. Pass it through to the flow function (needed for
creating `FinalFactAp` instances).

#### 5.1.4 `AnalysisManager.getMethodSequentFlowFunction`

**Design doc**: `fun getMethodSequentFlowFunction(inst: CommonInst, methodAnalysisContext: MethodAnalysisContext)`

**Actual interface**:
```kotlin
fun getMethodSequentFlowFunction(
    apManager: ApManager,
    analysisContext: MethodAnalysisContext,
    currentInst: CommonInst,
    generateTrace: Boolean = false,
): MethodSequentFlowFunction
```

**Correction**: Add `apManager` and `generateTrace` parameters. Ignore `generateTrace` for now (no trace support).

#### 5.1.5 `AnalysisManager.getMethodCallFlowFunction`

**Design doc**: `fun getMethodCallFlowFunction(inst: CommonInst, callExpr: CommonCallExpr, methodAnalysisContext: MethodAnalysisContext)`

**Actual interface**:
```kotlin
fun getMethodCallFlowFunction(
    apManager: ApManager,
    analysisContext: MethodAnalysisContext,
    returnValue: CommonValue?,
    callExpr: CommonCallExpr,
    statement: CommonInst,
    generateTrace: Boolean,
): MethodCallFlowFunction
```

**Correction**: Add `apManager`, `returnValue`, `generateTrace`. The `returnValue` is the LHS of the
call assignment (the variable receiving the return value). For `PIRCall`, this maps to `pirCall.target`.

#### 5.1.6 `AnalysisManager.getMethodCallSummaryHandler`

**Design doc**: mentions it briefly.

**Actual interface**:
```kotlin
fun getMethodCallSummaryHandler(
    apManager: ApManager,
    analysisContext: MethodAnalysisContext,
    statement: CommonInst,
): MethodCallSummaryHandler
```

#### 5.1.7 `AnalysisManager.getMethodSideEffectSummaryHandler`

**Actual interface**:
```kotlin
fun getMethodSideEffectSummaryHandler(
    apManager: ApManager,
    analysisContext: MethodAnalysisContext,
    statement: CommonInst,
    runner: AnalysisRunner,
): MethodSideEffectSummaryHandler
```

#### 5.1.8 `MethodCallFlowFunction` result types

**Design doc** uses simplified types like `CallToReturnZFact(newFact)`, `CallToStartFFact(initialFactAp, mappedFact)`.

**Actual types** (from the interface):
- `CallToReturnZFact(factAp: FinalFactAp, traceInfo: TraceInfo?)` — has `traceInfo`
- `CallToStartFFact(initialFactAp: InitialFactAp, callerFactAp: FinalFactAp, startFactBase: AccessPathBase, traceInfo: TraceInfo?)` — needs `callerFactAp` AND `startFactBase` (not just the mapped fact)
- `CallToReturnFFact(initialFactAp: InitialFactAp, factAp: FinalFactAp, traceInfo: TraceInfo?)` — exists

**Correction**: All flow function result constructors need `traceInfo = null` for the minimal prototype.
`CallToStartFFact` requires the caller-side fact (`callerFactAp`), the callee-side start base
(`startFactBase: AccessPathBase`), and the initial fact.

#### 5.1.9 `MethodSequentFlowFunction.Sequent` result types

**Design doc** uses `Sequent.FactToFact(initialFactAp, newFact)`.

**Actual types**:
- `Sequent.FactToFact(initialFactAp: InitialFactAp, factAp: FinalFactAp, traceInfo: TraceInfo?)` — has `traceInfo`
- `Sequent.ZeroToFact(factAp: FinalFactAp, traceInfo: TraceInfo?)` — has `traceInfo`

**Correction**: Always pass `traceInfo = null`.

#### 5.1.10 `MethodAnalysisContext` requires `methodCallFactMapper`

**Design doc** doesn't mention this.

**Actual interface**:
```kotlin
interface MethodAnalysisContext {
    val methodEntryPoint: MethodEntryPoint
    val methodCallFactMapper: MethodCallFactMapper  // Required!
}
```

**Correction**: `PIRMethodAnalysisContext` must implement `methodCallFactMapper`, returning
`PIRMethodCallFactMapper` (a singleton object).

### 5.2 File-by-file Implementation Plan

Below is every file in the module after implementation, with its status and detailed changes.

---

#### 5.2.1 `PIRLanguageManager.kt` — MODIFY (existing stub)

**Status**: All 8 methods are `TODO()`. Must implement all.

Since every `PIRInstruction` now carries its `location` (with `method` and `index`), the language
manager is straightforward — no lookup tables needed.

```kotlin
open class PIRLanguageManager(
    protected val cp: PIRClasspath,  // ADD constructor param
) : LanguageManager {

    // --- Flat instruction list cache (per-function) ---
    private val flatInstsCache = HashMap<PIRFunction, List<PIRInstruction>>()

    protected fun flattenCfg(method: PIRFunction): List<PIRInstruction> =
        flatInstsCache.getOrPut(method) {
            method.cfg.blocks
                .sortedBy { it.label }
                .flatMap { it.instructions }
        }

    // --- Interface implementations ---

    override fun getInstIndex(inst: CommonInst): Int =
        (inst as PIRInstruction).location.index

    override fun getMaxInstIndex(method: CommonMethod): Int =
        flattenCfg(method as PIRFunction).size - 1

    override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst =
        flattenCfg(method as PIRFunction)[index]

    override fun isEmpty(method: CommonMethod): Boolean =
        flattenCfg(method as PIRFunction).isEmpty()

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        val pirInst = inst as PIRInstruction
        return if (pirInst is PIRCall) PIRCallExprAdapter(pirInst) else null
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean =
        inst is PIRRaise

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val adapter = callExpr as PIRCallExprAdapter
        val qualifiedName = adapter.pirCall.resolvedCallee
            ?: error("Unresolved call: ${adapter.pirCall.callee}")
        return cp.findFunctionOrNull(qualifiedName)
            ?: error("Function not found: $qualifiedName")
    }

    override val methodContextSerializer: MethodContextSerializer =
        PIRMethodContextSerializer()
}
```

**Key decisions**:
- Takes `PIRClasspath` in constructor (needed for `getCalleeMethod`)
- `getInstIndex()` reads directly from `inst.location.index` (wired in MypyModuleBuilder)
- `flattenCfg()` sorts blocks by label — same order used during location wiring
- `getCallExpr()` wraps `PIRCall` in `PIRCallExprAdapter` (new class, see 5.2.4)

---

#### 5.2.2 `analysis/PIRAnalysisManager.kt` — REWRITE (existing stub)

**Status**: 20 methods are `TODO()`. Must implement all.

```kotlin
class PIRAnalysisManager(
    cp: PIRClasspath,
    private val taintConfig: PIRTaintConfig,
) : PIRLanguageManager(cp), TaintAnalysisManager {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy
    // Python is dynamically typed; type-based fact filtering is not meaningful
    // for the minimal prototype. Use Dummy which accepts all facts.

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        callResolver: MethodCallResolver,
        taintAnalysisContext: TaintAnalysisContext,
        contextForEmptyMethod: MethodAnalysisContext?,
    ): MethodAnalysisContext {
        val method = methodEntryPoint.method as PIRFunction
        return PIRMethodAnalysisContext(methodEntryPoint, method, taintAnalysisContext)
    }

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner,
    ): MethodCallResolver =
        PIRMethodCallResolver(PIRCallResolver(cp), runner)

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartFlowFunction {
        val ctx = analysisContext as PIRMethodAnalysisContext
        return PIRMethodStartFlowFunction(ctx.method, ctx, taintConfig, apManager)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
        generateTrace: Boolean,
    ): MethodSequentFlowFunction {
        val ctx = analysisContext as PIRMethodAnalysisContext
        return PIRMethodSequentFlowFunction(
            currentInst as PIRInstruction, ctx.method, ctx, taintConfig, apManager
        )
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
        generateTrace: Boolean,
    ): MethodCallFlowFunction {
        val ctx = analysisContext as PIRMethodAnalysisContext
        val pirCall = statement as PIRCall
        val callee = PIRCallResolver(cp).resolve(pirCall)
        return PIRMethodCallFlowFunction(
            pirCall, ctx.method, ctx, taintConfig, callee, apManager, returnValue
        )
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
    ): MethodCallPrecondition = MethodCallPrecondition.Default
    // No preconditions for minimal prototype

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartPrecondition = MethodStartPrecondition.Default
    // No preconditions

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
    ): MethodSequentPrecondition = MethodSequentPrecondition.Default
    // No preconditions

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
    ): MethodCallSummaryHandler {
        val ctx = analysisContext as PIRMethodAnalysisContext
        return PIRMethodCallSummaryHandler(
            statement as PIRCall, ctx, factTypeChecker
        )
    }

    override fun getMethodSideEffectSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
        runner: AnalysisRunner,
    ): MethodSideEffectSummaryHandler =
        PIRMethodSideEffectSummaryHandler()
    // No side effects for minimal prototype

    override fun getMethodSummaryEdgeProcessor(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
    ): MethodSummaryEdgeProcessor? = null
    // No summary edge processing

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst,
    ): Boolean = true
    // Conservative: all variables are always reachable (no dead-code analysis)

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp,
    ): Boolean = fact.base !is AccessPathBase.LocalVar
    // Local variables cannot escape method scope

    override fun onInstructionReached(inst: CommonInst) {
        // Register the instruction's method for our reverse lookup map
        val pirInst = inst as PIRInstruction
        // Already registered during graph construction — no-op
    }
}
```

**Key decisions**:
- Constructor takes `PIRClasspath` (passed to super `PIRLanguageManager`) and `PIRTaintConfig`
- `FactTypeChecker.Dummy` — Python is dynamically typed; no meaningful type filtering
- All preconditions return `Default` (no-op)
- `isReachable` always `true` (conservative)
- `isValidMethodExitFact` rejects `LocalVar` (standard IFDS correctness)
- `getMethodSummaryEdgeProcessor` returns `null` (no rewriting)

**Problem**: The current `AnalysisTest.kt` creates `PIRAnalysisManager()` with no arguments (line 127).
The constructor must change to accept `PIRClasspath` and `PIRTaintConfig`. See Section 7
(Module 4 changes) for the test fix.

Actually, looking more carefully at the test code:

```kotlin
val engine = TaintAnalysisUnitRunnerManager(
    PIRAnalysisManager(),   // ← No args
    ifdsGraph as ApplicationGraph<CommonMethod, CommonInst>,
    ...
    taintConfig = config,   // ← Config passed to engine, not manager
    ...
)
```

The `TaintAnalysisUnitRunnerManager` receives the `taintConfig` separately and passes it via
`TaintAnalysisContext` to `getMethodAnalysisContext()`. So we have two options:

**Option A**: `PIRAnalysisManager` gets config from `TaintAnalysisContext` in each factory method.
This means the constructor takes only `PIRClasspath`, and each flow function reads config from
`PIRMethodAnalysisContext.taint.taintConfig`.

**Option B**: `PIRAnalysisManager` takes config in constructor. Test must be updated.

**Chosen: Option A** — aligns with how the engine passes config. The `PIRAnalysisManager` constructor
takes only `PIRClasspath`. Taint rules are accessed via `ctx.taint.taintConfig as PIRTaintConfig`
within each flow function.

But wait — the test creates `PIRAnalysisManager()` with NO arguments at all. We need the classpath.
The test already has `cp` available. So the minimal change:

```kotlin
// In AnalysisTest.runAnalysis():
val engine = TaintAnalysisUnitRunnerManager(
    PIRAnalysisManager(cp),   // ← Add cp
    ...
)
```

This is a one-line change in the test.

**Revised constructor**: `class PIRAnalysisManager(cp: PIRClasspath) : PIRLanguageManager(cp), TaintAnalysisManager`

---

#### 5.2.3 `analysis/PIRMethodAnalysisContext.kt` — NEW

Local variable name collection uses the existing `PIRInstVisitor.Default` and `PIRValueVisitor.Default`
interfaces. This avoids brittle `when` exhaustiveness — new instruction/value types automatically
fall through to `defaultVisit`/`defaultVisitValue` rather than silently being missed.

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.ir.api.python.*

class PIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {

    override val methodCallFactMapper: MethodCallFactMapper
        get() = PIRMethodCallFactMapper(this)

    /** Map from PIRLocal names to integer indices (for AccessPathBase.LocalVar) */
    val localNameToIndex: Map<String, Int>

    /** Reverse map from index to local name */
    val indexToLocalName: Map<Int, String>

    init {
        val names = linkedSetOf<String>()
        val collector = LocalNameCollector(names)
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
                inst.accept(collector)
            }
        }
        localNameToIndex = names.withIndex().associate { (idx, name) -> name to idx }
        indexToLocalName = localNameToIndex.entries.associate { (name, idx) -> idx to name }
    }

    fun localIndex(name: String): Int =
        localNameToIndex[name] ?: error("Unknown local: $name in ${method.qualifiedName}")

    fun localName(index: Int): String =
        indexToLocalName[index] ?: error("Unknown index: $index in ${method.qualifiedName}")
}

/**
 * Collects PIRLocal names from all instructions using the visitor pattern.
 * Uses PIRInstVisitor.Default so new instruction types are handled safely
 * (they fall through to defaultVisit → no-op).
 */
private class LocalNameCollector(
    private val names: MutableSet<String>,
) : PIRInstVisitor.Default<Unit> {

    private val valueVisitor = object : PIRValueVisitor.Default<Unit> {
        override fun defaultVisitValue(value: PIRValue) = Unit
        override fun visitLocal(value: PIRLocal) { names.add(value.name) }
        // PIRParameterRef → NOT collected (parameters use AccessPathBase.Argument)
        // PIRGlobalRef → NOT collected (globals not tracked as locals)
        // PIRConst → NOT collected
    }

    private fun visit(value: PIRValue) = value.accept(valueVisitor)
    private fun visit(value: PIRValue?) { value?.accept(valueVisitor) }

    private fun visitExpr(expr: PIRExpr) {
        when (expr) {
            is PIRValue -> visit(expr)
            is PIRBinExpr -> { visit(expr.left); visit(expr.right) }
            is PIRUnaryExpr -> visit(expr.operand)
            is PIRCompareExpr -> { visit(expr.left); visit(expr.right) }
            is PIRAttrExpr -> visit(expr.obj)
            is PIRSubscriptExpr -> { visit(expr.obj); visit(expr.index) }
            is PIRListExpr -> expr.elements.forEach(::visit)
            is PIRTupleExpr -> expr.elements.forEach(::visit)
            is PIRSetExpr -> expr.elements.forEach(::visit)
            is PIRDictExpr -> {
                expr.keys.forEach { it?.let(::visit) }
                expr.values.forEach(::visit)
            }
            is PIRStringExpr -> expr.parts.forEach(::visit)
            is PIRIterExpr -> visit(expr.iterable)
            is PIRSliceExpr -> { visit(expr.lower); visit(expr.upper); visit(expr.step) }
            is PIRTypeCheckExpr -> visit(expr.value)
        }
    }

    override fun defaultVisit(inst: PIRInstruction) = Unit  // safe fallback for new types

    override fun visitAssign(inst: PIRAssign) { visit(inst.target); visitExpr(inst.expr) }
    override fun visitCall(inst: PIRCall) {
        visit(inst.target); visit(inst.callee)
        inst.args.forEach { visit(it.value) }
    }
    override fun visitReturn(inst: PIRReturn) { visit(inst.value) }
    override fun visitBranch(inst: PIRBranch) { visit(inst.condition) }
    override fun visitStoreAttr(inst: PIRStoreAttr) { visit(inst.obj); visit(inst.value) }
    override fun visitStoreSubscript(inst: PIRStoreSubscript) {
        visit(inst.obj); visit(inst.index); visit(inst.value)
    }
    override fun visitUnpack(inst: PIRUnpack) {
        inst.targets.forEach(::visit); visit(inst.source)
    }
    override fun visitYield(inst: PIRYield) { visit(inst.value) }
    override fun visitYieldFrom(inst: PIRYieldFrom) { visit(inst.iterable) }
    override fun visitAwait(inst: PIRAwait) { visit(inst.awaitable) }
    override fun visitDeleteLocal(inst: PIRDeleteLocal) { visit(inst.local) }
    override fun visitNextIter(inst: PIRNextIter) { visit(inst.target); visit(inst.iterator) }
    override fun visitRaise(inst: PIRRaise) { visit(inst.exception); visit(inst.cause) }
    override fun visitStoreGlobal(inst: PIRStoreGlobal) { visit(inst.value) }
    override fun visitStoreClosure(inst: PIRStoreClosure) { visit(inst.value) }
    override fun visitDeleteAttr(inst: PIRDeleteAttr) { visit(inst.obj) }
    override fun visitDeleteSubscript(inst: PIRDeleteSubscript) { visit(inst.obj); visit(inst.index) }
}
```

---

#### 5.2.4 `adapter/PIRCallExprAdapter.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.adapter

import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRCallArgKind

/**
 * Adapts PIRCall (an instruction) to CommonCallExpr (an expression).
 *
 * The dataflow engine expects CommonCallExpr with args: List<CommonValue>.
 * PIRCall has args: List<PIRCallArg> where each PIRCallArg wraps a PIRValue.
 *
 * Since PIRValue now extends CommonValue (after Module 1 changes), we can
 * directly extract the values.
 */
class PIRCallExprAdapter(
    val pirCall: PIRCall,
) : CommonCallExpr {

    override val typeName: String get() = "call"

    override val args: List<CommonValue>
        get() = pirCall.args
            .filter { it.kind == PIRCallArgKind.POSITIONAL || it.kind == PIRCallArgKind.KEYWORD }
            .map { it.value }
    // For the minimal prototype, only positional and keyword args are mapped.
    // STAR (*args) and DOUBLE_STAR (**kwargs) are not handled.
}
```

---

#### 5.2.5 `analysis/PIRCallResolver.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.ir.api.python.PIRCall
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction

/**
 * Resolves PIRCall instructions to concrete PIRFunction callees.
 * Uses mypy's static resolution (PIRCall.resolvedCallee).
 */
class PIRCallResolver(private val cp: PIRClasspath) {

    fun resolve(call: PIRCall): PIRFunction? {
        val qualifiedName = call.resolvedCallee ?: return null
        return cp.findFunctionOrNull(qualifiedName)
    }
}
```

---

#### 5.2.6 `analysis/PIRMethodCallResolver.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunner
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.python.PIRCall
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter

class PIRMethodCallResolver(
    private val callResolver: PIRCallResolver,
    private val runner: TaintAnalysisUnitRunner,
) : MethodCallResolver {

    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    ) {
        val pirCall = location as PIRCall
        val callee = callResolver.resolve(pirCall)
        if (callee == null) {
            failureHandler.onResolutionFailed()
            return
        }
        val methodWithContext = MethodWithContext(callee, EmptyMethodContext)
        val analyzer = runner.getMethodAnalyzer(methodWithContext)
        if (analyzer != null) {
            handler.onCallResolved(analyzer)
        } else {
            failureHandler.onResolutionFailed()
        }
    }

    override fun resolvedMethodCalls(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
    ): List<MethodWithContext> {
        val pirCall = location as PIRCall
        val callee = callResolver.resolve(pirCall) ?: return emptyList()
        return listOf(MethodWithContext(callee, EmptyMethodContext))
    }
}
```

**Note**: `MethodAnalyzer.MethodCallHandler` and `MethodCallResolutionFailureHandler` are sealed
interfaces with multiple subtypes. The `handler.onCallResolved(analyzer)` and
`failureHandler.onResolutionFailed()` need to match the actual API. Let me verify:

Looking at the JVM reference (`JIRMethodCallResolver`), the pattern is:
```kotlin
runner.getOrCreateMethodAnalyzer(methodWithContext)?.let { analyzer ->
    handler.handleResolvedMethodCall(method, analyzer)
} ?: failureHandler.handleMethodCallResolutionFailure(callExpr)
```

The `MethodCallResolver` interface methods receive `handler: MethodAnalyzer.MethodCallHandler`
and `failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler`. These are sealed interfaces
whose subtypes carry the edge being processed. The `resolveMethodCall` method is called by the engine
with the appropriate handler subtype; the resolver just needs to call
`runner.getOrCreateMethodAnalyzer(methodWithContext)` and then the handler/failureHandler callbacks.

**Revised implementation** (delegating to the MethodAnalyzer infrastructure, same as JVM):

The actual pattern from JVM is that `resolveMethodCall` calls back into `MethodAnalyzer` via
`handleResolvedMethodCall` and `handleMethodCallResolutionFailure`. The handler types passed in
already carry the context. We just need to resolve the callee and delegate.

For the minimal prototype, the exact callback mechanism needs to match what
`NormalMethodAnalyzer.processCall()` expects. Reviewing the JVM code, the call to
`resolveMethodCall` is internal to the engine. The resolver's job is to:
1. Find the callee `MethodWithContext`
2. Call `handler.handleResolvedMethodCall(methodWithContext, calleeAnalyzer)` on success
3. Call `failureHandler.handleMethodCallResolutionFailure(callExpr)` on failure

Since these are internal engine APIs, the exact signatures depend on the `MethodAnalyzer` class.
The implementation above is correct in pattern — it may need minor adjustments once we compile
against the actual interfaces.

---

#### 5.2.7 `util/PIRFlowFunctionUtils.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.util

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor.*
import org.opentaint.ir.api.python.*

/**
 * Maps PIR values and expressions to AccessPathBase and Access representations.
 * Python equivalent of JVM's MethodFlowFunctionUtils.
 */
object PIRFlowFunctionUtils {

    sealed interface Access {
        val base: AccessPathBase

        data class Simple(override val base: AccessPathBase) : Access
        data class AttrAccess(
            override val base: AccessPathBase,
            val accessor: org.opentaint.dataflow.ap.ifds.Accessor,
        ) : Access
    }

    /**
     * Maps a PIRValue to an AccessPathBase.
     *
     * PIRLocal("x") → LocalVar(ctx.localIndex("x"))
     * PIRParameterRef("arg") → Argument(parameterIndex)
     * PIRConst → Constant(type, value)
     * PIRGlobalRef → null (not tracked for now)
     */
    fun accessPathBase(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (value) {
        is PIRLocal -> AccessPathBase.LocalVar(ctx.localIndex(value.name))
        is PIRParameterRef -> {
            val param = method.parameters.firstOrNull { it.name == value.name }
                ?: error("Parameter not found: ${value.name} in ${method.qualifiedName}")
            AccessPathBase.Argument(param.index)
        }
        is PIRIntConst -> AccessPathBase.Constant("int", value.value.toString())
        is PIRStrConst -> AccessPathBase.Constant("str", value.value)
        is PIRBoolConst -> AccessPathBase.Constant("bool", value.value.toString())
        is PIRFloatConst -> AccessPathBase.Constant("float", value.value.toString())
        is PIRNoneConst -> AccessPathBase.Constant("NoneType", "None")
        is PIRBytesConst -> AccessPathBase.Constant("bytes", value.value.toString())
        is PIRComplexConst -> AccessPathBase.Constant("complex", "${value.real}+${value.imag}j")
        is PIREllipsisConst -> AccessPathBase.Constant("ellipsis", "...")
        is PIRGlobalRef -> null  // Not tracked as a local
    }

    /**
     * Converts a PIRValue to an Access.
     */
    fun mkAccess(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): Access? {
        val base = accessPathBase(value, method, ctx) ?: return null
        return Access.Simple(base)
    }

    /**
     * Converts a PIRExpr to an Access.
     * Simple values → Simple(base)
     * PIRAttrExpr → AttrAccess(obj.base, FieldAccessor)
     * PIRSubscriptExpr → AttrAccess(obj.base, ElementAccessor)
     */
    fun mkAccess(
        expr: PIRExpr,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): Access? = when (expr) {
        is PIRValue -> mkAccess(expr, method, ctx)
        is PIRAttrExpr -> {
            val objBase = accessPathBase(expr.obj, method, ctx) ?: return null
            val accessor = FieldAccessor(
                expr.obj.type.typeName,
                expr.attribute,
                expr.resultType.typeName,
            )
            Access.AttrAccess(objBase, accessor)
        }
        is PIRSubscriptExpr -> {
            val objBase = accessPathBase(expr.obj, method, ctx) ?: return null
            Access.AttrAccess(objBase, ElementAccessor)
        }
        else -> null  // Binary ops, unary ops, etc. — not resolved to access paths
    }
}
```

**Import note**: `PIRMethodAnalysisContext` is used here — file is in `util` package, context is in
`analysis` package. Adjust imports accordingly.

---

#### 5.2.8 `analysis/PIRMethodStartFlowFunction.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.python.rules.PIRTaintConfig
import org.opentaint.ir.api.python.PIRFunction

class PIRMethodStartFlowFunction(
    private val method: PIRFunction,
    private val ctx: PIRMethodAnalysisContext,
    private val taintConfig: PIRTaintConfig,
    private val apManager: ApManager,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> =
        listOf(StartFact.Zero)
    // For the minimal prototype, no entry-point source rules.
    // Source rules are applied at call sites (in PIRMethodCallFlowFunction).

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> =
        listOf(StartFact.Fact(fact))
    // Pass through all incoming facts at method entry.
}
```

---

#### 5.2.9 `analysis/PIRMethodSequentFlowFunction.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.python.rules.PIRTaintConfig
import org.opentaint.dataflow.python.util.PIRFlowFunctionUtils
import org.opentaint.ir.api.python.*

class PIRMethodSequentFlowFunction(
    private val instruction: PIRInstruction,
    private val method: PIRFunction,
    private val ctx: PIRMethodAnalysisContext,
    private val taintConfig: PIRTaintConfig,
    private val apManager: ApManager,
) : MethodSequentFlowFunction {

    override fun propagateZeroToZero(): Set<Sequent> = setOf(Sequent.ZeroToZero)

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> =
        setOf(Sequent.Unchanged)

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = when (instruction) {
        is PIRAssign -> handleAssign(instruction, initialFactAp, currentFactAp)
        is PIRReturn -> handleReturn(instruction, initialFactAp, currentFactAp)
        is PIRStoreAttr -> handleStoreAttr(instruction, initialFactAp, currentFactAp)
        is PIRStoreSubscript -> handleStoreSubscript(instruction, initialFactAp, currentFactAp)
        else -> setOf(Sequent.Unchanged)
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = setOf(Sequent.Unchanged)

    // --- Assignment: target = expr ---

    private fun handleAssign(
        assign: PIRAssign,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val assignFrom = PIRFlowFunctionUtils.mkAccess(assign.expr, method, ctx)

        // Case 1: Simple assignment (x = y)
        if (assignFrom is PIRFlowFunctionUtils.Access.Simple) {
            return simpleAssign(assignTo, assignFrom.base, initialFactAp, currentFactAp)
        }

        // Case 2: Attribute read (x = y.attr)
        if (assignFrom is PIRFlowFunctionUtils.Access.AttrAccess) {
            return fieldRead(assignTo, assignFrom, initialFactAp, currentFactAp)
        }

        // Case 3: Compound expression — kill if overwriting target, else pass through
        return if (currentFactAp.base == assignTo) {
            emptySet()  // Strong update
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    /**
     * x = y: copy taint from y to x, keep taint on y, kill old taint on x.
     */
    private fun simpleAssign(
        to: AccessPathBase,
        from: AccessPathBase,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>()

        if (currentFactAp.base == from) {
            // Copy taint from source to target (rebase)
            val newFact = currentFactAp.rebase(to)
            results.add(Sequent.FactToFact(initialFactAp, newFact, null))
            // Keep original fact too (y still tainted after x = y)
            results.add(Sequent.Unchanged)
        } else if (currentFactAp.base == to) {
            // Strong update: x is overwritten → kill old fact about x
            // Do not add Unchanged
        } else {
            results.add(Sequent.Unchanged)
        }

        return results
    }

    /**
     * x = y.attr: if fact matches y with accessor .attr, read past accessor, rebase to x.
     */
    private fun fieldRead(
        to: AccessPathBase,
        access: PIRFlowFunctionUtils.Access.AttrAccess,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>()

        if (currentFactAp.base == access.base &&
            currentFactAp.startsWithAccessor(access.accessor)) {
            // Read the field: remove the accessor and rebase to target
            val readFact = currentFactAp.readAccessor(access.accessor)
            if (readFact != null) {
                val newFact = readFact.rebase(to)
                results.add(Sequent.FactToFact(initialFactAp, newFact, null))
            }
            results.add(Sequent.Unchanged) // Keep original fact
        } else if (currentFactAp.base == to) {
            // Strong update: target overwritten
        } else {
            results.add(Sequent.Unchanged)
        }

        return results
    }

    /**
     * return value: rebase fact to AccessPathBase.Return if it matches the return value.
     */
    private fun handleReturn(
        ret: PIRReturn,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>(Sequent.Unchanged)
        val retVal = ret.value ?: return results
        val retBase = PIRFlowFunctionUtils.accessPathBase(retVal, method, ctx) ?: return results
        if (currentFactAp.base == retBase) {
            val returnFact = currentFactAp.rebase(AccessPathBase.Return)
            results.add(Sequent.FactToFact(initialFactAp, returnFact, null))
        }
        return results
    }

    /**
     * obj.attr = value: propagate taint from value to obj's field.
     */
    private fun handleStoreAttr(
        store: PIRStoreAttr,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        val results = mutableSetOf<Sequent>(Sequent.Unchanged)

        if (currentFactAp.base == valueBase) {
            // Taint flows from value into obj.attr
            val accessor = org.opentaint.dataflow.ap.ifds.Accessor.FieldAccessor(
                store.obj.type.typeName,
                store.attribute,
                store.value.type.typeName,
            )
            val newFact = currentFactAp.rebase(objBase).prependAccessor(accessor)
            results.add(Sequent.FactToFact(initialFactAp, newFact, null))
        }

        return results
    }

    /**
     * obj[index] = value: propagate taint from value to obj's element.
     */
    private fun handleStoreSubscript(
        store: PIRStoreSubscript,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val valueBase = PIRFlowFunctionUtils.accessPathBase(store.value, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val objBase = PIRFlowFunctionUtils.accessPathBase(store.obj, method, ctx)
            ?: return setOf(Sequent.Unchanged)

        val results = mutableSetOf<Sequent>(Sequent.Unchanged)

        if (currentFactAp.base == valueBase) {
            val newFact = currentFactAp.rebase(objBase)
                .prependAccessor(org.opentaint.dataflow.ap.ifds.Accessor.ElementAccessor)
            results.add(Sequent.FactToFact(initialFactAp, newFact, null))
        }

        return results
    }
}
```

---

#### 5.2.10 `analysis/PIRMethodCallFlowFunction.kt` — NEW

This is the most complex file — handles source rules, sink rules, and call-to-start mapping.

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.*
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.python.rules.PIRTaintConfig
import org.opentaint.dataflow.python.rules.TaintRules
import org.opentaint.dataflow.python.util.PIRFlowFunctionUtils
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.*

class PIRMethodCallFlowFunction(
    private val callInst: PIRCall,
    private val method: PIRFunction,
    private val ctx: PIRMethodAnalysisContext,
    private val taintConfig: PIRTaintConfig,
    private val calleeMethod: PIRFunction?,
    private val apManager: ApManager,
    private val returnValue: CommonValue?,
) : MethodCallFlowFunction {

    override fun propagateZeroToZero(): Set<ZeroCallFact> {
        val results = mutableSetOf<ZeroCallFact>()

        // Always pass zero through call-to-return
        results.add(CallToReturnZeroFact)

        // Apply source rules: if this call is a taint source, generate new taint fact
        for (source in taintConfig.sources) {
            if (matchesCall(source.function, callInst)) {
                val targetBase = resolvePosition(source.pos, callInst, method, ctx)
                    ?: continue
                val newFact = apManager.mostAbstractFinalAp(targetBase)
                    .prependAccessor(TaintMarkAccessor(source.mark))
                results.add(CallToReturnZFact(newFact, null))
            }
        }

        // If we have a callee to enter, propagate zero into it
        if (calleeMethod != null) {
            results.add(CallToStartZeroFact)
        }

        return results
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> {
        val results = mutableSetOf<ZeroCallFact>()

        // 1. Check sink rules (same as in propagateFactToFact)
        for (sink in taintConfig.sinks) {
            if (matchesCall(sink.function, callInst)) {
                val sinkBase = resolvePosition(sink.pos, callInst, method, ctx)
                if (sinkBase != null && currentFactAp.base == sinkBase) {
                    if (factHasMark(currentFactAp, sink.mark)) {
                        ctx.taint.taintSinkTracker.addUnconditionalVulnerability(
                            methodEntryPoint = ctx.methodEntryPoint,
                            statement = callInst,
                            rule = sink.toCommonSink(),
                        )
                    }
                }
            }
        }

        // 2. Apply pass-through rules (same as in propagateFactToFact)
        for (pass in taintConfig.propagators) {
            if (matchesCall(pass.function, callInst)) {
                val fromBase = resolvePositionWithModifiers(pass.from, callInst, method, ctx)
                val toBase = resolvePositionWithModifiers(pass.to, callInst, method, ctx)
                if (fromBase != null && toBase != null && currentFactAp.base == fromBase) {
                    val newFact = currentFactAp.rebase(toBase)
                    results.add(CallToReturnZFact(newFact, null))
                }
            }
        }

        // 3. Call-to-return: keep fact
        results.add(Unchanged)

        // 4. Call-to-start: map fact into callee frame
        if (calleeMethod != null) {
            mapCallToStart(currentFactAp)?.let { (startBase, callerFact) ->
                results.add(CallToStartZFact(callerFact, startBase, null))
            }
        }

        return results
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> {
        val results = mutableSetOf<FactCallFact>()

        // 1. Check sink rules
        for (sink in taintConfig.sinks) {
            if (matchesCall(sink.function, callInst)) {
                val sinkBase = resolvePosition(sink.pos, callInst, method, ctx)
                if (sinkBase != null && currentFactAp.base == sinkBase) {
                    if (factHasMark(currentFactAp, sink.mark)) {
                        ctx.taint.taintSinkTracker.addUnconditionalVulnerability(
                            methodEntryPoint = ctx.methodEntryPoint,
                            statement = callInst,
                            rule = sink.toCommonSink(),
                        )
                    }
                }
            }
        }

        // 2. Apply pass-through rules
        for (pass in taintConfig.propagators) {
            if (matchesCall(pass.function, callInst)) {
                val fromBase = resolvePositionWithModifiers(pass.from, callInst, method, ctx)
                val toBase = resolvePositionWithModifiers(pass.to, callInst, method, ctx)
                if (fromBase != null && toBase != null && currentFactAp.base == fromBase) {
                    val newFact = currentFactAp.rebase(toBase)
                    results.add(CallToReturnFFact(initialFactAp, newFact, null))
                }
            }
        }

        // 3. Call-to-return: fact stays in caller frame
        results.add(Unchanged)

        // 4. Call-to-start: map fact into callee frame
        if (calleeMethod != null) {
            mapCallToStart(currentFactAp)?.let { (startBase, callerFact) ->
                results.add(CallToStartFFact(initialFactAp, callerFact, startBase, null))
            }
        }

        return results
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> = setOf(Unchanged)

    // --- Helpers ---

    /**
     * Maps a caller fact to a callee start base.
     * Returns (startBase, callerFact) or null if not mappable.
     */
    private fun mapCallToStart(
        callerFact: FinalFactAp,
    ): Pair<AccessPathBase, FinalFactAp>? {
        val base = callerFact.base

        // Check if fact base matches any argument
        for ((i, arg) in callInst.args.withIndex()) {
            val argBase = PIRFlowFunctionUtils.accessPathBase(arg.value, method, ctx)
                ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(i)
                return startBase to callerFact
            }
        }

        // ClassStatic facts pass through (not applicable for Python minimal prototype)
        if (base is AccessPathBase.ClassStatic) {
            return base to callerFact
        }

        return null
    }

    companion object {
        /**
         * Matches a taint rule's function pattern against a PIRCall.
         */
        fun matchesCall(ruleFunction: String, call: PIRCall): Boolean {
            val callee = call.resolvedCallee ?: return false
            return callee == ruleFunction || callee.endsWith(".$ruleFunction")
        }

        /**
         * Resolves a PositionBase to an AccessPathBase at a call site.
         */
        fun resolvePosition(
            pos: PositionBase,
            call: PIRCall,
            method: PIRFunction,
            ctx: PIRMethodAnalysisContext,
        ): AccessPathBase? = when (pos) {
            is PositionBase.Result -> {
                call.target?.let { PIRFlowFunctionUtils.accessPathBase(it, method, ctx) }
            }
            is PositionBase.Argument -> {
                val idx = pos.idx ?: return null
                call.args.getOrNull(idx)?.value?.let {
                    PIRFlowFunctionUtils.accessPathBase(it, method, ctx)
                }
            }
            is PositionBase.This -> null  // No 'this' in Python
            is PositionBase.ClassStatic -> null  // Not handled
            is PositionBase.AnyArgument -> null  // Not handled
        }

        fun resolvePositionWithModifiers(
            pos: org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers,
            call: PIRCall,
            method: PIRFunction,
            ctx: PIRMethodAnalysisContext,
        ): AccessPathBase? = resolvePosition(pos.base, call, method, ctx)
        // Modifiers ignored for minimal prototype

        /**
         * Checks if a fact carries a specific taint mark.
         */
        fun factHasMark(fact: FinalFactAp, mark: String): Boolean {
            return fact.startsWithAccessor(TaintMarkAccessor(mark))
        }
    }
}

/**
 * Adapts TaintRules.Sink to CommonTaintConfigurationSink for the TaintSinkTracker API.
 * TaintSinkTracker.addUnconditionalVulnerability requires CommonTaintConfigurationSink.
 */
private fun TaintRules.Sink.toCommonSink(): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
    override val id: String = this@toCommonSink.id
    override val meta: CommonTaintConfigurationSinkMeta = object : CommonTaintConfigurationSinkMeta {
        override val message: String = "Taint sink: ${this@toCommonSink.function}"
        override val severity: CommonTaintConfigurationSinkMeta.Severity =
            CommonTaintConfigurationSinkMeta.Severity.Error
    }
}
```

**Critical note on sink reporting**: The design doc used `ctx.taint.sinkTracker.reportVulnerability(...)`.
The actual API is `TaintSinkTracker.addVulnerability(sinkId, sinkStatement, fact)`.
This needs to be verified against the actual `TaintSinkTracker` interface.

---

#### 5.2.11 `analysis/PIRMethodCallFactMapper.kt` — NEW

The `MethodCallFactMapper` interface doesn't receive `MethodAnalysisContext`. In the JVM this works
because `JIRLocalVar` already carries an integer index. In Python, `PIRLocal` has a `String name`,
so we need the `PIRMethodAnalysisContext` to resolve names to indices.

**Solution**: Make `PIRMethodCallFactMapper` a **class** (not singleton) that receives the caller
context at construction time. `PIRMethodAnalysisContext.methodCallFactMapper` creates a new instance
each time, passing `this`.

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.python.adapter.PIRCallExprAdapter
import org.opentaint.dataflow.python.util.PIRFlowFunctionUtils
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.python.*

/**
 * Maps facts between caller and callee frames at call boundaries.
 * Constructed per-method with the caller's analysis context for name→index resolution.
 */
class PIRMethodCallFactMapper(
    private val callerCtx: PIRMethodAnalysisContext,
) : MethodCallFactMapper {

    private val callerMethod: PIRFunction get() = callerCtx.method

    private fun valueToBase(value: PIRValue): AccessPathBase? =
        PIRFlowFunctionUtils.accessPathBase(value, callerMethod, callerCtx)

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        val call = callStatement as PIRCall
        return when (val base = factAp.base) {
            is AccessPathBase.Argument -> {
                val argValue = call.args.getOrNull(base.idx)?.value ?: return emptyList()
                val callerBase = valueToBase(argValue) ?: return emptyList()
                listOf(factAp.rebase(callerBase))
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return emptyList()
                val targetBase = valueToBase(target) ?: return emptyList()
                listOf(factAp.rebase(targetBase))
            }
            is AccessPathBase.LocalVar -> emptyList()  // Cannot escape
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        val call = callStatement as PIRCall
        return when (val base = factAp.base) {
            is AccessPathBase.Argument -> {
                val argValue = call.args.getOrNull(base.idx)?.value ?: return emptyList()
                val callerBase = valueToBase(argValue) ?: return emptyList()
                listOf(factAp.rebase(callerBase))
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return emptyList()
                val targetBase = valueToBase(target) ?: return emptyList()
                listOf(factAp.rebase(targetBase))
            }
            is AccessPathBase.LocalVar -> emptyList()
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        val call = (callExpr as PIRCallExprAdapter).pirCall
        val base = factAp.base

        for ((i, arg) in call.args.withIndex()) {
            val argBase = valueToBase(arg.value) ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(i)
                onMappedFact(factAp.rebase(startBase), startBase)
            }
        }

        if (base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, base)
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        val call = (callExpr as PIRCallExprAdapter).pirCall
        val base = fact.base

        for ((i, arg) in call.args.withIndex()) {
            val argBase = valueToBase(arg.value) ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(i)
                onMappedFact(fact.rebase(startBase), startBase)
            }
        }

        if (base is AccessPathBase.ClassStatic) {
            onMappedFact(fact, base)
        }
    }

    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        val base = factAp.base
        if (base is AccessPathBase.ClassStatic || base is AccessPathBase.Constant) return true

        val call = (callExpr as PIRCallExprAdapter).pirCall
        for (arg in call.args) {
            if (base == valueToBase(arg.value)) return true
        }

        if (returnValue != null) {
            if (base == valueToBase(returnValue as PIRValue)) return true
        }

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean =
        factAp.base !is AccessPathBase.LocalVar
}
```

And the corresponding change in `PIRMethodAnalysisContext`:
```kotlin
override val methodCallFactMapper: MethodCallFactMapper
    get() = PIRMethodCallFactMapper(this)
```

---

#### 5.2.12 `analysis/PIRMethodCallSummaryHandler.kt` — NEW

Since `PIRMethodCallFactMapper` is now a class with the caller context, the summary handler
simply delegates to the context's `methodCallFactMapper`.

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.ir.api.python.PIRCall

class PIRMethodCallSummaryHandler(
    private val callInst: PIRCall,
    private val ctx: PIRMethodAnalysisContext,
    override val factTypeChecker: FactTypeChecker,
) : MethodCallSummaryHandler {

    private val factMapper get() = ctx.methodCallFactMapper

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> =
        factMapper.mapMethodExitToReturnFlowFact(callInst, fact, factTypeChecker)

    override fun handleZeroToZero(summaryFact: FinalFactAp?): Set<Sequent> {
        if (summaryFact == null) return setOf(Sequent.ZeroToZero)
        val mappedFacts = mapMethodExitToReturnFlowFact(summaryFact)
        return mappedFacts.mapTo(mutableSetOf<Sequent>()) { Sequent.ZeroToFact(it, null) }
            .also { it.add(Sequent.ZeroToZero) }
    }

    override fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: MethodCallSummaryHandler.SummaryEdgeApplication,
        summaryEdge: MethodCallSummaryHandler.SummaryEdge,
    ): Set<Sequent> = handleSummaryDefault(summaryEdge)

    override fun handleFactToFact(
        initialFactAp: org.opentaint.dataflow.ap.ifds.access.InitialFactAp,
        currentFactAp: FinalFactAp,
        summaryEffect: MethodCallSummaryHandler.SummaryEdgeApplication,
        summaryEdge: MethodCallSummaryHandler.SummaryEdge,
    ): Set<Sequent> = handleSummaryDefault(summaryEdge)

    override fun handleNDFactToFact(
        initialFacts: Set<org.opentaint.dataflow.ap.ifds.access.InitialFactAp>,
        currentFactAp: FinalFactAp,
        summaryEffect: MethodCallSummaryHandler.SummaryEdgeApplication,
        summaryEdge: MethodCallSummaryHandler.SummaryEdge,
    ): Set<Sequent> = handleSummaryDefault(summaryEdge)

    override fun prepareFactToFactSummary(summaryEdge: Edge.FactToFact): List<Edge.FactToFact> =
        listOf(summaryEdge) // No rewriting

    override fun prepareNDFactToFactSummary(summaryEdge: Edge.NDFactToFact): List<Edge.NDFactToFact> =
        listOf(summaryEdge) // No rewriting

    private fun handleSummaryDefault(
        summaryEdge: MethodCallSummaryHandler.SummaryEdge,
    ): Set<Sequent> {
        val mappedFacts = mapMethodExitToReturnFlowFact(summaryEdge.final)
        return mappedFacts.mapTo(mutableSetOf()) {
            when (summaryEdge) {
                is MethodCallSummaryHandler.SummaryEdge.F2F ->
                    Sequent.FactToFact(summaryEdge.initial, it, null)
                is MethodCallSummaryHandler.SummaryEdge.NdF2F ->
                    Sequent.ZeroToFact(it, null) // Simplified
            }
        }
    }
}
```

---

#### 5.2.13 `analysis/PIRMethodSideEffectSummaryHandler.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.SideEffectKind
import org.opentaint.dataflow.ap.ifds.SideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodSideEffectSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication

/**
 * No-op side effect handler for the minimal prototype.
 */
class PIRMethodSideEffectSummaryHandler : MethodSideEffectSummaryHandler {

    override fun handleZeroToZero(
        sideEffects: List<SideEffectSummary.ZeroSideEffectSummary>,
    ): Set<Sequent> = emptySet()

    override fun handleZeroToFact(
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        kind: SideEffectKind,
    ): Set<Sequent> = emptySet()

    override fun handleFactToFact(
        currentInitialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        summaryEffect: SummaryEdgeApplication,
        kind: SideEffectKind,
    ): Set<Sequent> = emptySet()
}
```

---

#### 5.2.14 `serialization/PIRMethodContextSerializer.kt` — NEW

```kotlin
package org.opentaint.dataflow.python.serialization

import org.opentaint.dataflow.ap.ifds.EmptyMethodContext
import org.opentaint.dataflow.ap.ifds.MethodContext
import org.opentaint.dataflow.ap.ifds.serialization.MethodContextSerializer
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Minimal context serializer for Python. Since we only use EmptyMethodContext,
 * serialization is trivial.
 */
class PIRMethodContextSerializer : MethodContextSerializer {

    override fun writeMethodContext(context: MethodContext, output: DataOutputStream) {
        // Only EmptyMethodContext is used
        output.writeByte(0) // tag for EmptyMethodContext
    }

    override fun readMethodContext(input: DataInputStream): MethodContext {
        val tag = input.readByte().toInt()
        return when (tag) {
            0 -> EmptyMethodContext
            else -> error("Unknown method context tag: $tag")
        }
    }
}
```

---

#### 5.2.15 `graph/PIRApplicationGraph.kt` — REWRITE (existing stub)

```kotlin
package org.opentaint.dataflow.python.graph

import org.opentaint.ir.api.python.*
import org.opentaint.util.analysis.ApplicationGraph
import java.util.IdentityHashMap

class PIRApplicationGraph(
    val cp: PIRClasspath,
) : ApplicationGraph<PIRFunction, PIRInstruction> {

    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        if (node !is PIRCall) return emptySequence()
        val calleeName = node.resolvedCallee ?: return emptySequence()
        val fn = cp.findFunctionOrNull(calleeName)
        return if (fn != null) sequenceOf(fn) else emptySequence()
    }

    override fun callers(method: PIRFunction): Sequence<PIRInstruction> =
        emptySequence()  // Not needed for forward analysis

    override fun methodOf(node: PIRInstruction): PIRFunction =
        node.location.method

    override fun methodGraph(method: PIRFunction): ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> =
        PIRFunctionGraph(method, this)

    class PIRFunctionGraph(
        override val method: PIRFunction,
        override val applicationGraph: ApplicationGraph<PIRFunction, PIRInstruction>,
    ) : ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> {

        private val flatInstructions: List<PIRInstruction> by lazy {
            method.cfg.blocks.sortedBy { it.label }.flatMap { it.instructions }
        }

        private val succs: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildSuccessors()
        }

        private val preds: Map<PIRInstruction, List<PIRInstruction>> by lazy {
            buildPredecessors()
        }

        private fun buildSuccessors(): Map<PIRInstruction, List<PIRInstruction>> {
            val cfg = method.cfg
            val result = IdentityHashMap<PIRInstruction, List<PIRInstruction>>()

            for (block in cfg.blocks) {
                val insts = block.instructions
                if (insts.isEmpty()) continue

                // Within-block successors
                for (i in 0 until insts.size - 1) {
                    result[insts[i]] = listOf(insts[i + 1])
                }

                // Last instruction → successor blocks' first instructions
                val terminator = insts.last()
                val succBlocks = cfg.successors(block)
                result[terminator] = succBlocks.mapNotNull { it.instructions.firstOrNull() }
            }

            return result
        }

        private fun buildPredecessors(): Map<PIRInstruction, List<PIRInstruction>> {
            val result = IdentityHashMap<PIRInstruction, MutableList<PIRInstruction>>()
            for ((inst, successors) in succs) {
                for (succ in successors) {
                    result.getOrPut(succ) { mutableListOf() }.add(inst)
                }
            }
            return result
        }

        override fun predecessors(node: PIRInstruction): Sequence<PIRInstruction> =
            (preds[node] ?: emptyList()).asSequence()

        override fun successors(node: PIRInstruction): Sequence<PIRInstruction> =
            (succs[node] ?: emptyList()).asSequence()

        override fun entryPoints(): Sequence<PIRInstruction> =
            method.cfg.entry.instructions.firstOrNull()
                ?.let { sequenceOf(it) } ?: emptySequence()

        override fun exitPoints(): Sequence<PIRInstruction> =
            method.cfg.exits.asSequence()
                .mapNotNull { it.instructions.lastOrNull() }

        override fun statements(): Sequence<PIRInstruction> =
            flatInstructions.asSequence()
    }
}
```

---

#### 5.2.16 `rules/TaintRules.kt` — NO CHANGES

Already complete. Contains `TaintRules.Source`, `TaintRules.Sink`, `TaintRules.Pass`, and `PIRTaintConfig`.

---

### 5.3 File Summary Table

| File | Status | Package | Lines (est.) |
|------|--------|---------|-------------|
| `PIRLanguageManager.kt` | MODIFY | `dataflow.python` | ~80 |
| `analysis/PIRAnalysisManager.kt` | REWRITE | `dataflow.python.analysis` | ~120 |
| `analysis/PIRMethodAnalysisContext.kt` | **NEW** | `dataflow.python.analysis` | ~120 |
| `analysis/PIRCallResolver.kt` | **NEW** | `dataflow.python.analysis` | ~15 |
| `analysis/PIRMethodCallResolver.kt` | **NEW** | `dataflow.python.analysis` | ~50 |
| `analysis/PIRMethodStartFlowFunction.kt` | **NEW** | `dataflow.python.analysis` | ~25 |
| `analysis/PIRMethodSequentFlowFunction.kt` | **NEW** | `dataflow.python.analysis` | ~170 |
| `analysis/PIRMethodCallFlowFunction.kt` | **NEW** | `dataflow.python.analysis` | ~180 |
| `analysis/PIRMethodCallFactMapper.kt` | **NEW** | `dataflow.python.analysis` | ~120 |
| `analysis/PIRMethodCallSummaryHandler.kt` | **NEW** | `dataflow.python.analysis` | ~90 |
| `analysis/PIRMethodSideEffectSummaryHandler.kt` | **NEW** | `dataflow.python.analysis` | ~30 |
| `adapter/PIRCallExprAdapter.kt` | **NEW** | `dataflow.python.adapter` | ~25 |
| `serialization/PIRMethodContextSerializer.kt` | **NEW** | `dataflow.python.serialization` | ~30 |
| `util/PIRFlowFunctionUtils.kt` | **NEW** | `dataflow.python.util` | ~100 |
| `graph/PIRApplicationGraph.kt` | REWRITE | `dataflow.python.graph` | ~120 |
| `rules/TaintRules.kt` | NO CHANGE | `dataflow.python.rules` | 17 |

**Total new/modified files**: 15 (2 modified, 12 new, 1 unchanged)
**Estimated total new code**: ~1,200 lines

---

## 6. Module 3 — Dependency Wiring Between Components

The `PIRAnalysisManager` is the central factory. The engine calls it to create components.
Here is the flow:

```
TaintAnalysisUnitRunnerManager(analysisManager, graph, ...)
    │
    ├─ analysisManager.getMethodCallResolver(graph, unitResolver, runner)
    │   └─ creates PIRMethodCallResolver(PIRCallResolver(cp), runner)
    │
    ├─ For each method entry point:
    │   ├─ analysisManager.getMethodAnalysisContext(entryPoint, graph, resolver, taintCtx, ...)
    │   │   └─ creates PIRMethodAnalysisContext(entryPoint, method, taintCtx)
    │   │
    │   ├─ analysisManager.getMethodStartFlowFunction(apManager, ctx)
    │   │   └─ creates PIRMethodStartFlowFunction(method, ctx, taintConfig, apManager)
    │   │
    │   ├─ For each instruction in method:
    │   │   ├─ if non-call: analysisManager.getMethodSequentFlowFunction(apManager, ctx, inst, ...)
    │   │   │   └─ creates PIRMethodSequentFlowFunction(inst, method, ctx, config, apManager)
    │   │   │
    │   │   └─ if call: analysisManager.getMethodCallFlowFunction(apManager, ctx, retVal, callExpr, inst, ...)
    │   │       └─ creates PIRMethodCallFlowFunction(pirCall, method, ctx, config, callee, apManager, retVal)
    │   │
    │   └─ analysisManager.getMethodCallSummaryHandler(apManager, ctx, inst)
    │       └─ creates PIRMethodCallSummaryHandler(pirCall, method, ctx, factTypeChecker)
    │
    └─ PIRMethodCallFactMapper (per-method instance, created by PIRMethodAnalysisContext)
        └─ holds reference to PIRMethodAnalysisContext for name→index resolution
```

**Critical wiring: taint config access path**

The `PIRTaintConfig` must reach the flow functions. The path is:
1. Test creates `PIRTaintConfig` and passes to `TaintAnalysisUnitRunnerManager(taintConfig = config)`
2. Engine wraps it in `TaintAnalysisContext(taintConfig = config, taintSinkTracker = ...)`
3. Engine calls `getMethodAnalysisContext(taintAnalysisContext = taintCtx)`
4. `PIRMethodAnalysisContext` stores `taint: TaintAnalysisContext`
5. Flow functions access `ctx.taint.taintConfig as PIRTaintConfig`

So `PIRAnalysisManager` does NOT need `PIRTaintConfig` in its constructor. Each flow function
reads it from the method analysis context.

**Revised `PIRAnalysisManager` constructor**:
```kotlin
class PIRAnalysisManager(cp: PIRClasspath) : PIRLanguageManager(cp), TaintAnalysisManager
```

And in each flow function factory:
```kotlin
override fun getMethodCallFlowFunction(...): MethodCallFlowFunction {
    val ctx = analysisContext as PIRMethodAnalysisContext
    val config = ctx.taint.taintConfig as PIRTaintConfig
    val pirCall = statement as PIRCall
    val callee = PIRCallResolver(cp).resolve(pirCall)
    return PIRMethodCallFlowFunction(pirCall, ctx.method, ctx, config, callee, apManager, returnValue)
}
```

---

## 7. Module 4: `core` — Test and Sample Changes

### 7.1 `AnalysisTest.kt` — One-line fix

**Path**: `core/src/test/kotlin/org/opentaint/python/sast/dataflow/AnalysisTest.kt`

**Current** (line ~127):
```kotlin
val engine = TaintAnalysisUnitRunnerManager(
    PIRAnalysisManager(),
    ...
)
```

**Required**:
```kotlin
val engine = TaintAnalysisUnitRunnerManager(
    PIRAnalysisManager(cp),
    ...
)
```

Since every `PIRInstruction` now carries `location` (with `method` and `index` already wired
by `MypyModuleBuilder`), there are no shared maps to worry about. Both `PIRApplicationGraph.methodOf()`
and `PIRLanguageManager.getInstIndex()` read directly from `inst.location`.

### 7.3 Python Samples — No changes needed for existing tests

The existing `Sample.py` is sufficient for the two tests. New samples for the test system expansion
(Phase 2) will be added in a later phase.

### 7.4 Future: New Test Files (Phase 2)

Per the test system design (`python-dataflow-test-system.md`), new test files will be added:
- `core/samples/src/main/python/intraprocedural/*.py`
- `core/samples/src/main/python/interprocedural/*.py`
- etc.

And corresponding Kotlin test classes:
- `core/src/test/kotlin/org/opentaint/python/sast/dataflow/IntraproceduralFlowTest.kt`
- etc.

These are out of scope for the immediate implementation but the infrastructure supports them.

---

## 8. Alignment: Design ↔ Test System Requirements

The test system (`python-dataflow-test-system.md`) defines P0-P3 priority test cases.
Here's what the minimal prototype (this implementation) supports:

### P0 Tests — Must Pass

| Test | Required Feature | Supported? | Notes |
|------|-----------------|-----------|-------|
| `assign_direct` | Simple assignment propagation | ✅ | `PIRMethodSequentFlowFunction.handleAssign` |
| `assign_chain` | Multi-hop assignment | ✅ | Same mechanism, chained |
| `assign_long_chain` | 4-hop chain | ✅ | Same |
| `assign_overwrite` | Strong update (kill) | ✅ | `simpleAssign` kills when `currentFactAp.base == to` |
| `assign_overwrite_other` | Kill doesn't affect other vars | ✅ | Other vars are `Unchanged` |
| `call_simple` | Call with argument | ✅ | `PIRMethodCallFlowFunction` + `PIRCallResolver` |
| `call_return` | Return value propagation | ✅ | `PIRMethodSequentFlowFunction.handleReturn` |
| `call_pass_through` | Arg → return value | ✅ | Interprocedural via call-to-start + return |

### P1 Tests — Should Pass with Basic Interprocedural

| Test | Required Feature | Supported? | Notes |
|------|-----------------|-----------|-------|
| `branch_if_true` | Branch propagation | ✅ | IFDS naturally handles both branches |
| `branch_if_else_both` | Both branches tainted | ✅ | Merge at join point |
| `branch_if_else_one` | One branch tainted | ✅ | Over-approximation: reports reachable |
| `call_chain_2` | 2-level call chain | ✅ | Recursive analysis |
| `call_multiple_args` | Multiple argument mapping | ✅ | `PIRMethodCallFactMapper` maps by index |
| `field_simple_read` | `obj.x = source(); sink(obj.x)` | ⚠️ Partial | `PIRStoreAttr` + `PIRAttrExpr` handling exists but needs `FieldAccessor` matching |
| `class_method_call` | `self` parameter flow | ⚠️ Partial | Depends on mypy resolution of method calls |
| `loop_for_body` | Loop body propagation | ✅ | IFDS fixpoint handles loops |
| `loop_while_body` | While loop propagation | ✅ | Same |

### P2+ Tests — Require Extensions

| Feature | Status | What's Missing |
|---------|--------|---------------|
| Keyword arguments | Not handled | `PIRCallArgKind.KEYWORD` ignored in fact mapper |
| Default arguments | Not handled | Default value analysis |
| `*args` / `**kwargs` | Not handled | Variadic argument mapping |
| String operations (`str.upper()`) | Not handled | Pass-through rules for builtins |
| Dict/list element tracking | Partial | `ElementAccessor` exists but index-sensitivity missing |
| Lambda/closure | Not handled | Closure variable flow |
| Generators/yield | Not handled | Generator frame semantics |
| Context sensitivity | Not handled | Uses `EmptyMethodContext` always |
| Cross-file imports | Depends | On mypy resolution + classpath |
| Exception flow | Not handled | `PIRRaise` / `PIRExceptHandler` not in flow functions |

### Gap Analysis: What the Test System Needs That the Design Doesn't Cover

1. **Pass-through rules for builtins** (`str.upper()`, `list.append()`, etc.)
   - The `PIRTaintConfig.propagators` field exists but is empty
   - Implementation: Add `TaintRules.Pass` entries in `commonPathRules` in test base class
   - The `PIRMethodCallFlowFunction` already has pass-through rule handling code

2. **Benchmark adaptation script** (`tools/adapt_benchmark.py`)
   - Not part of the engine implementation
   - Should be implemented after the engine works

3. **Multiple Python files in one classpath**
   - Already supported by `PIRClasspathImpl.create()` which accepts a list of source files
   - The samples JAR already bundles multiple `.py` files
   - Cross-file tests just need proper import resolution (depends on mypy)

4. **Test execution tags (P0, P1, etc.)**
   - JUnit 5 `@Tag` annotations — purely a test infrastructure concern
   - Add after the engine works

---

## 9. Implementation Order

### Phase A: IR Fixes (Module 1-2)

1. **Make `PIRExpr` extend `CommonExpr`** — add `typeName` property
2. **Make `PIRValue` extend `CommonValue`** — add `typeName` override
3. **Create `PIRLocationImpl`** — data class wrapping `PIRFunction`

Estimated effort: ~30 minutes

### Phase B: Core Engine (Module 3)

Implement in this order (each step builds on the previous):

1. **`PIRFlowFunctionUtils`** — value→AccessPathBase mapping (no dependencies)
2. **`PIRCallExprAdapter`** — PIRCall→CommonCallExpr bridge (depends on PIRValue extending CommonValue)
3. **`PIRMethodContextSerializer`** — trivial, EmptyMethodContext only
4. **`PIRLanguageManager`** — instruction indexing, call expression extraction
5. **`PIRApplicationGraph` + `PIRFunctionGraph`** — instruction-level CFG from block-level CFG
6. **`PIRMethodAnalysisContext`** — name→index mapping, local variable collection
7. **`PIRCallResolver`** — mypy static resolution
8. **`PIRMethodCallResolver`** — MethodCallResolver wrapper
9. **`PIRMethodCallFactMapper`** — caller↔callee fact mapping
10. **`PIRMethodStartFlowFunction`** — entry point facts
11. **`PIRMethodSequentFlowFunction`** — assignment, return, store handling
12. **`PIRMethodCallFlowFunction`** — source/sink rules, call-to-start
13. **`PIRMethodCallSummaryHandler`** — summary application
14. **`PIRMethodSideEffectSummaryHandler`** — no-op
15. **`PIRAnalysisManager`** — wire everything together

Estimated effort: 2-3 days

### Phase C: Test Fix (Module 4)

1. **Update `AnalysisTest.kt`** — pass `cp` to `PIRAnalysisManager`
2. **Run tests** — `./gradlew :test --tests "org.opentaint.python.sast.dataflow.PythonDataflowTest"`
3. **Debug and fix** — iterate until both tests pass

Estimated effort: 1-2 days (mostly debugging)

### Phase D: Test System Expansion

1. Add P0 test cases to `Sample.py` (chain, overwrite_other, etc.)
2. Create new sample files for P1 cases (interprocedural, branch, loop)
3. Create new test classes (`IntraproceduralFlowTest.kt`, etc.)
4. Add `commonPathRules` for builtins (P2)
5. Run the benchmark adaptation script (when engine is stable)

Estimated effort: 3-5 days

---

## 10. Risk Areas

### 10.1 `TaintSinkTracker` API

The design doc uses `ctx.taint.sinkTracker.reportVulnerability(...)`. The actual class is
`TaintSinkTracker` with method `addVulnerability(sinkId, sinkStatement, fact)`.
Need to verify the exact API signature.

### 10.2 `MethodAnalyzer.MethodCallHandler` callback pattern

The `resolveMethodCall` in `PIRMethodCallResolver` needs to correctly interact with the engine's
method analyzer callbacks. The JVM code uses `runner.getOrCreateMethodAnalyzer(methodWithContext)`.
Need to verify this method exists on `TaintAnalysisUnitRunner`.

### 10.3 Precondition interfaces

The design returns `MethodCallPrecondition.Default`, `MethodStartPrecondition.Default`, etc.
Need to verify these `Default` objects exist. If not, implement no-op preconditions.

### 10.4 `PIRFunction.isUnknown` handling

Unknown functions (where mypy failed to parse) have empty CFGs and should be skipped.
The `registerMethod()` in `PIRLanguageManager` should check `if (function.isUnknown) return`.

### 10.5 `PIRMethodCallFactMapper` per-instance overhead

`PIRMethodCallFactMapper` is created per-method (by `PIRMethodAnalysisContext.methodCallFactMapper`).
This is a lightweight object (holds one reference to the context). If performance profiling shows
this is a concern, the getter could cache the instance. But this is unlikely to matter.

### 10.6 `PIRCall.target` semantics

In PIR, `PIRCall.target` is the variable that receives the return value. After lowering,
a call like `data = source()` may produce:
```
PIRCall(target=$t0, callee=GlobalRef("source"), args=[])
PIRAssign(target=Local("data"), expr=Local("$t0"))
```

The source rule with `PositionBase.Result` should map to `PIRCall.target` (i.e., `$t0`), and then
the subsequent `PIRAssign` propagates taint from `$t0` to `data`. This is the correct pattern.

However, if PIR sometimes produces `PIRCall(target=Local("data"), ...)` directly (without the
intermediate temp), the behavior must still be correct. Need to verify PIR's lowering pattern
for function calls.

---

## 11. Appendix: Complete New File Listing

```
NEW FILES:
  core/opentaint-ir/python/opentaint-ir-impl-python/
    src/main/kotlin/org/opentaint/ir/impl/python/PIRLocationImpl.kt

  core/opentaint-dataflow-core/opentaint-python-dataflow/
    src/main/kotlin/org/opentaint/dataflow/python/
      adapter/PIRCallExprAdapter.kt
      analysis/PIRMethodAnalysisContext.kt
      analysis/PIRCallResolver.kt
      analysis/PIRMethodCallResolver.kt
      analysis/PIRMethodStartFlowFunction.kt
      analysis/PIRMethodSequentFlowFunction.kt
      analysis/PIRMethodCallFlowFunction.kt
      analysis/PIRMethodCallFactMapper.kt
      analysis/PIRMethodCallSummaryHandler.kt
      analysis/PIRMethodSideEffectSummaryHandler.kt
      serialization/PIRMethodContextSerializer.kt
      util/PIRFlowFunctionUtils.kt

MODIFIED FILES:
  core/opentaint-ir/python/opentaint-ir-api-python/
    src/main/kotlin/org/opentaint/ir/api/python/Values.kt          (PIRValue extends CommonValue, PIRExpr extends CommonExpr)
    src/main/kotlin/org/opentaint/ir/api/python/Instructions.kt     (PIRLocation adds index, PIRInstruction adds lateinit var location,
                                                                      all 21 instruction data classes get `override lateinit var location`)

  core/opentaint-ir/python/opentaint-ir-impl-python/
    src/main/kotlin/org/opentaint/ir/impl/python/builder/MypyModuleBuilder.kt  (wire location post-construction in 3 places)

  core/opentaint-dataflow-core/opentaint-python-dataflow/
    src/main/kotlin/org/opentaint/dataflow/python/PIRLanguageManager.kt     (implement all methods)
    src/main/kotlin/org/opentaint/dataflow/python/analysis/PIRAnalysisManager.kt  (implement all methods)
    src/main/kotlin/org/opentaint/dataflow/python/graph/PIRApplicationGraph.kt    (implement all methods)

  core/src/test/kotlin/org/opentaint/python/sast/dataflow/AnalysisTest.kt
    (one-line change: pass cp to PIRAnalysisManager)

UNCHANGED FILES:
  core/opentaint-dataflow-core/opentaint-python-dataflow/
    src/main/kotlin/org/opentaint/dataflow/python/rules/TaintRules.kt
```
