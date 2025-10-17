package org.opentaint.api.checkers

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.TaintRuleFilter
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider

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
}
