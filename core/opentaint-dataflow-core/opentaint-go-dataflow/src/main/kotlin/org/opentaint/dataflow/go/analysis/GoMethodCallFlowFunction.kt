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
import org.opentaint.dataflow.go.GoFlowFunctionUtils
import org.opentaint.dataflow.go.GoMethodCallFactMapper
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.cfg.GoIRCallInfo
import org.opentaint.ir.go.inst.GoIRInst
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

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> {
        val result = mutableSetOf<ZeroCallFact>()

        // Apply source rules (same as zero-to-zero)
        result.add(CallToReturnZeroFact)
        result.add(CallToStartZeroFact)
        applySourceRules(result)

        // Check sinks for this fact
        applyZeroToFactSinkRules(currentFactAp, result)

        // Apply pass-through rules for this fact
        applyZeroToFactPassRules(currentFactAp, result)

        // Map fact to callee
        mapZeroFactToCallee(currentFactAp, result)

        // Fact survives call (call-to-return)
        val traceInfo = if (generateTrace) TraceInfo.Flow else null
        result.add(CallToReturnZFact(currentFactAp, traceInfo))

        return result
    }

    // ── Fact propagation ─────────────────────────────────────────────

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> {
        val result = mutableSetOf<FactCallFact>()

        val isRelevant = GoMethodCallFactMapper.factIsRelevantToMethodCall(
            returnValue as? CommonValue, callExpr, currentFactAp
        )

        if (!isRelevant) {
            result.add(Unchanged)
            return result
        }

        // 1. Sink rules
        applySinkRules(initialFactAp, currentFactAp, result)

        // 2. Pass-through rules
        applyPassRules(initialFactAp, currentFactAp, result)

        // 3. Map fact to callee (call-to-start)
        mapFactToCallee(initialFactAp, currentFactAp, result)

        // 4. Fact survives call (call-to-return)
        val traceInfo = if (generateTrace) TraceInfo.Flow else null
        result.add(CallToReturnFFact(initialFactAp, currentFactAp, traceInfo))

        return result
    }

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> {
        return setOf(Unchanged)
    }

    // ── Zero-to-fact helpers (for propagateZeroToFact) ─────────────────

    private fun applyZeroToFactSinkRules(
        currentFactAp: FinalFactAp,
        result: MutableSet<ZeroCallFact>,
    ) {
        val name = calleeName ?: return
        val sinkRules = rulesProvider.sinkRulesForCall(name)

        for (rule in sinkRules) {
            val sinkArgBase = GoFlowFunctionUtils.resolvePosition(rule.pos)
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

            if (currentFactAp.base != callerArgBase) continue

            val markAccessor = TaintMarkAccessor(rule.mark)
            if (currentFactAp.startsWithAccessor(markAccessor)) {
                context.taint.taintSinkTracker.addVulnerability(
                    methodEntryPoint = context.methodEntryPoint,
                    facts = emptySet(),
                    statement = statement,
                    rule = rule,
                )
            }
        }
    }

    private fun applyZeroToFactPassRules(
        currentFactAp: FinalFactAp,
        result: MutableSet<ZeroCallFact>,
    ) {
        val name = calleeName ?: return
        val passRules = rulesProvider.passRulesForCall(name)
        if (passRules.isEmpty()) return

        for (rule in passRules) {
            val (fromBase, _) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.from)
            val (toBase, toAccessors) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.to)

            val callerFromBase = mapPositionToCallerBase(fromBase) ?: continue
            if (currentFactAp.base != callerFromBase) continue

            val callerToBase = mapPositionToCallerBase(toBase) ?: continue

            var newFact = currentFactAp.rebase(callerToBase)
            for (accessor in toAccessors) {
                newFact = newFact.prependAccessor(accessor)
            }

            val traceInfo = if (generateTrace) TraceInfo.Flow else null
            result.add(CallToReturnZFact(newFact, traceInfo))
        }
    }

    private fun mapZeroFactToCallee(
        currentFactAp: FinalFactAp,
        result: MutableSet<ZeroCallFact>,
    ) {
        val traceInfo = if (generateTrace) TraceInfo.Flow else null

        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && currentFactAp.base == argBase) {
                result.add(CallToStartZFact(currentFactAp, AccessPathBase.Argument(i), traceInfo))
            }
        }

        if (callInfo.receiver != null) {
            val recvBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (recvBase != null && currentFactAp.base == recvBase) {
                result.add(CallToStartZFact(currentFactAp, AccessPathBase.This, traceInfo))
            }
        }

        if (currentFactAp.base is AccessPathBase.ClassStatic) {
            result.add(CallToStartZFact(currentFactAp, AccessPathBase.ClassStatic, traceInfo))
        }
    }

    // ── Source rule application ───────────────────────────────────────

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
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ) {
        val name = calleeName ?: return
        val sinkRules = rulesProvider.sinkRulesForCall(name)

        for (rule in sinkRules) {
            val sinkArgBase = GoFlowFunctionUtils.resolvePosition(rule.pos)

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

            if (currentFactAp.base != callerArgBase) continue

            if (checkFactMark(currentFactAp, rule.mark, initialFactAp, result)) {
                context.taint.taintSinkTracker.addVulnerability(
                    methodEntryPoint = context.methodEntryPoint,
                    facts = setOf(initialFactAp),
                    statement = statement,
                    rule = rule,
                )
            }
        }
    }

    /**
     * Check if a fact carries a specific taint mark.
     * For concrete facts: taint mark is the first accessor.
     * For abstract facts: triggers refinement via exclusion set.
     */
    private fun checkFactMark(
        fact: FinalFactAp,
        mark: String,
        initialFact: InitialFactAp?,
        result: MutableSet<FactCallFact>,
    ): Boolean {
        val markAccessor = TaintMarkAccessor(mark)

        if (fact.startsWithAccessor(markAccessor)) return true

        if (fact.isAbstract() && !fact.exclusions.contains(markAccessor)) {
            val refinedFact = fact.exclude(markAccessor)
            if (initialFact != null) {
                result.add(CallToReturnFFact(initialFact, refinedFact, null))
            }
            return false
        }

        return false
    }

    // ── Pass-through rule application ────────────────────────────────

    private fun applyPassRules(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ) {
        val name = calleeName ?: return
        val passRules = rulesProvider.passRulesForCall(name)
        if (passRules.isEmpty()) return

        for (rule in passRules) {
            val (fromBase, _) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.from)
            val (toBase, toAccessors) = GoFlowFunctionUtils.resolvePositionWithModifiers(rule.to)

            val callerFromBase = mapPositionToCallerBase(fromBase) ?: continue
            if (currentFactAp.base != callerFromBase) continue

            val callerToBase = mapPositionToCallerBase(toBase) ?: continue

            var newFact = currentFactAp.rebase(callerToBase)
            for (accessor in toAccessors) {
                newFact = newFact.prependAccessor(accessor)
            }

            val traceInfo = if (generateTrace) TraceInfo.Flow else null
            result.add(CallToReturnFFact(initialFactAp, newFact, traceInfo))
        }
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

    // ── Map fact to callee (call-to-start) ───────────────────────────

    private fun mapFactToCallee(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
        result: MutableSet<FactCallFact>,
    ) {
        val traceInfo = if (generateTrace) TraceInfo.Flow else null

        // Map arguments → callee's Argument(i)
        for ((i, arg) in callInfo.args.withIndex()) {
            val argBase = GoFlowFunctionUtils.accessPathBaseFromValue(arg)
            if (argBase != null && currentFactAp.base == argBase) {
                result.add(CallToStartFFact(initialFactAp, currentFactAp, AccessPathBase.Argument(i), traceInfo))
            }
        }

        // Map receiver → callee's This
        if (callInfo.receiver != null) {
            val recvBase = GoFlowFunctionUtils.accessPathBaseFromValue(callInfo.receiver!!)
            if (recvBase != null && currentFactAp.base == recvBase) {
                result.add(CallToStartFFact(initialFactAp, currentFactAp, AccessPathBase.This, traceInfo))
            }
        }

        // ClassStatic passes through
        if (currentFactAp.base is AccessPathBase.ClassStatic) {
            result.add(CallToStartFFact(initialFactAp, currentFactAp, AccessPathBase.ClassStatic, traceInfo))
        }
    }
}
