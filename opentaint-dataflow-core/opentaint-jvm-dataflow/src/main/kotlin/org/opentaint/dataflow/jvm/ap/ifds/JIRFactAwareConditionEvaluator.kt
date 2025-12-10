package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.And
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Or
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator.EvaluationResult
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveBaseAp

class JIRFactAwareConditionEvaluator(
    facts: List<FactReader>,
) : FactAwareConditionEvaluator {
    private val basedFacts = facts.groupByTo(hashMapOf()) { it.base }

    private var hasEvaluatedContainsMark: Boolean = false
    private var assumptionPossible: Boolean = false
    private val evaluatedFacts = mutableListOf<EvaluatedFact>()

    override fun evalWithAssumptionsCheck(condition: JIRMarkAwareConditionExpr): Boolean {
        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false

        val exprResult = evalExpr(condition)

        assumptionPossible = exprResult != EvaluationResult.False && hasEvaluatedContainsMark
        return exprResult == EvaluationResult.True
    }

    override fun assumptionsPossible(): Boolean = assumptionPossible

    override fun facts(): List<InitialFactAp> = evaluatedFacts.map { it.eval() }

    private fun evalExpr(expr: JIRMarkAwareConditionExpr): EvaluationResult = when (expr) {
        is Literal -> evalLiteral(expr)
        is And -> evalAndExpr(expr)
        is Or -> evalOrExpr(expr)
    }

    private fun evalAndExpr(expr: And): EvaluationResult {
        return evalArrayOrUnknown(expr.args, EvaluationResult.True) {
            when (it) {
                EvaluationResult.False -> return EvaluationResult.False
                EvaluationResult.True -> false
                EvaluationResult.Unknown -> true
            }
        }
    }

    private fun evalOrExpr(expr: Or): EvaluationResult {
        return evalArrayOrUnknown(expr.args, EvaluationResult.False) {
            when (it) {
                EvaluationResult.True -> return EvaluationResult.True
                EvaluationResult.False -> false
                EvaluationResult.Unknown -> true
            }
        }
    }

    private inline fun evalArrayOrUnknown(
        elements: Array<JIRMarkAwareConditionExpr>,
        default: EvaluationResult,
        isUnknown: (EvaluationResult) -> Boolean,
    ): EvaluationResult {
        var hasUnknown = false
        for (element in elements) {
            val elementResult = evalExpr(element)
            if (isUnknown(elementResult)) {
                hasUnknown = true
            }
        }
        return if (hasUnknown) EvaluationResult.Unknown else default
    }

    private fun evalLiteral(literal: Literal): EvaluationResult {
        if (literal.negated) return EvaluationResult.True
        return evalContainsMark(literal.condition)
    }

    private val markEvalCache = hashMapOf<ContainsMark, MarkEvaluationResult>()

    private fun evalContainsMark(condition: ContainsMark): EvaluationResult {
        if (basedFacts.isEmpty()) return EvaluationResult.False

        val conditionBase = condition.position.resolveBaseAp()
        val relevantFacts = basedFacts[conditionBase] ?: return EvaluationResult.Unknown

        val result = markEvalCache.computeIfAbsent(condition) {
            val conditionPosAp = condition.position.resolveAp(conditionBase)

            val evaluatedFact = relevantFacts.firstOrNull {
                it.containsPositionWithTaintMark(conditionPosAp, condition.mark)
            }

            evaluatedFact?.let { EvaluatedFact(it, conditionPosAp, condition.mark) } ?: NoFact
        }

        return when (result) {
            is NoFact -> EvaluationResult.Unknown
            is EvaluatedFact -> {
                hasEvaluatedContainsMark = true
                evaluatedFacts += result

                EvaluationResult.True
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
