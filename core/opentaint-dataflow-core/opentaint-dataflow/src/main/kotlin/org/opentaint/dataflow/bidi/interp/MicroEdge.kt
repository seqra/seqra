package org.opentaint.dataflow.bidi.interp

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.bidi.ap.ExclusionSet
import org.opentaint.dataflow.bidi.ap.MarkSlot
import org.opentaint.dataflow.bidi.ap.PathEdge
import org.opentaint.dataflow.bidi.ap.PathFact
import org.opentaint.dataflow.bidi.ap.Pattern
import org.opentaint.dataflow.bidi.ap.Tail
import org.opentaint.dataflow.bidi.ap.TaintMark
import java.util.Collections

/** A directed edge retains its forward object for source and may classification. */
class MicroEdge(
    val edge: PathEdge,
    val forward: PathEdge,
    val conjunctive: Boolean = false,
    val actionId: ActionId? = null,
) {
    init {
        check(forward.from.tail != Tail.ANY_TAINT) { "I14: a forward micro edge cannot have a must premise" }
        check(edge == forward || (edge.from.tail != Tail.ANY_TAINT && edge.to.tail != Tail.ANY_TAINT)) {
            "A1: a reversed micro edge cannot have a must side"
        }
    }

    val isSource: Boolean
        get() = forward.from.base == AccessPathBase.Zero && forward.to.base != AccessPathBase.Zero
    val isIdentity: Boolean
        get() = edge.from == edge.to && edge.exclusion == ExclusionSet.Empty
    val may: Boolean get() = forward.to.tail == Tail.ANY || conjunctive

    companion object { fun of(e: PathEdge): MicroEdge = MicroEdge(e, e) }
}

val ZERO_FACT: PathFact = PathFact(AccessPathBase.Zero, emptyList(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO))
val ZERO_PATTERN: Pattern = Pattern(ZERO_FACT, ExclusionSet.Empty)
val ZERO_KEEP: PathEdge = PathEdge(ZERO_FACT, ZERO_FACT, ExclusionSet.Empty)

/** Helper-created facts own their paths; supplied MicroEdge objects keep their original identities. */
fun starSide(b: AccessPathBase, p: List<AccessorIdx> = emptyList()): PathFact =
    PathFact(b, Collections.unmodifiableList(ArrayList(p)), Tail.STAR, MarkSlot.STAR)

fun keepEdge(b: AccessPathBase, p: List<AccessorIdx> = emptyList(), e: ExclusionSet = ExclusionSet.Empty): PathEdge {
    val side = starSide(b, p)
    return PathEdge(side, side, e)
}

/** Each proper prefix keeps every continuation except the next step of the written path. */
fun strongKeep(b: AccessPathBase, path: List<AccessorIdx>): List<PathEdge> =
    Collections.unmodifiableList(path.indices.map { k -> keepEdge(b, path.take(k), ExclusionSet.of(path[k])) })

fun starEdge(from: AccessPathBase, fp: List<AccessorIdx>, to: AccessPathBase, tp: List<AccessorIdx>): PathEdge =
    PathEdge(starSide(from, fp), starSide(to, tp), ExclusionSet.Empty)
