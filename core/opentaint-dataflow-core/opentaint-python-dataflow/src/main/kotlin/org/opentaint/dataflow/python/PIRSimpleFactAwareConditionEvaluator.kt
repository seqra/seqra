package org.opentaint.dataflow.python

import org.opentaint.dataflow.configuration.python.PIRCondition
import org.opentaint.dataflow.taint.TaintFactAwareConditionEvaluator

class PIRSimpleFactAwareConditionEvaluator(
    private val conditionRewriter: PIRConditionRewriter,
    private val evaluator: TaintFactAwareConditionEvaluator?,
) {
    fun eval(condition: PIRCondition): Boolean {
        val simplifiedCondition = conditionRewriter.rewrite(condition)
        val conditionExpr = when {
            simplifiedCondition.isFalse -> return false
            simplifiedCondition.isTrue -> return true
            else -> simplifiedCondition.expr
        }

        if (evaluator == null) {
            return false
        }

        return evaluator.evalWithAssumptionsCheck(conditionExpr)
    }
}
