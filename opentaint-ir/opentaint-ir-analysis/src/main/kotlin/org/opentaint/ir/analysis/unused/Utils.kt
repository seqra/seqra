package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.analysis.util.Traits
import org.opentaint.ir.analysis.util.values
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.ext.callExpr
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRBranchingInst
import org.opentaint.ir.api.jvm.cfg.JIRLocal
import org.opentaint.ir.api.jvm.cfg.JIRSpecialCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRTerminatingInst

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath.isUsedAt(
    expr: CommonExpr,
): Boolean {
    return expr.values.any { it.toPathOrNull() == this }
}

context(Traits<CommonMethod<*, *>, CommonInst<*, *>>)
internal fun AccessPath.isUsedAt(
    inst: CommonInst<*, *>,
): Boolean {
    val callExpr = inst.callExpr

    if (callExpr != null) {
        // Don't count constructor calls as usages
        if (callExpr is JIRSpecialCallExpr
            && callExpr.method.method.isConstructor
            && isUsedAt(callExpr.instance)
        ) {
            return false
        }

        return isUsedAt(callExpr)
    }
    if (inst is JIRAssignInst) {
        if (inst.lhv is JIRArrayAccess && isUsedAt(inst.lhv)) {
            return true
        }
        return isUsedAt(inst.rhv) && (inst.lhv !is JIRLocal || inst.rhv !is JIRLocal)
    }
    if (inst is JIRTerminatingInst || inst is JIRBranchingInst) {
        return inst.operands.any { isUsedAt(it) }
    }
    return false
}
