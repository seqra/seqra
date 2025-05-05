package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.core.cfg.CoreExprVisitor
import org.opentaint.ir.api.core.cfg.InstVisitor

interface JIRInstVisitor<T> : InstVisitor<T> {
    fun visitJIRAssignInst(inst: JIRAssignInst): T
    fun visitJIREnterMonitorInst(inst: JIREnterMonitorInst): T
    fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst): T
    fun visitJIRCallInst(inst: JIRCallInst): T
    fun visitJIRReturnInst(inst: JIRReturnInst): T
    fun visitJIRThrowInst(inst: JIRThrowInst): T
    fun visitJIRCatchInst(inst: JIRCatchInst): T
    fun visitJIRGotoInst(inst: JIRGotoInst): T
    fun visitJIRIfInst(inst: JIRIfInst): T
    fun visitJIRSwitchInst(inst: JIRSwitchInst): T
    fun visitExternalJIRInst(inst: JIRInst): T

}

@JvmDefaultWithoutCompatibility
interface DefaultJIRInstVisitor<T> : JIRInstVisitor<T> {
    val defaultInstHandler: (JIRInst) -> T

    override fun visitJIRAssignInst(inst: JIRAssignInst): T = defaultInstHandler(inst)

    override fun visitJIREnterMonitorInst(inst: JIREnterMonitorInst): T = defaultInstHandler(inst)

    override fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst): T = defaultInstHandler(inst)

    override fun visitJIRCallInst(inst: JIRCallInst): T = defaultInstHandler(inst)

    override fun visitJIRReturnInst(inst: JIRReturnInst): T = defaultInstHandler(inst)

    override fun visitJIRThrowInst(inst: JIRThrowInst): T = defaultInstHandler(inst)

    override fun visitJIRCatchInst(inst: JIRCatchInst): T = defaultInstHandler(inst)

    override fun visitJIRGotoInst(inst: JIRGotoInst): T = defaultInstHandler(inst)

    override fun visitJIRIfInst(inst: JIRIfInst): T = defaultInstHandler(inst)

    override fun visitJIRSwitchInst(inst: JIRSwitchInst): T = defaultInstHandler(inst)

    override fun visitExternalJIRInst(inst: JIRInst): T = defaultInstHandler(inst)
}

interface JIRExprVisitor<T> : CoreExprVisitor<T> {
    fun visitJIRAddExpr(expr: JIRAddExpr): T
    fun visitJIRAndExpr(expr: JIRAndExpr): T
    fun visitJIRCmpExpr(expr: JIRCmpExpr): T
    fun visitJIRCmpgExpr(expr: JIRCmpgExpr): T
    fun visitJIRCmplExpr(expr: JIRCmplExpr): T
    fun visitJIRDivExpr(expr: JIRDivExpr): T
    fun visitJIRMulExpr(expr: JIRMulExpr): T
    fun visitJIREqExpr(expr: JIREqExpr): T
    fun visitJIRNeqExpr(expr: JIRNeqExpr): T
    fun visitJIRGeExpr(expr: JIRGeExpr): T
    fun visitJIRGtExpr(expr: JIRGtExpr): T
    fun visitJIRLeExpr(expr: JIRLeExpr): T
    fun visitJIRLtExpr(expr: JIRLtExpr): T
    fun visitJIROrExpr(expr: JIROrExpr): T
    fun visitJIRRemExpr(expr: JIRRemExpr): T
    fun visitJIRShlExpr(expr: JIRShlExpr): T
    fun visitJIRShrExpr(expr: JIRShrExpr): T
    fun visitJIRSubExpr(expr: JIRSubExpr): T
    fun visitJIRUshrExpr(expr: JIRUshrExpr): T
    fun visitJIRXorExpr(expr: JIRXorExpr): T
    fun visitJIRLengthExpr(expr: JIRLengthExpr): T
    fun visitJIRNegExpr(expr: JIRNegExpr): T
    fun visitJIRCastExpr(expr: JIRCastExpr): T
    fun visitJIRNewExpr(expr: JIRNewExpr): T
    fun visitJIRNewArrayExpr(expr: JIRNewArrayExpr): T
    fun visitJIRInstanceOfExpr(expr: JIRInstanceOfExpr): T
    fun visitJIRLambdaExpr(expr: JIRLambdaExpr): T
    fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): T
    fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): T
    fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): T
    fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): T

    fun visitJIRThis(value: JIRThis): T
    fun visitJIRArgument(value: JIRArgument): T
    fun visitJIRLocalVar(value: JIRLocalVar): T
    fun visitJIRFieldRef(value: JIRFieldRef): T
    fun visitJIRArrayAccess(value: JIRArrayAccess): T
    fun visitJIRBool(value: JIRBool): T
    fun visitJIRByte(value: JIRByte): T
    fun visitJIRChar(value: JIRChar): T
    fun visitJIRShort(value: JIRShort): T
    fun visitJIRInt(value: JIRInt): T
    fun visitJIRLong(value: JIRLong): T
    fun visitJIRFloat(value: JIRFloat): T
    fun visitJIRDouble(value: JIRDouble): T
    fun visitJIRNullConstant(value: JIRNullConstant): T
    fun visitJIRStringConstant(value: JIRStringConstant): T
    fun visitJIRClassConstant(value: JIRClassConstant): T
    fun visitJIRMethodConstant(value: JIRMethodConstant): T
    fun visitJIRMethodType(value: JIRMethodType): T
    fun visitJIRPhiExpr(expr: JIRPhiExpr): T

    fun visitExternalJIRExpr(expr: JIRExpr): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJIRExprVisitor<T> : JIRExprVisitor<T> {
    val defaultExprHandler: (JIRExpr) -> T

    override fun visitJIRAddExpr(expr: JIRAddExpr): T = defaultExprHandler(expr)

    override fun visitJIRAndExpr(expr: JIRAndExpr): T = defaultExprHandler(expr)

    override fun visitJIRCmpExpr(expr: JIRCmpExpr): T = defaultExprHandler(expr)

    override fun visitJIRCmpgExpr(expr: JIRCmpgExpr): T = defaultExprHandler(expr)

    override fun visitJIRCmplExpr(expr: JIRCmplExpr): T = defaultExprHandler(expr)

    override fun visitJIRDivExpr(expr: JIRDivExpr): T = defaultExprHandler(expr)

    override fun visitJIRMulExpr(expr: JIRMulExpr): T = defaultExprHandler(expr)

    override fun visitJIREqExpr(expr: JIREqExpr): T = defaultExprHandler(expr)

    override fun visitJIRNeqExpr(expr: JIRNeqExpr): T = defaultExprHandler(expr)

    override fun visitJIRGeExpr(expr: JIRGeExpr): T = defaultExprHandler(expr)

    override fun visitJIRGtExpr(expr: JIRGtExpr): T = defaultExprHandler(expr)

    override fun visitJIRLeExpr(expr: JIRLeExpr): T = defaultExprHandler(expr)

    override fun visitJIRLtExpr(expr: JIRLtExpr): T = defaultExprHandler(expr)

    override fun visitJIROrExpr(expr: JIROrExpr): T = defaultExprHandler(expr)

    override fun visitJIRRemExpr(expr: JIRRemExpr): T = defaultExprHandler(expr)

    override fun visitJIRShlExpr(expr: JIRShlExpr): T = defaultExprHandler(expr)

    override fun visitJIRShrExpr(expr: JIRShrExpr): T = defaultExprHandler(expr)

    override fun visitJIRSubExpr(expr: JIRSubExpr): T = defaultExprHandler(expr)

    override fun visitJIRUshrExpr(expr: JIRUshrExpr): T = defaultExprHandler(expr)

    override fun visitJIRXorExpr(expr: JIRXorExpr): T = defaultExprHandler(expr)

    override fun visitJIRLengthExpr(expr: JIRLengthExpr): T = defaultExprHandler(expr)

    override fun visitJIRNegExpr(expr: JIRNegExpr): T = defaultExprHandler(expr)

    override fun visitJIRCastExpr(expr: JIRCastExpr): T = defaultExprHandler(expr)

    override fun visitJIRNewExpr(expr: JIRNewExpr): T = defaultExprHandler(expr)

    override fun visitJIRNewArrayExpr(expr: JIRNewArrayExpr): T = defaultExprHandler(expr)

    override fun visitJIRInstanceOfExpr(expr: JIRInstanceOfExpr): T = defaultExprHandler(expr)

    override fun visitJIRLambdaExpr(expr: JIRLambdaExpr): T = defaultExprHandler(expr)

    override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRThis(value: JIRThis): T = defaultExprHandler(value)

    override fun visitJIRArgument(value: JIRArgument): T = defaultExprHandler(value)

    override fun visitJIRLocalVar(value: JIRLocalVar): T = defaultExprHandler(value)

    override fun visitJIRFieldRef(value: JIRFieldRef): T = defaultExprHandler(value)

    override fun visitJIRArrayAccess(value: JIRArrayAccess): T = defaultExprHandler(value)

    override fun visitJIRBool(value: JIRBool): T = defaultExprHandler(value)

    override fun visitJIRByte(value: JIRByte): T = defaultExprHandler(value)

    override fun visitJIRChar(value: JIRChar): T = defaultExprHandler(value)

    override fun visitJIRShort(value: JIRShort): T = defaultExprHandler(value)

    override fun visitJIRInt(value: JIRInt): T = defaultExprHandler(value)

    override fun visitJIRLong(value: JIRLong): T = defaultExprHandler(value)

    override fun visitJIRFloat(value: JIRFloat): T = defaultExprHandler(value)

    override fun visitJIRDouble(value: JIRDouble): T = defaultExprHandler(value)

    override fun visitJIRNullConstant(value: JIRNullConstant): T = defaultExprHandler(value)

    override fun visitJIRStringConstant(value: JIRStringConstant): T = defaultExprHandler(value)

    override fun visitJIRClassConstant(value: JIRClassConstant): T = defaultExprHandler(value)

    override fun visitJIRMethodConstant(value: JIRMethodConstant): T = defaultExprHandler(value)

    override fun visitJIRMethodType(value: JIRMethodType): T = defaultExprHandler(value)

    override fun visitJIRPhiExpr(expr: JIRPhiExpr): T = defaultExprHandler(expr)

    override fun visitExternalJIRExpr(expr: JIRExpr): T = defaultExprHandler(expr)
}
