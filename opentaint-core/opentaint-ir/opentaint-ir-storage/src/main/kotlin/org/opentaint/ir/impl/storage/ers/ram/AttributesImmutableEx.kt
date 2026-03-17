package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.hash.IntHashMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.opentaint.ir.util.ByteArrayBuilder
import org.opentaint.ir.util.io.readByteBuffer
import org.opentaint.ir.util.io.readVlqUnsigned
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

internal fun List<Pair<Long, ByteArray>>.toAttributesImmutable(builder: ByteArrayBuilder): AttributesImmutable {
    if (isEmpty()) {
        return EmptyAttributesImmutable
    }

    val instanceIds = ArrayList<Long>(size)
    val offsetAndLensBuffer = ByteBuffer.allocate(size * Long.SIZE_BYTES)
    val offsetAndLens = offsetAndLensBuffer.asLongBuffer()
    val differentValues = IntHashMap<Pair<ByteArray, Long>>(size)

    builder.reset()

    var offset = 0

    forEachIndexed { i, (instanceId, value) ->
        instanceIds.add(i, instanceId)
        val hc = value.contentHashCode()
        val pair = differentValues[hc]
        if (value contentEquals pair?.first) {
            offsetAndLens.put(i, pair.second)
        } else {
            builder.append(value)
            val len = value.size
            val indexValue = (len.toLong() shl 32) + offset
            offsetAndLens.put(i, indexValue)
            differentValues[hc] = value to indexValue
            offset += len
        }
    }

    return AttributesImmutable(builder.toByteBuffer(), instanceIds, offsetAndLensBuffer)
}

internal fun PersistentLongMap.ImmutableMap<ByteArray>.toAttributesImmutable(builder: ByteArrayBuilder): AttributesImmutable {
    val size = size()
    if (size == 0) {
        return EmptyAttributesImmutable
    }

    val instanceIds = ArrayList<Long>(size)
    val offsetAndLensBuffer = ByteBuffer.allocate(size * Long.SIZE_BYTES)
    val offsetAndLens = offsetAndLensBuffer.asLongBuffer()
    val differentValues = IntHashMap<Pair<ByteArray, Long>>(size)

    builder.reset()

    var offset = 0

    forEachIndexed { i, entry ->
        instanceIds.add(i, entry.key)
        val value = entry.value
        val hc = value.contentHashCode()
        val pair = differentValues[hc]
        if (value contentEquals pair?.first) {
            offsetAndLens.put(i, pair.second)
        } else {
            builder.append(value)
            val len = value.size
            val indexValue = (len.toLong() shl 32) + offset
            offsetAndLens.put(i, indexValue)
            differentValues[hc] = value to indexValue
            offset += len
        }
    }

    return AttributesImmutable(builder.toByteBuffer(), instanceIds, offsetAndLensBuffer)
}

internal fun InputStream.readAttributesImmutable(): AttributesImmutable {
    val sortedByValue = read() == 1
    val values = readByteBuffer()
    // read instance ids
    val instanceIds = when (val instanceIdCollectionType = read()) {
        0 -> IntBufferList(readByteBuffer().asIntBuffer().asReadOnlyBuffer())
        1 -> LongBufferList(readByteBuffer().asLongBuffer().asReadOnlyBuffer())
        2 -> {
            val first = readVlqUnsigned()
            val last = readVlqUnsigned() + first
            LongRangeList(first, last)
        }

        else -> error("Unknown type of InstanceIdCollection: $instanceIdCollectionType")
    }
    return AttributesImmutable(
        values = values,
        instanceIds = instanceIds,
        offsetsAndLens = readByteBuffer(),
        sortedByValue = sortedByValue
    )
}

private object EmptyAttributesImmutable :
    AttributesImmutable(emptyBuffer, emptyList(), emptyBuffer)

private val emptyBuffer = ByteBuffer.wrap(byteArrayOf())

private abstract class InstanceIdList : List<Long> {

    override fun isEmpty(): Boolean = size == 0

    override fun listIterator() = throwNotImplementedError()

    override fun listIterator(index: Int) = throwNotImplementedError()

    override fun subList(fromIndex: Int, toIndex: Int) = throwNotImplementedError()

    override fun lastIndexOf(element: Long) = throwNotImplementedError()

    override fun indexOf(element: Long) = throwNotImplementedError()

    override fun containsAll(elements: Collection<Long>) = throwNotImplementedError()

    override fun contains(element: Long) = throwNotImplementedError()

    private fun throwNotImplementedError(): Nothing {
        throw NotImplementedError()
    }
}

private class LongRangeList(private val first: Long, private val last: Long) : InstanceIdList() {

    override val size: Int get() = (last - first + 1L).toInt()

    override fun get(index: Int): Long = first + index

    override fun iterator(): Iterator<Long> = LongRange(first, last).iterator()
}

private class IntBufferList(private val buffer: IntBuffer) : InstanceIdList() {

    override val size: Int get() = buffer.limit()

    override fun get(index: Int): Long = buffer.get(index).toLong()

    override fun iterator(): Iterator<Long> = sequence {
        for (i in 0 until size) {
            yield(get(i))
        }
    }.iterator()
}

private class LongBufferList(private val buffer: LongBuffer) : InstanceIdList() {

    override val size: Int get() = buffer.limit()

    override fun get(index: Int): Long = buffer.get(index)

    override fun iterator(): Iterator<Long> = sequence {
        for (i in 0 until size) {
            yield(get(i))
        }
    }.iterator()
}