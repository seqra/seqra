package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Summary
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.api.JIRMethod

data class SummaryEdge(
    val edge: Edge<Fact>,
) : Summary {
    override val method: JIRMethod
        get() = edge.method
}

data class Vulnerability(
    val message: String,
    val sink: Vertex<Fact>,
    val edge: Edge<Fact>? = null,
) : Summary {
    override val method: JIRMethod
        get() = sink.method
}
