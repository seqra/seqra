package org.opentaint.ir.analysis.config

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.Maybe
import org.opentaint.ir.analysis.ifds.fmap
import org.opentaint.ir.analysis.ifds.map
import org.opentaint.ir.analysis.taint.Tainted
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.CopyMark
import org.opentaint.ir.taint.configuration.PositionResolver
import org.opentaint.ir.taint.configuration.RemoveAllMarks
import org.opentaint.ir.taint.configuration.RemoveMark

class TaintActionEvaluator(
    private val positionResolver: PositionResolver<Maybe<AccessPath>>,
) {
    fun evaluate(action: CopyAllMarks, fact: Tainted): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.from).map { from ->
            if (from != fact.variable) return@map Maybe.none()
            positionResolver.resolve(action.to).fmap { to ->
                setOf(fact, fact.copy(variable = to))
            }
        }

    fun evaluate(action: CopyMark, fact: Tainted): Maybe<Collection<Tainted>> {
        if (fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.from).map { from ->
            if (from != fact.variable) return@map Maybe.none()
            positionResolver.resolve(action.to).fmap { to ->
                setOf(fact, fact.copy(variable = to))
            }
        }
    }

    fun evaluate(action: AssignMark): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.position).fmap { variable ->
            setOf(Tainted(variable, action.mark))
        }

    fun evaluate(action: RemoveAllMarks, fact: Tainted): Maybe<Collection<Tainted>> =
        positionResolver.resolve(action.position).map { variable ->
            if (variable != fact.variable) return@map Maybe.none()
            Maybe.some(emptySet())
        }

    fun evaluate(action: RemoveMark, fact: Tainted): Maybe<Collection<Tainted>> {
        if (fact.mark != action.mark) return Maybe.none()
        return positionResolver.resolve(action.position).map { variable ->
            if (variable != fact.variable) return@map Maybe.none()
            Maybe.some(emptySet())
        }
    }
}
