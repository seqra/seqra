package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import java.util.IdentityHashMap

/** Raw exit conclusions and selected publications are separate collections. */
class RunSummaryStore(private val m: ApManager) {
    private val groups = IdentityHashMap<PremiseKey, ConclusionGroup>()
    private val demand = IdentityHashMap<PremiseKey, ConclusionGroup>()
    private val interners = m.newInterners()

    /** Raw delta. R1 persistence reads this collection at the barrier. */
    fun add(premise: PremiseKey, g: Facts): Facts? {
        m.cancellation.checkpoint()
        checkShape(premise, g)
        return groups.getOrPut(premise) { ConclusionGroup(m, interners) }.add(g)
    }

    /** Publication delta. The caller has selected the raw group before restriction. */
    fun addDemand(premise: PremiseKey, piece: Facts): Facts? {
        m.cancellation.checkpoint()
        checkShape(premise, piece)
        return demand.getOrPut(premise) { ConclusionGroup(m, interners) }.add(piece)
    }

    fun all(): Sequence<Pair<PremiseKey, Facts>> = entries(groups)
    fun demandEdges(): Sequence<Pair<PremiseKey, Facts>> = entries(demand)

    private fun checkShape(premise: PremiseKey, facts: Facts) {
        check(facts.base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot enter a summary" }
        check((facts is FlowTree) == premise.isFlow) { "K6: the conclusion kind follows the premise" }
        check(premise !is PremiseSet || facts is TaintTree) { "A multi-premise summary must be TAINT" }
    }

    private fun entries(source: IdentityHashMap<PremiseKey, ConclusionGroup>): Sequence<Pair<PremiseKey, Facts>> = sequence {
        m.cancellation.checkpoint()
        for ((premise, group) in source) {
            m.cancellation.checkpoint()
            for (facts in group.all()) {
                m.cancellation.checkpoint()
                yield(premise to facts)
            }
        }
    }
}
