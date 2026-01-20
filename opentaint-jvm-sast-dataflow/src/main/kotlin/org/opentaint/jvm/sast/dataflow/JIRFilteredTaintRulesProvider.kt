package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRuleFilter
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField

class JIRFilteredTaintRulesProvider(
    private val provider: TaintRulesProvider,
    private val filter: TaintRuleFilter
) : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?) =
        provider.entryPointRulesForMethod(method, fact)
            .filter { filter.ruleEnabled(it) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        provider.sourceRulesForMethod(method, statement, fact)
            .filter { filter.ruleEnabled(it) }

    override fun exitSourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        provider.exitSourceRulesForMethod(method, statement, fact)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        provider.sinkRulesForMethod(method, statement, fact)
            .filter { filter.ruleEnabled(it) }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        provider.passTroughRulesForMethod(method, statement, fact)
            .filter { filter.ruleEnabled(it) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?) =
        provider.cleanerRulesForMethod(method, statement, fact)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst, fact: FactAp?, initialFacts: Set<InitialFactAp>?): Iterable<TaintMethodExitSink> =
        provider.sinkRulesForMethodExit(method, statement, fact, initialFacts)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?) =
        provider.sinkRulesForMethodEntry(method, fact)
            .filter { filter.ruleEnabled(it) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst, fact: FactAp?) =
        provider.sourceRulesForStaticField(field, statement, fact)
            .filter { filter.ruleEnabled(it) }
}
