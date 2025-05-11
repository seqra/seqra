package org.opentaint.ir.analysis.graph

import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.cfg.JIRInstLocation
import org.opentaint.ir.api.cfg.JIRInstVisitor

data class JIRNoopInst(override val location: JIRInstLocation) : JIRInst {
    override val operands: List<JIRExpr>
        get() = emptyList()

    override fun <T> accept(visitor: JIRInstVisitor<T>): T {
        return visitor.visitExternalJIRInst(this)
    }

    override fun toString(): String = "noop"
}
