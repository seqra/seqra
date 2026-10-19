package org.opentaint.dataflow.jvm.ap.ifds.taint

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintEntryPointSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource

/**
 * For unchanged query inputs in one selected rule/body generation, keep candidate, action and relevant-mark
 * iteration order stable, including allRelevant queries. Equivalent rule copies are allowed. Rebuilt bidi
 * forms use these original ordinals as logical identities; a new rule/body generation needs new form owners.
 */
interface TaintRulesProvider : CommonTaintRulesProvider {
    /** Full generated identity -> outer rule ID, captured by the rule generator. */
    val markOwners: Map<String, String> get() = emptyMap()

    fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintEntryPointSource>
    fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintMethodSource>
    fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintMethodExitSource>
    fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintMethodSink>
    fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintMethodEntrySink>
    fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?, allRelevant: Boolean = false): Iterable<TaintMethodExitSink>
    fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst?, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintPassThrough>
    fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintCleaner>
    fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean = false): Iterable<TaintStaticFieldSource>

    fun selectRules(ruleIds: Set<String>)
}

/** Explicit capability: delegation must not silently replace a provider's overridden roles. */
interface BidirectionalRulesProviderFactory {
    fun bidirectionalRulesView(): TaintRulesProvider
}

/** Unknown/custom providers keep all of their roles. Known configuration owners
 * select an isolated pre-resolution view without changing the legacy provider. */
fun TaintRulesProvider.forBidirectionalCore(): TaintRulesProvider =
    (this as? BidirectionalRulesProviderFactory)?.bidirectionalRulesView() ?: this
