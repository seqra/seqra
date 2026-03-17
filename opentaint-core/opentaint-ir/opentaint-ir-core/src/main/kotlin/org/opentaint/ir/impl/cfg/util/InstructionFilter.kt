package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInstVisitor

class InstructionFilter(
    val predicate: (JIRRawInst) -> Boolean,
) : JIRRawInstVisitor.Default<Boolean> {
    override fun defaultVisitJIRRawInst(inst: JIRRawInst): Boolean {
        return predicate(inst)
    }
}
