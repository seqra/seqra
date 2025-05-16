package org.opentaint.ir.api.jvm.cfg

interface JIRRawInstVisitor<out T> {
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

    interface Default<out T> : JIRRawInstVisitor<T> {
        fun defaultVisitJIRRawInst(inst: JIRRawInst): T

        override fun visitJIRRawAssignInst(inst: JIRRawAssignInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawEnterMonitorInst(inst: JIRRawEnterMonitorInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawExitMonitorInst(inst: JIRRawExitMonitorInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawCallInst(inst: JIRRawCallInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawLabelInst(inst: JIRRawLabelInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawLineNumberInst(inst: JIRRawLineNumberInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawReturnInst(inst: JIRRawReturnInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawThrowInst(inst: JIRRawThrowInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawCatchInst(inst: JIRRawCatchInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawGotoInst(inst: JIRRawGotoInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawIfInst(inst: JIRRawIfInst): T = defaultVisitJIRRawInst(inst)
        override fun visitJIRRawSwitchInst(inst: JIRRawSwitchInst): T = defaultVisitJIRRawInst(inst)
    }
}
