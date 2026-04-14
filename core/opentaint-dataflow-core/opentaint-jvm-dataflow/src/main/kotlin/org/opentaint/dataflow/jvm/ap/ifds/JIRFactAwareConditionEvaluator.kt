package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.ContainsMarkOnAnyField
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr.Literal
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRFactWithMarkAfterAnyFieldResolver
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.removeSuffix
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveBaseAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.withSuffix

class JIRFactAwareConditionEvaluator(
    facts: List<FactReader>,
    private val markAfterAnyFieldResolver: JIRFactWithMarkAfterAnyFieldResolver?
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

        return when (literal) {
            is JIRMarkAwareConditionExpr.ContainsMarkLiteral -> evalContainsMark(literal.condition)
            is JIRMarkAwareConditionExpr.ContainsMarkOnAnyFieldLiteral -> evalContainsMarkOnAnyField(literal.condition)
        }
    }

    private fun evalContainsMarkOnAnyField(condition: ContainsMarkOnAnyField): Boolean {
        val conditionBase = condition.position.resolveBaseAp()
        val relevantFacts = basedFacts[conditionBase] ?: return false

        val conditionPosAp = condition.position.resolveAp(conditionBase)

        val tmAccessor = TaintMarkAccessor(condition.mark.name)

        val requiredPosition = conditionPosAp.withSuffix(listOf(tmAccessor))
        for (reader in relevantFacts) {
            val positionWithTaintMark = reader.containsAnyPosition(requiredPosition) ?: continue

            val finalPositionWithTaintMark = positionWithTaintMark.withSuffix(listOf(FinalAccessor))
            if (!reader.containsPosition(finalPositionWithTaintMark)) continue

            val tmPosition = positionWithTaintMark.removeSuffix(listOf(tmAccessor))

            hasEvaluatedContainsMark = true
            evaluatedFacts += EvaluatedFact(reader, tmPosition, condition.mark)

            return true
        }

        markAfterAnyFieldResolver?.resolve(tmAccessor)

        return false
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
