package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.jvm.ap.ifds.taint.ConditionEvaluator

class JIRSimpleFactAwareConditionEvaluator(
    private val conditionRewriter: JIRMarkAwareConditionRewriter,
    private val evaluator: JIRFactAwareConditionEvaluator?,
) : ConditionEvaluator<Boolean> {
    override fun eval(condition: Condition): Boolean {
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
