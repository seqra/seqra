package org.opentaint.ir.api.python

/**
 * Visitor for PIR instructions.
 */
interface PIRInstVisitor<out T> {
    fun visitAssign(inst: PIRAssign): T
    fun visitStoreAttr(inst: PIRStoreAttr): T
    fun visitStoreSubscript(inst: PIRStoreSubscript): T
    fun visitStoreGlobal(inst: PIRStoreGlobal): T
    fun visitStoreClosure(inst: PIRStoreClosure): T
    fun visitCall(inst: PIRCall): T
    fun visitNextIter(inst: PIRNextIter): T
    fun visitUnpack(inst: PIRUnpack): T
    fun visitGoto(inst: PIRGoto): T
    fun visitBranch(inst: PIRBranch): T
    fun visitReturn(inst: PIRReturn): T
    fun visitRaise(inst: PIRRaise): T
    fun visitExceptHandler(inst: PIRExceptHandler): T
    fun visitYield(inst: PIRYield): T
    fun visitYieldFrom(inst: PIRYieldFrom): T
    fun visitAwait(inst: PIRAwait): T
    fun visitDeleteLocal(inst: PIRDeleteLocal): T
    fun visitDeleteAttr(inst: PIRDeleteAttr): T
    fun visitDeleteSubscript(inst: PIRDeleteSubscript): T
    fun visitDeleteGlobal(inst: PIRDeleteGlobal): T
    fun visitUnreachable(inst: PIRUnreachable): T

    /**
     * Default implementation: all methods delegate to defaultVisit.
     */
    interface Default<out T> : PIRInstVisitor<T> {
        fun defaultVisit(inst: PIRInstruction): T

        override fun visitAssign(inst: PIRAssign): T = defaultVisit(inst)
        override fun visitStoreAttr(inst: PIRStoreAttr): T = defaultVisit(inst)
        override fun visitStoreSubscript(inst: PIRStoreSubscript): T = defaultVisit(inst)
        override fun visitStoreGlobal(inst: PIRStoreGlobal): T = defaultVisit(inst)
        override fun visitStoreClosure(inst: PIRStoreClosure): T = defaultVisit(inst)
        override fun visitCall(inst: PIRCall): T = defaultVisit(inst)
        override fun visitNextIter(inst: PIRNextIter): T = defaultVisit(inst)
        override fun visitUnpack(inst: PIRUnpack): T = defaultVisit(inst)
        override fun visitGoto(inst: PIRGoto): T = defaultVisit(inst)
        override fun visitBranch(inst: PIRBranch): T = defaultVisit(inst)
        override fun visitReturn(inst: PIRReturn): T = defaultVisit(inst)
        override fun visitRaise(inst: PIRRaise): T = defaultVisit(inst)
        override fun visitExceptHandler(inst: PIRExceptHandler): T = defaultVisit(inst)
        override fun visitYield(inst: PIRYield): T = defaultVisit(inst)
        override fun visitYieldFrom(inst: PIRYieldFrom): T = defaultVisit(inst)
        override fun visitAwait(inst: PIRAwait): T = defaultVisit(inst)
        override fun visitDeleteLocal(inst: PIRDeleteLocal): T = defaultVisit(inst)
        override fun visitDeleteAttr(inst: PIRDeleteAttr): T = defaultVisit(inst)
        override fun visitDeleteSubscript(inst: PIRDeleteSubscript): T = defaultVisit(inst)
        override fun visitDeleteGlobal(inst: PIRDeleteGlobal): T = defaultVisit(inst)
        override fun visitUnreachable(inst: PIRUnreachable): T = defaultVisit(inst)
    }
}

/**
 * Visitor for PIR expressions (right-hand sides of PIRAssign).
 */
interface PIRExprVisitor<out T> {
    // Compound expressions
    fun visitBinExpr(expr: PIRBinExpr): T
    fun visitUnaryExpr(expr: PIRUnaryExpr): T
    fun visitCompareExpr(expr: PIRCompareExpr): T
    fun visitAttrExpr(expr: PIRAttrExpr): T
    fun visitSubscriptExpr(expr: PIRSubscriptExpr): T
    fun visitListExpr(expr: PIRListExpr): T
    fun visitTupleExpr(expr: PIRTupleExpr): T
    fun visitSetExpr(expr: PIRSetExpr): T
    fun visitDictExpr(expr: PIRDictExpr): T
    fun visitSliceExpr(expr: PIRSliceExpr): T
    fun visitStringExpr(expr: PIRStringExpr): T
    fun visitIterExpr(expr: PIRIterExpr): T
    fun visitTypeCheckExpr(expr: PIRTypeCheckExpr): T
    // Values (also expressions)
    fun visitValue(value: PIRValue): T
}

/**
 * Visitor for PIR values.
 */
interface PIRValueVisitor<out T> {
    fun visitLocal(value: PIRLocal): T
    fun visitParameterRef(value: PIRParameterRef): T
    fun visitIntConst(value: PIRIntConst): T
    fun visitFloatConst(value: PIRFloatConst): T
    fun visitStrConst(value: PIRStrConst): T
    fun visitBoolConst(value: PIRBoolConst): T
    fun visitNoneConst(value: PIRNoneConst): T
    fun visitEllipsisConst(value: PIREllipsisConst): T
    fun visitBytesConst(value: PIRBytesConst): T
    fun visitComplexConst(value: PIRComplexConst): T
    fun visitGlobalRef(value: PIRGlobalRef): T

    interface Default<out T> : PIRValueVisitor<T> {
        fun defaultVisitValue(value: PIRValue): T

        override fun visitLocal(value: PIRLocal): T = defaultVisitValue(value)
        override fun visitParameterRef(value: PIRParameterRef): T = defaultVisitValue(value)
        override fun visitIntConst(value: PIRIntConst): T = defaultVisitValue(value)
        override fun visitFloatConst(value: PIRFloatConst): T = defaultVisitValue(value)
        override fun visitStrConst(value: PIRStrConst): T = defaultVisitValue(value)
        override fun visitBoolConst(value: PIRBoolConst): T = defaultVisitValue(value)
        override fun visitNoneConst(value: PIRNoneConst): T = defaultVisitValue(value)
        override fun visitEllipsisConst(value: PIREllipsisConst): T = defaultVisitValue(value)
        override fun visitBytesConst(value: PIRBytesConst): T = defaultVisitValue(value)
        override fun visitComplexConst(value: PIRComplexConst): T = defaultVisitValue(value)
        override fun visitGlobalRef(value: PIRGlobalRef): T = defaultVisitValue(value)
    }
}
