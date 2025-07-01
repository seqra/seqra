package org.opentaint.ir.impl.storage.ers.ram

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap

internal class PackedPersistentLongSet(
    private val map: PersistentLongMap<Long> = PersistentLong23TreeMap(),
    override val size: Int = 0
) : Collection<Long> {

    fun add(element: Long): PackedPersistentLongSet {
        val key = element.index
        val (added, newMap) = map.write {
            val bits = get(key) ?: 0L
            bits.setBit(element).let { newBits ->
                (newBits != bits).also {
                    if (it) {
                        put(key, newBits)
                    }
                }
            }
        }
        return if (added == true) PackedPersistentLongSet(newMap, size = size + 1) else this
    }

    fun remove(element: Long): PackedPersistentLongSet {
        val key = element.index
        val (removed, newMap) = map.write {
            get(key)?.let { bits ->
                val newBits = bits.clearBit(element)
                (newBits != bits).also {
                    if (it) {
                        if (newBits == 0L) remove(key) else put(key, newBits)
                    }
                }
            } ?: false
        }
        return if (removed == true) PackedPersistentLongSet(newMap, size = size - 1) else this
    }

    fun addAll(elements: Collection<Long>): PackedPersistentLongSet {
        var newSize = size
        val (added, newMap) = map.write {
            elements.forEach { element ->
                val key = element.index
                val bits = get(key) ?: 0L
                val oneBitAdded = bits.setBit(element).let { newBits ->
                    (newBits != bits).also {
                        if (it) {
                            put(key, newBits)
                        }
                    }
                }
                if (oneBitAdded) {
                    ++newSize
                }
            }
            newSize > size
        }
        return if (added == true) PackedPersistentLongSet(newMap, size = newSize) else this
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<Long> = buildList {
        map.read {
            forEach { entry ->
                val base = entry.key shl 6
                var value = entry.value as Long
                var n = value.countOneBits()
                var bit = value.countTrailingZeroBits()
                value = value shr bit
                while (n > 0) {
                    if ((value and 1L) != 0L) {
                        this@buildList.add(base + bit)
                        --n
                    }
                    value = value shr 1
                    ++bit
                }
            }
        }
    }.iterator()

    override fun containsAll(elements: Collection<Long>): Boolean {
        elements.forEach { element ->
            if (element !in this) return false
        }
        return true
    }

    override fun contains(element: Long): Boolean {
        return map[element.index]?.hasBit(element) ?: false
    }

    /**
     * Bits operations follow:
     */
    private val Long.index get() = this shr 6

    private fun Long.hasBit(element: Long): Boolean {
        val bit = element.toInt() and 63
        val mask = 1L shl bit
        return (this and mask) != 0L
    }

    private fun Long.setBit(element: Long): Long {
        val bit = element.toInt() and 63
        val mask = 1L shl bit
        return this or mask
    }

    private fun Long.clearBit(element: Long): Long {
        val bit = element.toInt() and 63
        val mask = 1L shl bit
        return this and mask.inv()
    }
}