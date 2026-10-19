package org.opentaint.dataflow.bidi.engine

/** Unchanged work precedes stored deltas. A repeat set lasts through an entire unchanged burst. */
class DeltaWorklist {
    private val unchanged = ArrayList<EdgeDelta>()
    private data class Seen(val delta: EdgeDelta, val support: GroundingSupport = delta.grounding)
    private val seen = HashSet<Seen>()
    private var zeroUnchanged = 0
    private val zero = ArrayList<EdgeDelta>()
    private val other = ArrayList<EdgeDelta>()

    val isEmpty: Boolean get() = unchanged.isEmpty() && zero.isEmpty() && other.isEmpty()
    val hasZeroWork: Boolean get() = zero.isNotEmpty() || zeroUnchanged != 0
    val size: Int get() = unchanged.size + zero.size + other.size

    fun add(delta: EdgeDelta) {
        if (delta.zeroToZero) zero += delta else other += delta
    }

    fun addUnchanged(delta: EdgeDelta): Boolean {
        if (!seen.add(Seen(delta))) return false
        unchanged += delta
        if (delta.zeroToZero) zeroUnchanged++
        return true
    }

    fun removeNext(): EdgeDelta {
        unchanged.removeLastOrNull()?.let {
            if (it.zeroToZero) zeroUnchanged--
            return it
        }
        return zero.removeLastOrNull() ?: other.removeLast()
    }

    /** Call after processing and successors, including after the final item of a Work event. */
    fun afterProcess() {
        if (unchanged.isEmpty()) seen.clear()
    }

    fun clear() {
        unchanged.clear()
        seen.clear()
        zero.clear()
        other.clear()
        zeroUnchanged = 0
    }
}
