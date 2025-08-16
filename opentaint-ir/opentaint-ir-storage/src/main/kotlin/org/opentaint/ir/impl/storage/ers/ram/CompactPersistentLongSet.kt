package org.opentaint.ir.impl.storage.ers.ram

internal class CompactPersistentLongSet(private val value: Any? = null) : Collection<Long> {

    override val size: Int
        get() = when (value) {
            null -> 0
            is Long -> 1
            is LongPair -> 2
            is PackedPersistentLongSet -> value.size
            else -> throw illegalStateException()
        }

    override fun isEmpty(): Boolean = value == null

    override fun iterator(): Iterator<Long> {
        return when (value) {
            null -> emptyList()
            is Long -> listOf(value)
            is LongPair -> listOf(value.one, value.two)
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
            is LongPair -> value.one == element || value.two == element
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
                    CompactPersistentLongSet(
                        if (value < element) LongPair(value, element) else LongPair(element, value)
                    )
                }

            is LongPair ->
                if (value.one == element || value.two == element) {
                    this
                } else {
                    CompactPersistentLongSet(
                        PackedPersistentLongSet().addAll(
                            listOf(value.one.interned, value.two.interned, element.interned)
                        )
                    )
                }

            is PackedPersistentLongSet -> {
                val newValue = value.add(element.interned)
                if (newValue === value) {
                    this
                } else {
                    CompactPersistentLongSet(newValue)
                }
            }

            else -> throw illegalStateException()
        }
    }

    fun remove(element: Long): CompactPersistentLongSet {
        return when (value) {
            null -> this
            is Long -> if (value == element) CompactPersistentLongSet() else this
            is LongPair ->
                if (value.one == element) {
                    CompactPersistentLongSet(value.two.interned)
                } else if (value.two == element) {
                    CompactPersistentLongSet(value.one.interned)
                } else {
                    this
                }

            is PackedPersistentLongSet -> {
                val newValue = value.remove(element)
                if (newValue === value) {
                    this
                } else {
                    CompactPersistentLongSet(
                        if (newValue.size == 2) newValue.iterator().let { LongPair(it.next(), it.next()) } else newValue
                    )
                }
            }

            else -> throw illegalStateException()
        }
    }

    private fun illegalStateException() =
        IllegalStateException("CompactPersistentLongSet.value can only be Long or PersistentLongSet")
}

internal val Long.interned: Long
    get() =
        if (this in 0 until LongInterner.boxedLongs.size) LongInterner.boxedLongs[this.toInt()]
        else this

// TODO: remove this interner if specialized persistent collections would be used
private object LongInterner {

    val boxedLongs = Array(200000) { it.toLong() }
}

private data class LongPair(val one: Long, val two: Long)