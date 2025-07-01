package org.opentaint.ir.impl.storage.ers.ram

internal class CompactPersistentLongSet(private val value: Any? = null) : Collection<Long> {

    override val size: Int
        get() = when (value) {
            null -> 0
            is Long -> 1
            is PackedPersistentLongSet -> value.size
            else -> throw illegalStateException()
        }

    override fun isEmpty(): Boolean = value == null

    override fun iterator(): Iterator<Long> {
        return when (value) {
            null -> emptyList()
            is Long -> listOf(value)
            is PackedPersistentLongSet -> value
            else -> throw illegalStateException()
        }.iterator()
    }

    override fun containsAll(elements: Collection<Long>): Boolean {
        elements.forEach { element ->
            if (element !in this) return false
        }
        return true
    }

    override fun contains(element: Long): Boolean {
        return when (value) {
            null -> false
            is Long -> value == element
            is PackedPersistentLongSet -> element in value
            else -> throw illegalStateException()
        }
    }

    fun add(element: Long): CompactPersistentLongSet {
        return when (value) {
            null -> CompactPersistentLongSet(element.interned)
            is Long ->
                if (value == element) {
                    this
                } else {
                    CompactPersistentLongSet(PackedPersistentLongSet().addAll(listOf(value, element)))
                }

            is PackedPersistentLongSet -> {
                val newValue = value.add(element)
                if (newValue === value) {
                    this
                } else {
                    CompactPersistentLongSet(value.add(element))
                }
            }

            else -> throw illegalStateException()
        }
    }

    fun remove(element: Long): CompactPersistentLongSet {
        return when (value) {
            null -> this
            is Long -> if (value == element) CompactPersistentLongSet() else this
            is PackedPersistentLongSet -> {
                val newValue = value.remove(element)
                if (newValue === value) {
                    this
                } else {
                    newValue.run {
                        if (size == 1) {
                            CompactPersistentLongSet(first().interned)
                        } else {
                            CompactPersistentLongSet(newValue)
                        }
                    }
                }
            }

            else -> throw illegalStateException()
        }
    }

    private fun illegalStateException() =
        IllegalStateException("CompactPersistentLongSet.value can only be Long or PersistentLongSet")
}

private val Any?.interned: Any?
    get() =
        if (this is Long && this >= 0 && this < LongInterner.boxedLongs.size) LongInterner.boxedLongs[this.toInt()]
        else this

// TODO: remove this interner if specialized persistent collections would be used
object LongInterner {

    val boxedLongs = Array<Any>(200000) { it.toLong() }
}