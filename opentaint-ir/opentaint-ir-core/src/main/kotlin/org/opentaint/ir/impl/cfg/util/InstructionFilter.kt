package org.opentaint.opentaint-ir.impl.cfg.util

import org.opentaint.opentaint-ir.api.cfg.DefaultJIRRawInstVisitor
import org.opentaint.opentaint-ir.api.cfg.JIRRawInst

class InstructionFilter(val predicate: (JIRRawInst) -> Boolean) : DefaultJIRRawInstVisitor<Boolean> {
    override val defaultInstHandler: (JIRRawInst) -> Boolean
        get() = { predicate(it) }
}
