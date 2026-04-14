package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.configuration.jvm.AssignAction
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.ContainsMarkOnAnyField
import org.opentaint.dataflow.configuration.jvm.PositionAccessor
import org.opentaint.dataflow.configuration.jvm.PositionWithAccess
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationSource
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionExpr
import org.opentaint.dataflow.jvm.ap.ifds.JIRMarkAwareConditionRewriter
import org.opentaint.dataflow.jvm.ap.ifds.removeTrueLiterals
import org.opentaint.dataflow.jvm.ap.ifds.taint.PositionAccess
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintSourceActionPreconditionEvaluator
import org.opentaint.dataflow.jvm.ap.ifds.taint.mkInitialAccessPath
import org.opentaint.dataflow.jvm.ap.ifds.taint.resolveAp
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.util.maybeFlatMap

fun <R: TaintConfigurationSource> evaluateSourceRulePrecondition(
    rule: R,
    sourcePreconditionEvaluator: TaintSourceActionPreconditionEvaluator,
    conditionRewriter: JIRMarkAwareConditionRewriter,
    mkSource: (R, Set<AssignAction>) -> Unit,
    mkPass: (R, Set<AssignAction>, JIRMarkAwareConditionExpr) -> Unit,
) {
    val assignedMarks = rule.actionsAfter.maybeFlatMap {
        sourcePreconditionEvaluator.evaluate(rule, it)
    }
    if (assignedMarks.isNone) return

    val sourceActions = assignedMarks.getOrThrow().mapTo(hashSetOf()) { it.second }

    val simplifiedCondition = conditionRewriter.rewrite(rule.condition)

    val simplifiedExpr = when {
        simplifiedCondition.isFalse -> return
        simplifiedCondition.isTrue -> null
        else -> simplifiedCondition.expr
    }

    // We always treat negated mark condition as satisfied
    val exprWithoutNegations = simplifiedExpr?.removeNegated()
    if (exprWithoutNegations == null) {
        mkSource(rule, sourceActions)
        return
    }

    mkPass(rule, sourceActions, exprWithoutNegations)
}

fun JIRMarkAwareConditionExpr.removeNegated() = removeTrueLiterals { it.negated }

private fun ContainsMark.preconditionFact(apManager: ApManager): InitialFactAp {
    return createPositionWithTaintMark(apManager, position.resolveAp(), mark)
}

private fun ContainsMarkOnAnyField.preconditionFact(apManager: ApManager): InitialFactAp {
    return createPositionWithTaintMark(apManager, PositionAccess.Complex(position.resolveAp(), AnyAccessor), mark)
}

private fun createPositionWithTaintMark(
    apManager: ApManager,
    position: PositionAccess,
    mark: TaintMark,
): InitialFactAp {
    val positionWithMark = PositionAccess.Complex(position, TaintMarkAccessor(mark.name))
    val finalPositionWithMark = PositionAccess.Complex(positionWithMark, FinalAccessor)
    return createPosition(apManager, finalPositionWithMark)
}

private fun createPosition(apManager: ApManager, position: PositionAccess): InitialFactAp {
    var normalizedPosition = position
    if (position is PositionAccess.Complex && position.accessor is FinalAccessor) {
        // mkInitialAccessPath starts with final ap
        normalizedPosition = position.base
    }
    return apManager.mkInitialAccessPath(normalizedPosition, ExclusionSet.Universe)
}

data class PreconditionCube(val facts: Set<InitialFactAp>)

fun JIRMarkAwareConditionExpr.preconditionDnf(
    apManager: ApManager,
    mapFacts: (InitialFactAp) -> List<InitialFactAp>,
): List<PreconditionCube> = when (this) {
    is JIRMarkAwareConditionExpr.ContainsMarkLiteral -> {
        val preconditionFact = condition.preconditionFact(apManager)
        mapFacts(preconditionFact).map { PreconditionCube(setOf(it)) }
    }

    is JIRMarkAwareConditionExpr.ContainsMarkOnAnyFieldLiteral -> {
        val preconditionFact = condition.preconditionFact(apManager)
        mapFacts(preconditionFact).map { PreconditionCube(setOf(it)) }
    }

    is JIRMarkAwareConditionExpr.Or -> args.flatMap { it.preconditionDnf(apManager, mapFacts) }
    is JIRMarkAwareConditionExpr.And -> {
        val result = mutableListOf<PreconditionCube>()
        val cubeLists = args.map { it.preconditionDnf(apManager, mapFacts) }
        cubeLists.cartesianProductMapTo { cubes ->
            val facts = hashSetOf<InitialFactAp>()
            cubes.flatMapTo(facts) { it.facts }
            result += PreconditionCube(facts)
        }
        result
    }
}
