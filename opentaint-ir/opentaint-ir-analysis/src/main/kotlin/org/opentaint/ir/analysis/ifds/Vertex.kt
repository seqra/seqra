package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class Vertex<out Fact, out Method, out Statement>(
    val statement: Statement,
    val fact: Fact,
) where Method : CommonMethod<Method, Statement>,
        Statement : CommonInst<Method, Statement> {

    val method: Method
        get() = statement.location.method
}
