package org.opentaint.ir.analysis.paths

import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRCastExpr
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.values

internal fun JIRExpr.toPathOrNull(): AccessPath? {
    if (this is JIRCastExpr) {
        return operand.toPathOrNull()
    }
    if (this is JIRLocal) {
        return AccessPath.fromLocal(this)
    }

    if (this is JIRArrayAccess) {
        return array.toPathOrNull()?.let {
            AccessPath.fromOther(it, listOf(ElementAccessor))
        }
    }

    if (this is JIRFieldRef) {
        val instance = instance // enables smart cast

        return if (instance == null) {
            AccessPath.fromStaticField(field.field)
        } else {
            instance.toPathOrNull()?.let {
                AccessPath.fromOther(it, listOf(FieldAccessor(field.field)))
            }
        }
    }
    return null
}

internal fun JIRValue.toPath(): AccessPath {
    return toPathOrNull() ?: error("Unable to build access path for value $this")
}

internal fun AccessPath?.minus(other: AccessPath): List<Accessor>? {
    if (this == null) {
        return null
    }
    if (value != other.value) {
        return null
    }
    if (accesses.take(other.accesses.size) != other.accesses) {
        return null
    }

    return accesses.drop(other.accesses.size)
}

internal fun AccessPath?.startsWith(other: AccessPath?): Boolean {
    if (this == null || other == null) {
        return false
    }

    return minus(other) != null
}

fun AccessPath?.isDereferencedAt(expr: JIRExpr): Boolean {
    if (this == null) {
        return false
    }

    (expr as? JIRInstanceCallExpr)?.let {
        val instancePath = it.instance.toPathOrNull()
        if (instancePath.startsWith(this)) {
            return true
        }
    }

    (expr as? JIRLengthExpr)?.let {
        val arrayPath = it.array.toPathOrNull()
        if (arrayPath.startsWith(this)) {
            return true
        }
    }

    return expr.values
        .mapNotNull { it.toPathOrNull() }
        .any {
            it.minus(this)?.isNotEmpty() == true
        }
}

fun AccessPath?.isDereferencedAt(inst: JIRInst): Boolean {
    if (this == null) {
        return false
    }

    return inst.operands.any { isDereferencedAt(it) }
}