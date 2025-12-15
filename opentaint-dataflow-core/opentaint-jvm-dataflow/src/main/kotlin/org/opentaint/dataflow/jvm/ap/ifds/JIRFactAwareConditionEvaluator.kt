package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveBaseAp

class JIRFactAwareConditionEvaluator(
    facts: List<FactReader>,
) : FactAwareConditionEvaluator {
    private val basedFacts = facts.groupByTo(hashMapOf()) { it.base }

    private var hasEvaluatedContainsMark: Boolean = false
    private var remainingExpr: JIRMarkAwareConditionExpr? = null
    private val evaluatedFacts = mutableListOf<EvaluatedFact>()

    override fun evalWithAssumptionsCheck(condition: JIRMarkAwareConditionExpr): Boolean {
        if (basedFacts.isEmpty()) return false

        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false

        remainingExpr = condition.removeTrueLiterals {
            evalLiteral(it)
        }

        return remainingExpr == null
    }

    override fun assumptionExpr(): JIRMarkAwareConditionExpr? =
        remainingExpr?.takeIf { hasEvaluatedContainsMark }

    override fun facts(): List<InitialFactAp> = evaluatedFacts.map { it.eval() }

    private fun evalLiteral(literal: Literal): Boolean {
        if (literal.negated) return true
        return evalContainsMark(literal.condition)
    }

    private val markEvalCache = hashMapOf<ContainsMark, MarkEvaluationResult>()

    private fun evalContainsMark(condition: ContainsMark): Boolean {
        val conditionBase = condition.position.resolveBaseAp()
        val relevantFacts = basedFacts[conditionBase] ?: return false

        val result = markEvalCache.computeIfAbsent(condition) {
            val conditionPosAp = condition.position.resolveAp(conditionBase)

            val evaluatedFact = relevantFacts.firstOrNull {
                it.containsPositionWithTaintMark(conditionPosAp, condition.mark)
            }

            evaluatedFact?.let { EvaluatedFact(it, conditionPosAp, condition.mark) } ?: NoFact
        }

        return when (result) {
            is NoFact -> false
            is EvaluatedFact -> {
                hasEvaluatedContainsMark = true
                evaluatedFacts += result

                true
            }
        }
    }

    private sealed interface MarkEvaluationResult

    private data class EvaluatedFact(
        val reader: FactReader, val variable: PositionAccess, val mark: TaintMark
    ): MarkEvaluationResult {
        fun eval(): InitialFactAp = reader.createInitialFactWithTaintMark(variable, mark)
    }

    private data object NoFact: MarkEvaluationResult
}
