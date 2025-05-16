package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.ifds.CommonAccessPath
import org.opentaint.ir.analysis.ifds.JIRAccessPath
import org.opentaint.ir.analysis.ifds.minus
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.analysis.util.startsWith
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr
import org.opentaint.ir.api.jvm.cfg.values

internal fun CommonAccessPath?.isDereferencedAt(expr: CommonExpr): Boolean {
    if (this == null) {
        return false
    }
    if (this is JIRAccessPath && expr is JIRExpr) {
        return isDereferencedAt(expr)
    }
    error("Cannot check whether path $this is dereferenced at expr: $expr")
}

internal fun CommonAccessPath?.isDereferencedAt(inst: CommonInst<*, *>): Boolean {
    if (this == null) {
        return false
    }
    if (this is JIRAccessPath && inst is JIRInst) {
        return isDereferencedAt(inst)
    }
    error("Cannot check whether path $this is dereferenced at inst: $inst")
}

internal fun JIRAccessPath?.isDereferencedAt(expr: JIRExpr): Boolean {
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

internal fun JIRAccessPath?.isDereferencedAt(inst: JIRInst): Boolean {
    if (this == null) return false
    return inst.operands.any { isDereferencedAt(it) }
}
