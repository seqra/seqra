package org.opentaint.dataflow.taint

import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.taint.TaintMarkAwareConditionExpr.Literal

class TaintFactAwareConditionEvaluator(
    facts: List<FactReader>,
    private val markAfterAnyAccessorResolver: FactWithMarkAfterAnyAccessorResolver?
) : FactAwareConditionEvaluator {
    private val basedFacts = facts.groupByTo(hashMapOf()) { it.base }

    private var hasEvaluatedContainsMark: Boolean = false
    private var remainingExpr: TaintMarkAwareConditionExpr? = null
    private val evaluatedFacts = mutableListOf<EvaluatedFact>()

    override fun evalWithAssumptionsCheck(condition: TaintMarkAwareConditionExpr): Boolean {
        if (basedFacts.isEmpty()) return false

        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false

        remainingExpr = condition.removeTrueLiterals {
            evalLiteral(it)
        }

        return remainingExpr == null
    }

    override fun assumptionExpr(): TaintMarkAwareConditionExpr? =
        remainingExpr?.takeIf { hasEvaluatedContainsMark }

    override fun facts(): List<InitialFactAp> = evaluatedFacts.map { it.eval() }

    private fun evalLiteral(literal: Literal): Boolean {
        if (literal.negated) return true

        return when (literal) {
            is TaintMarkAwareConditionExpr.ContainsMarkLiteral -> evalContainsMark(literal.position, literal.mark)
            is TaintMarkAwareConditionExpr.ContainsMarkOnAnyAccessorLiteral -> evalContainsMarkOnAnyField(literal.position, literal.mark)
        }
    }

    private val anyFieldMarkEvalCache = hashMapOf<Pair<PositionAccess, TaintMarkAccessor>, MarkEvaluationResult>()

    private fun evalContainsMarkOnAnyField(positionAccess: PositionAccess, mark: TaintMarkAccessor): Boolean {
        val conditionBase = positionAccess.base()
        val relevantFacts = basedFacts[conditionBase] ?: return false

        val result = anyFieldMarkEvalCache.computeIfAbsent(positionAccess to mark) {
            val evaluatedFact = containsMarkOnAnyField(positionAccess, mark, relevantFacts)

            if (evaluatedFact != null) {
                evaluatedFact
            } else {
                markAfterAnyAccessorResolver?.resolve(mark)
                NoFact
            }
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

    private fun containsMarkOnAnyField(
        positionAccess: PositionAccess,
        mark: TaintMarkAccessor,
        relevantFacts: List<FactReader>
    ): EvaluatedFact? {
        val requiredPosition = positionAccess.withSuffix(listOf(mark))

        for (reader in relevantFacts) {
            val positionWithTaintMark = reader.containsAnyPosition(requiredPosition) ?: continue

            val finalPositionWithTaintMark = positionWithTaintMark.withSuffix(listOf(FinalAccessor))
            if (!reader.containsPosition(finalPositionWithTaintMark)) continue

            val tmPosition = positionWithTaintMark.removeSuffix(listOf(mark))
            return EvaluatedFact(reader, tmPosition, mark)
        }
        return null
    }

    private val markEvalCache = hashMapOf<Pair<PositionAccess, TaintMarkAccessor>, MarkEvaluationResult>()

    private fun evalContainsMark(positionAccess: PositionAccess, mark: TaintMarkAccessor): Boolean {
        val conditionBase = positionAccess.base()
        val relevantFacts = basedFacts[conditionBase] ?: return false

        val result = markEvalCache.computeIfAbsent(positionAccess to mark) {
            val evaluatedFact = relevantFacts.firstOrNull {
                it.containsPositionWithTaintMark(positionAccess, mark)
            }

            evaluatedFact?.let { EvaluatedFact(it, positionAccess, mark) } ?: NoFact
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
        val reader: FactReader, val variable: PositionAccess, val mark: TaintMarkAccessor
    ): MarkEvaluationResult {
        fun eval(): InitialFactAp = reader.createInitialFactWithTaintMark(variable, mark)
    }

    private data object NoFact: MarkEvaluationResult
}
