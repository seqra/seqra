package org.opentaint.dataflow.jvm.util

import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonExpr
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRLocalVar
import org.opentaint.ir.api.jvm.cfg.values
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.dataflow.util.SarifTraits

class JIRSarifTraits(
    val cp: JIRClasspath,
) : SarifTraits<JIRMethod, JIRInst> {


    override fun getCallee(callExpr: CommonCallExpr): JIRMethod {
        check(callExpr is JIRCallExpr)
        return callExpr.callee
    }

    override fun getCalleeClassName(callExpr: CommonCallExpr): String {
        check(callExpr is JIRCallExpr)
        return callExpr.callee.enclosingClass.simpleName
    }

    override fun getCallExpr(statement: JIRInst): JIRCallExpr? {
        return statement.callExpr
    }

    override fun getReadableValue(expr: CommonExpr): String? {
        if (expr !is JIRValue) return null
        return when (expr) {
            is JIRFieldRef -> "${expr.instance ?: expr.field.enclosingType.jirClass.simpleName}.${expr.field.name}"
            else -> expr.toString()
        }
    }

    override fun getReadableAssignee(statement: JIRInst): String? {
        if (statement !is JIRAssignInst) return null
        return when (val expr = statement.lhv) {
            is JIRFieldRef -> "${expr.instance ?: expr.field.enclosingType.jirClass.simpleName}.${expr.field.name}"
            else -> expr.toString()
        }
    }

    override fun getLocals(expr: CommonExpr): List<SarifTraits.LocalInfo> {
        expr as JIRExpr
        return expr.values.filterIsInstance<JIRLocalVar>().map { SarifTraits.LocalInfo(it.index, it.name) }
    }

    override fun getAssign(statement: JIRInst): CommonAssignInst? {
        if (statement !is JIRAssignInst) return null
        return statement
    }

    override fun lineNumber(statement: JIRInst): Int {
        return statement.lineNumber
    }

    override fun locationFQN(statement: JIRInst): String {
        val method = statement.location.method
        return "${method.enclosingClass.name}#${method.name}"
    }

    override fun locationMachineName(statement: JIRInst): String =
        "${statement.method}:${statement.location.index}:($statement)"
}

val JIRMethod.thisInstance: JIRThis
    get() = JIRThis(enclosingClass.toType())

val JIRCallExpr.callee: JIRMethod
    get() = method.method
