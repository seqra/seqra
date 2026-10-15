package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.opentaint.dataflow.bidi.ap.MarkSet
import org.opentaint.dataflow.bidi.ap.TaintMark

/** Owned request marks. Temporary versions never enter the AP manager's strong tables. */
interface RequestMarkSelection {
    fun containsId(id: Int): Boolean
    fun cursor(): RequestMarkCursor
}

interface RequestMarkCursor {
    val isComplete: Boolean
    fun step(budget: Int, visit: (TaintMark) -> Unit): Int
}

class RequestMarkSet private constructor(private val data: IntArray, private val hash: Int = data.contentHashCode()) : RequestMarkSelection {
    val size: Int get() = data.size
    val isEmpty: Boolean get() = data.isEmpty()
    val primitiveCapacity: Int get() = data.size

    fun idAt(index: Int): Int = data[index]
    fun markAt(index: Int): TaintMark = TaintMark(data[index])
    override fun containsId(id: Int): Boolean = data.binarySearch(id) >= 0
    override fun cursor(): RequestMarkCursor = object : RequestMarkCursor {
        private var offset = 0
        override val isComplete: Boolean get() = offset == data.size
        override fun step(budget: Int, visit: (TaintMark) -> Unit): Int {
            require(budget > 0)
            var used = 0
            while (used < budget && offset < data.size) { visit(TaintMark(data[offset++])); used++ }
            return used
        }
    }
    operator fun contains(mark: TaintMark): Boolean = containsId(mark.id)
    fun forEachId(visit: (Int) -> Unit) { for (id in data) visit(id) }
    fun forEachMark(visit: (TaintMark) -> Unit) { for (id in data) visit(TaintMark(id)) }

    /** Share an unchanged immutable set, including the common empty-exclusion case. */
    operator fun minus(excluded: MarkSet): RequestMarkSet {
        if (isEmpty || excluded.isEmpty) return this
        val result = IntArray(size)
        var count = 0
        var other = 0
        for (id in data) {
            while (other < excluded.data.size && excluded.data[other] < id) other++
            if (other == excluded.data.size || excluded.data[other] != id) result[count++] = id
        }
        return if (count == size) this else owned(result, count)
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is RequestMarkSet && hash == other.hash && data.contentEquals(other.data)
    override fun hashCode(): Int = hash
    override fun toString(): String = data.joinToString(prefix = "{", postfix = "}")

    companion object {
        @JvmField val EMPTY = RequestMarkSet(IntArray(0))
        fun of(mark: TaintMark): RequestMarkSet = RequestMarkSet(intArrayOf(mark.id))
        fun fromIds(ids: IntArray): RequestMarkSet {
            if (ids.isEmpty()) return EMPTY
            val sorted = ids.sortedArray()
            var count = 1
            for (index in 1 until sorted.size) if (sorted[index] != sorted[count - 1]) sorted[count++] = sorted[index]
            return owned(sorted, count)
        }
        private fun owned(ids: IntArray, size: Int): RequestMarkSet = when {
            size == 0 -> EMPTY
            size == ids.size -> RequestMarkSet(ids)
            else -> RequestMarkSet(ids.copyOf(size))
        }
        internal fun ownedSorted(ids: IntArray, hash: Int): RequestMarkSet = if (ids.isEmpty()) EMPTY else RequestMarkSet(ids, hash)
    }
}

/** One writer owns the mutable frontier; build/takeSnapshot detach immutable primitive storage. */
class RequestMarkSetBuilder {
    private class IdSet : IntOpenHashSet() {
        val capacity: Int get() = key.size
        val cells: IntArray get() = key
        val hasZero: Boolean get() = containsNull
    }
    private var values: IdSet? = null
    val size: Int get() = values?.size ?: 0
    val isEmpty: Boolean get() = size == 0
    val primitiveCapacity: Int get() = values?.capacity ?: 0
    fun add(mark: TaintMark): Boolean = addId(mark.id)
    fun addId(id: Int): Boolean = (values ?: IdSet().also { values = it }).add(id)
    operator fun contains(mark: TaintMark): Boolean = values?.contains(mark.id) == true
    fun remove(mark: TaintMark): Boolean = values?.remove(mark.id) == true
    fun addAll(marks: RequestMarkSet) { marks.forEachId { addId(it) } }
    fun build(): RequestMarkSet = values?.let { RequestMarkSet.fromIds(it.toIntArray()) } ?: RequestMarkSet.EMPTY
    fun takeSnapshot(): RequestMarkSet = build().also { clear() }
    /** Detach first: later builder additions cannot change the cursor's backing table. */
    fun snapshotCursor(): RequestMarkSnapshotCursor {
        val captured = values
        values = null
        return RequestMarkSnapshotCursor(captured?.cells ?: IntArray(0), captured?.hasZero == true, captured?.size ?: 0)
    }
    fun clear() { values = null }
}

/** Bounded primitive copy and signed radix sort. Every examined table/member/count cell uses one unit. */
class RequestMarkSnapshotCursor internal constructor(
    private var cells: IntArray,
    hasZero: Boolean,
    size: Int,
) {
    private var source = IntArray(size)
    private var scratch = IntArray(if (size > 1) size else 0)
    private val counts = IntArray(if (size > 1) 256 else 0)
    private var offset = 0
    private var copied = if (hasZero) 1 else 0
    private var pass = 0
    private var phase = if (size == 0) 4 else 0 // copy, count, prefix, scatter, done, clear, hash
    private var total = 0
    private var hash = 1
    private var completed: RequestMarkSet? = if (size == 0) RequestMarkSet.EMPTY else null
    val isComplete: Boolean get() = phase == 4
    val primitiveCapacity: Int get() = cells.size + source.size + scratch.size + counts.size
    val result: RequestMarkSet get() = checkNotNull(completed) { "The mark snapshot is not complete" }

    fun step(budget: Int): Int {
        require(budget > 0)
        var used = 0
        while (used < budget && !isComplete) {
            when (phase) {
                0 -> if (offset < cells.size) {
                    val id = cells[offset++]
                    if (id != 0) source[copied++] = id
                    used++
                } else {
                    check(copied == source.size)
                    cells = IntArray(0)
                    phase = if (source.size == 1) 6 else 1; offset = 0
                }
                1 -> if (offset < source.size) {
                    counts[digit(source[offset++])]++
                    used++
                } else { phase = 2; offset = 0; total = 0 }
                2 -> if (offset < counts.size) {
                    val count = counts[offset]
                    counts[offset++] = total
                    total += count
                    used++
                } else { phase = 3; offset = 0 }
                3 -> if (offset < source.size) {
                    val id = source[offset++]
                    scratch[counts[digit(id)]++] = id
                    used++
                } else {
                    val old = source; source = scratch; scratch = old
                    pass++
                    if (pass == 4) {
                        scratch = IntArray(0)
                        phase = 6; offset = 0
                    } else {
                        phase = 5; offset = 0
                    }
                }
                5 -> if (offset < counts.size) { counts[offset++] = 0; used++ }
                    else { phase = 1; offset = 0 }
                6 -> if (offset < source.size) { hash = hash * 31 + source[offset++]; used++ }
                    else { completed = RequestMarkSet.ownedSorted(source, hash); phase = 4 }
            }
        }
        return used
    }

    private fun digit(id: Int): Int = ((id xor Int.MIN_VALUE) ushr (pass * 8)) and 255
}
