package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.And
import org.opentaint.dataflow.configuration.jvm.Condition
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.Not
import org.opentaint.dataflow.configuration.jvm.Or
import org.opentaint.dataflow.configuration.jvm.PositionResolver
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactAwareConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.FactReader
import org.opentaint.dataflow.jvm.ap.ifds.taint.JIRBasicConditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.util.Maybe
import org.opentaint.util.onSome

class JIRFactAwareConditionEvaluator(
    private val facts: Iterable<FactReader>,
    private val accessPathResolver: PositionResolver<Maybe<List<PositionAccess>>>,
    positionResolver: PositionResolver<Maybe<JIRValue>>,
) : JIRBasicConditionEvaluator(positionResolver), FactAwareConditionEvaluator {
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
