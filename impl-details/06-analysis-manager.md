# 6. Analysis Manager & Supporting Classes

---

## 6.1 `GoMethodAnalysisContext`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodAnalysisContext.kt`

Holds per-method analysis state. Significantly simpler than JVM's `JIRMethodAnalysisContext`.

```kotlin
class GoMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val taint: TaintAnalysisContext,
    val rulesProvider: GoTaintRulesProvider,
) : MethodAnalysisContext {
    override val methodCallFactMapper: MethodCallFactMapper
        get() = GoMethodCallFactMapper

    val method: GoIRFunction
        get() = methodEntryPoint.method as GoIRFunction
}
```

### What JVM has that Go MVP doesn't need

| JVM Component | Purpose | Go Status |
|---------------|---------|-----------|
| `JIRFactTypeChecker` | Type-based fact filtering | Use `FactTypeChecker.Dummy` |
| `JIRLocalVariableReachability` | Liveness analysis for locals | Not needed in SSA form |
| `JIRLocalAliasAnalysis` | Must-alias for field updates | Future work |
| `taintMarksAssignedOnMethodEnter` | Tracks entry marks for cleanup | Not needed until entry-point rules are added |

---

## 6.2 `GoMethodCallFactMapper`

> **File**: `org/opentaint/dataflow/go/GoMethodCallFactMapper.kt`

Maps taint facts between caller and callee namespaces. Singleton `object` like JVM's `JIRMethodCallFactMapper`.

### 6.2.1 Exit-to-Return Mapping (callee → caller)

```kotlin
object GoMethodCallFactMapper : MethodCallFactMapper {

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
    ): List<FinalFactAp> {
        val goInst = callStatement as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return emptyList()
        val method = goInst.location.functionBody.function

        return when (factAp.base) {
            is AccessPathBase.Return -> {
                // Map Return → caller's result register
                val resultRegister = GoFlowFunctionUtils.extractResultRegister(goInst)
                    ?: return emptyList()
                val resultBase = AccessPathBase.LocalVar(resultRegister.index)
                listOf(factAp.rebase(resultBase))
            }
            is AccessPathBase.Argument -> {
                // Map Argument(i) → caller's i-th argument value
                val idx = (factAp.base as AccessPathBase.Argument).idx
                if (idx < callInfo.args.size) {
                    val argBase = GoFlowFunctionUtils.accessPathBase(callInfo.args[idx], method)
                        ?: return emptyList()
                    listOf(factAp.rebase(argBase))
                } else emptyList()
            }
            is AccessPathBase.This -> {
                // Map This → caller's receiver
                val receiver = callInfo.receiver
                    ?: return emptyList()
                val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method)
                    ?: return emptyList()
                listOf(factAp.rebase(recvBase))
            }
            is AccessPathBase.ClassStatic -> {
                // Static/global facts pass through unchanged
                listOf(factAp)
            }
            is AccessPathBase.Constant -> listOf(factAp)
            is AccessPathBase.LocalVar -> emptyList()  // locals can't escape
            is AccessPathBase.Exception -> emptyList()  // Go has no exceptions (panic is separate)
        }
    }

    override fun mapMethodExitToReturnFlowFact(
        callStatement: CommonInst,
        factAp: InitialFactAp,
    ): List<InitialFactAp> {
        // Same logic but for InitialFactAp
        val goInst = callStatement as GoIRInst
        val callInfo = GoFlowFunctionUtils.extractCallInfo(goInst) ?: return emptyList()
        val method = goInst.location.functionBody.function

        return when (factAp.base) {
            is AccessPathBase.Return -> {
                val resultRegister = GoFlowFunctionUtils.extractResultRegister(goInst)
                    ?: return emptyList()
                listOf(factAp.rebase(AccessPathBase.LocalVar(resultRegister.index)))
            }
            is AccessPathBase.Argument -> {
                val idx = (factAp.base as AccessPathBase.Argument).idx
                if (idx < callInfo.args.size) {
                    val argBase = GoFlowFunctionUtils.accessPathBase(callInfo.args[idx], method)
                        ?: return emptyList()
                    listOf(factAp.rebase(argBase))
                } else emptyList()
            }
            is AccessPathBase.This -> {
                val receiver = callInfo.receiver ?: return emptyList()
                val recvBase = GoFlowFunctionUtils.accessPathBase(receiver, method) ?: return emptyList()
                listOf(factAp.rebase(recvBase))
            }
            is AccessPathBase.ClassStatic -> listOf(factAp)
            is AccessPathBase.Constant -> listOf(factAp)
            else -> emptyList()
        }
    }
```

### 6.2.2 Call-to-Start Mapping (caller → callee)

```kotlin
    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        factAp: FinalFactAp,
        checker: FactTypeChecker,
        onMappedFact: (FinalFactAp, AccessPathBase) -> Unit,
    ) {
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo
        val callerMethod = goCallExpr.resolvedCallee  // could be null for multi-target

        // Map receiver → This
        if (callInfo.receiver != null) {
            val receiverBase = GoFlowFunctionUtils.accessPathBase(callInfo.receiver!!, callerMethod ?: return)
            if (receiverBase != null && factAp.base == receiverBase) {
                onMappedFact(factAp.rebase(AccessPathBase.This), AccessPathBase.This)
            }
        }

        // Map arguments → Argument(i)
        for ((i, arg) in callInfo.args.withIndex()) {
            // We need the caller's method to resolve arg bases
            // But we don't have the caller method here — use the fact's base directly
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && factAp.base == argBase) {
                onMappedFact(factAp.rebase(AccessPathBase.Argument(i)), AccessPathBase.Argument(i))
            }
        }

        // ClassStatic passes through
        if (factAp.base is AccessPathBase.ClassStatic) {
            onMappedFact(factAp, AccessPathBase.ClassStatic)
        }
    }

    override fun mapMethodCallToStartFlowFact(
        callee: CommonMethod,
        callExpr: CommonCallExpr,
        fact: InitialFactAp,
        onMappedFact: (InitialFactAp, AccessPathBase) -> Unit,
    ) {
        // Same logic as above but for InitialFactAp
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo

        if (callInfo.receiver != null) {
            val receiverBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (receiverBase != null && fact.base == receiverBase) {
                onMappedFact(fact.rebase(AccessPathBase.This), AccessPathBase.This)
            }
        }

        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && fact.base == argBase) {
                onMappedFact(fact.rebase(AccessPathBase.Argument(i)), AccessPathBase.Argument(i))
            }
        }

        if (fact.base is AccessPathBase.ClassStatic) {
            onMappedFact(fact, AccessPathBase.ClassStatic)
        }
    }
```

### 6.2.3 Relevance and Validity Checks

```kotlin
    override fun factIsRelevantToMethodCall(
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        factAp: FactAp,
    ): Boolean {
        val goCallExpr = callExpr as GoCallExpr
        val callInfo = goCallExpr.callInfo

        // Check if fact's base matches any call position
        for (arg in callInfo.args) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && argBase == factAp.base) return true
        }

        if (callInfo.receiver != null) {
            val recvBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (recvBase != null && recvBase == factAp.base) return true
        }

        if (returnValue != null) {
            val retBase = GoFlowFunctionUtils.accessPathBaseFromValue(returnValue as GoIRValue)
            if (retBase != null && retBase == factAp.base) return true
        }

        if (factAp.base is AccessPathBase.ClassStatic) return true

        return false
    }

    override fun isValidMethodExitFact(factAp: FactAp): Boolean {
        // LocalVar facts cannot escape method scope
        return factAp.base !is AccessPathBase.LocalVar
    }
}
```

### 6.2.4 Note on `accessPathBaseFromValue`

The fact mapper needs to map values without a `method` context (the `method` parameter isn't always available in the mapper's interface). We add a convenience method:

```kotlin
// In GoFlowFunctionUtils:
fun accessPathBaseFromValue(value: GoIRValue): AccessPathBase? {
    return when (value) {
        is GoIRParameterValue -> AccessPathBase.Argument(value.paramIndex)
        is GoIRRegister -> AccessPathBase.LocalVar(value.index)
        is GoIRConstValue -> AccessPathBase.Constant(value.type.displayName, value.value.toString())
        is GoIRGlobalValue -> AccessPathBase.ClassStatic
        is GoIRFunctionValue -> AccessPathBase.Constant("func", value.function.fullName)
        is GoIRBuiltinValue -> AccessPathBase.Constant("builtin", value.name)
        is GoIRFreeVarValue -> null  // Can't map without knowing the method's param count
    }
}
```

This is the same as `accessPathBase` but doesn't require a `method` parameter (at the cost of not handling `GoIRFreeVarValue`).

---

## 6.3 `GoMethodCallSummaryHandler`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodCallSummaryHandler.kt`

Applies callee summaries back to the caller. Mostly delegates to default interface implementations.

```kotlin
class GoMethodCallSummaryHandler(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val statement: GoIRInst,
) : MethodCallSummaryHandler {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy

    override fun mapMethodExitToReturnFlowFact(fact: FinalFactAp): List<FinalFactAp> {
        return GoMethodCallFactMapper.mapMethodExitToReturnFlowFact(
            statement, fact, factTypeChecker
        )
    }

    // All other methods use default implementations from the interface:
    // - handleZeroToZero: default
    // - handleZeroToFact: calls handleSummary default
    // - handleFactToFact: calls handleSummary default
    // - handleNDFactToFact: calls handleSummary default
    // - prepareFactToFactSummary: returns listOf(summaryEdge) (no rewriting)
    // - prepareNDFactToFactSummary: returns listOf(summaryEdge)
    // - handleSummary: default implementation that maps exit facts through mapMethodExitToReturnFlowFact
}
```

### Design Notes

The JVM's `JIRMethodCallSummaryHandler` adds:
- `JIRMethodCallRuleBasedSummaryRewriter` — strips user-defined marks from summaries (used when a source rule assigns a mark that shouldn't propagate through other summaries)
- Alias propagation in `handleSummary` — propagates summary results to aliased access paths

Neither is needed for Go MVP.

---

## 6.4 `GoMethodSideEffectHandler`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodSideEffectHandler.kt`

```kotlin
class GoMethodSideEffectHandler : MethodSideEffectSummaryHandler {
    // All methods use default implementations that return emptySet()
}
```

---

## 6.5 Precondition Stubs

> **Files**: `org/opentaint/dataflow/go/trace/GoMethod{Start,Sequent,Call}Precondition.kt`

Preconditions are used for trace generation (finding the source-to-sink path). MVP returns minimal results.

```kotlin
// GoMethodStartPrecondition.kt
class GoMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
) : MethodStartPrecondition {

    override fun zeroPrecondition(): List<TaintRulePrecondition.Source> = emptyList()

    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> =
        emptyList()
}

// GoMethodSequentPrecondition.kt
class GoMethodSequentPrecondition(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val currentInst: GoIRInst,
) : MethodSequentPrecondition {

    override fun zeroPrecondition(): Set<SequentPrecondition> = setOf(SequentPrecondition.Unchanged)

    override fun factPrecondition(fact: InitialFactAp): Set<SequentPrecondition> =
        setOf(SequentPrecondition.Unchanged)
}

// GoMethodCallPrecondition.kt
class GoMethodCallPrecondition(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val returnValue: GoIRValue?,
    private val callExpr: GoCallExpr,
    private val statement: GoIRInst,
) : MethodCallPrecondition {

    override fun zeroPrecondition(): List<CallPrecondition> =
        listOf(CallPrecondition.Unchanged)

    override fun factPrecondition(fact: InitialFactAp): List<CallPrecondition> =
        listOf(CallPrecondition.Unchanged)
}
```

---

## 6.6 `GoAnalysisManager` — Complete Implementation

> **File**: `org/opentaint/dataflow/go/analysis/GoAnalysisManager.kt` (modify existing)

This is the central factory that wires everything together.

```kotlin
class GoAnalysisManager(cp: GoIRProgram) : GoLanguageManager(cp), TaintAnalysisManager {

    override val factTypeChecker: FactTypeChecker = FactTypeChecker.Dummy

    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        callResolver: MethodCallResolver,
        taintAnalysisContext: TaintAnalysisContext,
        contextForEmptyMethod: MethodAnalysisContext?,
    ): MethodAnalysisContext {
        val config = taintAnalysisContext.taintConfig as GoTaintConfig
        val rulesProvider = GoTaintRulesProvider(config)
        return GoMethodAnalysisContext(methodEntryPoint, taintAnalysisContext, rulesProvider)
    }

    // Non-taint overload (called by AnalysisManager default)
    override fun getMethodAnalysisContext(
        methodEntryPoint: MethodEntryPoint,
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        callResolver: MethodCallResolver,
        contextForEmptyMethod: MethodAnalysisContext?,
    ): MethodAnalysisContext {
        error("Taint context required — use the TaintAnalysisManager overload")
    }

    override fun getMethodCallResolver(
        graph: ApplicationGraph<CommonMethod, CommonInst>,
        unitResolver: UnitResolver<CommonMethod>,
        runner: TaintAnalysisUnitRunner
    ): MethodCallResolver {
        val goGraph = graph as GoApplicationGraph
        return GoMethodCallResolver(goGraph.callResolver, cp)
    }

    override fun getMethodStartFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartFlowFunction {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodStartFlowFunction(apManager, ctx)
    }

    override fun getMethodSequentFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
        generateTrace: Boolean,
    ): MethodSequentFlowFunction {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodSequentFlowFunction(apManager, ctx, currentInst as GoIRInst, generateTrace)
    }

    override fun getMethodCallFlowFunction(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
        generateTrace: Boolean,
    ): MethodCallFlowFunction {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodCallFlowFunction(
            apManager, ctx,
            returnValue as? GoIRValue,
            callExpr as GoCallExpr,
            statement as GoIRInst,
            generateTrace,
        )
    }

    override fun getMethodCallSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
    ): MethodCallSummaryHandler {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodCallSummaryHandler(apManager, ctx, statement as GoIRInst)
    }

    override fun getMethodSideEffectSummaryHandler(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        statement: CommonInst,
        runner: AnalysisRunner,
    ): MethodSideEffectSummaryHandler {
        return GoMethodSideEffectHandler()
    }

    override fun getMethodStartPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
    ): MethodStartPrecondition {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodStartPrecondition(apManager, ctx)
    }

    override fun getMethodSequentPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        currentInst: CommonInst,
    ): MethodSequentPrecondition {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodSequentPrecondition(apManager, ctx, currentInst as GoIRInst)
    }

    override fun getMethodCallPrecondition(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr,
        statement: CommonInst,
    ): MethodCallPrecondition {
        val ctx = analysisContext as GoMethodAnalysisContext
        return GoMethodCallPrecondition(
            apManager, ctx,
            returnValue as? GoIRValue,
            callExpr as GoCallExpr,
            statement as GoIRInst,
        )
    }

    override fun isReachable(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        base: AccessPathBase,
        statement: CommonInst,
    ): Boolean {
        // In SSA form, all defined registers are reachable at their use points.
        // No liveness analysis needed.
        return true
    }

    override fun isValidMethodExitFact(
        apManager: ApManager,
        analysisContext: MethodAnalysisContext,
        fact: FinalFactAp,
    ): Boolean {
        return GoMethodCallFactMapper.isValidMethodExitFact(fact)
    }

    override fun onInstructionReached(inst: CommonInst) {
        // No-op for MVP. Future: track closure allocations for dynamic call resolution.
    }
}
```

### Key Design Decisions

1. **`GoTaintRulesProvider` created per method context** — Each method analysis context gets its own `GoTaintRulesProvider`. Since `GoTaintConfig` is immutable, the provider can be shared. But creating it in `getMethodAnalysisContext` keeps it scoped to the taint config passed in.

2. **`callResolver` shared via `GoApplicationGraph`** — The `GoCallResolver` is created in `GoApplicationGraph` and shared with `GoMethodCallResolver`. This avoids re-building the interface→implementors map.

3. **All downcasts are explicit** — `as GoMethodAnalysisContext`, `as GoIRInst`, etc. Following JVM's pattern of `jIRDowncast<T>()`. For Go, simple `as` casts with clear error messages are sufficient.

4. **`isReachable` always returns `true`** — In SSA form, a register defined by an instruction is reachable at all subsequent uses (SSA guarantees single-definition, and the use-def chain is implicit). The JVM engine uses `JIRLocalVariableReachability` for non-SSA representations where a variable may be used before definition in some paths.
