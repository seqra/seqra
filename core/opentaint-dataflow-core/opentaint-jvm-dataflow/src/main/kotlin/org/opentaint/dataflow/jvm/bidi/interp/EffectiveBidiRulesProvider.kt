package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.MarkTable
import org.opentaint.dataflow.configuration.jvm.*
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.util.Cancellation

/** Effective new-core rules. Keep full conditions and eagerly remove sink end actions. */
class EffectiveBidiRulesProvider(private val delegate: TaintRulesProvider,
                                 private val catalog: MarkTable) : TaintRulesProvider by delegate {
    /** Select only after full signed folding, binding and original role admission. */
    internal fun groupConditions(cancellation: Cancellation): GroupRuleConditions = GroupRuleConditions(catalog, cancellation)

    private fun <R> materialized(values: Iterable<R>): List<R> =
        if (values is SpecializedRules<R>) values else values.toList()

    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.entryPointRulesForMethod(method, statement, fact, allRelevant))
    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.sourceRulesForMethod(method, statement, fact, allRelevant))
    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.exitSourceRulesForMethod(method, statement, fact, allRelevant))
    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.sourceRulesForStaticField(field, statement, fact, allRelevant))
    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.cleanerRulesForMethod(method, statement, fact, allRelevant))
    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.passTroughRulesForMethod(method, statement, fact, allRelevant))
    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.sinkRulesForMethod(method, statement, fact, allRelevant).map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        materialized(delegate.sinkRulesForMethodEntry(method, statement, fact, allRelevant).map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?,
                                       initialFacts: Set<InitialFactAp>?, allRelevant: Boolean) =
        materialized(delegate.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant)
            .map { if (it.trackFactsReachAnalysisEnd.isEmpty()) it else it.copy(trackFactsReachAnalysisEnd = emptyList()) })
}
