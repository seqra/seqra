package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.JIRField
import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRCastExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRFieldRef
import org.opentaint.ir.api.cfg.JIRSimpleValue
import org.opentaint.ir.api.cfg.JIRValue

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values
 * (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath internal constructor(
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

    operator fun minus(other: AccessPath): List<Accessor>? {
        if (value != other.value) return null
        if (accesses.take(other.accesses.size) != other.accesses) return null
        return accesses.drop(other.accesses.size)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }

    companion object {
        fun from(value: JIRSimpleValue): AccessPath = AccessPath(value, emptyList())

        fun from(field: JIRField): AccessPath {
            require(field.isStatic) { "Expected static field" }
            return AccessPath(null, listOf(FieldAccessor(field)))
        }
    }
}

fun JIRExpr.toPathOrNull(): AccessPath? = when (this) {
    is JIRSimpleValue -> AccessPath.from(this)

    is JIRCastExpr -> operand.toPathOrNull()

    is JIRArrayAccess -> {
        array.toPathOrNull()?.let {
            it / ElementAccessor(index)
        }
    }

    is JIRFieldRef -> {
        val instance = instance
        if (instance == null) {
            AccessPath.from(field.field)
        } else {
            instance.toPathOrNull()?.let {
                it / FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
