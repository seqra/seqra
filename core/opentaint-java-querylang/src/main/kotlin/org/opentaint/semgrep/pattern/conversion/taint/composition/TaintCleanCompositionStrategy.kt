package org.opentaint.semgrep.pattern.conversion.taint.composition

import org.opentaint.dataflow.configuration.jvm.serialized.PositionBase
import org.opentaint.dataflow.configuration.jvm.serialized.PositionBaseWithModifiers
import org.opentaint.dataflow.configuration.jvm.serialized.PositionModifier
import org.opentaint.semgrep.pattern.Mark
import org.opentaint.semgrep.pattern.conversion.MetavarAtom
import org.opentaint.semgrep.pattern.conversion.TaintRuleStrategy
import org.opentaint.semgrep.pattern.conversion.taint.TaintAutomataEdges
import org.opentaint.semgrep.pattern.conversion.taint.TaintRegisterStateAutomata
import org.opentaint.semgrep.pattern.conversion.taint.TaintRuleGenerationCtx
import org.opentaint.semgrep.pattern.conversion.taint.base
import org.opentaint.semgrep.pattern.conversion.taint.withAnyField

class TaintCleanCompositionStrategy<Item, Cond, Assign, Clean>(
    private val rule: TaintAutomataEdges,
    private val bySideEffect: Boolean,
    private val cleans: Set<Mark.GeneratedMark>,
    val strategy: TaintRuleStrategy<Item, Cond, Assign, Clean>
) : TaintRuleGenerationCtx.CompositionStrategy<Item, Cond, Assign, Clean> {
    override fun stateClean(
        state: TaintRegisterStateAutomata.State,
        stateBefore: TaintRegisterStateAutomata.State,
        varName: MetavarAtom?,
        pos: PositionBaseWithModifiers?
    ): List<Clean>? {
        if (state !in rule.automata.finalAcceptStates) return null

        val cleanerPos = cleanerPositions(pos)

        return cleans.flatMap { c -> cleanerPos.map { strategy.createCleanAction(c, it) } }
    }

    private fun cleanerPositions(pos: PositionBaseWithModifiers?): List<PositionBaseWithModifiers> {
        val cleanerPos = mutableListOf(PositionBase.Result.base())
        if (bySideEffect) {
            cleanerPos += PositionBase.AnyArgument(classifier = "tainted").base()
            cleanerPos += PositionBase.This.base()
        }

        val isStar = pos is PositionBaseWithModifiers.WithModifiers &&
                pos.modifiers.contains(PositionModifier.AnyField)

        // star ($*X): clean the any-field of each cleaner position (Result.*, etc.),
        // on the SAME base as the plain value clean — not the raw metavar position.
        val cleanerEmitPositions = if (isStar) cleanerPos.map { it.withAnyField() } else cleanerPos

        // Also clean the focus metavar's own position (`pos`, e.g. the sanitized argument). A
        // pass-through sanitizer (`clean($C) { return $C }`) carries the argument's taint into its
        // result, but the clean runs on the argument-keyed fact at call-to-start, where the Result
        // position does not yet exist — cleaning only Result misses it. Cleaning the focus position
        // removes the taint on the flow entering the call; it is flow-specific, so a separate use of
        // the same variable outside this call stays tainted. For a star clean `pos` already carries
        // the AnyField modifier, so this stays coherent with the plain-value arm's base.
        val emitPositions = (cleanerEmitPositions + listOfNotNull(pos)).distinct()
        return emitPositions
    }

    override fun stateAccessedMarks(
        state: TaintRegisterStateAutomata.State,
        varName: MetavarAtom
    ): Set<Mark.GeneratedMark>? {
        if (state !in rule.automata.finalAcceptStates) return null
        return cleans
    }
}
