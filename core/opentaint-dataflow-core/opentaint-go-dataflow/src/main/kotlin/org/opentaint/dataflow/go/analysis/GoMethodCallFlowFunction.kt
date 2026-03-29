package org.opentaint.dataflow.go.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.*
import org.opentaint.dataflow.go.GoCallExpr
import org.opentaint.dataflow.go.GoCallResolver
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.dataflow.go.GoMethodCallFactMapper
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.expr.GoIRMakeClosureExpr
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.ir.go.type.GoIRCallMode
import org.opentaint.ir.go.value.GoIRRegister
import org.opentaint.ir.go.value.GoIRValue

/**
 * Handles interprocedural taint propagation at call sites:
 * source rule application, sink rule checking, pass-through rules, and fact mapping.
 */
class GoMethodCallFlowFunction(
    private val apManager: ApManager,
    private val context: GoMethodAnalysisContext,
    private val returnValueFromFramework: GoIRValue?,
    private val callExpr: GoCallExpr,
    private val statement: GoIRInst,
    private val generateTrace: Boolean,
) : MethodCallFlowFunction {

    private val method: GoIRFunction get() = context.method
    private val rulesProvider get() = context.rulesProvider
    private val callInfo: GoIRCallInfo get() = callExpr.callInfo
    private val calleeName: String? get() = callExpr.calleeName

    /**
     * Get the return value register. GoIRCall doesn't implement CommonAssignInst,
     * so the framework passes null for returnValue. We extract it directly from the statement.
     */
    private val returnValue: GoIRValue?
        get() = returnValueFromFramework ?: GoFlowFunctionUtils.extractResultRegister(statement)

    // ── Zero propagation ─────────────────────────────────────────────

    override fun propagateZeroToZero(): Set<ZeroCallFact> {
        val result = mutableSetOf<ZeroCallFact>(
            CallToReturnZeroFact,
            CallToStartZeroFact,
        )
        applySourceRules(result)
        return result
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> = buildSet {
        propagateFact(
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addCallToReturn = { factAp, trace -> this += CallToReturnZFact(factAp, trace) },
            addCallToStart = { callerFact, startBase, trace -> this += CallToStartZFact(callerFact, startBase, trace) },
        )
    }

    // ── Fact propagation ─────────────────────────────────────────────

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> = buildSet {
        propagateFact(
            factAp = currentFactAp,
            skipCall = { this += Unchanged },
            addCallToReturn = { factAp, trace ->
                this += CallToReturnFFact(initialFactAp.replaceExclusions(factAp.exclusions), factAp, trace)
            },
            addCallToStart = { callerFact, startBase, trace ->
                this += CallToStartFFact(initialFactAp.replaceExclusions(callerFact.exclusions), callerFact, startBase, trace)
            },
        )
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> {
        return setOf(Unchanged)
    }

    // ── Shared propagation logic (like JVM's propagateFact) ──────────

    /**
     * Unified fact propagation logic shared between zero-to-fact and fact-to-fact.
     * Mirrors JVM's `propagateFact` pattern with lambdas for edge creation.
     */
    private fun propagateFact(
        factAp: FinalFactAp,
        skipCall: () -> Unit,
        addCallToReturn: (FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (callerFact: FinalFactAp, startBase: AccessPathBase, TraceInfo) -> Unit,
    ) {
        // 0. Relevance check
        if (!GoMethodCallFactMapper.factIsRelevantToMethodCall(
                returnValue as? CommonValue, callExpr, factAp
            )
        ) {
            skipCall()
            return
        }

        val traceInfo = if (generateTrace) TraceInfo.Flow else null

        // 1. Sink rules
        applySinkRules(factAp, addCallToReturn)

        // 2. Map fact to callee + pass-through rules
        mapFactToCalleeOrApplyPass(factAp, addCallToReturn, addCallToStart)

        // 3. Fact survives call (call-to-return)
        addCallToReturn(factAp, TraceInfo.Flow)
    }

    // ── Source rule application (zero-to-zero only) ──────────────────

    private fun applySourceRules(result: MutableSet<ZeroCallFact>) {
        val name = calleeName ?: return
        val sourceRules = rulesProvider.sourceRulesForCall(name)
        val retVal = returnValue

        for (rule in sourceRules) {
            val base = GoFlowFunctionUtils.resolvePosition(rule.pos)

            val callerBase = when (base) {
                is AccessPathBase.Return -> {
                    if (retVal != null) {
                        GoFlowFunctionUtils.accessPathBase(retVal, method) ?: continue
                    } else continue
                }
                is AccessPathBase.Argument -> {
                    val argIdx = base.idx
                    if (argIdx < callInfo.args.size) {
                        GoFlowFunctionUtils.accessPathBase(callInfo.args[argIdx], method) ?: continue
                    } else continue
                }
                else -> continue
            }

            val factAp = apManager.createAbstractAp(callerBase, ExclusionSet.Universe)
                .prependAccessor(TaintMarkAccessor(rule.mark))

            val traceInfo = if (generateTrace) TraceInfo.Flow else null
            result.add(CallToReturnZFact(factAp, traceInfo))
        }
    }

    // ── Sink rule application ────────────────────────────────────────

    private fun applySinkRules(
        currentFactAp: FinalFactAp,
        addCallToReturn: (FinalFactAp, TraceInfo) -> Unit,
    ) {
        val name = calleeName ?: return
        val sinkRules = rulesProvider.sinkRulesForCall(name)

        for (rule in sinkRules) {
            val sinkArgBase = GoFlowFunctionUtils.resolvePosition(rule.pos)
            val callerArgBase = mapPositionToCallerBase(sinkArgBase) ?: continue

            if (currentFactAp.base != callerArgBase) continue

            val markAccessor = TaintMarkAccessor(rule.mark)
            if (currentFactAp.startsWithAccessor(markAccessor)) {
                context.taint.taintSinkTracker.addVulnerability(
                    methodEntryPoint = context.methodEntryPoint,
                    facts = emptySet(),
                    statement = statement,
                    rule = rule,
                )
            } else if (currentFactAp.isAbstract() && !currentFactAp.exclusions.contains(markAccessor)) {
                // Trigger refinement
                val refinedFact = currentFactAp.exclude(markAccessor)
                addCallToReturn(refinedFact, TraceInfo.Flow)
            }
        }
    }

    // ── Map fact to callee + pass-through rules ──────────────────────

    private fun mapFactToCalleeOrApplyPass(
        factAp: FinalFactAp,
        addCallToReturn: (FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (callerFact: FinalFactAp, startBase: AccessPathBase, TraceInfo) -> Unit,
    ) {
        val isInvoke = callInfo.receiver != null
        val argOffset = if (isInvoke) 1 else 0

        // Map receiver → Argument(0) for INVOKE calls
        if (isInvoke) {
            val recvBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (recvBase != null && factAp.base == recvBase) {
                applyPassRulesOrCallToStart(factAp, AccessPathBase.Argument(0), addCallToReturn, addCallToStart)
            }
        }

        // Map arguments → Argument(i + argOffset)
        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && factAp.base == argBase) {
                applyPassRulesOrCallToStart(factAp, AccessPathBase.Argument(i + argOffset), addCallToReturn, addCallToStart)
            }
        }

        // Map closure bindings → free-var arguments for DYNAMIC calls
        if (callInfo.mode == GoIRCallMode.DYNAMIC) {
            val closureExpr = findClosureExpr()
            if (closureExpr != null) {
                val paramCount = closureExpr.fn.params.size
                for ((i, binding) in closureExpr.bindings.withIndex()) {
                    val bindingBase = GoFlowFunctionUtils.accessPathBase(binding, method)
                    if (bindingBase != null && factAp.base == bindingBase) {
                        val freeVarArgBase = AccessPathBase.Argument(paramCount + i)
                        applyPassRulesOrCallToStart(factAp, freeVarArgBase, addCallToReturn, addCallToStart)
                    }
                }
            }
        }

        // ClassStatic passes through
        if (factAp.base is AccessPathBase.ClassStatic) {
            addCallToStart(factAp, AccessPathBase.ClassStatic, TraceInfo.Flow)
        }
    }

    /**
     * For DYNAMIC calls, trace the function value back to a MakeClosureExpr.
     */
    private fun findClosureExpr(): GoIRMakeClosureExpr? {
        val funcValue = callInfo.function as? GoIRRegister ?: return null
        return GoFlowFunctionUtils.findMakeClosureExpr(funcValue, method)
    }

    /**
     * For a fact mapped to a callee argument, either apply pass-through rules
     * (producing call-to-return edges) or forward to the callee (call-to-start).
     */
    private fun applyPassRulesOrCallToStart(
        callerFactAp: FinalFactAp,
        startFactBase: AccessPathBase,
        addCallToReturn: (FinalFactAp, TraceInfo) -> Unit,
        addCallToStart: (callerFact: FinalFactAp, startBase: AccessPathBase, TraceInfo) -> Unit,
    ) {
        val name = calleeName
        val passRules = if (name != null) rulesProvider.passRulesForCall(name) else emptyList()

        for (rule in passRules) {
            val (fromBase, _) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.from)
            val (toBase, toAccessors) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.to)

            val callerFromBase = mapPositionToCallerBase(fromBase) ?: continue
            if (callerFactAp.base != callerFromBase) continue

            val callerToBase = mapPositionToCallerBase(toBase) ?: continue

            var newFact = callerFactAp.rebase(callerToBase)
            for (accessor in toAccessors) {
                newFact = newFact.prependAccessor(accessor)
            }

            addCallToReturn(newFact, TraceInfo.Flow)
        }

        // Always also forward to callee (pass rules are summaries, callee is still entered)
        addCallToStart(callerFactAp, startFactBase, TraceInfo.Flow)
    }

    /**
     * Maps a rule position (Argument/This/Return) to the corresponding
     * caller-side AccessPathBase.
     */
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
}
