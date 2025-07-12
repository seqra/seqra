package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class Edge<out Fact, out Statement : CommonInst>(
    val from: Vertex<Fact, Statement>,
    val to: Vertex<Fact, Statement>,
) {
    init {
        require(from.method == to.method)
    }

    val method: CommonMethod
        get() = from.method

    override fun toString(): String {
        return "(${from.fact} at ${from.statement}) -> (${to.fact} at ${to.statement}) in $method"
    }
}
