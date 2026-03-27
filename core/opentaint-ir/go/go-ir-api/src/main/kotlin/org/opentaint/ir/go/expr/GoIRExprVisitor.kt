package org.opentaint.ir.go.expr

interface GoIRExprVisitor<out T> {
    fun visitAlloc(expr: GoIRAllocExpr): T
    fun visitBinOp(expr: GoIRBinOpExpr): T
    fun visitUnOp(expr: GoIRUnOpExpr): T
    fun visitChangeType(expr: GoIRChangeTypeExpr): T
    fun visitConvert(expr: GoIRConvertExpr): T
    fun visitMultiConvert(expr: GoIRMultiConvertExpr): T
    fun visitChangeInterface(expr: GoIRChangeInterfaceExpr): T
    fun visitSliceToArrayPointer(expr: GoIRSliceToArrayPointerExpr): T
    fun visitMakeInterface(expr: GoIRMakeInterfaceExpr): T
    fun visitTypeAssert(expr: GoIRTypeAssertExpr): T
    fun visitMakeClosure(expr: GoIRMakeClosureExpr): T
    fun visitMakeMap(expr: GoIRMakeMapExpr): T
    fun visitMakeChan(expr: GoIRMakeChanExpr): T
    fun visitMakeSlice(expr: GoIRMakeSliceExpr): T
    fun visitFieldAddr(expr: GoIRFieldAddrExpr): T
    fun visitField(expr: GoIRFieldExpr): T
    fun visitIndexAddr(expr: GoIRIndexAddrExpr): T
    fun visitIndex(expr: GoIRIndexExpr): T
    fun visitSlice(expr: GoIRSliceExpr): T
    fun visitLookup(expr: GoIRLookupExpr): T
    fun visitRange(expr: GoIRRangeExpr): T
    fun visitNext(expr: GoIRNextExpr): T
    fun visitSelect(expr: GoIRSelectExpr): T
    fun visitExtract(expr: GoIRExtractExpr): T

    /** Default implementation that delegates all methods to a single handler. */
    interface Default<out T> : GoIRExprVisitor<T> {
        fun defaultVisit(expr: GoIRExpr): T

        override fun visitAlloc(expr: GoIRAllocExpr) = defaultVisit(expr)
        override fun visitBinOp(expr: GoIRBinOpExpr) = defaultVisit(expr)
        override fun visitUnOp(expr: GoIRUnOpExpr) = defaultVisit(expr)
        override fun visitChangeType(expr: GoIRChangeTypeExpr) = defaultVisit(expr)
        override fun visitConvert(expr: GoIRConvertExpr) = defaultVisit(expr)
        override fun visitMultiConvert(expr: GoIRMultiConvertExpr) = defaultVisit(expr)
        override fun visitChangeInterface(expr: GoIRChangeInterfaceExpr) = defaultVisit(expr)
        override fun visitSliceToArrayPointer(expr: GoIRSliceToArrayPointerExpr) = defaultVisit(expr)
        override fun visitMakeInterface(expr: GoIRMakeInterfaceExpr) = defaultVisit(expr)
        override fun visitTypeAssert(expr: GoIRTypeAssertExpr) = defaultVisit(expr)
        override fun visitMakeClosure(expr: GoIRMakeClosureExpr) = defaultVisit(expr)
        override fun visitMakeMap(expr: GoIRMakeMapExpr) = defaultVisit(expr)
        override fun visitMakeChan(expr: GoIRMakeChanExpr) = defaultVisit(expr)
        override fun visitMakeSlice(expr: GoIRMakeSliceExpr) = defaultVisit(expr)
        override fun visitFieldAddr(expr: GoIRFieldAddrExpr) = defaultVisit(expr)
        override fun visitField(expr: GoIRFieldExpr) = defaultVisit(expr)
        override fun visitIndexAddr(expr: GoIRIndexAddrExpr) = defaultVisit(expr)
        override fun visitIndex(expr: GoIRIndexExpr) = defaultVisit(expr)
        override fun visitSlice(expr: GoIRSliceExpr) = defaultVisit(expr)
        override fun visitLookup(expr: GoIRLookupExpr) = defaultVisit(expr)
        override fun visitRange(expr: GoIRRangeExpr) = defaultVisit(expr)
        override fun visitNext(expr: GoIRNextExpr) = defaultVisit(expr)
        override fun visitSelect(expr: GoIRSelectExpr) = defaultVisit(expr)
        override fun visitExtract(expr: GoIRExtractExpr) = defaultVisit(expr)
    }
}
