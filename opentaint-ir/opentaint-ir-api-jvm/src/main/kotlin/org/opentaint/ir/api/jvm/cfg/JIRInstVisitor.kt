package org.opentaint.ir.api.jvm.cfg

interface JIRInstVisitor<out T> {
    fun visitExternalJIRInst(inst: JIRInst): T

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

    interface Default<out T> : JIRInstVisitor<T> {
        fun defaultVisitJIRInst(inst: JIRInst): T

        override fun visitExternalJIRInst(inst: JIRInst): T = defaultVisitJIRInst(inst)

        override fun visitJIRAssignInst(inst: JIRAssignInst): T = defaultVisitJIRInst(inst)
        override fun visitJIREnterMonitorInst(inst: JIREnterMonitorInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRExitMonitorInst(inst: JIRExitMonitorInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRCallInst(inst: JIRCallInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRReturnInst(inst: JIRReturnInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRThrowInst(inst: JIRThrowInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRCatchInst(inst: JIRCatchInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRGotoInst(inst: JIRGotoInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRIfInst(inst: JIRIfInst): T = defaultVisitJIRInst(inst)
        override fun visitJIRSwitchInst(inst: JIRSwitchInst): T = defaultVisitJIRInst(inst)
    }
}
