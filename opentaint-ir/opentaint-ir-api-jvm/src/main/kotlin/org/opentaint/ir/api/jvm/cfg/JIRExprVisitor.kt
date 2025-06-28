package org.opentaint.ir.api.jvm.cfg

interface JIRValueVisitor<out T> {
    fun visitExternalJIRValue(value: JIRValue): T

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

    interface Default<out T> : JIRValueVisitor<T> {
        fun defaultVisitJIRValue(value: JIRValue): T

        override fun visitExternalJIRValue(value: JIRValue): T = defaultVisitJIRValue(value)

        override fun visitJIRThis(value: JIRThis): T = defaultVisitJIRValue(value)
        override fun visitJIRArgument(value: JIRArgument): T = defaultVisitJIRValue(value)
        override fun visitJIRLocalVar(value: JIRLocalVar): T = defaultVisitJIRValue(value)
        override fun visitJIRFieldRef(value: JIRFieldRef): T = defaultVisitJIRValue(value)
        override fun visitJIRArrayAccess(value: JIRArrayAccess): T = defaultVisitJIRValue(value)
        override fun visitJIRBool(value: JIRBool): T = defaultVisitJIRValue(value)
        override fun visitJIRByte(value: JIRByte): T = defaultVisitJIRValue(value)
        override fun visitJIRChar(value: JIRChar): T = defaultVisitJIRValue(value)
        override fun visitJIRShort(value: JIRShort): T = defaultVisitJIRValue(value)
        override fun visitJIRInt(value: JIRInt): T = defaultVisitJIRValue(value)
        override fun visitJIRLong(value: JIRLong): T = defaultVisitJIRValue(value)
        override fun visitJIRFloat(value: JIRFloat): T = defaultVisitJIRValue(value)
        override fun visitJIRDouble(value: JIRDouble): T = defaultVisitJIRValue(value)
        override fun visitJIRNullConstant(value: JIRNullConstant): T = defaultVisitJIRValue(value)
        override fun visitJIRStringConstant(value: JIRStringConstant): T = defaultVisitJIRValue(value)
        override fun visitJIRClassConstant(value: JIRClassConstant): T = defaultVisitJIRValue(value)
        override fun visitJIRMethodConstant(value: JIRMethodConstant): T = defaultVisitJIRValue(value)
        override fun visitJIRMethodType(value: JIRMethodType): T = defaultVisitJIRValue(value)
    }
}

interface JIRExprVisitor<out T> : JIRValueVisitor<T> {
    fun visitExternalJIRExpr(expr: JIRExpr): T

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
    fun visitJIRPhiExpr(expr: JIRPhiExpr): T
    fun visitJIRLambdaExpr(expr: JIRLambdaExpr): T
    fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): T
    fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): T
    fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): T
    fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): T

    interface Default<out T> : JIRExprVisitor<T>, JIRValueVisitor.Default<T> {
        fun defaultVisitJIRExpr(expr: JIRExpr): T

        override fun defaultVisitJIRValue(value: JIRValue): T = defaultVisitJIRExpr(value)

        override fun visitExternalJIRExpr(expr: JIRExpr): T = defaultVisitJIRExpr(expr)

        override fun visitJIRAddExpr(expr: JIRAddExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRAndExpr(expr: JIRAndExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRCmpExpr(expr: JIRCmpExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRCmpgExpr(expr: JIRCmpgExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRCmplExpr(expr: JIRCmplExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRDivExpr(expr: JIRDivExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRMulExpr(expr: JIRMulExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIREqExpr(expr: JIREqExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRNeqExpr(expr: JIRNeqExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRGeExpr(expr: JIRGeExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRGtExpr(expr: JIRGtExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRLeExpr(expr: JIRLeExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRLtExpr(expr: JIRLtExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIROrExpr(expr: JIROrExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRRemExpr(expr: JIRRemExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRShlExpr(expr: JIRShlExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRShrExpr(expr: JIRShrExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRSubExpr(expr: JIRSubExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRUshrExpr(expr: JIRUshrExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRXorExpr(expr: JIRXorExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRLengthExpr(expr: JIRLengthExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRNegExpr(expr: JIRNegExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRCastExpr(expr: JIRCastExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRNewExpr(expr: JIRNewExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRNewArrayExpr(expr: JIRNewArrayExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRInstanceOfExpr(expr: JIRInstanceOfExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRPhiExpr(expr: JIRPhiExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRLambdaExpr(expr: JIRLambdaExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRDynamicCallExpr(expr: JIRDynamicCallExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRVirtualCallExpr(expr: JIRVirtualCallExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRStaticCallExpr(expr: JIRStaticCallExpr): T = defaultVisitJIRExpr(expr)
        override fun visitJIRSpecialCallExpr(expr: JIRSpecialCallExpr): T = defaultVisitJIRExpr(expr)
    }
}
