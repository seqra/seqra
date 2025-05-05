package org.opentaint.ir.api.jvm.cfg

interface JIRRawInstVisitor<T> {
    fun visitJIRRawAssignInst(inst: JIRRawAssignInst): T
    fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): T
    fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): T
    fun visitJIRRawCallInst(inst: JIRRawCallInst): T
    fun visitJIRRawLabelInst(inst: JIRRawLabelInst): T
    fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): T
    fun visitJIRRawReturnInst(inst: JIRRawReturnInst): T
    fun visitJIRRawThrowInst(inst: JIRRawThrowInst): T
    fun visitJIRRawCatchInst(inst: JIRRawCatchInst): T
    fun visitJIRRawGotoInst(inst: JIRRawGotoInst): T
    fun visitJIRRawIfInst(inst: JIRRawIfInst): T
    fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJIRRawInstVisitor<T> : JIRRawInstVisitor<T> {
    val defaultInstHandler: (JIRRawInst) -> T

    override fun visitJIRRawAssignInst(inst: JIRRawAssignInst): T = defaultInstHandler(inst)

    override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): T = defaultInstHandler(inst)

    override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): T = defaultInstHandler(inst)

    override fun visitJIRRawCallInst(inst: JIRRawCallInst): T = defaultInstHandler(inst)

    override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): T = defaultInstHandler(inst)

    override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): T = defaultInstHandler(inst)

    override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): T = defaultInstHandler(inst)

    override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): T = defaultInstHandler(inst)

    override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): T = defaultInstHandler(inst)

    override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): T = defaultInstHandler(inst)

    override fun visitJIRRawIfInst(inst: JIRRawIfInst): T = defaultInstHandler(inst)

    override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): T = defaultInstHandler(inst)

}

interface JIRRawExprVisitor<T> {
    fun visitJIRRawAddExpr(expr: JIRRawAddExpr): T
    fun visitJIRRawAndExpr(expr: JIRRawAndExpr): T
    fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr): T
    fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr): T
    fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr): T
    fun visitJIRRawDivExpr(expr: JIRRawDivExpr): T
    fun visitJIRRawMulExpr(expr: JIRRawMulExpr): T
    fun visitJIRRawEqExpr(expr: JIRRawEqExpr): T
    fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr): T
    fun visitJIRRawGeExpr(expr: JIRRawGeExpr): T
    fun visitJIRRawGtExpr(expr: JIRRawGtExpr): T
    fun visitJIRRawLeExpr(expr: JIRRawLeExpr): T
    fun visitJIRRawLtExpr(expr: JIRRawLtExpr): T
    fun visitJIRRawOrExpr(expr: JIRRawOrExpr): T
    fun visitJIRRawRemExpr(expr: JIRRawRemExpr): T
    fun visitJIRRawShlExpr(expr: JIRRawShlExpr): T
    fun visitJIRRawShrExpr(expr: JIRRawShrExpr): T
    fun visitJIRRawSubExpr(expr: JIRRawSubExpr): T
    fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr): T
    fun visitJIRRawXorExpr(expr: JIRRawXorExpr): T
    fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr): T
    fun visitJIRRawNegExpr(expr: JIRRawNegExpr): T
    fun visitJIRRawCastExpr(expr: JIRRawCastExpr): T
    fun visitJIRRawNewExpr(expr: JIRRawNewExpr): T
    fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): T
    fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): T
    fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): T
    fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): T
    fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): T
    fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): T
    fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): T

    fun visitJIRRawThis(value: JIRRawThis): T
    fun visitJIRRawArgument(value: JIRRawArgument): T
    fun visitJIRRawLocalVar(value: JIRRawLocalVar): T
    fun visitJIRRawFieldRef(value: JIRRawFieldRef): T
    fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): T
    fun visitJIRRawBool(value: JIRRawBool): T
    fun visitJIRRawByte(value: JIRRawByte): T
    fun visitJIRRawChar(value: JIRRawChar): T
    fun visitJIRRawShort(value: JIRRawShort): T
    fun visitJIRRawInt(value: JIRRawInt): T
    fun visitJIRRawLong(value: JIRRawLong): T
    fun visitJIRRawFloat(value: JIRRawFloat): T
    fun visitJIRRawDouble(value: JIRRawDouble): T
    fun visitJIRRawNullConstant(value: JIRRawNullConstant): T
    fun visitJIRRawStringConstant(value: JIRRawStringConstant): T
    fun visitJIRRawClassConstant(value: JIRRawClassConstant): T
    fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): T
    fun visitJIRRawMethodType(value: JIRRawMethodType): T
}

@JvmDefaultWithoutCompatibility
interface DefaultJIRRawExprVisitor<T> : JIRRawExprVisitor<T> {
    val defaultExprHandler: (JIRRawExpr) -> T

    override fun visitJIRRawAddExpr(expr: JIRRawAddExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawAndExpr(expr: JIRRawAndExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawDivExpr(expr: JIRRawDivExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawMulExpr(expr: JIRRawMulExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawEqExpr(expr: JIRRawEqExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawGeExpr(expr: JIRRawGeExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawGtExpr(expr: JIRRawGtExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawLeExpr(expr: JIRRawLeExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawLtExpr(expr: JIRRawLtExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawOrExpr(expr: JIRRawOrExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawRemExpr(expr: JIRRawRemExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawShlExpr(expr: JIRRawShlExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawShrExpr(expr: JIRRawShrExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawSubExpr(expr: JIRRawSubExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawXorExpr(expr: JIRRawXorExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawNegExpr(expr: JIRRawNegExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawCastExpr(expr: JIRRawCastExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawNewExpr(expr: JIRRawNewExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): T = defaultExprHandler(expr)

    override fun visitJIRRawThis(value: JIRRawThis): T = defaultExprHandler(value)

    override fun visitJIRRawArgument(value: JIRRawArgument): T = defaultExprHandler(value)

    override fun visitJIRRawLocalVar(value: JIRRawLocalVar): T = defaultExprHandler(value)

    override fun visitJIRRawFieldRef(value: JIRRawFieldRef): T = defaultExprHandler(value)

    override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): T = defaultExprHandler(value)

    override fun visitJIRRawBool(value: JIRRawBool): T = defaultExprHandler(value)

    override fun visitJIRRawByte(value: JIRRawByte): T = defaultExprHandler(value)

    override fun visitJIRRawChar(value: JIRRawChar): T = defaultExprHandler(value)

    override fun visitJIRRawShort(value: JIRRawShort): T = defaultExprHandler(value)

    override fun visitJIRRawInt(value: JIRRawInt): T = defaultExprHandler(value)

    override fun visitJIRRawLong(value: JIRRawLong): T = defaultExprHandler(value)

    override fun visitJIRRawFloat(value: JIRRawFloat): T = defaultExprHandler(value)

    override fun visitJIRRawDouble(value: JIRRawDouble): T = defaultExprHandler(value)

    override fun visitJIRRawNullConstant(value: JIRRawNullConstant): T = defaultExprHandler(value)

    override fun visitJIRRawStringConstant(value: JIRRawStringConstant): T = defaultExprHandler(value)

    override fun visitJIRRawClassConstant(value: JIRRawClassConstant): T = defaultExprHandler(value)

    override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): T = defaultExprHandler(value)

    override fun visitJIRRawMethodType(value: JIRRawMethodType): T = defaultExprHandler(value)
}
