package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.CommonAccessPath
import org.opentaint.ir.analysis.ifds.JIRAccessPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.cfg.callExpr

internal fun CommonAccessPath.isUsedAt(expr: CommonExpr): Boolean {
    if (this is JIRAccessPath && expr is JIRExpr) {
        return isUsedAt(expr)
    }
    error("Cannot determine whether path $this is used at expr: $expr")
}

internal fun CommonAccessPath.isUsedAt(inst: CommonInst<*, *>): Boolean {
    if (this is JIRAccessPath && inst is JIRInst) {
        return isUsedAt(inst)
    }
    error("Cannot determine whether path $this is used at inst: $inst")
}

internal fun JIRAccessPath.isUsedAt(expr: JIRExpr): Boolean {
    return expr.values.any { it.toPathOrNull() == this }
}

internal fun JIRAccessPath.isUsedAt(inst: JIRInst): Boolean {
    val callExpr = inst.callExpr

    if (callExpr != null) {
        // Don't count constructor calls as usages
        if (callExpr.method.method.isConstructor && isUsedAt((callExpr as JIRSpecialCallExpr).instance)) {
            return false
        }

        return isUsedAt(callExpr)
    }
    if (inst is JIRAssignInst) {
        if (inst.lhv is JIRArrayAccess && isUsedAt((inst.lhv as JIRArrayAccess))) {
            return true
        }
        return isUsedAt(inst.rhv) && (inst.lhv !is JIRLocal || inst.rhv !is JIRLocal)
    }
    if (inst is JIRTerminatingInst || inst is JIRBranchingInst) {
        return inst.operands.any { isUsedAt(it) }
    }
    return false
}
