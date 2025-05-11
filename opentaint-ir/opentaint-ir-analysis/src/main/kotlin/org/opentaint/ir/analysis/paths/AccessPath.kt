package org.opentaint.ir.analysis.paths

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.JIRTypedField
import org.opentaint.ir.api.cfg.JIRSimpleValue

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(
    val value: JIRSimpleValue?, // null for static field
    val accesses: List<Accessor>,
) {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            require(a.field.isStatic)
        }
    }

    val isOnHeap: Boolean
        get() = accesses.isNotEmpty()

    val isStatic: Boolean
        get() = value == null

    fun limit(n: Int): AccessPath = AccessPath(value, accesses.take(n))

    operator fun div(accesses: List<Accessor>): AccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && accessor.field.isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
            }
        }

        return AccessPath(value, this.accesses + accesses)
    }

    operator fun div(accessor: Accessor): AccessPath {
        if (accessor is FieldAccessor && accessor.field.isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        }

        return AccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }

    companion object {
        fun from(value: JIRSimpleValue): AccessPath = AccessPath(value, emptyList())

        fun fromStaticField(field: JIRField): AccessPath {
            require(field.isStatic) { "Expected static field" }
            return AccessPath(null, listOf(FieldAccessor(field)))
        }

        fun fromStaticField(field: JIRTypedField): AccessPath {
            require(field.isStatic) { "Expected static field" }
            return AccessPath(null, listOf(FieldAccessor(field.field)))
        }
    }
}
