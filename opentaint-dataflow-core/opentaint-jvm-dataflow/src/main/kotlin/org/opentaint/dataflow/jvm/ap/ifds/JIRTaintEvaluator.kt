package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.And
import org.opentaint.ir.taint.configuration.Condition
import org.opentaint.ir.taint.configuration.ContainsMark
import org.opentaint.ir.taint.configuration.Not
import org.opentaint.ir.taint.configuration.Or
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.ap.ifds.FactReader
import org.opentaint.dataflow.ap.ifds.PositionAccess
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.config.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.util.Traits
import org.opentaint.util.Maybe
import org.opentaint.util.onSome

class JIRFactAwareConditionEvaluator(
    traits: Traits<CommonMethod, CommonInst>,
    private val facts: Iterable<FactReader>,
    private val accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    positionResolver: PositionResolver<Maybe<CommonValue>>,
) : JIRBasicConditionEvaluator(traits, positionResolver), FactAwareConditionEvaluator {
    private var hasEvaluatedContainsMark: Boolean = false
    private val evaluatedFacts = mutableListOf<InitialFactAp>()

    override fun evalWithAssumptionsCheck(condition: Condition): Boolean {
        evaluatedFacts.clear()
        hasEvaluatedContainsMark = false
        return condition.accept(this)
    }

    override fun assumptionsPossible(): Boolean = hasEvaluatedContainsMark

    override fun facts(): List<InitialFactAp> = evaluatedFacts.toList()

    // Force evaluation of all branches
    override fun visit(condition: And): Boolean =
        condition.args.map { it.accept(this) }.all { it }

    override fun visit(condition: Or): Boolean =
        condition.args.map { it.accept(this) }.any { it }

    override fun visit(condition: Not): Boolean {
        if (condition.arg is ContainsMark) {
            return true
        }
        return super.visit(condition)
    }

    override fun visit(condition: ContainsMark): Boolean {
        accessPathResolver.resolve(condition.position).onSome { variables ->
            for (variable in variables) {
                for (fact in facts) {
                    if (evalContainsMark(fact, condition.mark, variable)) return true
                }
            }
        }
        return false
    }

    override fun evalContainsMark(factReader: FactReader, mark: TaintMark, variable: PositionAccess): Boolean {
        if (factReader.containsPositionWithTaintMark(variable, mark)) {
            evaluatedFacts += factReader.createInitialFactWithTaintMark(variable, mark)
            hasEvaluatedContainsMark = true
            return true
        }

        return false
    }
}

class JIRFactIgnoreConditionEvaluator(
    traits: JIRTraits,
    positionResolver: PositionResolver<Maybe<CommonValue>>
) : JIRBasicConditionEvaluator(traits, positionResolver) {
    override fun visit(condition: ContainsMark): Boolean {
        return false
    }
}