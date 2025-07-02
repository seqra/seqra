package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf

sealed interface ExclusionSet {
    operator fun contains(accessor: Accessor): Boolean
    fun add(accessor: Accessor): ExclusionSet
    fun union(other: ExclusionSet): ExclusionSet
    fun subtract(accessor: Accessor): ExclusionSet

    fun contains(other: ExclusionSet): Boolean

    object Empty : ExclusionSet {
        override fun contains(accessor: Accessor): Boolean = false
        override fun add(accessor: Accessor): ExclusionSet = Concrete(accessor)
        override fun union(other: ExclusionSet): ExclusionSet = other
        override fun subtract(accessor: Accessor): ExclusionSet = this
        override fun contains(other: ExclusionSet): Boolean = other is Empty

        override fun toString(): String = "{}"
    }

    object Universe : ExclusionSet {
        override fun contains(accessor: Accessor): Boolean = true
        override fun add(accessor: Accessor): ExclusionSet = this
        override fun union(other: ExclusionSet): ExclusionSet = this
        override fun subtract(accessor: Accessor): ExclusionSet = error("Can't subtract from $this")
        override fun contains(other: ExclusionSet): Boolean = true

        override fun toString(): String = "*"
    }

    data class Concrete(
        val set: PersistentSet<Accessor>,
        private val hash: Int,
    ) : ExclusionSet {
        constructor(accessor: Accessor) : this(persistentHashSetOf(accessor), accessor.hashCode())

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Concrete) return false

            if (hash != other.hash) return false
            return set == other.set
        }

        override fun contains(accessor: Accessor): Boolean = set.contains(accessor)

        override fun add(accessor: Accessor): ExclusionSet {
            val setWithAccessor = set.add(accessor)
            if (setWithAccessor === set) return this

            return Concrete(setWithAccessor, hash + accessor.hashCode())
        }

        override fun union(other: ExclusionSet): ExclusionSet = when (other) {
            Empty -> this
            Universe -> other
            is Concrete -> {
                val union = set.addAll(other.set)
                if (union === set) this else Concrete(union, union.hashCode())
            }
        }

        override fun subtract(accessor: Accessor): ExclusionSet {
            val subtractResult = set.remove(accessor)
            return when {
                subtractResult === set -> this
                subtractResult.isEmpty() -> Empty
                else -> Concrete(subtractResult, hash - accessor.hashCode())
            }
        }

        override fun contains(other: ExclusionSet): Boolean = when (other) {
            Empty -> true
            Universe -> false
            is Concrete -> set.containsAll(other.set)
        }

        override fun toString(): String = set.joinToString(prefix = "{", postfix = "}") { it.toSuffix() }
    }
}
