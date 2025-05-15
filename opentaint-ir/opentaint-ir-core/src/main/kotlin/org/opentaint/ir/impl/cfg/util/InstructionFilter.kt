package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.cfg.DefaultJIRRawInstVisitor
import org.opentaint.ir.api.cfg.JIRRawInst

class InstructionFilter(val predicate: (JIRRawInst) -> Boolean) : DefaultJIRRawInstVisitor<Boolean> {
    override val defaultInstHandler: (JIRRawInst) -> Boolean
        get() = { predicate(it) }
}
