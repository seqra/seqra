package org.opentaint.ir.api.common.ext

import org.opentaint.ir.api.common.CommonTypedMethod
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst

val CommonTypedMethod<*, *>.name: String
    get() = method.name

// val CommonTypedField.name: String
//     get() = field.name

val CommonInst<*, *>.lineNumber: Int
    get() = location.lineNumber

object CallExprVisitor : CommonInst.Visitor.Default<CommonCallExpr?> {
    override fun defaultVisitCommonInst(inst: CommonInst<*, *>): CommonCallExpr? {
        return inst.operands.filterIsInstance<CommonCallExpr>().firstOrNull()
    }
}

val CommonInst<*, *>.callExpr: CommonCallExpr?
    get() = accept(CallExprVisitor)
