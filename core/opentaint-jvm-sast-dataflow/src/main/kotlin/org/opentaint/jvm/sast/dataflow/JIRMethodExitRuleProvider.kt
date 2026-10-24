package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMRuleSelectionCapability
import org.opentaint.dataflow.jvm.ap.ifds.taint.executableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.includeExecutableRuleScope
import org.opentaint.dataflow.jvm.ap.ifds.taint.currentMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.JVMExecutableRuleCatalog
import org.opentaint.dataflow.jvm.ap.ifds.taint.ExecutableMarkReduction
import org.opentaint.dataflow.jvm.ap.ifds.taint.originalPassThroughRulesForAliases
import org.opentaint.dataflow.jvm.ap.ifds.taint.BidirectionalRulesProviderFactory
import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

class JIRMethodExitRuleProvider(val base: TaintRulesProvider) : TaintRulesProvider by base, BidirectionalRulesProviderFactory, JVMRuleSelectionCapability {
    override fun executableRuleCatalog() = base.executableRuleCatalog()
    override fun includeExecutableRuleScope(key: Any, scope: () -> JVMExecutableRuleCatalog?) =
        base.includeExecutableRuleScope(key, scope)
    override fun currentMarkReduction() = base.currentMarkReduction()
    override fun originalPassThroughRulesForAliases(method: org.opentaint.ir.api.common.CommonMethod) =
        base.originalPassThroughRulesForAliases(method)
    override fun applyExecutableMarkReduction(reduction: ExecutableMarkReduction) =
        base.applyExecutableMarkReduction(reduction)

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
