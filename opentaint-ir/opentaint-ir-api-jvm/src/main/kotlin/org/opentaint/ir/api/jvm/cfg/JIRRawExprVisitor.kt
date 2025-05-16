package org.opentaint.ir.api.jvm.cfg

interface JIRRawValueVisitor<out T> {
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

    interface Default<out T> : JIRRawValueVisitor<T> {
        fun defaultVisitJIRRawValue(value: JIRRawValue): T

        override fun visitJIRRawThis(value: JIRRawThis): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawArgument(value: JIRRawArgument): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawLocalVar(value: JIRRawLocalVar): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawFieldRef(value: JIRRawFieldRef): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawArrayAccess(value: JIRRawArrayAccess): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawBool(value: JIRRawBool): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawByte(value: JIRRawByte): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawChar(value: JIRRawChar): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawShort(value: JIRRawShort): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawInt(value: JIRRawInt): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawLong(value: JIRRawLong): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawFloat(value: JIRRawFloat): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawDouble(value: JIRRawDouble): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawNullConstant(value: JIRRawNullConstant): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawStringConstant(value: JIRRawStringConstant): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawClassConstant(value: JIRRawClassConstant): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawMethodConstant(value: JIRRawMethodConstant): T = defaultVisitJIRRawValue(value)
        override fun visitJIRRawMethodType(value: JIRRawMethodType): T = defaultVisitJIRRawValue(value)
    }
}

interface JIRRawExprVisitor<out T> : JIRRawValueVisitor<T> {
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

    interface Default<out T> : JIRRawExprVisitor<T>, JIRRawValueVisitor.Default<T> {
        fun visitJIRRawExpr(expr: JIRRawExpr): T

        override fun defaultVisitJIRRawValue(value: JIRRawValue): T = visitJIRRawExpr(value)

        override fun visitJIRRawAddExpr(expr: JIRRawAddExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawAndExpr(expr: JIRRawAndExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawCmpExpr(expr: JIRRawCmpExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawCmpgExpr(expr: JIRRawCmpgExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawCmplExpr(expr: JIRRawCmplExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawDivExpr(expr: JIRRawDivExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawMulExpr(expr: JIRRawMulExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawEqExpr(expr: JIRRawEqExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawNeqExpr(expr: JIRRawNeqExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawGeExpr(expr: JIRRawGeExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawGtExpr(expr: JIRRawGtExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawLeExpr(expr: JIRRawLeExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawLtExpr(expr: JIRRawLtExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawOrExpr(expr: JIRRawOrExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawRemExpr(expr: JIRRawRemExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawShlExpr(expr: JIRRawShlExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawShrExpr(expr: JIRRawShrExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawSubExpr(expr: JIRRawSubExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawUshrExpr(expr: JIRRawUshrExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawXorExpr(expr: JIRRawXorExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawLengthExpr(expr: JIRRawLengthExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawNegExpr(expr: JIRRawNegExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawCastExpr(expr: JIRRawCastExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawNewExpr(expr: JIRRawNewExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawNewArrayExpr(expr: JIRRawNewArrayExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawInstanceOfExpr(expr: JIRRawInstanceOfExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawDynamicCallExpr(expr: JIRRawDynamicCallExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawVirtualCallExpr(expr: JIRRawVirtualCallExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawInterfaceCallExpr(expr: JIRRawInterfaceCallExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawStaticCallExpr(expr: JIRRawStaticCallExpr): T = visitJIRRawExpr(expr)
        override fun visitJIRRawSpecialCallExpr(expr: JIRRawSpecialCallExpr): T = visitJIRRawExpr(expr)
    }
}
