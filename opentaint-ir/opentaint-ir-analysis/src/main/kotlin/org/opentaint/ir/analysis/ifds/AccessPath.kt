package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRSimpleValue
import org.opentaint.ir.api.jvm.cfg.JIRValue

interface CommonAccessPath {
    val value: CommonValue?
    val accesses: List<Accessor>

    fun limit(n: Int): CommonAccessPath

    operator fun plus(accesses: List<Accessor>): CommonAccessPath
    operator fun plus(accessor: Accessor): CommonAccessPath
}

val CommonAccessPath.isOnHeap: Boolean
    get() = accesses.isNotEmpty()

val CommonAccessPath.isStatic: Boolean
    get() = value == null

operator fun CommonAccessPath.minus(other: CommonAccessPath): List<Accessor>? {
    if (value != other.value) return null
    if (accesses.take(other.accesses.size) != other.accesses) return null
    return accesses.drop(other.accesses.size)
}

fun CommonExpr.toPathOrNull(): CommonAccessPath? = when (this) {
    is JIRExpr -> toPathOrNull()
    is CommonValue -> toPathOrNull()
    else -> error("Cannot")
}

fun CommonValue.toPathOrNull(): CommonAccessPath? = when (this) {
    is JIRValue -> toPathOrNull()
    else -> error("Cannot")
}

fun CommonValue.toPath(): CommonAccessPath = when (this) {
    is JIRValue -> toPath()
    else -> error("Cannot")
}

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values
 * (such as NPE, uninitialized variable, etc.)
 */
data class JIRAccessPath internal constructor(
    override val value: JIRSimpleValue?, // null for static field
    override val accesses: List<Accessor>,
) : CommonAccessPath {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            require(a.field is JIRField)
            require(a.field.isStatic)
        }
    }

    override fun limit(n: Int): JIRAccessPath = JIRAccessPath(value, accesses.take(n))

    override operator fun plus(accesses: List<Accessor>): JIRAccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
            }
        }

        return JIRAccessPath(value, this.accesses + accesses)
    }

    override operator fun plus(accessor: Accessor): JIRAccessPath {
        if (accessor is FieldAccessor && (accessor.field as JIRField).isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        }

        return JIRAccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }

    companion object {
        fun from(value: JIRSimpleValue): JIRAccessPath = JIRAccessPath(value, emptyList())

        fun from(field: JIRField): JIRAccessPath {
            require(field.isStatic) { "Expected static field" }
            return JIRAccessPath(null, listOf(FieldAccessor(field)))
        }
    }
}

fun JIRExpr.toPathOrNull(): JIRAccessPath? = when (this) {
    is JIRValue -> toPathOrNull()
    is JIRCastExpr -> operand.toPathOrNull()
    else -> null
}

fun JIRValue.toPathOrNull(): JIRAccessPath? = when (this) {
    is JIRSimpleValue -> JIRAccessPath.from(this)

    is JIRArrayAccess -> {
        array.toPathOrNull()?.let {
            it + ElementAccessor
        }
    }

    is JIRFieldRef -> {
        val instance = instance
        if (instance == null) {
            JIRAccessPath.from(field.field)
        } else {
            instance.toPathOrNull()?.let {
                it + FieldAccessor(field.field)
            }
        }
    }

    else -> null
}

fun JIRValue.toPath(): JIRAccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}
