package org.opentaint.dataflow.go.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition

class GoMethodCallPrecondition : MethodCallPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<CallPrecondition> =
        listOf(CallPrecondition.Unchanged)

    override fun resolvePassRuleCondition(
        precondition: org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition.PassRuleCondition
    ): List<MethodCallPrecondition.PassRuleConditionFacts> = emptyList()
}
