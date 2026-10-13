package org.opentaint.dataflow.bidi.fixtures

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*

/** N2 oracle: raw accepted leaves, independent bounded matching and per-leaf reference transfer.
 * No subscription accumulator, candidate index, tree selector or production predicate store is read. */
class CumulativeSubscriptionReference(private val universe: BoundedDenotation) {
    fun selects(premise: Pattern, added: Conclusion, restricted: Boolean): Boolean {
        val a = Pattern(added.fact, added.exclusion)
        val pPositions = universe.spatial(premise)
        val aPositions = universe.spatial(a)
        val applicable = universe.marked(premise).containsAll(universe.marked(a)) &&
            (premise.fact.tail !in listOf(Tail.ANY, Tail.ANY_TAINT) || a.fact.tail in listOf(Tail.ANY, Tail.ANY_TAINT))
        if (!restricted) return applicable
        val inside = aPositions.containsAll(pPositions) &&
            universe.markedValues(premise.fact.mark).containsAll(universe.markedValues(a.fact.mark))
        val canonicalFlow = premise.fact.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty &&
            premise.fact.mark == MarkSlot.STAR
        return if (canonicalFlow) pPositions.any { it in aPositions }
        else inside || (premise.fact.mark == MarkSlot.STAR && applicable)
    }

    fun transfer(premise: Pattern, conclusion: Conclusion, added: Conclusion, restricted: Boolean): EdgeOutcome =
        concat(added, summaryEdge(premise, conclusion), edgeDemand = conclusion.demand,
            staticIdentity = false, restricted = restricted)

    private data class OutputScope(val base: AccessPathBase, val layer: Boolean, val component: String)

    /** Keep normal must and exact components separate; observe FLOW as correlated marked-location pairs. */
    fun denotation(premise: Set<Pattern>, values: Collection<Conclusion>): Map<Any, Set<Any>> {
        val result = LinkedHashMap<Any, MutableSet<Any>>()
        for (value in values) {
            val flow = value.fact.mark is MarkSlot.Star
            val component = if (flow) "FLOW" else if (!value.demand && value.fact.tail == Tail.ANY_TAINT) "MUST" else "EXACT_OR_MAY"
            val scope = OutputScope(value.fact.base, value.demand, component)
            val cells = result.getOrPut(scope) { LinkedHashSet() }
            if (flow) for (initial in premise) cells.addAll(universe.pairs(initial, value))
            else cells.addAll(universe.marked(Pattern(value.fact, value.exclusion)))
        }
        return result
    }
}

/** Build only the SUT input. Expected data remains the supplied raw leaves. */
fun cumulativeInput(manager: ApManager, raw: List<Conclusion>): Facts {
    require(raw.isNotEmpty())
    val first = raw.first()
    require(raw.all { it.fact.base == first.fact.base && it.demand == first.demand })
    if (first.fact.base == AccessPathBase.Zero) return Reach.of(if (first.demand) Layer.DEMAND else Layer.NORMAL)
    val layer = if (first.demand) Layer.DEMAND else Layer.NORMAL
    val leaves = raw.map { manager.factsOf(it.fact, it.exclusion, layer) }
    return if (first.fact.mark is MarkSlot.Star) {
        require(raw.all { it.fact.mark == first.fact.mark && it.exclusion == first.exclusion })
        val root = leaves.map { (it as FlowTree).root }.reduce { left, right ->
            FlowAlgebra.mergeAdd(left, right, manager.cancellation)
        }
        manager.flowTree(first.fact.base, layer, first.exclusion, (first.fact.mark as MarkSlot.Star).excluded, root)
    } else {
        val exclusion = raw.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
        require(raw.all { it.fact.tail != Tail.ANY_TAINT || it.exclusion == exclusion })
        val root = leaves.map { (it as TaintTree).root }.reduce { left, right ->
            manager.taintAlg.mergeAdd(left, right, manager.cancellation)
        }
        manager.taintTree(first.fact.base, layer, root, exclusion)
    }
}
