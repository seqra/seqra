
package org.opentaint.ir.impl.cfg.util

import org.opentaint.ir.api.JIRRawInst
import org.opentaint.ir.api.cfg.DefaultJcRawInstVisitor

class InstructionFilter(val predicate: (JIRRawInst) -> Boolean) : DefaultJcRawInstVisitor<Boolean> {
    override val defaultInstHandler: (JIRRawInst) -> Boolean
        get() = { predicate(it) }
}
