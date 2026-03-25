# Python Dataflow Analysis — Design Document

## 1. Executive Summary

This document describes the design for a minimal Python taint dataflow analysis prototype,
built on top of the existing IFDS-based dataflow engine (`opentaint-dataflow`) and the Python IR (`PIR`).

The JVM dataflow engine (`opentaint-jvm-dataflow`) serves as the reference implementation.
The Python engine follows the same architectural patterns but adapts them to Python-specific
IR characteristics — most critically, the fact that PIR uses **string-named variables** rather
than **integer-indexed locals** as in JVM bytecode.

### Goal

Make the two existing tests pass:
- `testSimpleSample` — taint flows from `source()` through variable assignment into `sink()`
- `testSimpleNonReachableSample` — taint is killed by overwriting with a clean literal

These tests exercise: intra-procedural assignment propagation, variable kill (strong update),
call resolution (for `source()` and `sink()`), and source/sink taint rule application.

---

## 2. Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                    Dataflow Engine (Core)                   │
│  TaintAnalysisUnitRunnerManager → MethodAnalyzer (IFDS)    │
│  Operates on: CommonMethod, CommonInst, CommonCallExpr     │
└──────────────────────┬─────────────────────────────────────┘
                       │ calls
                       ▼
┌────────────────────────────────────────────────────────────┐
│              PIRAnalysisManager (Factory)                   │
│  Implements: TaintAnalysisManager                          │
│  Creates all Python-specific analysis components           │
├────────────────────────────────────────────────────────────┤
│  PIRLanguageManager        ← instruction indexing, call    │
│                               expression extraction        │
│  PIRMethodAnalysisContext  ← per-method state, var mapping │
│  PIRCallResolver           ← resolves calls to callees    │
│  PIRMethodStartFlowFunction                                │
│  PIRMethodSequentFlowFunction                              │
│  PIRMethodCallFlowFunction                                 │
│  PIRMethodCallFactMapper   ← maps facts caller↔callee     │
│  PIRApplicationGraph       ← interprocedural CFG          │
│  PIRFunctionGraph          ← intraprocedural CFG          │
└────────────────────────────────────────────────────────────┘
                       │ operates on
                       ▼
┌────────────────────────────────────────────────────────────┐
│                    Python IR (PIR)                          │
│  PIRFunction, PIRInstruction, PIRValue, PIRCFG, etc.       │
└────────────────────────────────────────────────────────────┘
```

---

## 3. The Central Challenge: Named Variables → Integer Indices

### Problem

The dataflow core uses `AccessPathBase.LocalVar(idx: Int)` and `AccessPathBase.Argument(idx: Int)`
to identify variables. JVM bytecode naturally provides integer indices for all locals and arguments.

Python IR (`PIR`) identifies variables **by name**:
- `PIRLocal(name: String, type: PIRType)` — local variables and temporaries (`$t0`, `$t1`, ...)
- `PIRParameterRef(name: String, type: PIRType)` — parameter references by name
- `PIRGlobalRef(name: String, module: String)` — global/import references

### Solution: PIRMethodAnalysisContext Maintains a Name→Index Mapping

Each `PIRMethodAnalysisContext` builds and maintains a bidirectional mapping between
PIR variable names and integer indices, scoped per-function:

```kotlin
class PIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {

    // Name-to-index mapping for local variables
    private val localNameToIndex: Map<String, Int>
    private val indexToLocalName: Map<Int, String>

    // Built by scanning all instructions in the function's CFG
    init {
        val names = mutableSetOf<String>()
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
                collectLocalNames(inst, names)
            }
        }
        // Parameters are NOT included here — they use AccessPathBase.Argument(idx)
        // Only PIRLocal names go into the local variable index
        localNameToIndex = names.sorted().withIndex()
            .associate { (idx, name) -> name to idx }
        indexToLocalName = localNameToIndex.entries
            .associate { (name, idx) -> idx to name }
    }

    fun localIndex(name: String): Int =
        localNameToIndex[name] ?: error("Unknown local: $name")

    fun localName(index: Int): String =
        indexToLocalName[index] ?: error("Unknown index: $index")
}
```

The mapping is deterministic (sorted by name) and stable within a single analysis run.

### Why This Works

- `AccessPathBase.Argument(idx)` maps directly to `PIRParameter.index` (which PIR already provides)
- `AccessPathBase.LocalVar(idx)` maps to the name-based index computed above
- At call boundaries, `PIRMethodCallFactMapper` translates between caller-frame locals
  and callee-frame `Argument(i)` bases using the respective method contexts

---

## 4. Component Designs

### 4.1 PIRLanguageManager

**Purpose**: Provides instruction indexing and call expression extraction to the dataflow engine.

**Key design decisions**:

PIR instructions live in basic blocks within a CFG. The engine expects a flat integer index.
We flatten the CFG's basic blocks into a linear instruction list, assigning sequential indices.

```kotlin
open class PIRLanguageManager : LanguageManager {

    // Caches: PIRFunction → flat instruction list
    private val flatInsts = mutableMapOf<PIRFunction, List<PIRInstruction>>()

    private fun flattenCfg(method: PIRFunction): List<PIRInstruction> =
        flatInsts.getOrPut(method) {
            // Flatten blocks in label order, concatenate instructions
            method.cfg.blocks
                .sortedBy { it.label }
                .flatMap { it.instructions }
        }

    override fun getInstIndex(inst: CommonInst): Int {
        val pirInst = inst as PIRInstruction
        // We need the method to find the flat list — use PIRLocation
        val method = pirInst.location.method
        return flattenCfg(method).indexOf(pirInst)
    }

    override fun getMaxInstIndex(method: CommonMethod): Int {
        return flattenCfg(method as PIRFunction).size - 1
    }

    override fun getInstByIndex(method: CommonMethod, index: Int): CommonInst {
        return flattenCfg(method as PIRFunction)[index]
    }

    override fun isEmpty(method: CommonMethod): Boolean {
        return flattenCfg(method as PIRFunction).isEmpty()
    }

    override fun getCallExpr(inst: CommonInst): CommonCallExpr? {
        return (inst as? PIRCall)?.let { PIRCallExprAdapter(it) }
    }

    override fun producesExceptionalControlFlow(inst: CommonInst): Boolean {
        return inst is PIRRaise
    }

    override fun getCalleeMethod(callExpr: CommonCallExpr): CommonMethod {
        val adapted = callExpr as PIRCallExprAdapter
        // Use resolvedCallee from PIRCall to find the function
        val qualifiedName = adapted.pirCall.resolvedCallee
            ?: error("Unresolved call: ${adapted.pirCall.callee}")
        return adapted.classpath.findFunctionOrNull(qualifiedName)
            ?: error("Method not found: $qualifiedName")
    }

    override val methodContextSerializer: MethodContextSerializer =
        PIRMethodContextSerializer()
}
```

**Important**: `PIRInstruction.location` is currently a `TODO()`. This must be implemented first
(see Section 6 — Required PIR Changes).

**Alternative approach for `getInstIndex`**: Instead of `indexOf` (O(n) per call), we can precompute
an identity map `PIRInstruction → Int` during flattening. This is essential for performance.

```kotlin
private val instToIndex = mutableMapOf<PIRFunction, Map<PIRInstruction, Int>>()

private fun buildIndex(method: PIRFunction): Map<PIRInstruction, Int> =
    instToIndex.getOrPut(method) {
        flattenCfg(method).withIndex().associate { (idx, inst) -> inst to idx }
    }

override fun getInstIndex(inst: CommonInst): Int {
    val pirInst = inst as PIRInstruction
    val method = pirInst.location.method
    return buildIndex(method)[pirInst] ?: error("Instruction not found")
}
```

### 4.2 PIRCallExprAdapter — Bridging PIRCall to CommonCallExpr

**Problem**: The dataflow engine expects `CommonCallExpr` with `args: List<CommonValue>`.
PIR has `PIRCall` (an instruction, not an expression) with `args: List<PIRCallArg>` where
each arg is a `PIRValue` (which does NOT implement `CommonValue`).

**Solution**: Create adapter classes that wrap PIR types to implement Common interfaces.

1. Inherit `PIRValue` from `CommonValue`
2. Adapter for call statement

```kotlin
/**
 * Adapts PIRCall to CommonCallExpr for the dataflow engine.
 */
class PIRCallExprAdapter(
    val pirCall: PIRCall,
    val classpath: PIRClasspath,
) : CommonCallExpr {
    override val typeName: String get() = "call"

    override val args: List<CommonValue>
        get() = pirCall.args.map { PIRValueAdapter(it.value) }
}


### 4.3 PIRApplicationGraph and PIRFunctionGraph

**Purpose**: Provides the interprocedural and intraprocedural control flow graphs.

```kotlin
class PIRApplicationGraph(val cp: PIRClasspath)
    : ApplicationGraph<PIRFunction, PIRInstruction> {

    override fun callees(node: PIRInstruction): Sequence<PIRFunction> {
        if (node !is PIRCall) return emptySequence()
        val calleeName = node.resolvedCallee ?: return emptySequence()
        val fn = cp.findFunctionOrNull(calleeName)
        return if (fn != null) sequenceOf(fn) else emptySequence()
    }

    // For the minimal prototype, callers() can return empty
    // (it is mainly used for demand-driven analysis bootstrapping)
    override fun callers(method: PIRFunction): Sequence<PIRInstruction> =
        emptySequence()

    override fun methodOf(node: PIRInstruction): PIRFunction {
        return node.location.method
    }

    override fun methodGraph(method: PIRFunction): PIRFunctionGraph =
        PIRFunctionGraph(method, this)
}
```

```kotlin
class PIRFunctionGraph(
    override val method: PIRFunction,
    override val applicationGraph: PIRApplicationGraph,
) : ApplicationGraph.MethodGraph<PIRFunction, PIRInstruction> {

    // Flatten the CFG blocks into a linear instruction sequence
    private val flatInstructions: List<PIRInstruction> by lazy {
        method.cfg.blocks.sortedBy { it.label }.flatMap { it.instructions }
    }

    // Build successor/predecessor maps at instruction level
    // Within a block: instruction[i] → instruction[i+1]
    // Between blocks: last instruction of block → first instruction of successor blocks
    private val succs: Map<PIRInstruction, List<PIRInstruction>> by lazy {
        buildInstructionSuccessors(method.cfg)
    }
    private val preds: Map<PIRInstruction, List<PIRInstruction>> by lazy {
        buildInstructionPredecessors(method.cfg)
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
```

**Building instruction-level successors from block-level CFG**:

```
For each block B in CFG:
  For i in 0..<B.instructions.size-1:
    succs[B.instructions[i]] = [B.instructions[i+1]]
  
  terminator = B.instructions.last()
  succs[terminator] = cfg.successors(B)
      .mapNotNull { it.instructions.firstOrNull() }
```

### 4.4 PIRMethodAnalysisContext

**Purpose**: Holds per-method analysis state, including the critical variable name→index mapping.

```kotlin
class PIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {

    /** Map from PIRLocal names to integer indices (for AccessPathBase.LocalVar) */
    val localNameToIndex: Map<String, Int>

    /** Reverse map from index to local name */
    val indexToLocalName: Map<Int, String>

    init {
        // Collect all local variable names in the function
        val names = mutableLinkedSetOf<String>()
        for (block in method.cfg.blocks) {
            for (inst in block.instructions) {
                collectLocalNames(inst, names)
            }
        }
        // Stable ordering: insertion order (sorted or linear scan order)
        localNameToIndex = names.withIndex().associate { (idx, name) -> name to idx }
        indexToLocalName = localNameToIndex.entries.associate { (name, idx) -> idx to name }
    }

    private fun collectLocalNames(inst: PIRInstruction, names: MutableSet<String>) {
        when (inst) {
            is PIRAssign -> {
                collectFromValue(inst.target, names)
                collectFromExpr(inst.expr, names)
            }
            is PIRCall -> {
                inst.target?.let { collectFromValue(it, names) }
                collectFromValue(inst.callee, names)
                inst.args.forEach { collectFromValue(it.value, names) }
            }
            is PIRReturn -> inst.value?.let { collectFromValue(it, names) }
            is PIRBranch -> collectFromValue(inst.condition, names)
            // ... other instruction types as needed
            else -> {}
        }
    }

    private fun collectFromValue(value: PIRValue, names: MutableSet<String>) {
        when (value) {
            is PIRLocal -> names.add(value.name)
            // PIRParameterRef is NOT added — parameters use Argument(idx)
            // PIRGlobalRef is NOT added — globals use a different mechanism
            else -> {}
        }
    }

    private fun collectFromExpr(expr: PIRExpr, names: MutableSet<String>) {
        when (expr) {
            is PIRValue -> collectFromValue(expr, names)
            is PIRBinExpr -> { collectFromValue(expr.left, names); collectFromValue(expr.right, names) }
            is PIRAttrExpr -> collectFromValue(expr.obj, names)
            is PIRSubscriptExpr -> { collectFromValue(expr.obj, names); collectFromValue(expr.index, names) }
            // ... other expression types
            else -> {}
        }
    }
}
```

### 4.5 PIRFlowFunctionUtils — Value to AccessPathBase Mapping

**Purpose**: Converts PIR values to `AccessPathBase` (the analog of JVM's `MethodFlowFunctionUtils`).

```kotlin
object PIRFlowFunctionUtils {

    /**
     * Maps a PIRValue to an AccessPathBase using the method context's name→index mapping.
     *
     * PIRLocal("x") → AccessPathBase.LocalVar(ctx.localIndex("x"))
     * PIRParameterRef("arg") → AccessPathBase.Argument(parameterIndex)
     * PIRConst → AccessPathBase.Constant(type, value)
     * PIRGlobalRef → null (for now; could map to ClassStatic in future)
     */
    fun accessPathBase(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): AccessPathBase? = when (value) {
        is PIRLocal -> AccessPathBase.LocalVar(ctx.localIndex(value.name))
        is PIRParameterRef -> {
            val param = method.parameters.first { it.name == value.name }
            AccessPathBase.Argument(param.index)
        }
        is PIRIntConst -> AccessPathBase.Constant("int", value.value.toString())
        is PIRStrConst -> AccessPathBase.Constant("str", value.value)
        is PIRBoolConst -> AccessPathBase.Constant("bool", value.value.toString())
        is PIRFloatConst -> AccessPathBase.Constant("float", value.value.toString())
        is PIRNoneConst -> AccessPathBase.Constant("NoneType", "None")
        is PIRGlobalRef -> null  // globals not tracked as locals for now
        else -> null
    }

    /**
     * Represents a memory access for flow function computation.
     */
    sealed interface Access {
        val base: AccessPathBase

        data class Simple(override val base: AccessPathBase) : Access
        data class AttrAccess(
            override val base: AccessPathBase,
            val accessor: Accessor,
        ) : Access
    }

    /**
     * Converts a PIRValue or compound expression into an Access.
     * Simple values → Simple(accessPathBase)
     * PIRAttrExpr → AttrAccess(obj.base, FieldAccessor(attr))
     * PIRSubscriptExpr → AttrAccess(obj.base, ElementAccessor)
     */
    fun mkAccess(
        value: PIRValue,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): Access? {
        val base = accessPathBase(value, method, ctx) ?: return null
        return Access.Simple(base)
    }

    fun mkAccess(
        expr: PIRExpr,
        method: PIRFunction,
        ctx: PIRMethodAnalysisContext,
    ): Access? = when (expr) {
        is PIRValue -> mkAccess(expr, method, ctx)
        is PIRAttrExpr -> {
            val objBase = accessPathBase(expr.obj, method, ctx) ?: return null
            val accessor = Accessor.FieldAccessor(
                /* className = */ expr.obj.type.typeName,
                /* fieldName = */ expr.attribute,
                /* fieldType = */ expr.type.typeName,
            )
            Access.AttrAccess(objBase, accessor)
        }
        is PIRSubscriptExpr -> {
            val objBase = accessPathBase(expr.obj, method, ctx) ?: return null
            Access.AttrAccess(objBase, Accessor.ElementAccessor)
        }
        else -> null
    }
}
```

### 4.6 Call Resolver

**Purpose**: Resolves `PIRCall` instructions to concrete `PIRFunction` callees.

For the minimal prototype, Python call resolution uses the **statically resolved callee**
from mypy (available as `PIRCall.resolvedCallee: String?`).

```kotlin
class PIRCallResolver(
    val cp: PIRClasspath,
) {
    /**
     * For the minimal prototype: use mypy's static resolution.
     * PIRCall.resolvedCallee contains the qualified name of the callee
     * (e.g., "builtins.print", "simple.Sample.source").
     */
    fun resolve(call: PIRCall): PIRFunction? {
        val qualifiedName = call.resolvedCallee ?: return null
        return cp.findFunctionOrNull(qualifiedName)
    }
}
```

```kotlin
class PIRMethodCallResolver(
    val callResolver: PIRCallResolver,
    val runner: TaintAnalysisUnitRunner,
) : MethodCallResolver {

    override fun resolveMethodCall(
        callerContext: MethodAnalysisContext,
        callExpr: CommonCallExpr,
        location: CommonInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler,
    ) {
        val pirCall = (location as PIRCall)
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
        val pirCall = (location as PIRCall)
        val callee = callResolver.resolve(pirCall) ?: return emptyList()
        return listOf(MethodWithContext(callee, EmptyMethodContext))
    }
}
```

**Future extensions**: When mypy cannot resolve a call (dynamic dispatch, duck typing),
we would need a type-based or points-to-analysis-based resolver. For the minimal prototype,
static resolution is sufficient since the sample uses direct function calls.

### 4.7 Flow Functions

#### 4.7.1 PIRMethodStartFlowFunction

**Purpose**: Produces initial facts at method entry. Applies entry-point source rules.

```kotlin
class PIRMethodStartFlowFunction(
    val method: PIRFunction,
    val ctx: PIRMethodAnalysisContext,
    val taintRules: PIRTaintConfig,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>(StartFact.Zero)
        // No entry-point source rules for the minimal prototype
        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        // Pass through all incoming facts at method entry
        return listOf(StartFact.Fact(fact))
    }
}
```

#### 4.7.2 PIRMethodSequentFlowFunction

**Purpose**: Propagates facts through non-call instructions. This is the core of intra-procedural analysis.

```kotlin
class PIRMethodSequentFlowFunction(
    val instruction: PIRInstruction,
    val method: PIRFunction,
    val ctx: PIRMethodAnalysisContext,
    val taintRules: PIRTaintConfig,
) : MethodSequentFlowFunction {

    override fun propagateZeroToZero(): Set<Sequent> = setOf(Sequent.ZeroToZero)

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> =
        setOf(Sequent.Unchanged)

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        return when (instruction) {
            is PIRAssign -> handleAssign(instruction, initialFactAp, currentFactAp)
            is PIRReturn -> handleReturn(instruction, initialFactAp, currentFactAp)
            else -> setOf(Sequent.Unchanged)
        }
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> = setOf(Sequent.Unchanged)

    /**
     * Handle assignment: target = expr
     *
     * For `x = y` (simple copy):
     *   - If fact is about y → produce new fact about x (rebase y→x)
     *   - If fact is about x → kill it (x is being overwritten — strong update)
     *   - Otherwise → unchanged
     *
     * For `x = expr` where expr is a compound expression (binop, etc.):
     *   - If fact is about x → kill it (strong update)
     *   - Otherwise → unchanged
     *   (Taint through binary ops not tracked in minimal prototype)
     */
    private fun handleAssign(
        assign: PIRAssign,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val assignTo = PIRFlowFunctionUtils.accessPathBase(assign.target, method, ctx)
            ?: return setOf(Sequent.Unchanged)
        val assignFrom = PIRFlowFunctionUtils.mkAccess(assign.expr, method, ctx)

        // Case 1: Simple assignment (x = y) where expr is a PIRValue
        if (assignFrom is PIRFlowFunctionUtils.Access.Simple) {
            return simpleAssign(assignTo, assignFrom.base, initialFactAp, currentFactAp)
        }

        // Case 2: Attribute read (x = y.attr)
        if (assignFrom is PIRFlowFunctionUtils.Access.AttrAccess) {
            return fieldRead(assignTo, assignFrom, initialFactAp, currentFactAp)
        }

        // Case 3: Compound expression or unresolvable
        // Kill if overwriting the target, otherwise pass through
        return if (currentFactAp.base == assignTo) {
            emptySet()  // Strong update: kill the fact about target
        } else {
            setOf(Sequent.Unchanged)
        }
    }

    /**
     * Simple assign: x = y
     * - fact about y → rebase to x (copy taint)
     * - fact about x → kill (strong update: x is overwritten)
     * - other → unchanged
     */
    private fun simpleAssign(
        to: AccessPathBase,
        from: AccessPathBase,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>()

        if (currentFactAp.base == from) {
            // Copy taint from source to target
            val newFact = currentFactAp.rebase(to)
            results.add(Sequent.FactToFact(initialFactAp, newFact))
            // Also keep the original (y still has taint after x = y)
            results.add(Sequent.Unchanged)
        } else if (currentFactAp.base == to) {
            // Strong update: target is being overwritten → kill old fact
            // Don't add Unchanged — the fact about x is killed
        } else {
            results.add(Sequent.Unchanged)
        }

        return results
    }

    /**
     * Field read: x = y.attr
     * If fact matches y.attr.* → read past the accessor, rebase to x
     */
    private fun fieldRead(
        to: AccessPathBase,
        access: PIRFlowFunctionUtils.Access.AttrAccess,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        // For minimal prototype, delegate to simple logic
        // Full implementation would check if fact base matches access.base
        // and the fact's first accessor matches access.accessor
        if (currentFactAp.base == to) {
            return emptySet()  // Strong update: kill
        }
        return setOf(Sequent.Unchanged)
    }

    /**
     * Handle return: taint flows to AccessPathBase.Return
     */
    private fun handleReturn(
        ret: PIRReturn,
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<Sequent> {
        val results = mutableSetOf<Sequent>(Sequent.Unchanged)
        val retVal = ret.value ?: return results
        val retBase = PIRFlowFunctionUtils.accessPathBase(retVal, method, ctx)
            ?: return results
        if (currentFactAp.base == retBase) {
            val returnFact = currentFactAp.rebase(AccessPathBase.Return)
            results.add(Sequent.FactToFact(initialFactAp, returnFact))
        }
        return results
    }
}
```

#### 4.7.3 PIRMethodCallFlowFunction

**Purpose**: Handles fact propagation at call sites (inter-procedural).

There are two kinds of propagation at a call:
1. **Call-to-return**: the fact skips over the callee (stays in caller frame)
2. **Call-to-start**: the fact enters the callee

For taint source/sink rules, the call flow function also applies taint rules.

```kotlin
class PIRMethodCallFlowFunction(
    val callInst: PIRCall,
    val method: PIRFunction,
    val ctx: PIRMethodAnalysisContext,
    val taintRules: PIRTaintConfig,
    val calleeMethod: PIRFunction?,
) : MethodCallFlowFunction {

    override fun propagateZeroToZero(): Set<ZeroCallFact> {
        val results = mutableSetOf<ZeroCallFact>()
        // Always pass zero through
        results.add(CallToReturnZeroFact)

        // Apply source rules: if this call is a taint source,
        // generate a new taint fact from zero
        for (source in taintRules.sources) {
            if (matchesCall(source.function, callInst)) {
                // Source rule matches this call — create taint fact
                val targetBase = resolvePosition(source.pos, callInst, method, ctx)
                    ?: continue
                val taintMark = TaintMarkAccessor(source.mark)
                val newFact = apManager.createFact(targetBase, taintMark)
                results.add(CallToReturnZFact(newFact))
            }
        }

        if (calleeMethod != null) {
            results.add(CallToStartZeroFact)
        }
        return results
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> =
        setOf(Unchanged)

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> {
        val results = mutableSetOf<FactCallFact>()

        // 1. Check sink rules
        for (sink in taintRules.sinks) {
            if (matchesCall(sink.function, callInst)) {
                val sinkBase = resolvePosition(sink.pos, callInst, method, ctx)
                if (sinkBase != null && currentFactAp.base == sinkBase) {
                    // Check if fact carries the required taint mark
                    if (factHasMark(currentFactAp, sink.mark)) {
                        // Record vulnerability via TaintSinkTracker
                        ctx.taint.sinkTracker.reportVulnerability(...)
                    }
                }
            }
        }

        // 2. Call-to-return: fact stays in caller frame
        results.add(Unchanged)

        // 3. Call-to-start: map fact into callee frame
        if (calleeMethod != null) {
            val mappedFact = PIRMethodCallFactMapper.mapCallToStart(
                currentFactAp, callInst, method, ctx
            )
            if (mappedFact != null) {
                results.add(CallToStartFFact(initialFactAp, mappedFact))
            }
        }

        return results
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> = setOf(Unchanged)
}
```

### 4.8 PIRMethodCallFactMapper

**Purpose**: Maps facts between caller and callee frames at call boundaries.

```kotlin
object PIRMethodCallFactMapper : MethodCallFactMapper {

    /**
     * Caller → Callee entry:
     * - fact about arg[i] → Argument(i) in callee
     * - fact about ClassStatic → pass through
     * - fact about local var not related to call → null (not relevant)
     */
    fun mapCallToStart(
        fact: FinalFactAp,
        call: PIRCall,
        callerMethod: PIRFunction,
        callerCtx: PIRMethodAnalysisContext,
    ): FinalFactAp? {
        val base = fact.base

        // ClassStatic passes through unchanged
        if (base is AccessPathBase.ClassStatic) return fact

        // Check if fact base matches any argument
        for ((i, arg) in call.args.withIndex()) {
            val argBase = PIRFlowFunctionUtils.accessPathBase(
                arg.value, callerMethod, callerCtx
            ) ?: continue
            if (base == argBase) {
                return fact.rebase(AccessPathBase.Argument(i))
            }
        }

        return null  // Not relevant to this call
    }

    /**
     * Callee exit → Caller return:
     * - Argument(i) → map back to caller's arg[i] expression
     * - Return → map to call's target (LHS of assignment)
     * - LocalVar → null (cannot escape)
     * - ClassStatic → pass through
     */
    fun mapExitToReturn(
        fact: FinalFactAp,
        call: PIRCall,
        callerMethod: PIRFunction,
        callerCtx: PIRMethodAnalysisContext,
    ): FinalFactAp? {
        return when (val base = fact.base) {
            is AccessPathBase.Argument -> {
                val argValue = call.args.getOrNull(base.idx)?.value ?: return null
                val callerBase = PIRFlowFunctionUtils.accessPathBase(
                    argValue, callerMethod, callerCtx
                ) ?: return null
                fact.rebase(callerBase)
            }
            is AccessPathBase.Return -> {
                val target = call.target ?: return null
                val targetBase = PIRFlowFunctionUtils.accessPathBase(
                    target, callerMethod, callerCtx
                ) ?: return null
                fact.rebase(targetBase)
            }
            is AccessPathBase.LocalVar -> null  // Cannot escape
            is AccessPathBase.ClassStatic -> fact
            is AccessPathBase.Constant -> fact
            else -> null
        }
    }

    /**
     * Is the fact relevant to this call?
     * Returns true if fact base matches any argument, the return target, or is ClassStatic.
     */
    fun factIsRelevant(
        fact: FinalFactAp,
        call: PIRCall,
        callerMethod: PIRFunction,
        callerCtx: PIRMethodAnalysisContext,
    ): Boolean {
        val base = fact.base
        if (base is AccessPathBase.ClassStatic || base is AccessPathBase.Constant) return true

        // Check arguments
        for (arg in call.args) {
            val argBase = PIRFlowFunctionUtils.accessPathBase(
                arg.value, callerMethod, callerCtx
            )
            if (base == argBase) return true
        }

        // Check return target
        val target = call.target
        if (target != null) {
            val targetBase = PIRFlowFunctionUtils.accessPathBase(
                target, callerMethod, callerCtx
            )
            if (base == targetBase) return true
        }

        return false
    }
}
```

### 4.9 Taint Rule Application

For the minimal prototype, taint rules are applied directly in the flow functions:

**Source rules** (in `PIRMethodCallFlowFunction.propagateZeroToZero()`):
- When the engine processes a call instruction, check if the call matches any source rule
- If `source.function == callInst.resolvedCallee` and `source.pos == Result`:
  - Create a new taint fact: `AccessPathBase` for the call's target + `TaintMarkAccessor(source.mark)`
  - Emit as `CallToReturnZFact`

**Sink rules** (in `PIRMethodCallFlowFunction.propagateFactToFact()`, `PIRMethodCallFlowFunction.propagateZeroToFact()`):
- When a fact reaches a call that matches a sink rule:
  - Check if `sink.function == callInst.resolvedCallee`
  - Check if `sink.pos == Argument(i)` and the fact's base matches argument i
  - Check if the fact carries `TaintMarkAccessor(sink.mark)`
  - If all match → report vulnerability

**Pass-through rules** (for future extension):
- Defined in `TaintRules.Pass(function, from, to)`
- Maps taint from one position to another through a call
- Not needed for the minimal test cases

### 4.10 Taint Rule Matching Utilities

```kotlin
/**
 * Matches a taint rule's function pattern against a PIRCall.
 * For the minimal prototype, this is a simple qualified name match.
 */
fun matchesCall(ruleFunction: String, call: PIRCall): Boolean {
    val callee = call.resolvedCallee ?: return false
    // Rule function may be "Sample.source", callee may be "simple.Sample.source"
    return callee == ruleFunction || callee.endsWith(".$ruleFunction")
}

/**
 * Resolves a PositionBase to an AccessPathBase at a call site.
 *
 * PositionBase.Result     → AccessPathBase for call.target
 * PositionBase.Argument(i) → AccessPathBase for call.args[i]
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
        call.args.getOrNull(pos.index)?.value?.let {
            PIRFlowFunctionUtils.accessPathBase(it, method, ctx)
        }
    }
    else -> null
}
```

---

## 5. PIRAnalysisManager — The Central Factory

Ties everything together:

```kotlin
class PIRAnalysisManager(
    val cp: PIRClasspath,
) : PIRLanguageManager(), TaintAnalysisManager {

    override val factTypeChecker: FactTypeChecker =
        PIRFactTypeChecker()  // Permissive: accept all facts (no type filtering for now)

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
        runner: TaintAnalysisUnitRunner,
    ): MethodCallResolver {
        return PIRMethodCallResolver(PIRCallResolver(cp), runner)
    }

    override fun getMethodStartFlowFunction(
        methodAnalysisContext: MethodAnalysisContext,
    ): MethodStartFlowFunction {
        val ctx = methodAnalysisContext as PIRMethodAnalysisContext
        return PIRMethodStartFlowFunction(ctx.method, ctx, taintConfig)
    }

    override fun getMethodSequentFlowFunction(
        inst: CommonInst,
        methodAnalysisContext: MethodAnalysisContext,
    ): MethodSequentFlowFunction {
        val ctx = methodAnalysisContext as PIRMethodAnalysisContext
        val pirInst = inst as PIRInstruction
        return PIRMethodSequentFlowFunction(pirInst, ctx.method, ctx, taintConfig)
    }

    override fun getMethodCallFlowFunction(
        inst: CommonInst,
        callExpr: CommonCallExpr,
        methodAnalysisContext: MethodAnalysisContext,
    ): MethodCallFlowFunction {
        val ctx = methodAnalysisContext as PIRMethodAnalysisContext
        val pirCall = inst as PIRCall
        val callee = PIRCallResolver(cp).resolve(pirCall)
        return PIRMethodCallFlowFunction(pirCall, ctx.method, ctx, taintConfig, callee)
    }

    override fun getMethodCallSummaryHandler(
        inst: CommonInst,
        callExpr: CommonCallExpr,
        methodAnalysisContext: MethodAnalysisContext,
    ): MethodCallSummaryHandler {
        return PIRMethodCallSummaryHandler(inst as PIRCall, ...)
    }

    // Preconditions — TODO for minimal prototype
    override fun getMethodStartPrecondition(...) = TODO()
    override fun getMethodSequentPrecondition(...) = TODO()
    override fun getMethodCallPrecondition(...) = TODO()

    // Not needed for minimal prototype
    override fun getMethodSideEffectSummaryHandler(...) = TODO()
    override fun getMethodSummaryEdgeProcessor(...) = null

    override fun isReachable(...) = true  // All variables always reachable (conservative)
    override fun isValidMethodExitFact(...) =
        factBase !is AccessPathBase.LocalVar  // Locals cannot escape

    override fun onInstructionReached(inst: CommonInst) {
        // No-op for minimal prototype
    }
}
```

---

## 6. Required PIR Changes

### 6.1 PIRInstruction.location Must Be Implemented

Currently, `PIRInstruction.location` is `TODO("Not yet implemented")`.
The dataflow engine calls `inst.location.method` to find the owning method.

**Option A (Preferred)**: Add a `location` field to each PIRInstruction data class:

```kotlin
data class PIRAssign(
    val target: PIRValue,
    val expr: PIRExpr,
    override val lineNumber: Int,
    override val colOffset: Int,
    override val location: PIRLocation,  // NEW
) : PIRInstruction
```

Where `PIRLocation` wraps a reference to the owning `PIRFunction`:

```kotlin
data class PIRLocationImpl(
    override val method: PIRFunction,
) : PIRLocation
```

This requires changes in the PIR builder (`MypyModuleBuilder`, `CfgBuilder`, `ExpressionLowering`)
to pass the function reference when constructing instructions.


### 6.2 PIRFunction Should Support Lookup by Qualified Name

The classpath needs `findFunctionOrNull(qualifiedName: String): PIRFunction?`.
Based on the test, this already exists on `PIRClasspath` / `PIRClasspathImpl`.
Verify it works for module-level functions (e.g., `"simple.Sample.source"` or `"Sample.source"`).

### 6.3 Python Has No "This" Concept (Almost)

Python methods receive `self` as the first parameter. For the minimal prototype:
- Module-level functions: no `self`, no `AccessPathBase.This`
- Methods: `self` is treated as `AccessPathBase.Argument(0)` (standard Python convention)
- We do NOT need to use `AccessPathBase.This` at all — it simplifies the implementation

---

## 7. Summary of Required New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `PIRMethodAnalysisContext` | `analysis` | Per-method state, name→index mapping |
| `PIRCallResolver` | `analysis` | Resolves calls using mypy's static resolution |
| `PIRMethodCallResolver` | `analysis` | Wraps PIRCallResolver for the MethodCallResolver interface |
| `PIRMethodStartFlowFunction` | `analysis` | Entry-point fact generation |
| `PIRMethodSequentFlowFunction` | `analysis` | Intra-procedural fact propagation |
| `PIRMethodCallFlowFunction` | `analysis` | Inter-procedural fact propagation at call sites |
| `PIRMethodCallFactMapper` | (top-level or util) | Maps facts between caller/callee frames |
| `PIRFlowFunctionUtils` | (top-level or util) | PIRValue → AccessPathBase conversion |
| `PIRCallExprAdapter` | (adapter) | Bridges PIRCall to CommonCallExpr |
| `PIRValueAdapter` | (adapter) | Bridges PIRValue to CommonValue |
| `PIRFactTypeChecker` | (analysis) | Permissive type checker (accept all facts) |
| `PIRMethodContextSerializer` | (serialization) | Dummy/no-op serializer |
| `PIRMethodCallSummaryHandler` | `analysis` | Applies callee summaries back to caller |

---

## 8. Minimal Prototype Scope and Simplifications

For the two test cases to pass, we need:

1. **Intra-procedural assignment tracking** — `x = y` copies taint, `x = "safe"` kills taint
2. **Call resolution** — resolve `source()` and `sink()` to their PIRFunction definitions
3. **Source rule application** — `source()` return value gets taint mark
4. **Sink rule detection** — `sink(arg)` with tainted arg triggers vulnerability
5. **Return propagation** — taint from local → AccessPathBase.Return at `return` statement
6. **Heap tracking** - Attribute access/field tracking (`obj.field` flows), Subscript tracking (`list[i]` flows)

**What we can skip for now**:
- Global variable tracking
- Closure variable tracking
- Exception flow
- Alias analysis
- Type-based fact filtering (use permissive checker)
- Pass-through taint rules
- Context-sensitive analysis (use EmptyMethodContext)
- Non-distributive facts
- Side effect summaries
- Method summary serialization

---

## 9. Execution Flow (End-to-End)

For `testSimpleSample` with entry point `Sample.sample`:

```python
def sample():
    data = source()    # (1) PIRCall: target=$t0, callee=GlobalRef("source")
                       #     PIRAssign: target=Local("data"), expr=Local("$t0")
    other = data       # (2) PIRAssign: target=Local("other"), expr=Local("data")
    sink(other)        # (3) PIRCall: callee=GlobalRef("sink"), args=[Local("other")]
```

**Analysis trace**:

1. Engine starts at entry of `sample()`, propagates Zero fact
2. At instruction (1) — PIRCall to `source()`:
   - `propagateZeroToZero()` matches source rule → creates `{$t0: taint}`
   - Alternatively, enters `source()` body, but since body is `pass`, returns immediately
3. At PIRAssign `data = $t0`:
   - `propagateFactToFact({$t0: taint})` → `simpleAssign` rebases to `{data: taint}`
4. At instruction (2) — PIRAssign `other = data`:
   - `propagateFactToFact({data: taint})` → `simpleAssign` rebases to `{other: taint}`, keeps `{data: taint}`
5. At instruction (3) — PIRCall to `sink(other)`:
   - `propagateFactToFact({other: taint})` → matches sink rule (arg 0 = `other`, has taint mark)
   - → Reports vulnerability!

For `testSimpleNonReachableSample` with entry point `Sample.sample_non_reachable`:

```python
def sample_non_reachable():
    data = source()    # → {data: taint}
    other = "safe"     # PIRAssign: target=Local("other"), expr=StrConst("safe")
                       # No fact about "other" to kill, and "safe" has no taint
    sink(other)        # "other" has no taint → no vulnerability
```

At step for `other = "safe"`: since `other` was never tainted (only `data` was), and `"safe"` is
a constant with no taint, no taint fact exists for `other` at the `sink()` call. Correct.

---

## 10. Open Design Questions

1. **Instruction flattening order**: Should blocks be flattened by label number, BFS from entry,
   or topological order? Label order is simplest and deterministic. BFS gives better locality.
   **Recommendation**: Label order for simplicity.

2. **Variable index stability**: The name→index mapping is per-method and per-analysis-run.
   If we add serialization support later, we need to ensure indices are deterministic.
   **Recommendation**: Sort names lexicographically for determinism.

3. **PIRCall target vs PIRAssign**: In PIR, `PIRCall` has its own `target` field (not wrapped
   in `PIRAssign`). But after a call, there's often a `PIRAssign` that copies the call result.
   Need to handle both patterns: (a) `PIRCall(target=$t0)` followed by `PIRAssign(data=$t0)`,
   and (b) potential direct `PIRCall(target=data)`.
   **Recommendation**: Handle PIRCall's target directly in the call flow function, then let
   the subsequent PIRAssign propagate via the sequential flow function.

4. **Global function references**: Module-level functions like `source()` and `sink()` are
   referenced as `PIRGlobalRef` values. The classpath lookup must handle the module prefix
   correctly (e.g., `"simple.Sample.source"` or just `"Sample.source"`).
   **Recommendation**: Check both the full qualified name and the short name when matching rules.

5. **`self` parameter**: For the minimal prototype (module-level functions only), `self` is
   irrelevant. When extending to methods, `self` should be `Argument(0)`, not `This`.
   This differs from JVM where `this` is a separate `AccessPathBase.This`.
   **Recommendation**: Always use `Argument(idx)` for Python, never `AccessPathBase.This`.
