package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.PathNode
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.STATIC
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import org.opentaint.dataflow.bidi.ap.overlap
import org.opentaint.dataflow.util.Cancellation

sealed interface RequestKind {
    data class Mark(val mark: TaintMark) : RequestKind
    data class Position(val path: PathNode) : RequestKind               // a static position, cut to <= 2 accessors
}

/** Standing requests of one method in run 1. Mark and position requests have different positional index keys. */
class RequestStore(private val cancellation: Cancellation? = null) {
    private val seen = HashSet<Pair<InitialAp, RequestKind>>()
    private val index = PathTrie<Pair<InitialAp, RequestKind>>(cancellation)

    fun add(premise: InitialAp, kind: RequestKind): Boolean {
        cancellation?.checkpoint()
        check(premise.tail == Tail.STAR && premise.exclusion == ExclusionSet.Empty && premise.mark == MarkSlot.STAR) {
            "A request needs a policy initial or position answer"
        }
        val request = premise to kind
        if (!seen.add(request)) return false
        when (kind) {
            is RequestKind.Mark -> index.add(premise.base, premise.pathArray, request)
            is RequestKind.Position -> index.add(STATIC, kind.path.toIntArray(), request)
        }
        return true
    }

    /** Every standing request that overlaps a new added leaf, with annotations checked after the path lookup. */
    fun overlapping(a: Pattern): Sequence<Pair<InitialAp, RequestKind>> {
        cancellation?.checkpoint()
        return index.around(a.fact.base, a.fact.path.toIntArray()).asSequence().filter { (initial, kind) ->
            cancellation?.checkpoint()
            val query = when (kind) {
                is RequestKind.Mark -> initial.toPattern()
                is RequestKind.Position -> Pattern(PathFact(STATIC, kind.path.toList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            }
            overlap(a, query)
        }
    }
}
