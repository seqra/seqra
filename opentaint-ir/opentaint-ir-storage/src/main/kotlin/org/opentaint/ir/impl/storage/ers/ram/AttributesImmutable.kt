package org.opentaint.ir.impl.storage.ers.ram

import org.opentaint.ir.api.storage.ByteArrayKey
import org.opentaint.ir.api.storage.asComparable
import org.opentaint.ir.api.storage.ers.Entity
import org.opentaint.ir.api.storage.ers.EntityId
import org.opentaint.ir.api.storage.ers.EntityIterable

internal fun Any.toAttributesImmutable(instanceValues: List<Pair<Long, ByteArray>>): AttributesImmutable {
    if (instanceValues.isEmpty()) {
        return EmptyAttributesImmutable
    }

    val totalSize = instanceValues.fold(0) { sum, instanceValue -> sum + instanceValue.second.size }
    val values = ByteArray(totalSize)
    val instanceIds = LongArray(instanceValues.size)
    val offsetAndLens = LongArray(instanceValues.size)
    var offset = 0

    instanceValues.forEachIndexed { i, (instanceId, value) ->
        val len = value.size
        value.copyInto(destination = values, destinationOffset = offset)
        val indexValue = (len.toLong() shl 32) + offset
        instanceIds[i] = instanceId
        offsetAndLens[i] = indexValue
        offset += len
    }

    return AttributesImmutable(values, instanceIds, offsetAndLens)
}

internal open class AttributesImmutable(
    private val values: ByteArray,
    private val instanceIds: LongArray,
    private val offsetAndLens: LongArray
) {

    private val sameOrder: Boolean // `true` if order of instance ids is the same as the one sorted by value
    private val sortedByValueInstanceIds by lazy {
        // NB!
        // We need stable sorting here, and java.util.Collections.sort() guarantees the sort is stable
        instanceIds.sortedBy { get(it)!!.asComparable() }.toLongArray()
    }

    init {
        var sameOrder = true
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
            if (sameOrder) {
                val currentValue = ByteArrayKey(get(currentId)!!)
                prevValue?.let {
                    if (it > currentValue) {
                        sameOrder = false
                    }
                }
                prevValue = currentValue
            }
        }
        this.sameOrder = sameOrder
    }

    operator fun get(instanceId: Long): ByteArray? {
        val index = instanceIds.binarySearch(instanceId)
        if (index < 0) {
            return null
        }
        val offsetAndLen = offsetAndLens[index]
        val offset = offsetAndLen.toInt()
        val len = (offsetAndLen shr 32).toInt()
        return values.sliceArray(offset until offset + len)
    }

    fun navigate(value: ByteArray, leftBound: Boolean): AttributesCursor {
        if (instanceIds.isEmpty()) {
            return EmptyAttributesCursor
        }
        val ids = if (sameOrder) instanceIds else sortedByValueInstanceIds
        val valueComparable = value.asComparable()
        // in order to find exact left or right bound, we have to use binary search without early break on equality
        var low = 0
        var high = ids.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midValue = get(ids[mid])!!.asComparable()
            val cmp = valueComparable.compareTo(midValue)
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
        val index = if (found in ids.indices) found else -(low + 1)
        return object : AttributesCursor {

            private var idx: Int = if (index < 0) -index - 1 else index

            override val hasMatch: Boolean = index >= 0

            override val current: Pair<Long, ByteArray>
                get() {
                    val instanceId = ids[idx]
                    return instanceId to get(instanceId)!!
                }

            override fun moveNext(): Boolean = ++idx < ids.size

            override fun movePrev(): Boolean = --idx >= 0
        }
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