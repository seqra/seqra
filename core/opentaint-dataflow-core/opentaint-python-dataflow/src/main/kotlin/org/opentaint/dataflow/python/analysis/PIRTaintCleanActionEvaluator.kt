package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.TaintCleanReach
import org.opentaint.dataflow.configuration.python.TaintCleanAction
import org.opentaint.dataflow.python.PIRFlowFunctionUtils.resolveAp
import org.opentaint.dataflow.taint.EvaluatedCleanAction
import org.opentaint.dataflow.taint.TaintCleanActionEvaluator
import org.opentaint.ir.api.python.PIRCall

class PIRTaintCleanActionEvaluator(private val call: PIRCall) {
    private val evaluator = TaintCleanActionEvaluator()

    fun evaluate(
        initialFact: EvaluatedCleanAction,
        rule: CommonTaintConfigurationItem,
        action: TaintCleanAction,
    ): List<EvaluatedCleanAction> {
        val variable = action.pos.resolveAp(call) ?: return listOf(initialFact)
        val mark = TaintMarkAccessor(action.mark.name)
        return evaluator.removeFinalFact(initialFact, variable, mark, rule, action, TaintCleanReach.Exact)
    }
}
