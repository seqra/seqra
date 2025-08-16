package org.opentaint.ir.impl.storage.ers.ram

import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap

object EmptySparseBitSet: SparseBitSet(immutable = true)

open class SparseBitSet(bits: Collection<Long> = emptyList(), immutable: Boolean = false) {

    private val map: NavigableMap<Long, Long> =
        if (immutable) Collections.unmodifiableNavigableMap(TreeMap()) else TreeMap()

    init {
        bits.forEach { set(it) }
    }

    val isEmpty: Boolean get() = map.isEmpty()

    /**
     * Sets bit and returns `true` if the bit was actually set, i.e. if bitmap was mutated.
     *
     * @param bit bit value
     * @return `true` if the bit was actually set, i.e. if bitmap was mutated.
     */
    fun set(bit: Long) = setBit(bit, true)

    /**
     * Clears bit and returns `true if the bit was actually cleared, i.e. if bitmap was mutated.
     *
     * @param bit bit value
     * @return `true` if the bit was actually cleared, i.e. if bitmap was mutated.
     */
    fun clear(bit: Long) = setBit(bit, false)

    fun test(bit: Long): Boolean {
        val bucket = bit shr 6
        val pos = bit.toInt() and 0x3f;
        val mask = 1L shl pos
        return (map.getOrDefault(bucket, 0L) and mask) != 0L
    }

    fun contains(bit: Long) = test(bit)

    private fun setBit(bit: Long, bitValue: Boolean): Boolean {
        val bucket = bit shr 6
        val pos = bit.toInt() and 0x3f
        val mask = 1L shl pos
        var bits = map.getOrDefault(bucket, 0L)
        val mutated = bitValue == ((bits and mask) == 0L)
        if (!mutated) {
            return false
        }
        bits = bits xor mask
        if (bits == 0L) {
            map.remove(bucket)
        } else {
            map[bucket] = if (bits == -1L) ALL_BITS_SET else bits.interned
        }
        return true
    }

    companion object {

        private const val ALL_BITS_SET = -1L
    }
}
