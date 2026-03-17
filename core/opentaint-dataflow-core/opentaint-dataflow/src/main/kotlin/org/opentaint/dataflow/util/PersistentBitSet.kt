package org.opentaint.dataflow.util

import java.util.BitSet

class PersistentBitSet : BitSet() {
    private var _size: Int = 0

    val size: Int get() = _size

    fun persistentAdd(element: Int): PersistentBitSet {
        if (get(element)) return this

        val result = persistentClone()
        result.mutableSet(element)
        result._size++
        return result
    }

    fun persistentAddAll(other: BitSet): PersistentBitSet {
        if (other.isEmpty) return this

        val result = persistentClone()
        result.mutableOr(other)
        result._size = result.cardinality()
        return result
    }

    fun persistentRemove(element: Int): PersistentBitSet {
        if (!get(element)) return this

        if (_size == 1) return EMPTY

        val result = persistentClone()
        result.mutableClear(element)
        result._size--
        return result
    }

    fun persistentRemoveAll(other: BitSet): PersistentBitSet {
        if (this.isEmpty) return this
        if (other.isEmpty) return this

        val result = persistentClone()
        result.mutableAndNot(other)
        result._size = result.cardinality()

        if (result._size == 0) return EMPTY

        return result
    }

    private fun persistentClone(): PersistentBitSet {
        return super.clone() as PersistentBitSet
    }

    override fun clone(): BitSet {
        val result = BitSet(size)
        result.or(this)
        return result
    }

    private fun mutableSet(element: Int) {
        super.set(element)
    }

    private fun mutableClear(element: Int) {
        super.clear(element)
    }

    private fun mutableOr(other: BitSet) {
        super.or(other)
    }

    private fun mutableAndNot(other: BitSet) {
        super.andNot(other)
    }

    // override size to avoid misusages
    override fun size(): Int = error("Use size property")

    // disable mutable operations
    override fun set(bitIndex: Int): Unit = error("Unsupported operation")
    override fun clear(bitIndex: Int): Unit = error("Unsupported operation")
    override fun or(set: BitSet): Unit = error("Unsupported operation")
    override fun and(set: BitSet): Unit = error("Unsupported operation")
    override fun andNot(set: BitSet): Unit = error("Unsupported operation")

    companion object {
        private val EMPTY = PersistentBitSet()

        fun emptyPersistentBitSet(): PersistentBitSet = EMPTY
    }
}
