package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.PathEdge

class SourceAction(val id: ActionId, target: PathFact) {
    val target: PathFact = target.copy(path = ownedList(target.path))
}

/** Conditions and action targets are separate vectors, with no leaf/action product. */
class SourceRuleOccurrence(
    val id: RuleOccurrenceId,
    val predicate: RulePredicate,
    actions: List<SourceAction>,
    val backward: Boolean = false,
) {
    val actions: List<SourceAction> = ownedList(actions)
    init { require(!predicate.isFalse && !predicate.isTrue) { "Constants use the unconditional placement path" } }

    fun reversed(): SourceRuleOccurrence = SourceRuleOccurrence(id, predicate, actions, !backward)

    /** This short-lived transfer view is built only when its leaf and action are used. */
    fun transfer(leaf: Pattern, action: SourceAction, multi: Boolean = false): MicroEdge {
        val forward = PathEdge(leaf.fact, action.target, ExclusionSet.Empty)
        return if (backward) MicroEdge(revOrFail(forward), forward, conjunctive = multi, actionId = action.id)
        else MicroEdge(forward, forward, conjunctive = multi, actionId = action.id)
    }
}
