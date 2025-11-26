package org.opentaint.api.checkers

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRuleFilter
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider

class JIRFilteredTaintRulesProvider(
    private val provider: TaintRulesProvider,
    private val filter: TaintRuleFilter
) : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod) =
        provider.entryPointRulesForMethod(method)
            .filter { filter.ruleEnabled(it) }

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst) =
        provider.sourceRulesForMethod(method, statement)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst) =
        provider.sinkRulesForMethod(method, statement)
            .filter { filter.ruleEnabled(it) }

    override fun passTroughRulesForMethod(method: CommonMethod, statement: CommonInst) =
        provider.passTroughRulesForMethod(method, statement)
            .filter { filter.ruleEnabled(it) }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst) =
        provider.cleanerRulesForMethod(method, statement)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethodExit(method: CommonMethod, statement: CommonInst) =
        provider.sinkRulesForMethodExit(method, statement)
            .filter { filter.ruleEnabled(it) }

    override fun sinkRulesForMethodEntry(method: CommonMethod) =
        provider.sinkRulesForMethodEntry(method)
            .filter { filter.ruleEnabled(it) }

    override fun sourceRulesForStaticField(field: JIRField, statement: CommonInst) =
        provider.sourceRulesForStaticField(field, statement)
            .filter { filter.ruleEnabled(it) }
}
