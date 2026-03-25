package org.opentaint.ir.go.inst

interface GoIRInstVisitor<out T> {
    // Value-producing
    fun visitAlloc(inst: GoIRAlloc): T
    fun visitPhi(inst: GoIRPhi): T
    fun visitBinOp(inst: GoIRBinOp): T
    fun visitUnOp(inst: GoIRUnOp): T
    fun visitCall(inst: GoIRCall): T
    fun visitChangeType(inst: GoIRChangeType): T
    fun visitConvert(inst: GoIRConvert): T
    fun visitMultiConvert(inst: GoIRMultiConvert): T
    fun visitChangeInterface(inst: GoIRChangeInterface): T
    fun visitSliceToArrayPointer(inst: GoIRSliceToArrayPointer): T
    fun visitMakeInterface(inst: GoIRMakeInterface): T
    fun visitMakeClosure(inst: GoIRMakeClosure): T
    fun visitMakeMap(inst: GoIRMakeMap): T
    fun visitMakeChan(inst: GoIRMakeChan): T
    fun visitMakeSlice(inst: GoIRMakeSlice): T
    fun visitFieldAddr(inst: GoIRFieldAddr): T
    fun visitField(inst: GoIRField): T
    fun visitIndexAddr(inst: GoIRIndexAddr): T
    fun visitIndex(inst: GoIRIndex): T
    fun visitSlice(inst: GoIRSlice): T
    fun visitLookup(inst: GoIRLookup): T
    fun visitTypeAssert(inst: GoIRTypeAssert): T
    fun visitRange(inst: GoIRRange): T
    fun visitNext(inst: GoIRNext): T
    fun visitSelect(inst: GoIRSelect): T
    fun visitExtract(inst: GoIRExtract): T

    // Effect-only
    fun visitJump(inst: GoIRJump): T
    fun visitIf(inst: GoIRIf): T
    fun visitReturn(inst: GoIRReturn): T
    fun visitPanic(inst: GoIRPanic): T
    fun visitStore(inst: GoIRStore): T
    fun visitMapUpdate(inst: GoIRMapUpdate): T
    fun visitSend(inst: GoIRSend): T
    fun visitGo(inst: GoIRGo): T
    fun visitDefer(inst: GoIRDefer): T
    fun visitRunDefers(inst: GoIRRunDefers): T
    fun visitDebugRef(inst: GoIRDebugRef): T

    /** Default implementation that delegates all methods to a single handler. */
    interface Default<out T> : GoIRInstVisitor<T> {
        fun defaultVisit(inst: GoIRInst): T

        override fun visitAlloc(inst: GoIRAlloc) = defaultVisit(inst)
        override fun visitPhi(inst: GoIRPhi) = defaultVisit(inst)
        override fun visitBinOp(inst: GoIRBinOp) = defaultVisit(inst)
        override fun visitUnOp(inst: GoIRUnOp) = defaultVisit(inst)
        override fun visitCall(inst: GoIRCall) = defaultVisit(inst)
        override fun visitChangeType(inst: GoIRChangeType) = defaultVisit(inst)
        override fun visitConvert(inst: GoIRConvert) = defaultVisit(inst)
        override fun visitMultiConvert(inst: GoIRMultiConvert) = defaultVisit(inst)
        override fun visitChangeInterface(inst: GoIRChangeInterface) = defaultVisit(inst)
        override fun visitSliceToArrayPointer(inst: GoIRSliceToArrayPointer) = defaultVisit(inst)
        override fun visitMakeInterface(inst: GoIRMakeInterface) = defaultVisit(inst)
        override fun visitMakeClosure(inst: GoIRMakeClosure) = defaultVisit(inst)
        override fun visitMakeMap(inst: GoIRMakeMap) = defaultVisit(inst)
        override fun visitMakeChan(inst: GoIRMakeChan) = defaultVisit(inst)
        override fun visitMakeSlice(inst: GoIRMakeSlice) = defaultVisit(inst)
        override fun visitFieldAddr(inst: GoIRFieldAddr) = defaultVisit(inst)
        override fun visitField(inst: GoIRField) = defaultVisit(inst)
        override fun visitIndexAddr(inst: GoIRIndexAddr) = defaultVisit(inst)
        override fun visitIndex(inst: GoIRIndex) = defaultVisit(inst)
        override fun visitSlice(inst: GoIRSlice) = defaultVisit(inst)
        override fun visitLookup(inst: GoIRLookup) = defaultVisit(inst)
        override fun visitTypeAssert(inst: GoIRTypeAssert) = defaultVisit(inst)
        override fun visitRange(inst: GoIRRange) = defaultVisit(inst)
        override fun visitNext(inst: GoIRNext) = defaultVisit(inst)
        override fun visitSelect(inst: GoIRSelect) = defaultVisit(inst)
        override fun visitExtract(inst: GoIRExtract) = defaultVisit(inst)
        override fun visitJump(inst: GoIRJump) = defaultVisit(inst)
        override fun visitIf(inst: GoIRIf) = defaultVisit(inst)
        override fun visitReturn(inst: GoIRReturn) = defaultVisit(inst)
        override fun visitPanic(inst: GoIRPanic) = defaultVisit(inst)
        override fun visitStore(inst: GoIRStore) = defaultVisit(inst)
        override fun visitMapUpdate(inst: GoIRMapUpdate) = defaultVisit(inst)
        override fun visitSend(inst: GoIRSend) = defaultVisit(inst)
        override fun visitGo(inst: GoIRGo) = defaultVisit(inst)
        override fun visitDefer(inst: GoIRDefer) = defaultVisit(inst)
        override fun visitRunDefers(inst: GoIRRunDefers) = defaultVisit(inst)
        override fun visitDebugRef(inst: GoIRDebugRef) = defaultVisit(inst)
    }
}
