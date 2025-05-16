package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class Edge<out Fact, out Method, out Statement>(
    val from: Vertex<Fact, Method, Statement>,
    val to: Vertex<Fact, Method, Statement>,
) where Method : CommonMethod<Method, Statement>,
        Statement : CommonInst<Method, Statement> {

    init {
        require(from.method == to.method)
    }

    val method: Method
        get() = from.method
}
