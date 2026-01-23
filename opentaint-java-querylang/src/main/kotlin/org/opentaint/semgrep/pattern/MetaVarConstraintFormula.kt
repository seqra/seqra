package org.opentaint.semgrep.pattern

import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.And
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.Companion.mkAnd
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.Companion.mkOr
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.Constraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.NegatedConstraint
import org.opentaint.semgrep.pattern.MetaVarConstraintFormula.Or
import org.opentaint.semgrep.pattern.conversion.cartesianProductMapTo

sealed interface MetaVarConstraintFormula<C> {
    sealed interface Literal<C> : MetaVarConstraintFormula<C> {
        val constraint: C
    }

    data class Constraint<C>(override val constraint: C) : Literal<C>
    data class NegatedConstraint<C>(override val constraint: C) : Literal<C>

    data class And<C>(val args: Set<MetaVarConstraintFormula<C>>) : MetaVarConstraintFormula<C>
    data class Or<C>(val args: Set<MetaVarConstraintFormula<C>>) : MetaVarConstraintFormula<C>

    companion object {
        fun <C> mkNot(c: MetaVarConstraintFormula<C>) = c.toNnf(negated = true)

        fun <C> mkAnd(c: Set<MetaVarConstraintFormula<C>>) = when (c.size) {
            1 -> c.first()
            else -> And(c)
        }

        fun <C> mkOr(c: Set<MetaVarConstraintFormula<C>>) = when (c.size) {
            1 -> c.first()
            else -> Or(c)
        }
    }
}

fun <C, R> MetaVarConstraintFormula<C>.transform(
    mapper: (C) -> R
): MetaVarConstraintFormula<R> = transformLiteral { mapper(it.constraint) }

fun <C, R> MetaVarConstraintFormula<C>.transformLiteral(
    mapper: (MetaVarConstraintFormula.Literal<C>) -> R
): MetaVarConstraintFormula<R> = flatMap {
    when (it) {
        is Constraint<C> -> Constraint(mapper(it))
        is NegatedConstraint<C> -> NegatedConstraint(mapper(it))
    }
}

fun <C, R> MetaVarConstraintFormula<C>.flatMap(
    mapper: (MetaVarConstraintFormula.Literal<C>) -> MetaVarConstraintFormula<R>
): MetaVarConstraintFormula<R> = when (this) {
    is Constraint -> mapper(this)
    is NegatedConstraint -> mapper(this)
    is And -> mkAnd(args.mapTo(hashSetOf()) { it.flatMap(mapper) })
    is Or -> mkOr(args.mapTo(hashSetOf()) { it.flatMap(mapper) })
}

data class MetaVarConstraintFormulaCube<C>(
    val positive: Set<Constraint<C>>,
    val negative: Set<NegatedConstraint<C>>,
)

private fun <C> MetaVarConstraintFormula<C>.toNnf(
    negated: Boolean
): MetaVarConstraintFormula<C> = when (this) {
    is And<C> -> if (!negated) {
        mkAnd(args.mapTo(hashSetOf()) { it.toNnf(negated = false) })
    } else {
        mkOr(args.mapTo(hashSetOf()) { it.toNnf(negated = true) })
    }

    is Or<C> -> if (!negated) {
        mkOr(args.mapTo(hashSetOf()) { it.toNnf(negated = false) })
    } else {
        mkAnd(args.mapTo(hashSetOf()) { it.toNnf(negated = true) })
    }

    is Constraint<C> -> if (negated) NegatedConstraint(constraint) else this
    is NegatedConstraint<C> -> if (negated) Constraint(constraint) else this
}

fun <C> MetaVarConstraintFormula<C>.toDNF(): Set<MetaVarConstraintFormulaCube<C>> =
    toDNFUtil().toHashSet()

private fun <C> MetaVarConstraintFormula<C>.toDNFUtil(): List<MetaVarConstraintFormulaCube<C>> = when (this) {
    is Constraint<C> -> listOf(MetaVarConstraintFormulaCube(setOf(this), emptySet()))
    is NegatedConstraint<C> -> listOf(MetaVarConstraintFormulaCube(emptySet(), setOf(this)))
    is Or<C> -> args.flatMap { it.toDNFUtil() }
    is And<C> -> {
        val dnfChildren = args.map { it.toDNFUtil() }
        val resultCubes = mutableListOf<MetaVarConstraintFormulaCube<C>>()
        dnfChildren.cartesianProductMapTo { cubes ->
            val positive = hashSetOf<Constraint<C>>()
            val negative = hashSetOf<NegatedConstraint<C>>()
            cubes.forEach {
                positive.addAll(it.positive)
                negative.addAll(it.negative)
            }
            resultCubes += MetaVarConstraintFormulaCube(positive, negative)
        }
        resultCubes
    }
}
