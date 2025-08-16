package org.opentaint.ir.util.collections

import org.opentaint.ir.impl.storage.ers.ram.interned
import java.util.*

object EmptySparseBitSet : SparseBitSet(immutable = true)

open class SparseBitSet(bits: Iterable<Long> = emptyList(), immutable: Boolean = false) : Iterable<Long> {

    private val map = TreeMap<Long, Long>().let { map ->
        bits.forEach { setBit(map, it, true) }
        if (immutable) map.asImmutable() else map
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
        val pos = bit.toInt() and 0x3f
        val mask = 1L shl pos
        return (map.getOrDefault(bucket, 0L) and mask) != 0L
    }

    fun contains(bit: Long) = test(bit)

    private fun setBit(bit: Long, bitValue: Boolean) = setBit(map, bit, bitValue)

    private fun setBit(map: NavigableMap<Long, Long>, bit: Long, bitValue: Boolean): Boolean {
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

    override fun iterator(): Iterator<Long> = sequence {
        map.forEach { entry ->
            val base = entry.key shl 6
            var value = entry.value as Long
            var n = value.countOneBits()
            var bit = value.countTrailingZeroBits()
            value = value shr bit
            while (n > 0) {
                if ((value and 1L) != 0L) {
                    yield(base + bit)
                    --n
                }
                value = value shr 1
                ++bit
            }
        }
    }.iterator()
}

private fun NavigableMap<Long, Long>.asImmutable(): NavigableMap<Long, Long> =
    Collections.unmodifiableNavigableMap(this)