package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint

typealias MethodKey = MethodEntryPoint

sealed interface PremiseKey {
    val size: Int
    fun member(k: Int): InitialAp
    val isZero: Boolean
    val nonZeroCount: Int
}

val PremiseKey.members: List<InitialAp> get() = List(size, ::member)
inline fun PremiseKey.forEachMember(body: (InitialAp) -> Unit) {
    for (index in 0 until size) body(member(index))
}
val PremiseKey.isFlow: Boolean get() = this is InitialAp && mark is MarkSlot.Star

/** Equality is identity after interning. Mode-specific must checks belong to the run. */
class InitialAp internal constructor(
    val base: AccessPathBase,
    val path: PathNode?,
    val tail: Tail,
    val exclusion: ExclusionSet,
    val mark: MarkSlot,
    @JvmField val id: Int,
    @JvmField internal val manager: ApManager,
) : PremiseKey {
    init {
        check(base !is AccessPathBase.CleanerTemporary) { "A field-action temporary cannot be a premise" }
        check(mark !is MarkSlot.Star || mark.excluded.isEmpty) { "A premise cannot exclude marks" }
        check(tail == Tail.STAR || tail == Tail.ANY_TAINT || exclusion == ExclusionSet.Empty) {
            "Only a STAR or must premise can exclude fields"
        }
        check(tail != Tail.EXACT || mark is MarkSlot.Concrete) { "S8: an exact premise needs a concrete mark" }
        check(tail != Tail.ANY_TAINT || mark is MarkSlot.Concrete) { "W8: a must premise needs a concrete mark" }
        check(base != AccessPathBase.Zero || (path == null && tail == Tail.EXACT &&
            exclusion == ExclusionSet.Empty && mark == MarkSlot.Concrete(TaintMark.ZERO))) {
            "The zero base has only the canonical zero fact"
        }
    }

    internal val pathData: IntArray = path?.toIntArray() ?: EMPTY_PATH
    val pathArray: IntArray get() = pathData.copyOf()
    override val size: Int get() = 1
    override fun member(k: Int): InitialAp = also { check(k == 0) }
    override val isZero: Boolean get() = base == AccessPathBase.Zero
    override val nonZeroCount: Int get() = if (isZero) 0 else 1
    fun toPattern(): Pattern = Pattern(PathFact(base, pathArray.asList(), tail, mark), exclusion)
}

class PremiseSet internal constructor(members: Array<InitialAp>) : PremiseKey {
    private val data = members.copyOf()
    val members: Array<InitialAp> get() = data.copyOf()

    init {
        check(data.size >= 2)
        check(data.none { it.isZero }) { "A multiple-member premise cannot include zero" }
        check(data.all { it.mark is MarkSlot.Concrete }) { "Every multiple-member premise must be concrete" }
        check((1 until data.size).all { data[it - 1].id < data[it].id }) {
            "Premise members must be sorted and unique"
        }
    }

    override val size: Int get() = data.size
    override fun member(k: Int): InitialAp = data[k]
    override val isZero: Boolean get() = false
    override val nonZeroCount: Int get() = data.size
}
