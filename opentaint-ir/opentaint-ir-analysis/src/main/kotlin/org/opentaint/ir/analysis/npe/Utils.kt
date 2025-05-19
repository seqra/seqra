package org.opentaint.ir.analysis.npe

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.minus
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.analysis.util.startsWith
import org.opentaint.ir.analysis.util.values
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInstanceCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRLengthExpr

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath?.isDereferencedAt(expr: CommonExpr): Boolean {
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

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath?.isDereferencedAt(inst: CommonInst<*, *>): Boolean {
    if (this == null) return false
    return inst.operands.any { isDereferencedAt(it) }
}
