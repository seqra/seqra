package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.JIRMethod

data class Edge<out Fact>(
    val from: Vertex<Fact>,
    val to: Vertex<Fact>,
) {
    init {
        require(from.method == to.method)
    }

    val method: JIRMethod
        get() = from.method
}
