package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.bidi.ap.RawMarkDeclarations
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration

class JIRTaintRulesProvider(
    private val taintConfiguration: TaintConfiguration,
    private val ignoreSinkEndFacts: Boolean = false,
) : TaintRulesProvider, BidirectionalRulesProviderFactory {
    override val markDeclarations: RawMarkDeclarations
        get() = taintConfiguration.rawMarkDeclarations()

    override fun bidirectionalRulesView(): TaintRulesProvider =
        if (ignoreSinkEndFacts) this else JIRTaintRulesProvider(taintConfiguration, ignoreSinkEndFacts = true)
    override fun entryPointRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        taintConfiguration.entryPointForMethod(it, allRelevant)
    }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        taintConfiguration.sourceForMethod(it, allRelevant)
    }

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = getRules(method) {
        taintConfiguration.exitSourceForMethod(it, allRelevant)
    }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        taintConfiguration.sinkForMethod(it, allRelevant, ignoreSinkEndFacts)
    }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst?,
        fact: FactAp?,
        allRelevant: Boolean
    ) = getRules(method) {
        taintConfiguration.passThroughForMethod(it, allRelevant)
    }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        taintConfiguration.cleanerForMethod(it, allRelevant)
    }

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> = getRules(method) {
        taintConfiguration.methodExitSinkForMethod(it, allRelevant, ignoreSinkEndFacts)
    }

    override fun sinkRulesForMethodEntry(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        taintConfiguration.methodEntrySinkForMethod(it, allRelevant, ignoreSinkEndFacts)
    }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) =
        taintConfiguration.sourceForStaticField(field)

    private inline fun <T : TaintConfigurationItem> getRules(
        method: CommonMethod,
        body: (JIRMethod) -> Iterable<T>
    ): Iterable<T> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        return body(method)
    }

    override fun selectRules(ruleIds: Set<String>) {
        // do nothing
    }
}
