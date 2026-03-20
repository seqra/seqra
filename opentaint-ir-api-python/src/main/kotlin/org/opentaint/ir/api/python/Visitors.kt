package org.opentaint.ir.api.python

/**
 * Visitor for PIR instructions.
 * ~35 visit methods — one per instruction type.
 */
interface PIRInstVisitor<out T> {
    fun visitAssign(inst: PIRAssign): T
    fun visitLoadAttr(inst: PIRLoadAttr): T
    fun visitStoreAttr(inst: PIRStoreAttr): T
    fun visitLoadSubscript(inst: PIRLoadSubscript): T
    fun visitStoreSubscript(inst: PIRStoreSubscript): T
    fun visitLoadGlobal(inst: PIRLoadGlobal): T
    fun visitStoreGlobal(inst: PIRStoreGlobal): T
    fun visitLoadClosure(inst: PIRLoadClosure): T
    fun visitStoreClosure(inst: PIRStoreClosure): T
    fun visitBinOp(inst: PIRBinOp): T
    fun visitUnaryOp(inst: PIRUnaryOp): T
    fun visitCompare(inst: PIRCompare): T
    fun visitCall(inst: PIRCall): T
    fun visitBuildList(inst: PIRBuildList): T
    fun visitBuildTuple(inst: PIRBuildTuple): T
    fun visitBuildSet(inst: PIRBuildSet): T
    fun visitBuildDict(inst: PIRBuildDict): T
    fun visitBuildSlice(inst: PIRBuildSlice): T
    fun visitBuildString(inst: PIRBuildString): T
    fun visitGetIter(inst: PIRGetIter): T
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
    fun visitTypeCheck(inst: PIRTypeCheck): T
    fun visitUnreachable(inst: PIRUnreachable): T

    /**
     * Default implementation: all methods delegate to defaultVisit.
     */
    interface Default<out T> : PIRInstVisitor<T> {
        fun defaultVisit(inst: PIRInstruction): T

        override fun visitAssign(inst: PIRAssign): T = defaultVisit(inst)
        override fun visitLoadAttr(inst: PIRLoadAttr): T = defaultVisit(inst)
        override fun visitStoreAttr(inst: PIRStoreAttr): T = defaultVisit(inst)
        override fun visitLoadSubscript(inst: PIRLoadSubscript): T = defaultVisit(inst)
        override fun visitStoreSubscript(inst: PIRStoreSubscript): T = defaultVisit(inst)
        override fun visitLoadGlobal(inst: PIRLoadGlobal): T = defaultVisit(inst)
        override fun visitStoreGlobal(inst: PIRStoreGlobal): T = defaultVisit(inst)
        override fun visitLoadClosure(inst: PIRLoadClosure): T = defaultVisit(inst)
        override fun visitStoreClosure(inst: PIRStoreClosure): T = defaultVisit(inst)
        override fun visitBinOp(inst: PIRBinOp): T = defaultVisit(inst)
        override fun visitUnaryOp(inst: PIRUnaryOp): T = defaultVisit(inst)
        override fun visitCompare(inst: PIRCompare): T = defaultVisit(inst)
        override fun visitCall(inst: PIRCall): T = defaultVisit(inst)
        override fun visitBuildList(inst: PIRBuildList): T = defaultVisit(inst)
        override fun visitBuildTuple(inst: PIRBuildTuple): T = defaultVisit(inst)
        override fun visitBuildSet(inst: PIRBuildSet): T = defaultVisit(inst)
        override fun visitBuildDict(inst: PIRBuildDict): T = defaultVisit(inst)
        override fun visitBuildSlice(inst: PIRBuildSlice): T = defaultVisit(inst)
        override fun visitBuildString(inst: PIRBuildString): T = defaultVisit(inst)
        override fun visitGetIter(inst: PIRGetIter): T = defaultVisit(inst)
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
        override fun visitTypeCheck(inst: PIRTypeCheck): T = defaultVisit(inst)
        override fun visitUnreachable(inst: PIRUnreachable): T = defaultVisit(inst)
    }
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
