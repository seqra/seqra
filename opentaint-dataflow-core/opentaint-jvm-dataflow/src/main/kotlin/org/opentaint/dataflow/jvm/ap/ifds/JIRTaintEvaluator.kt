package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Argument
import org.opentaint.dataflow.configuration.jvm.ClassStatic
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ConstantEq
import org.opentaint.dataflow.configuration.jvm.ConstantGt
import org.opentaint.dataflow.configuration.jvm.ConstantLt
import org.opentaint.dataflow.configuration.jvm.ConstantMatches
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.IsConstant
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.Position
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.Result
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.This
import org.opentaint.dataflow.configuration.jvm.TypeMatches
import org.opentaint.dataflow.configuration.jvm.TypeMatchesPattern
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator.EvaluationResult
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.util.Maybe

class JIRFactAwareConditionEvaluator(
    private val facts: Iterable<FactReader>,
    positionResolver: PositionResolver<Maybe<JIRValue>>,
    typeChecker: JIRFactTypeChecker,
): FactAwareConditionEvaluator {
    private val basicEvaluator = JIRBasicConditionEvaluator(positionResolver, typeChecker)

    private var hasEvaluatedContainsMark: Boolean = false
    private var assumptionPossible: Boolean = false
    private val evaluatedFacts = mutableListOf<EvaluatedFact>()

    override fun evalWithAssumptionsCheck(condition: Condition): Boolean {
        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false
        val result = condition.accept(this)

        assumptionPossible = result != EvaluationResult.False && hasEvaluatedContainsMark
        return result == EvaluationResult.True
    }

    override fun assumptionsPossible(): Boolean = assumptionPossible

    override fun facts(): List<InitialFactAp> = evaluatedFacts.map { it.eval() }

    override fun visit(condition: And): EvaluationResult {
        var hasUnknown = false
        for (arg in condition.args) {
            val argResult = arg.accept(this)
            when (argResult) {
                EvaluationResult.False -> return EvaluationResult.False
                EvaluationResult.True -> continue
                EvaluationResult.Unknown -> hasUnknown = true
            }
        }

        if (hasUnknown) return EvaluationResult.Unknown
        return EvaluationResult.True
    }

    override fun visit(condition: Or): EvaluationResult {
        var hasUnknown = false
        for (arg in condition.args) {
            val argResult = arg.accept(this)
            when (argResult) {
                EvaluationResult.False -> continue
                EvaluationResult.True -> return EvaluationResult.True
                EvaluationResult.Unknown -> hasUnknown = true
            }
        }

        if (hasUnknown) return EvaluationResult.Unknown
        return EvaluationResult.False
    }

    override fun visit(condition: Not): EvaluationResult {
        if (condition.arg is ContainsMark) {
            return EvaluationResult.True
        }

        val result = condition.arg.accept(this)
        return when (result) {
            EvaluationResult.True -> EvaluationResult.False
            EvaluationResult.False -> EvaluationResult.True
            EvaluationResult.Unknown -> EvaluationResult.Unknown
        }
    }

    override fun visit(condition: ContainsMark): EvaluationResult {
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

    private data class EvaluatedFact(val reader: FactReader, val variable: PositionAccess, val mark: TaintMark) {
        fun eval(): InitialFactAp = reader.createInitialFactWithTaintMark(variable, mark)
    }
}
