package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx

/** Raw reference geometry also admits abstract exact values outside typed Facts. */
data class PathFact(val base: AccessPathBase, val path: List<AccessorIdx>, val tail: Tail, val mark: MarkSlot)
data class Pattern(val fact: PathFact, val exclusion: ExclusionSet)

data class Conclusion(val fact: PathFact, val exclusion: ExclusionSet, val demand: Boolean) {
    init {
        check(fact.tail != Tail.STAR || (!demand && fact.mark is MarkSlot.Star)) {
            "W2: a STAR conclusion needs an abstract mark and NORMAL layer"
        }
        check(fact.tail != Tail.ANY || demand) { "W6: a may conclusion must be DEMAND" }
        check(fact.tail != Tail.ANY_TAINT || (!demand && fact.mark is MarkSlot.Concrete)) {
            "W8: a must conclusion needs a concrete mark and NORMAL layer"
        }
        check(exclusion == ExclusionSet.Empty || (!demand && (fact.tail == Tail.STAR || fact.tail == Tail.ANY_TAINT))) {
            "Only a normal STAR or must conclusion can exclude fields"
        }
    }
}
