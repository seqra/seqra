package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark
import org.opentaint.ir.taint.configuration.TaintActionVisitor

class TaintActionEvaluator(
    internal val positionResolver: PositionResolver<AccessPath>,
) {
    fun evaluate(action: CopyAllMarks, fact: Tainted): Collection<Tainted> {
        val from = positionResolver.resolve(action.from)
        if (from == fact.variable) {
            val to = positionResolver.resolve(action.to)
            return setOf(fact, fact.copy(variable = to))
        }
        return setOf(fact) // TODO: empty of singleton?
        // return emptySet()
    }

    fun evaluate(action: CopyMark, fact: Tainted): Collection<Tainted> {
        if (fact.mark == action.mark) {
            val from = positionResolver.resolve(action.from)
            if (from == fact.variable) {
                val to = positionResolver.resolve(action.to)
                return setOf(fact, fact.copy(variable = to))
            }
        }
        return setOf(fact) // TODO: empty or singleton?
        // return emptySet()
    }

    fun evaluate(action: AssignMark): Tainted {
        val variable = positionResolver.resolve(action.position)
        return Tainted(variable, action.mark)
    }

    fun evaluate(action: RemoveAllMarks, fact: Tainted): Collection<Tainted> {
        val variable = positionResolver.resolve(action.position)
        if (variable == fact.variable) {
            return emptySet()
        }
        return setOf(fact)
    }

    fun evaluate(action: RemoveMark, fact: Tainted): Collection<Tainted> {
        if (fact.mark == action.mark) {
            val variable = positionResolver.resolve(action.position)
            if (variable == fact.variable) {
                return emptySet()
            }
        }
        return setOf(fact)
    }
}

class FactAwareTaintActionEvaluator(
    private val fact: Tainted,
    private val evaluator: TaintActionEvaluator,
) : TaintActionVisitor<Collection<Tainted>> {

    constructor(
        fact: Tainted,
        positionResolver: PositionResolver<AccessPath>,
    ) : this(fact, TaintActionEvaluator(positionResolver))

    override fun visit(action: CopyAllMarks): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: CopyMark): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: AssignMark): Collection<Tainted> {
        return setOf(fact, evaluator.evaluate(action))
    }

    override fun visit(action: RemoveAllMarks): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: RemoveMark): Collection<Tainted> {
        return evaluator.evaluate(action, fact)
    }

    override fun visit(action: Action): Collection<Tainted> {
        error("$this cannot handle $action")
    }
}
