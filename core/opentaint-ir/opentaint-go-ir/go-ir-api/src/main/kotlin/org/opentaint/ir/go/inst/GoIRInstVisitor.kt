package org.opentaint.ir.go.inst

interface GoIRInstVisitor<out T> {
    // Value-defining
    fun visitAssign(inst: GoIRAssignInst): T
    fun visitPhi(inst: GoIRPhi): T
    fun visitCall(inst: GoIRCall): T

    // Terminators
    fun visitJump(inst: GoIRJump): T
    fun visitIf(inst: GoIRIf): T
    fun visitReturn(inst: GoIRReturn): T
    fun visitPanic(inst: GoIRPanic): T

    // Effect-only
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

        override fun visitAssign(inst: GoIRAssignInst) = defaultVisit(inst)
        override fun visitPhi(inst: GoIRPhi) = defaultVisit(inst)
        override fun visitCall(inst: GoIRCall) = defaultVisit(inst)
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
