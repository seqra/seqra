package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ByteArrayKey
import org.opentaint.ir.api.storage.asComparable
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable
import org.opentaint.ir.util.ByteArrayBuilder
import kotlin.math.min

internal fun List<Pair<Long, ByteArray>>.toAttributesImmutable(): AttributesImmutable {
    if (isEmpty()) {
        return EmptyAttributesImmutable
    }

    val values = ByteArrayBuilder()
    val instanceIds = LongArray(size)
    val offsetAndLens = LongArray(size)
    val differentValues = hashMapOf<ByteArrayKey, Long>()

    var offset = 0

    forEachIndexed { i, (instanceId, value) ->
        instanceIds[i] = instanceId
        val valueKey = value.asComparable()
        var indexValue = differentValues[valueKey]
        if (indexValue == null) {
            val len = value.size
            values.append(value)
            indexValue = (len.toLong() shl 32) + offset
            differentValues[valueKey] = indexValue
            offset += len
        }
        offsetAndLens[i] = indexValue
    }

    return AttributesImmutable(values.toByteArray(), instanceIds, offsetAndLens)
}

internal open class AttributesImmutable(
    private val values: ByteArray,
    instanceIds: LongArray,
    private val offsetAndLens: LongArray
) {

    private val instanceIdCollection = instanceIds.toInstanceIdCollection(sorted = true)
    private val sortedByValue: Boolean // `true` if order of instance ids is the same as the one sorted by value
    private val sortedByValueInstanceIds by lazy {
        // NB!
        // We need stable sorting here, and java.util.Collections.sort() guarantees the sort is stable
        instanceIdCollection.asIterable()
            .sortedBy { get(it)!!.asComparable() }.toLongArray().toInstanceIdCollection(sorted = false)
    }

    init {
        var sortedByValue = true
        var prevId = Long.MIN_VALUE
        var prevValue: ByteArrayKey? = null
        for (i in instanceIds.indices) {
            // check if instanceIds are sorted in ascending order and there are no duplicates
            val currentId = instanceIds[i]
            if (prevId >= currentId) {
                error("AttributesImmutable: instanceIds should be sorted and have no duplicates")
            }
            prevId = currentId
            // check if order of values is the same as order of ids
            if (sortedByValue) {
                val currentValue = ByteArrayKey(getByIndex(i))
                prevValue?.let {
                    if (it > currentValue) {
                        sortedByValue = false
                    }
                }
                prevValue = currentValue
            }
        }
        this.sortedByValue = sortedByValue
    }

    operator fun get(instanceId: Long): ByteArray? {
        val index = instanceIdCollection.getIndex(instanceId)
        if (index < 0) {
            return null
        }
        return getByIndex(index)
    }

    fun navigate(value: ByteArray, leftBound: Boolean): AttributesCursor {
        if (instanceIdCollection.isEmpty) {
            return EmptyAttributesCursor
        }
        val ids = if (sortedByValue) instanceIdCollection else sortedByValueInstanceIds
        val valueComparable = value.asComparable()
        // in order to find exact left or right bound, we have to use binary search without early break on equality
        var low = 0
        var high = ids.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val cmp = if (sortedByValue) {
                val offsetAndLen = offsetAndLens[mid]
                val offset = offsetAndLen.toInt()
                val len = (offsetAndLen shr 32).toInt()
                -compareValueTo(offset, len, value)
            } else {
                valueComparable.compareTo(get(ids[mid])!!.asComparable())
            }
            if (cmp == 0) {
                found = mid
            }
            if (leftBound) {
                if (cmp > 0) {
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            } else {
                if (cmp < 0) {
                    high = mid - 1
                } else {
                    low = mid + 1
                }
            }
        }
        val index = if (found in 0 until ids.size) found else -(low + 1)
        return object : AttributesCursor {

            private var idx: Int = if (index < 0) -index - 1 else index

            override val hasMatch: Boolean = index >= 0

            override val current: Pair<Long, ByteArray>
                get() {
                    val instanceId = ids[idx]
                    return instanceId to if (sortedByValue) getByIndex(idx) else get(instanceId)!!
                }

            override fun moveNext(): Boolean = ++idx < ids.size

            override fun movePrev(): Boolean = --idx >= 0
        }
    }

    private fun getByIndex(index: Int): ByteArray {
        val offsetAndLen = offsetAndLens[index]
        val offset = offsetAndLen.toInt()
        val len = (offsetAndLen shr 32).toInt()
        return values.sliceArray(offset until offset + len)
    }

    /**
     * Compare a value from values array identified by offset in the array and length of the value
     */
    private fun compareValueTo(offset: Int, len: Int, other: ByteArray): Int {
        for (i in 0 until min(len, other.size)) {
            val cmp = (values[offset + i].toInt() and 0xff).compareTo(other[i].toInt() and 0xff)
            if (cmp != 0) return cmp
        }
        return len - other.size
    }
}

private object EmptyAttributesImmutable : AttributesImmutable(byteArrayOf(), longArrayOf(), longArrayOf())

internal interface AttributesCursor {

    val hasMatch: Boolean

    val current: Pair<Long, ByteArray>

    fun moveNext(): Boolean

    fun movePrev(): Boolean
}

private object EmptyAttributesCursor : AttributesCursor {
    override val hasMatch: Boolean = false
    override val current: Pair<Long, ByteArray> = error("EmptyAttributesCursor doesn't navigate")
    override fun moveNext(): Boolean = false
    override fun movePrev(): Boolean = false
}

internal class AttributesCursorEntityIterable(
    private val txn: RAMTransaction,
    private val typeId: Int,
    private val cursor: AttributesCursor,
    private val forwardDirection: Boolean,
    private val filter: ((Long, ByteArray) -> Boolean)? = null
) : EntityIterable {

    override fun iterator(): Iterator<Entity> = object : Iterator<Entity> {

        private var next: Entity? = null

        override fun hasNext(): Boolean {
            if (next == null) {
                next = advance()
            }
            return next != null
        }

        override fun next(): Entity {
            if (next == null) {
                next = advance()
            }
            return next.also { next = null } ?: throw NoSuchElementException()
        }

        private fun advance(): Entity? {
            if (next == null) {
                val moved = if (forwardDirection) cursor.moveNext() else cursor.movePrev()
                if (moved) {
                    val (instanceId, value) = cursor.current
                    filter?.let { func ->
                        if (!func(instanceId, value)) {
                            return null
                        }
                    }
                    next = txn.getEntityOrNull(EntityId(typeId, instanceId))
                }
            }
            return next
        }
    }
}

// Collection of instanceIds
private interface InstanceIdCollection {
    val isEmpty: Boolean get() = size == 0
    val size: Int
    operator fun get(index: Int): Long
    fun getIndex(instanceId: Long): Int
}

private fun LongArray.toInstanceIdCollection(sorted: Boolean): InstanceIdCollection {
    if (isEmpty()) {
        return EmptyInstanceIdCollection
    }
    if (sorted && this[0] == 0L && this[size - 1] == (size - 1).toLong()) {
        return LongRangeInstanceIdCollection(0L until size)
    }
    return if (sorted) {
        if (allInts()) {
            SortedIntArrayInstanceIdCollection(toIntArray())
        } else {
            SortedLongArrayInstanceIdCollection(this)
        }
    } else if (allInts()) {
        UnsortedIntArrayInstanceIdCollection(toIntArray())
    } else {
        UnsortedLongArrayInstanceIdCollection(this)
    }
}

private object EmptyInstanceIdCollection : InstanceIdCollection {
    override val size = 0
    override fun get(index: Int) = error("Can't get in EmptyInstanceIdCollection")
    override fun getIndex(instanceId: Long): Int = -1
}

// InstanceIdCollection wrapping unsorted LongArray
private class UnsortedLongArrayInstanceIdCollection(val array: LongArray) : InstanceIdCollection {
    override val size: Int get() = array.size
    override fun get(index: Int): Long = array[index]
    override fun getIndex(instanceId: Long): Int = array.indexOf(instanceId)
}

// InstanceIdCollection wrapping sorted LongArray
private class SortedLongArrayInstanceIdCollection(val array: LongArray) : InstanceIdCollection {
    override val size: Int get() = array.size
    override fun get(index: Int): Long = array[index]
    override fun getIndex(instanceId: Long): Int = array.binarySearch(instanceId)
}

// InstanceIdCollection wrapping LongRange which is growing progression with step 1
private class LongRangeInstanceIdCollection(val range: LongRange) : InstanceIdCollection {
    override val size: Int get() = (range.last - range.first).toInt() + 1
    override fun get(index: Int): Long = range.first + index
    override fun getIndex(instanceId: Long): Int = (instanceId - range.first).toInt()
}

// InstanceIdCollection wrapping unsorted IntArray
private class UnsortedIntArrayInstanceIdCollection(val array: IntArray) : InstanceIdCollection {
    override val size: Int get() = array.size
    override fun get(index: Int): Long = array[index].toLong()
    override fun getIndex(instanceId: Long): Int = if (instanceId.isInt()) array.indexOf(instanceId.toInt()) else -1
}

// InstanceIdCollection wrapping sorted LongArray
private class SortedIntArrayInstanceIdCollection(val array: IntArray) : InstanceIdCollection {
    override val size: Int get() = array.size
    override fun get(index: Int): Long = array[index].toLong()
    override fun getIndex(instanceId: Long): Int =
        if (instanceId.isInt()) array.binarySearch(instanceId.toInt()) else -1
}

private fun Long.isInt() = this in 0L..Int.MAX_VALUE

private fun LongArray.allInts(): Boolean {
    return all { it.isInt() }
}

private fun LongArray.toIntArray(): IntArray {
    return IntArray(size) { i -> this[i].toInt() }
}

private fun InstanceIdCollection.asIterable(): Iterable<Long> {
    return when (this) {
        is LongRangeInstanceIdCollection -> range
        is SortedLongArrayInstanceIdCollection -> array.asIterable()
        is UnsortedLongArrayInstanceIdCollection -> array.asIterable()
        is SortedIntArrayInstanceIdCollection -> array.map { it.toLong() }
        is UnsortedIntArrayInstanceIdCollection -> array.map { it.toLong() }
        else -> error("Unknown InstanceIdCollection class: $javaClass")
    }
}