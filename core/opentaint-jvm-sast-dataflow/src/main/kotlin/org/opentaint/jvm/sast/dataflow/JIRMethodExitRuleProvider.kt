package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

class JIRMethodExitRuleProvider(val base: TaintRulesProvider) : TaintRulesProvider by base, BidirectionalRulesProviderFactory {
    override fun bidirectionalRulesView(): TaintRulesProvider = JIRMethodExitRuleProvider(base.forBidirectionalCore())
    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> {
        // Apply method exit rules on Z2F edges only
        if (!initialFacts.isNullOrEmpty()) return emptyList()

        return base.sinkRulesForMethodExit(method, statement, fact, initialFacts, allRelevant)
    }
}
