package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation

data class IfdsVertex<Method, Location, Statement>(
    val statement: Statement, val domainFact: DomainFact
) where Location : CoreInstLocation<Method>,
        Statement : CoreInst<Location, Method, *> {
    val method: Method
        get() = statement.location.method
}