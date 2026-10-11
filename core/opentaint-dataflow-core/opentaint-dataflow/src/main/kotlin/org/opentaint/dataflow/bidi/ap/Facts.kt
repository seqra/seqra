package org.opentaint.dataflow.bidi.ap

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx

enum class Direction { FORWARD, BACKWARD }
enum class Layer { NORMAL, DEMAND }
enum class Tail { STAR, ANY, ANY_TAINT, EXACT }

val Tail.isAny: Boolean get() = this == Tail.ANY || this == Tail.ANY_TAINT
infix fun Layer.or(demand: Boolean): Layer = if (demand) Layer.DEMAND else this

/** Run 1 is forward. A zero limit is available for restricted AP utility checks. */
class ApMode(val run1: Boolean, val direction: Direction, val fieldLimit: Int) {
    val restricted: Boolean get() = !run1

    init {
        require(fieldLimit >= 0) { "The field limit must be nonnegative" }
        require(!run1 || (direction == Direction.FORWARD && fieldLimit >= 1)) {
            "Run 1 must be forward with a field limit of at least one"
        }
    }
}

@JvmInline
value class TaintMark(val id: Int) {
    companion object { val ZERO = TaintMark(0) }
}

/** A canonical immutable set. Public arrays are snapshots of the owned content. */
class MarkSet(ids: IntArray) {
    internal val data: IntArray = ids.sortedDistinct()
    val ids: IntArray get() = data.copyOf()
    private val hash = data.contentHashCode()
    val isEmpty: Boolean get() = data.isEmpty()

    operator fun contains(mark: TaintMark): Boolean = data.binarySearch(mark.id) >= 0
    fun isSubsetOf(other: MarkSet): Boolean = this === other || sortedSubset(data, other.data)
    operator fun plus(other: MarkSet): MarkSet =
        if (other.isSubsetOf(this)) this else MarkSet(sortedUnion(data, other.data))
    operator fun plus(mark: TaintMark): MarkSet =
        if (mark in this) this else MarkSet(sortedUnion(data, intArrayOf(mark.id)))
    operator fun minus(other: MarkSet): MarkSet =
        MarkSet(data.filter { other.data.binarySearch(it) < 0 }.toIntArray())
    fun intersect(other: MarkSet): MarkSet =
        MarkSet(data.filter { other.data.binarySearch(it) >= 0 }.toIntArray())

    override fun equals(other: Any?): Boolean = this === other ||
        (other is MarkSet && hash == other.hash && data.contentEquals(other.data))
    override fun hashCode(): Int = hash
    override fun toString(): String = data.joinToString(prefix = "{", postfix = "}")

    companion object { val EMPTY = MarkSet(IntArray(0)) }
}

sealed interface MarkSlot {
    data class Star(val excluded: MarkSet) : MarkSlot
    data class Concrete(val mark: TaintMark) : MarkSlot
    companion object { val STAR = Star(MarkSet.EMPTY) }
}

/** Only the first accessor of a nonempty continuation is tested against E. */
sealed interface ExclusionSet {
    fun admits(first: AccessorIdx): Boolean
    fun admits(rest: List<AccessorIdx>): Boolean = rest.isEmpty() || admits(rest[0])
    fun union(other: ExclusionSet): ExclusionSet
    fun intersect(other: ExclusionSet): ExclusionSet
    fun isSubsetOf(other: ExclusionSet): Boolean

    data object Empty : ExclusionSet {
        override fun admits(first: AccessorIdx): Boolean = true
        override fun union(other: ExclusionSet): ExclusionSet = other
        override fun intersect(other: ExclusionSet): ExclusionSet = this
        override fun isSubsetOf(other: ExclusionSet): Boolean = true
    }

    class Concrete internal constructor(ids: IntArray) : ExclusionSet {
        internal val data: IntArray = ids.sortedDistinct()
        val ids: IntArray get() = data.copyOf()
        private val hash = data.contentHashCode()

        init { check(data.isNotEmpty()) { "A concrete exclusion must be nonempty" } }

        override fun admits(first: AccessorIdx): Boolean = data.binarySearch(first) < 0
        override fun union(other: ExclusionSet): ExclusionSet =
            if (other is Concrete) of(sortedUnion(data, other.data)) else this
        override fun intersect(other: ExclusionSet): ExclusionSet =
            if (other is Concrete) of(data.filter { other.data.binarySearch(it) >= 0 }.toIntArray()) else Empty
        override fun isSubsetOf(other: ExclusionSet): Boolean =
            other is Concrete && sortedSubset(data, other.data)
        override fun equals(other: Any?): Boolean = this === other ||
            (other is Concrete && hash == other.hash && data.contentEquals(other.data))
        override fun hashCode(): Int = hash
        override fun toString(): String = data.joinToString(prefix = "{", postfix = "}")
    }

    companion object {
        fun of(ids: IntArray): ExclusionSet = if (ids.isEmpty()) Empty else Concrete(ids)
        fun of(accessor: AccessorIdx): ExclusionSet = Concrete(intArrayOf(accessor))
    }
}

val EMPTY_PATH: IntArray = IntArray(0)

internal fun IntArray.sortedDistinct(): IntArray {
    if (isEmpty()) return IntArray(0)
    val result = sortedArray()
    var size = 1
    for (index in 1 until result.size) {
        if (result[index] != result[size - 1]) result[size++] = result[index]
    }
    return if (size == result.size) result else result.copyOf(size)
}

internal fun sortedUnion(a: IntArray, b: IntArray): IntArray {
    val result = IntArray(Math.addExact(a.size, b.size))
    var ai = 0
    var bi = 0
    var size = 0
    while (ai < a.size || bi < b.size) {
        val value = when {
            bi == b.size || (ai < a.size && a[ai] < b[bi]) -> a[ai++]
            ai == a.size || b[bi] < a[ai] -> b[bi++]
            else -> a[ai++].also { bi++ }
        }
        result[size++] = value
    }
    return result.copyOf(size)
}

internal fun sortedSubset(a: IntArray, b: IntArray): Boolean {
    var ai = 0
    var bi = 0
    while (ai < a.size && bi < b.size) {
        when {
            a[ai] == b[bi] -> { ai++; bi++ }
            a[ai] > b[bi] -> bi++
            else -> return false
        }
    }
    return ai == a.size
}

fun IntArray.startsWith(prefix: IntArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
