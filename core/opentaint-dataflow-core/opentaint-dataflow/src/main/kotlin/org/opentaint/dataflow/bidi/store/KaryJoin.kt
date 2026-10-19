package org.opentaint.dataflow.bidi.store

import org.opentaint.dataflow.util.Cancellation

/** One owning writer; the last new slot value completes each product exactly once. */
class KaryJoin<T : Any>(val arity: Int, private val cancellation: Cancellation? = null) {
    init { require(arity >= 2) { "A k-ary join needs at least two slots" } }
    private val slots = Array(arity) { LinkedHashSet<T>() }
    private var missing = arity

    internal val memberCount: Int get() = slots.sumOf { it.size }

    fun add(slot: Int, value: T): List<List<T>> {
        cancellation?.checkpoint()
        require(slot in 0 until arity) { "A literal index must be within the join's arity" }
        val values = slots[slot]
        val wasEmpty = values.isEmpty()
        if (!values.add(value)) return emptyList()
        if (wasEmpty) missing--
        return products(slot, value)
    }

    /** Refresh metadata on one accepted relation without retaining its old version. */
    fun replace(slot: Int, previous: T, value: T): List<List<T>> {
        cancellation?.checkpoint()
        require(slot in 0 until arity)
        check(slots[slot].remove(previous)) { "Only a standing member can be refreshed" }
        check(slots[slot].add(value)) { "A replacement keeps one semantic member" }
        return products(slot, value)
    }

    private fun products(slot: Int, value: T): List<List<T>> {
        if (missing != 0) return emptyList()

        val choices = Array<List<T>>(arity) { index ->
            cancellation?.checkpoint()
            if (index == slot) listOf(value) else slots[index].toList()
        }
        val positions = IntArray(arity)
        val out = ArrayList<List<T>>()
        while (true) {
            cancellation?.checkpoint()
            val tuple = ArrayList<T>(arity)
            for (index in 0 until arity) {
                cancellation?.checkpoint()
                tuple += choices[index][positions[index]]
            }
            out += tuple
            var index = arity - 1
            while (index >= 0) {
                cancellation?.checkpoint()
                positions[index]++
                if (positions[index] < choices[index].size) break
                positions[index] = 0
                index--
            }
            if (index < 0) return out
        }
    }
}
