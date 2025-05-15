package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.ifds.toPathOrNull
import org.opentaint.ir.api.cfg.JIRArrayAccess
import org.opentaint.ir.api.cfg.JIRAssignInst
import org.opentaint.ir.api.cfg.JIRBranchingInst
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRLocal
import org.opentaint.ir.api.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.cfg.JIRTerminatingInst
import org.opentaint.ir.api.cfg.values
import org.opentaint.ir.api.ext.cfg.callExpr

internal fun AccessPath.isUsedAt(expr: JIRExpr): Boolean {
    return this in expr.values.map { it.toPathOrNull() }
}

internal fun AccessPath.isUsedAt(inst: JIRInst): Boolean {
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
