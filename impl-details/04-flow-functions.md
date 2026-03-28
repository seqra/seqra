# 4. Flow Functions

---

## 4.1 `GoMethodStartFlowFunction`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodStartFlowFunction.kt`

Entry-point flow function. Processes facts at method entry.

```kotlin
class GoMethodStartFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
) : MethodStartFlowFunction {

    override fun propagateZero(): List<StartFact> {
        val result = mutableListOf<StartFact>(StartFact.Zero)

        // Entry-point rules: taint method's own parameters when this method
        // is an analysis entry point (e.g., HTTP handler).
        // MVP: entry-point rules are not yet defined in GoTaintConfig.
        // When GoTaintConfig gains an entryPointSources field, apply them here:
        //   for (rule in rulesProvider.entryPointRulesForMethod(context.method)) {
        //       val base = resolvePosition(rule.pos)
        //       val factAp = apManager.createAbstractAp(base, ExclusionSet.Universe)
        //           .prependAccessor(TaintMarkAccessor(rule.mark))
        //       result.add(StartFact.Fact(factAp))
        //   }

        return result
    }

    override fun propagateFact(fact: FinalFactAp): List<StartFact.Fact> {
        // MVP: no type checking — accept all initial facts
        return listOf(StartFact.Fact(fact))
    }
}
```

### Design Notes

- **Zero propagation**: Always emits `StartFact.Zero` — this is the reachability fact that triggers source rule evaluation in the call flow function.
- **Entry-point rules vs source rules**: The start flow function applies entry-point rules (taint method's own params). Source rules are applied in the call flow function at call sites. Currently no entry-point rules are defined — all test cases use `source()` function calls.
- **No type checking**: The JVM engine's `JIRMethodStartFlowFunction` uses `JIRFactTypeChecker` to filter initial facts by type compatibility. For Go MVP, we use `FactTypeChecker.Dummy` and accept all facts.

---

## 4.2 `GoMethodSequentFlowFunction`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodSequentFlowFunction.kt`

The largest and most complex flow function. Handles intraprocedural taint propagation.

### 4.2.1 Class Structure

```kotlin
class GoMethodSequentFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val currentInst: GoIRInst,
    private val generateTrace: Boolean,
) : MethodSequentFlowFunction {

    private val method: GoIRFunction get() = context.method
    private val rulesProvider: GoTaintRulesProvider get() = context.rulesProvider
```

### 4.2.2 Top-Level Methods

```kotlin
    override fun propagateZeroToZero(): Set<Sequent> {
        return setOf(Sequent.ZeroToZero)
        // MVP: no unconditional sources at sequential statements.
        // Future: add static field source rules here.
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<Sequent> {
        return propagate(null, currentFactAp)
    }

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp
    ): Set<Sequent> {
        return propagate(initialFactAp, currentFactAp)
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp
    ): Set<Sequent> {
        return setOf(Sequent.Unchanged)  // MVP: no non-distributive support
    }
```

### 4.2.3 Core Dispatch

```kotlin
    private fun propagate(initialFact: InitialFactAp?, currentFact: FinalFactAp): Set<Sequent> {
        return when (currentInst) {
            is GoIRAssignInst -> handleAssign(initialFact, currentFact, currentInst)
            is GoIRStore      -> handleStore(initialFact, currentFact, currentInst)
            is GoIRReturn     -> handleReturn(initialFact, currentFact, currentInst)
            is GoIRPhi        -> handlePhi(initialFact, currentFact, currentInst)
            is GoIRMapUpdate  -> handleMapUpdate(initialFact, currentFact, currentInst)
            else              -> setOf(Sequent.Unchanged)
        }
    }
```

### 4.2.4 Assignment Handling (`GoIRAssignInst`)

```kotlin
    private fun handleAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRAssignInst,
    ): Set<Sequent> {
        val registerBase = AccessPathBase.LocalVar(inst.register.index)
        val expr = inst.expr

        // Special case: string concatenation — multiple operands
        if (expr is GoIRBinOpExpr && expr.op == GoIRBinaryOp.ADD
            && GoFlowFunctionUtils.isStringType(expr.type)) {
            return handleStringConcat(initialFact, currentFact, registerBase, expr)
        }

        // Special case: call instruction — handled by CallFlowFunction, not here
        // GoIRCall is a GoIRDefInst, but it is processed separately.
        // The sequent flow function should pass through unchanged for call results.
        // (The framework routes call insts to CallFlowFunction automatically.)

        val rhsAccess = GoFlowFunctionUtils.exprToAccess(expr, method)
            ?: return handleNonPropagatingExpr(currentFact, registerBase)

        return when (rhsAccess) {
            is Access.Simple -> handleSimpleAssign(initialFact, currentFact, registerBase, rhsAccess.base)
            is Access.RefAccess -> handleRefAssign(initialFact, currentFact, registerBase, rhsAccess)
        }
    }
```

### 4.2.5 Simple Assignment (copy propagation)

`register = otherValue` — if `otherValue` is tainted, `register` becomes tainted.

```kotlin
    private fun handleSimpleAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        toBase: AccessPathBase,
        fromBase: AccessPathBase,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        // Kill: if fact is about the destination, the assignment overwrites it
        if (currentFact.base == toBase) {
            // Fact is killed by this assignment (strong update on register)
            // But also check if from == to (self-assignment), in which case preserve
            if (fromBase == toBase) {
                result.add(Sequent.Unchanged)
                return result
            }
            // Don't add Unchanged — fact is killed
        } else {
            // Fact is not about the destination — preserve unchanged
            result.add(Sequent.Unchanged)
        }

        // Gen: if fact is about the source, generate taint on destination
        if (currentFact.base == fromBase) {
            val newFact = currentFact.rebase(toBase)
            result.add(makeEdge(initialFact, newFact))
        }

        return result
    }
```

### 4.2.6 Reference Assignment (field read / element read)

`register = x.field` or `register = x[i]`

**Key mechanism — abstract fact refinement**: When the current fact is abstract (e.g., `base(x).*  {}`) and we need to read an accessor (e.g., `FieldAccessor("f")`), the accessor is not explicitly present — it's behind the abstraction point `*`. We cannot simply propagate the abstract fact. Instead, we must **add the accessor to the fact's exclusion set**. This produces a refined fact like `base(x).*  {f}` which tells the framework "we tried to read accessor `f` but it wasn't there." The framework detects the new exclusion and re-analyzes with a more specific fact where accessor `f` is materialized (e.g., `base(x).f.*  {}`). On the next pass, `startsWithAccessor(f)` returns true and the read proceeds normally.

This is the proper JVM-style refinement mechanism (see `edges-knowledge.md` Lesson 4).

```kotlin
    private fun handleRefAssign(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        toBase: AccessPathBase,
        rhsAccess: Access.RefAccess,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        // Kill: assignment to register overwrites previous value
        if (currentFact.base == toBase) {
            // Don't add Unchanged — the register gets a new value
        } else {
            result.add(Sequent.Unchanged)
        }

        // Gen: if fact is about the source object AND the accessor matches
        if (currentFact.base == rhsAccess.base) {
            // Check if the fact has the accessor (field read)
            if (currentFact.startsWithAccessor(rhsAccess.accessor)) {
                // Concrete: fact starts with this accessor → strip accessor and rebase
                val readFact = currentFact.readAccessor(rhsAccess.accessor)
                if (readFact != null) {
                    val newFact = readFact.rebase(toBase)
                    result.add(makeEdge(initialFact, newFact))
                }
            } else if (currentFact.isAbstract()
                       && rhsAccess.accessor !in currentFact.exclusions) {
                // Abstract: accessor is not excluded, so it MAY be behind *.
                // Add the accessor to the exclusion set to trigger refinement.
                // The framework will see the updated exclusion and re-analyze
                // with a more specific fact where this accessor is materialized.
                val refinedFact = currentFact.exclude(rhsAccess.accessor)
                result.add(makeEdge(initialFact, refinedFact))
            }
        }

        return result
    }
```

### 4.2.7 Store Handling (`GoIRStore`)

`*addr = value` — writes value into memory at addr.

```kotlin
    private fun handleStore(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRStore,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()
        val valueBase = GoFlowFunctionUtils.accessPathBase(inst.value, method)
            ?: return setOf(Sequent.Unchanged)
        val addrAccess = GoFlowFunctionUtils.accessForAddr(inst.addr, method)
            ?: return setOf(Sequent.Unchanged)

        when (addrAccess) {
            is Access.RefAccess -> {
                // Field/element store: *fieldAddr = value
                val destBase = addrAccess.base
                val accessor = addrAccess.accessor

                // Kill: strong update for fields, weak update for elements
                if (currentFact.base == destBase) {
                    if (currentFact.startsWithAccessor(accessor)) {
                        if (accessor is ElementAccessor) {
                            // Weak update: don't kill existing element taint
                            result.add(Sequent.Unchanged)
                        }
                        // For FieldAccessor: strong update — don't preserve old fact (it's killed)
                    } else if (currentFact.isAbstract()
                               && accessor !in currentFact.exclusions) {
                        // Abstract fact: accessor may be behind *.
                        // Add to exclusions to trigger refinement.
                        val refinedFact = currentFact.exclude(accessor)
                        result.add(makeEdge(initialFact, refinedFact))
                    } else {
                        result.add(Sequent.Unchanged)
                    }
                } else {
                    result.add(Sequent.Unchanged)
                }

                // Gen: if value is tainted, write taint into dest.accessor
                if (currentFact.base == valueBase) {
                    val newFact = currentFact.rebase(destBase).prependAccessor(accessor)
                    result.add(makeEdge(initialFact, newFact))
                }
            }

            is Access.Simple -> {
                // Pointer store: *ptr = value
                val destBase = addrAccess.base

                // Kill
                if (currentFact.base == destBase) {
                    // Don't preserve — overwritten
                } else {
                    result.add(Sequent.Unchanged)
                }

                // Gen
                if (currentFact.base == valueBase) {
                    val newFact = currentFact.rebase(destBase)
                    result.add(makeEdge(initialFact, newFact))
                }
            }
        }

        return result
    }
```

### 4.2.8 Return Handling (`GoIRReturn`)

```kotlin
    private fun handleReturn(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRReturn,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>(Sequent.Unchanged)

        // Map return values to AccessPathBase.Return
        // Go supports multiple return values. In SSA IR, GoIRReturn.results
        // is the list of returned values.
        for (retVal in inst.results) {
            val retBase = GoFlowFunctionUtils.accessPathBase(retVal, method) ?: continue
            if (currentFact.base == retBase) {
                val exitFact = currentFact.rebase(AccessPathBase.Return)
                result.add(makeEdge(initialFact, exitFact))
            }
        }

        // For methods with multiple return values, the caller uses GoIRExtractExpr
        // to decompose the tuple. The Return base covers the whole tuple.
        // The ExtractExpr in the caller extracts individual components.
        // For now, any tainted return value maps to Return base.

        return result
    }
```

### 4.2.9 Phi Node Handling (`GoIRPhi`)

```kotlin
    private fun handlePhi(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRPhi,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()
        val registerBase = AccessPathBase.LocalVar(inst.register.index)

        // Kill: phi assigns to register, so previous value is overwritten
        if (currentFact.base == registerBase) {
            // Don't add Unchanged — overwritten by phi
        } else {
            result.add(Sequent.Unchanged)
        }

        // Gen: if any incoming edge value is tainted, phi result is tainted
        for (edge in inst.edges) {
            val edgeBase = GoFlowFunctionUtils.accessPathBase(edge, method) ?: continue
            if (currentFact.base == edgeBase) {
                val newFact = currentFact.rebase(registerBase)
                result.add(makeEdge(initialFact, newFact))
                break  // One match is enough — the fact is propagated
            }
        }

        return result
    }
```

### 4.2.10 Map Update Handling (`GoIRMapUpdate`)

```kotlin
    private fun handleMapUpdate(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        inst: GoIRMapUpdate,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>(Sequent.Unchanged)  // map update is weak
        val mapBase = GoFlowFunctionUtils.accessPathBase(inst.map, method)
            ?: return setOf(Sequent.Unchanged)
        val valueBase = GoFlowFunctionUtils.accessPathBase(inst.value, method)
            ?: return setOf(Sequent.Unchanged)

        // Gen: if value is tainted, write element-level taint on map
        if (currentFact.base == valueBase) {
            val newFact = currentFact.rebase(mapBase).prependAccessor(ElementAccessor)
            result.add(makeEdge(initialFact, newFact))
        }

        return result
    }
```

### 4.2.11 String Concatenation

```kotlin
    private fun handleStringConcat(
        initialFact: InitialFactAp?,
        currentFact: FinalFactAp,
        registerBase: AccessPathBase,
        expr: GoIRBinOpExpr,
    ): Set<Sequent> {
        val result = mutableSetOf<Sequent>()

        // Kill: register gets new value
        if (currentFact.base == registerBase) {
            // Don't add unchanged
        } else {
            result.add(Sequent.Unchanged)
        }

        // Gen: if either operand is tainted, result is tainted
        val leftBase = GoFlowFunctionUtils.accessPathBase(expr.x, method)
        val rightBase = GoFlowFunctionUtils.accessPathBase(expr.y, method)

        if (leftBase != null && currentFact.base == leftBase) {
            result.add(makeEdge(initialFact, currentFact.rebase(registerBase)))
        }
        if (rightBase != null && currentFact.base == rightBase) {
            result.add(makeEdge(initialFact, currentFact.rebase(registerBase)))
        }

        return result
    }
```

### 4.2.12 Non-Propagating Expression

```kotlin
    private fun handleNonPropagatingExpr(
        currentFact: FinalFactAp,
        registerBase: AccessPathBase,
    ): Set<Sequent> {
        // The expression doesn't propagate taint (e.g., alloc, make, arithmetic)
        // Kill fact if it was about this register, otherwise preserve
        return if (currentFact.base == registerBase) {
            emptySet()  // register overwritten with clean value
        } else {
            setOf(Sequent.Unchanged)
        }
    }
```

### 4.2.13 Abstract Fact Refinement — Core Principle

**When a flow function encounters an abstract fact and needs a specific accessor that isn't there, it must add that accessor to the fact's exclusion set.** This is the mechanism that drives the framework's iterative refinement.

The pattern applies everywhere an accessor is expected:

| Situation | What happens | Result |
|-----------|-------------|--------|
| Concrete fact, accessor present | `startsWithAccessor(a)` → true | Read/strip accessor, propagate |
| Concrete fact, accessor absent | `startsWithAccessor(a)` → false | No propagation (accessor doesn't match) |
| Abstract fact, accessor excluded | `a in fact.exclusions` → true | No propagation (accessor was already tried) |
| **Abstract fact, accessor NOT excluded** | `a !in fact.exclusions` | **Add `a` to exclusions → `fact.exclude(a)`** |

The last case is critical. By emitting `fact.exclude(accessor)`, the flow function tells the framework: "I tried to use accessor `a` but it wasn't concrete." The framework detects the new exclusion, creates a refined fact with `a` materialized as a concrete accessor, and re-runs the analysis. On the next pass, `startsWithAccessor(a)` returns true and the normal concrete path executes.

This pattern is used in:
- `handleRefAssign` — field/element read from abstract fact
- `handleStore` — field/element write kill check on abstract fact
- `checkFactMark` — taint mark detection at sink on abstract fact

### 4.2.14 Edge Creation Helpers

```kotlin
    private fun makeEdge(initialFact: InitialFactAp?, newFact: FinalFactAp): Sequent {
        val traceInfo = if (generateTrace) MethodSequentFlowFunction.TraceInfo.Flow else null
        return if (initialFact != null) {
            Sequent.FactToFact(initialFact, newFact, traceInfo)
        } else {
            Sequent.ZeroToFact(newFact, traceInfo)
        }
    }
```

---

## 4.3 `GoMethodCallFlowFunction`

> **File**: `org/opentaint/dataflow/go/analysis/GoMethodCallFlowFunction.kt`

Handles interprocedural taint propagation at call sites.

### 4.3.1 Class Structure

```kotlin
class GoMethodCallFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val returnValue: GoIRValue?,
    private val callExpr: GoCallExpr,
    private val statement: GoIRInst,
    private val generateTrace: Boolean,
) : MethodCallFlowFunction {

    private val method: GoIRFunction get() = context.method
    private val rulesProvider: GoTaintRulesProvider get() = context.rulesProvider
    private val callInfo: GoIRCallInfo get() = callExpr.callInfo
    private val calleeName: String? get() = callExpr.calleeName
```

### 4.3.2 Zero Propagation

```kotlin
    override fun propagateZeroToZero(): Set<ZeroCallFact> {
        val result = mutableSetOf<ZeroCallFact>(
            CallToReturnZeroFact,
            CallToStartZeroFact,
        )

        // Apply source rules: if callee is a taint source, create tainted return value
        applySourceRules(result)

        // Apply unconditional sink rules (sinks that don't require a specific tainted arg)
        // MVP: all sinks require a tainted argument, so this is a no-op

        return result
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> {
        return propagateZeroToZero()
    }
```

### 4.3.3 Source Rule Application

```kotlin
    private fun applySourceRules(result: MutableSet<ZeroCallFact>) {
        val name = calleeName ?: return
        val sourceRules = rulesProvider.sourceRulesForCall(name)

        for (rule in sourceRules) {
            val base = GoFlowFunctionUtils.resolvePosition(rule.pos)

            // For source rules, the position is typically Result (the return value)
            // Map Result → the caller's register that receives the return value
            val callerBase = when (base) {
                is AccessPathBase.Return -> {
                    if (returnValue != null) {
                        GoFlowFunctionUtils.accessPathBase(returnValue, method) ?: continue
                    } else {
                        continue  // void call, no return value to taint
                    }
                }
                is AccessPathBase.Argument -> {
                    // Source on argument: taint the caller's argument at that position
                    val argIdx = base.idx
                    if (argIdx < callInfo.args.size) {
                        GoFlowFunctionUtils.accessPathBase(callInfo.args[argIdx], method) ?: continue
                    } else continue
                }
                else -> continue
            }

            // Create the tainted fact with Universe exclusions (concrete source)
            val factAp = apManager.createAbstractAp(callerBase, ExclusionSet.Universe)
                .prependAccessor(TaintMarkAccessor(rule.mark))

            val traceInfo = if (generateTrace) MethodCallFlowFunction.TraceInfo.Flow else null
            result.add(CallToReturnZFact(factAp, traceInfo))
        }
    }
```

### 4.3.4 Fact-to-Fact Propagation

```kotlin
    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> {
        val result = mutableSetOf<FactCallFact>()

        // Check if fact is relevant to this call
        val isRelevant = GoMethodCallFactMapper.factIsRelevantToMethodCall(
            returnValue as? CommonValue, callExpr, currentFactAp
        )

        if (!isRelevant) {
            result.add(Unchanged)
            return result
        }

        // 1. Check sink rules
        applySinkRules(initialFactAp, currentFactAp, result)

        // 2. Apply pass-through rules (if callee is a known propagator)
        val passApplied = applyPassRules(initialFactAp, currentFactAp, result)

        // 3. Map fact to callee (call-to-start)
        mapFactToCallee(initialFactAp, currentFactAp, result)

        // 4. Fact survives call (call-to-return) — always, unless pass rule explicitly dropped it
        val traceInfo = if (generateTrace) MethodCallFlowFunction.TraceInfo.Flow else null
        result.add(CallToReturnFFact(initialFactAp, currentFactAp, traceInfo))

        return result
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> {
        return setOf(Unchanged)  // MVP: no non-distributive support
    }
```

### 4.3.5 Sink Rule Application

```kotlin
    private fun applySinkRules(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ) {
        val name = calleeName ?: return
        val sinkRules = rulesProvider.sinkRulesForCall(name)

        for (rule in sinkRules) {
            val sinkArgBase = GoFlowFunctionUtils.resolvePosition(rule.pos)

            // Map the sink's position to the caller's value
            val callerArgBase = when (sinkArgBase) {
                is AccessPathBase.Argument -> {
                    val argIdx = sinkArgBase.idx
                    if (argIdx < callInfo.args.size) {
                        GoFlowFunctionUtils.accessPathBase(callInfo.args[argIdx], method)
                    } else null
                }
                is AccessPathBase.This -> {
                    callInfo.receiver?.let { GoFlowFunctionUtils.accessPathBase(it, method) }
                }
                else -> null
            } ?: continue

            // Check if the current fact's base matches the sink argument
            if (currentFactAp.base != callerArgBase) continue

            // Check if fact has the required taint mark.
            // For abstract facts, this triggers refinement — the sink will be
            // detected on a subsequent pass once the mark is materialized.
            if (checkFactMark(currentFactAp, rule.mark, initialFactAp, result)) {
                // Report vulnerability
                context.taint.taintSinkTracker.addVulnerability(
                    TaintSinkTracker.TaintVulnerabilityWithFact(
                        sink = rule,
                        fact = currentFactAp,
                    )
                )
            }
        }
    }

    /**
     * Check if a fact carries a specific taint mark.
     *
     * For concrete facts: the taint mark is explicitly the first accessor → return true.
     *
     * For abstract facts: the mark is behind the abstraction point *.
     * We must NOT simply return true for abstract facts. Instead, we add the
     * TaintMarkAccessor to the exclusion set to trigger the framework's
     * refinement mechanism. The framework will re-analyze with a more specific
     * fact where the taint mark is materialized. On the next pass,
     * startsWithAccessor(TaintMarkAccessor(mark)) will return true.
     *
     * See edges-knowledge.md Lesson 4 for the refinement mechanism.
     */
    private fun checkFactMark(
        fact: FinalFactAp,
        mark: String,
        initialFact: InitialFactAp?,
        result: MutableSet<FactCallFact>,
    ): Boolean {
        val markAccessor = TaintMarkAccessor(mark)

        // Concrete: taint mark is the first accessor
        if (fact.startsWithAccessor(markAccessor)) return true

        // Abstract: mark is not excluded — trigger refinement
        if (fact.isAbstract() && markAccessor !in fact.exclusions) {
            // Add the mark accessor to exclusions. The framework sees the
            // updated exclusion set and re-analyzes with a refined fact
            // where the mark is materialized as a concrete accessor.
            val refinedFact = fact.exclude(markAccessor)
            if (initialFact != null) {
                result.add(CallToReturnFFact(initialFact, refinedFact, null))
            }
            return false  // Not yet confirmed — will be confirmed after refinement
        }

        return false
    }
```

### 4.3.6 Pass-Through Rule Application

```kotlin
    private fun applyPassRules(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ): Boolean {
        val name = calleeName ?: return false
        val passRules = rulesProvider.passRulesForCall(name)
        if (passRules.isEmpty()) return false

        var applied = false
        for (rule in passRules) {
            val (fromBase, fromAccessors) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.from)
            val (toBase, toAccessors) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.to)

            // Map "from" position to caller's value
            val callerFromBase = mapPositionToCallerBase(fromBase) ?: continue
            if (currentFactAp.base != callerFromBase) continue

            // Map "to" position to caller's value
            val callerToBase = mapPositionToCallerBase(toBase) ?: continue

            // Create the propagated fact
            val newFact = currentFactAp.rebase(callerToBase)
            val traceInfo = if (generateTrace) MethodCallFlowFunction.TraceInfo.Flow else null
            result.add(CallToReturnFFact(initialFactAp, newFact, traceInfo))
            applied = true
        }
        return applied
    }

    private fun mapPositionToCallerBase(posBase: AccessPathBase): AccessPathBase? {
        return when (posBase) {
            is AccessPathBase.Return -> {
                returnValue?.let { GoFlowFunctionUtils.accessPathBase(it, method) }
            }
            is AccessPathBase.Argument -> {
                val idx = posBase.idx
                if (idx < callInfo.args.size) {
                    GoFlowFunctionUtils.accessPathBase(callInfo.args[idx], method)
                } else null
            }
            is AccessPathBase.This -> {
                callInfo.receiver?.let { GoFlowFunctionUtils.accessPathBase(it, method) }
            }
            else -> null
        }
    }
```

### 4.3.7 Mapping Facts to Callee (Call-to-Start)

```kotlin
    private fun mapFactToCallee(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ) {
        // Use GoMethodCallFactMapper to map the fact into the callee's namespace
        GoMethodCallFactMapper.mapMethodCallToStartFlowFact(
            callee = null as? CommonMethod,  // callee not yet known (multiple possible)
            callExpr = callExpr,
            factAp = currentFactAp,
            checker = FactTypeChecker.Dummy,
        ) { mappedFact, startBase ->
            val traceInfo = if (generateTrace) MethodCallFlowFunction.TraceInfo.Flow else null
            result.add(CallToStartFFact(initialFactAp, currentFactAp, startBase, traceInfo))
        }
    }
```

---

## 4.4 Important: GoIRCall Instructions and Flow Function Routing

The framework routes instructions to flow functions based on whether they contain a call:

1. **If `getCallExpr(inst)` returns non-null** → `MethodCallFlowFunction` is used
2. **If `getCallExpr(inst)` returns null** → `MethodSequentFlowFunction` is used

Since `GoIRCall`, `GoIRGo`, and `GoIRDefer` all have `GoIRCallInfo`, `getCallExpr()` returns non-null for them. Therefore:
- `GoIRCall` → handled by `GoMethodCallFlowFunction` (source/sink/pass rules + interprocedural mapping)
- `GoIRGo` → handled by `GoMethodCallFlowFunction` (goroutine — MVP: treated as a normal call)
- `GoIRDefer` → handled by `GoMethodCallFlowFunction` (deferred call — MVP: treated as a normal call)
- All other instructions → handled by `GoMethodSequentFlowFunction`

**Defer semantics note**: In Go, `defer f(args)` evaluates `args` immediately but defers the call to `f`. The Go SSA IR models this accurately — the `GoIRDefer` instruction appears at the point where `defer` is written, and the arguments are the values at that point. The actual call happens at function exit (modeled by `GoIRRunDefers`). For the MVP, treating `GoIRDefer` as a normal call at the point of the `defer` statement correctly handles the argument evaluation semantics. The deferred execution order (LIFO at function exit) is a refinement for later phases.
