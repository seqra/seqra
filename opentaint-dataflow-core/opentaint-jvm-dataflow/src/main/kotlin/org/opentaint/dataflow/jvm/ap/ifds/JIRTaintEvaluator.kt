package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConditionVisitor
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.util.Maybe
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator.EvaluationResult
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicAtomEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp

class JIRFactAwareConditionEvaluator(
    private val facts: Iterable<FactReader>,
    private val positionResolver: PositionResolver<Maybe<JIRValue>>,
    private val typeChecker: JIRFactTypeChecker,
): FactAwareConditionEvaluator {
    private val positiveAtomEvaluator = AtomEvaluator(negated = false)
    private val negativeAtomEvaluator = AtomEvaluator(negated = true)

    private var hasEvaluatedContainsMark: Boolean = false
    private var assumptionPossible: Boolean = false
    private val evaluatedFacts = mutableListOf<EvaluatedFact>()

    override fun evalWithAssumptionsCheck(condition: Condition): Boolean {
        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false
        val result = evalCondition(condition)

        assumptionPossible = result != EvaluationResult.False && hasEvaluatedContainsMark
        return result == EvaluationResult.True
    }

    override fun assumptionsPossible(): Boolean = assumptionPossible

    override fun facts(): List<InitialFactAp> = evaluatedFacts.map { it.eval() }

    private fun evalCondition(condition: Condition): EvaluationResult = when (condition) {
        is And -> evalAndCondition(condition)
        is Or -> evalOrCondition(condition)
        is Not -> evalNotCondition(condition)
        else -> condition.accept(positiveAtomEvaluator)
    }

    private fun evalAndCondition(condition: And): EvaluationResult {
        var hasUnknown = false
        for (arg in condition.args) {
            val argResult = evalCondition(arg)
            when (argResult) {
                EvaluationResult.False -> return EvaluationResult.False
                EvaluationResult.True -> continue
                EvaluationResult.Unknown -> hasUnknown = true
            }
        }

        if (hasUnknown) return EvaluationResult.Unknown
        return EvaluationResult.True
    }

    private fun evalOrCondition(condition: Or): EvaluationResult {
        var hasUnknown = false
        for (arg in condition.args) {
            val argResult = evalCondition(arg)
            when (argResult) {
                EvaluationResult.False -> continue
                EvaluationResult.True -> return EvaluationResult.True
                EvaluationResult.Unknown -> hasUnknown = true
            }
        }

        if (hasUnknown) return EvaluationResult.Unknown
        return EvaluationResult.False
    }

    private fun evalNotCondition(condition: Not): EvaluationResult {
        val result = condition.arg.accept(negativeAtomEvaluator)
        return when (result) {
            EvaluationResult.True -> EvaluationResult.False
            EvaluationResult.False -> EvaluationResult.True
            EvaluationResult.Unknown -> EvaluationResult.Unknown
        }
    }

    private data class EvaluatedFact(val reader: FactReader, val variable: PositionAccess, val mark: TaintMark) {
        fun eval(): InitialFactAp = reader.createInitialFactWithTaintMark(variable, mark)
    }

    private inner class AtomEvaluator(val negated: Boolean): ConditionVisitor<EvaluationResult> {
        private val basicEvaluator = JIRBasicAtomEvaluator(negated, positionResolver, typeChecker)

        override fun visit(condition: Not): EvaluationResult = error("Non-atomic condition")
        override fun visit(condition: And): EvaluationResult = error("Non-atomic condition")
        override fun visit(condition: Or): EvaluationResult = error("Non-atomic condition")

        override fun visit(condition: ContainsMark): EvaluationResult {
            if (negated) return EvaluationResult.False

            val conditionPosAp = condition.position.resolveAp()

            var hasUnknown = false
            for (fact in facts) {
                val evalResult = evalContainsMark(fact, condition.mark, conditionPosAp)
                if (evalResult) return EvaluationResult.True
                hasUnknown = true
            }

            return if (hasUnknown) EvaluationResult.Unknown else EvaluationResult.False
        }

        private fun evalContainsMark(factReader: FactReader, mark: TaintMark, variable: PositionAccess): Boolean {
            if (factReader.containsPositionWithTaintMark(variable, mark)) {
                evaluatedFacts += EvaluatedFact(factReader, variable, mark)
                hasEvaluatedContainsMark = true
                return true
            }

            return false
        }

        private fun basic(condition: Condition): EvaluationResult {
            val result = condition.accept(basicEvaluator)
            return if (result) EvaluationResult.True else EvaluationResult.False
        }

        override fun visit(condition: TypeMatches): EvaluationResult = basic(condition)
        override fun visit(condition: TypeMatchesPattern): EvaluationResult = basic(condition)
        override fun visit(condition: IsConstant): EvaluationResult = basic(condition)
        override fun visit(condition: ConstantEq): EvaluationResult = basic(condition)
        override fun visit(condition: ConstantLt): EvaluationResult = basic(condition)
        override fun visit(condition: ConstantGt): EvaluationResult = basic(condition)
        override fun visit(condition: ConstantMatches): EvaluationResult = basic(condition)
        override fun visit(condition: ConstantTrue): EvaluationResult = EvaluationResult.True
    }
}
