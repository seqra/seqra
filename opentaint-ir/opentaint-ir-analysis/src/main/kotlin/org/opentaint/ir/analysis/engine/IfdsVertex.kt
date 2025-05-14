package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRInst

data class IfdsVertex(val statement: JIRInst, val domainFact: DomainFact) {
    val method: JIRMethod
        get() = statement.location.method
}
