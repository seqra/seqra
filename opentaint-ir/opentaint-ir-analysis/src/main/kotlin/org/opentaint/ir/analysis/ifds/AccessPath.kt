package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRField

data class AccessPath internal constructor(
    val value: CommonValue?,
    val accesses: List<Accessor>,
) {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            if (a.field is JIRField) {
                require(a.field.isStatic)
            }
        }
    }

    fun limit(n: Int): AccessPath = AccessPath(value, accesses.take(n))

    operator fun plus(accesses: List<Accessor>): AccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && accessor.field is JIRField && accessor.field.isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
            }
        }

        return AccessPath(value, this.accesses + accesses)
    }

    operator fun plus(accessor: Accessor): AccessPath {
        if (accessor is FieldAccessor && accessor.field is JIRField && accessor.field.isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        }

        return AccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }
}

val AccessPath.isOnHeap: Boolean
    get() = accesses.isNotEmpty()

val AccessPath.isStatic: Boolean
    get() = value == null

operator fun AccessPath.minus(other: AccessPath): List<Accessor>? {
    if (value != other.value) return null
    if (accesses.take(other.accesses.size) != other.accesses) return null
    return accesses.drop(other.accesses.size)
}
