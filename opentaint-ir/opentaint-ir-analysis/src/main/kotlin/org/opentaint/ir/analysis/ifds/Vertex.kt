package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst

data class Vertex<out Fact>(
    val statement: JIRInst,
    val fact: Fact,
) {
    val method: JIRMethod
        get() = statement.location.method
}
