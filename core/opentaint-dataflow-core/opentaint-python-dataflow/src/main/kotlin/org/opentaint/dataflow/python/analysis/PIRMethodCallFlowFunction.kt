package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
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
                val newFact = apManager.createAbstractAp(targetBase, ExclusionSet.Universe)
                    .prependAccessor(TaintMarkAccessor(source.mark))
                results.add(CallToReturnZFact(newFact, null))
            }
        }

        // Propagate zero into callee for interprocedural analysis
        if (calleeMethod != null) {
            results.add(CallToStartZeroFact)
        }

        return results
    }

    override fun propagateZeroToFact(currentFactAp: FinalFactAp): Set<ZeroCallFact> =
        propagateFact(currentFactAp,
            mkCallToReturnFact = { _: PIRFactRefinement, fact -> CallToReturnZFact(fact, null) },
            mkCallToStartFact = { _: PIRFactRefinement, callerFact, startBase -> CallToStartZFact(callerFact, startBase, null) },
            mkUnchanged = { @Suppress("UNCHECKED_CAST") (Unchanged as ZeroCallFact) },
        )

    override fun propagateFactToFact(
        initialFactAp: InitialFactAp,
        currentFactAp: FinalFactAp,
    ): Set<FactCallFact> =
        propagateFact(currentFactAp,
            mkCallToReturnFact = { refinement, fact ->
                CallToReturnFFact(refinement.refine(initialFactAp), refinement.refine(fact), null)
            },
            mkCallToStartFact = { refinement, callerFact, startBase ->
                CallToStartFFact(refinement.refine(initialFactAp), refinement.refine(callerFact), startBase, null)
            },
            mkUnchanged = { @Suppress("UNCHECKED_CAST") (Unchanged as FactCallFact) },
        )

    override fun propagateNDFactToFact(
        initialFacts: Set<InitialFactAp>,
        currentFactAp: FinalFactAp,
    ): Set<NDFactCallFact> = setOf(Unchanged)

    // --- Shared propagation logic ---

    /**
     * Shared logic for both zero-to-fact and fact-to-fact propagation at call sites.
     * Handles sinks, pass-through rules, and call-to-start mapping.
     *
     * [T] is the specific CallFact subtype ([ZeroCallFact] or [FactCallFact]).
     * [mkCallToReturnFact] creates a call-to-return fact from a rebased fact.
     * [mkCallToStartFact] creates a call-to-start fact from (callerFact, startBase).
     * [mkUnchanged] creates the "unchanged" fact to keep in caller frame.
     */
    private inline fun <T : CallFact> propagateFact(
        currentFactAp: FinalFactAp,
        mkCallToReturnFact: (PIRFactRefinement, FinalFactAp) -> T,
        mkCallToStartFact: (PIRFactRefinement, FinalFactAp, AccessPathBase) -> T,
        mkUnchanged: () -> T,
    ): MutableSet<T> {
        val results = mutableSetOf<T>()
        val refinement = PIRFactRefinement()

        // 1. Check sink rules (accumulates refinements for abstract facts)
        checkSinks(currentFactAp, refinement)

        // 2. Apply pass-through rules
        for (pass in taintConfig.propagators) {
            if (matchesCall(pass.function, callInst)) {
                val fromBase = resolvePositionWithModifiers(pass.from, callInst, method, ctx)
                val toBase = resolvePositionWithModifiers(pass.to, callInst, method, ctx)
                if (fromBase != null && toBase != null && currentFactAp.base == fromBase) {
                    val newFact = currentFactAp.rebase(toBase)
                    results.add(mkCallToReturnFact(refinement, newFact))
                }
            }
        }

        // 3. Call-to-start: map caller fact to callee's argument space
        if (calleeMethod != null) {
            val mappings = mapCallToStart(currentFactAp)
            for ((startBase, callerFact) in mappings) {
                results.add(mkCallToStartFact(refinement, callerFact, startBase))
            }
        }

        // 4. Call-to-return: keep fact in caller frame
        results.add(mkUnchanged())

        return results
    }

    // --- Helpers ---

    /**
     * Checks sink rules against the current fact.
     * For concrete facts (taint mark explicitly present), reports vulnerability.
     * For abstract facts (taint mark might be behind `*`), accumulates refinement
     * so the framework will re-analyze with more specific facts.
     */
    private fun checkSinks(currentFactAp: FinalFactAp, refinement: PIRFactRefinement) {
        for (sink in taintConfig.sinks) {
            if (matchesCall(sink.function, callInst)) {
                val sinkBase = resolvePosition(sink.pos, callInst, method, ctx)
                if (sinkBase != null && currentFactAp.base == sinkBase) {
                    val accessor = TaintMarkAccessor(sink.mark)
                    if (currentFactAp.startsWithAccessor(accessor)) {
                        // Concrete match: taint mark is explicitly present
                        ctx.taint.taintSinkTracker.addUnconditionalVulnerability(
                            methodEntryPoint = ctx.methodEntryPoint,
                            statement = callInst,
                            rule = sink.toCommonSink(),
                        )
                    } else if (currentFactAp.isAbstract() && accessor !in currentFactAp.exclusions) {
                        // Abstract: mark might be behind `*`. Record refinement so
                        // the framework re-analyzes with the mark made concrete.
                        refinement.add(accessor)
                    }
                }
            }
        }
    }

    /**
     * Maps a caller fact to callee start bases.
     * Returns list of (startBase, callerFact) pairs.
     * - startBase: the AccessPathBase in the callee's frame (e.g. Argument(0))
     * - callerFact: the original fact with the caller's base (NOT rebased)
     *
     * A single fact can map to multiple callee parameters (e.g., f(x, x)).
     * Ensures callee argument indices don't exceed callee's parameter count
     * to avoid AccessPathBaseStorage crashes.
     */
    private fun mapCallToStart(
        callerFact: FinalFactAp,
    ): List<Pair<AccessPathBase, FinalFactAp>> {
        val base = callerFact.base
        val results = mutableListOf<Pair<AccessPathBase, FinalFactAp>>()
        val calleeParamCount = calleeMethod?.parameters?.size ?: 0

        for ((i, arg) in callInst.args.withIndex()) {
            // Skip if callee-side argument index exceeds formal parameter count
            if (i >= calleeParamCount) break

            val argBase = PIRFlowFunctionUtils.accessPathBase(arg.value, method, ctx)
                ?: continue
            if (base == argBase) {
                val startBase = AccessPathBase.Argument(i)
                results.add(startBase to callerFact)
            }
        }

        if (base is AccessPathBase.ClassStatic) {
            results.add(base to callerFact)
        }

        return results
    }

    companion object {
        fun matchesCall(ruleFunction: String, call: PIRCall): Boolean {
            val callee = call.resolvedCallee ?: return false
            return callee == ruleFunction || callee.endsWith(".$ruleFunction")
        }

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
            is PositionBase.This -> null
            is PositionBase.ClassStatic -> null
            is PositionBase.AnyArgument -> null
        }

        fun resolvePositionWithModifiers(
            pos: PositionBaseWithModifiers,
            call: PIRCall,
            method: PIRFunction,
            ctx: PIRMethodAnalysisContext,
        ): AccessPathBase? = resolvePosition(pos.base, call, method, ctx)
    }
}

internal fun TaintRules.Sink.toCommonSink(): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
    override val id: String = this@toCommonSink.id
    override val meta: CommonTaintConfigurationSinkMeta = object : CommonTaintConfigurationSinkMeta {
        override val message: String = "Taint sink: ${this@toCommonSink.function}"
        override val severity: CommonTaintConfigurationSinkMeta.Severity =
            CommonTaintConfigurationSinkMeta.Severity.Error
    }
}
