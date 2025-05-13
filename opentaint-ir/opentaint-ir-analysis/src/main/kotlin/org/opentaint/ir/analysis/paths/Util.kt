package org.opentaint.ir.analysis.paths

import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRCastExpr
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRFieldRef
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.cfg.JIRLengthExpr
import org.opentaint.ir.api.cfg.JIRSimpleValue
import org.opentaint.ir.api.cfg.JIRValue
import org.opentaint.ir.api.cfg.values

/**
 * Converts `JIRExpr` (in particular, `JIRValue`) to `AccessPath`.
 *   - For `JIRSimpleValue`, this method simply wraps the value.
 *   - For `JIRArrayAccess` and `JIRFieldRef`, this method "reverses" it and recursively constructs a list of accessors (`ElementAccessor` for array access, `FieldAccessor` for field access).
 *   - Returns `null` when the conversion to `AccessPath` is not possible.
 *
 * Example:
 *   `x.f[0].y` is `AccessPath(value = x, accesses = [Field(f), Element(0), Field(y)])`
 */
internal fun JIRExpr.toPathOrNull(): AccessPath? {
    return when (this) {
        is JIRSimpleValue -> {
            AccessPath.from(this)
        }

        is JIRCastExpr -> {
            operand.toPathOrNull()
        }

        is JIRArrayAccess -> {
            array.toPathOrNull()?.let {
                it / listOf(ElementAccessor(index))
            }
        }

        is JIRFieldRef -> {
            val instance = instance // enables smart cast

            if (instance == null) {
                AccessPath.fromStaticField(field.field)
            } else {
                instance.toPathOrNull()?.let { it / FieldAccessor(field.field) }
            }
        }

        else -> null
    }
}

internal fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

// this = value.x.y[0]
// this = (value, accesses = listOf(Field, Field, Element))
//
// other = value.x
// other = (value, accesses = listOf(Field))
//
// (this - other) = listOf(Field, Element) = ".y[0]"
internal operator fun AccessPath?.minus(other: AccessPath): List<Accessor>? {
    if (this == null) {
        return null
    }
    if (value != other.value) {
        return null
    }
    if (this.accesses.take(other.accesses.size) != other.accesses) {
        return null
    }

    return accesses.drop(other.accesses.size)
}

internal fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }
    if (this.value != other.value) {
        return false
    }
    // Unnecessary check:
    // if (this.accesses.size < other.accesses.size) {
    //     return false
    // }
    return this.accesses.take(other.accesses.size) == other.accesses
}

fun AccessPath?.isDereferencedAt(expr: JIRExpr): Boolean {
    if (this == null) {
        return false
    }

    if (expr is JIRInstanceCallExpr) {
        val instancePath = expr.instance.toPathOrNull()
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    if (expr is JIRLengthExpr) {
        val arrayPath = expr.array.toPathOrNull()
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { it.toPathOrNull() }
        .any {
            (it - this)?.isNotEmpty() == true
        }
}

fun AccessPath?.isDereferencedAt(inst: JIRInst): Boolean {
    if (this == null) {
        return false
    }

    return inst.operands.any { isDereferencedAt(it) }
}
