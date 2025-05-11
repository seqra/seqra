package org.opentaint.ir.analysis.ifds2

import org.opentaint.ir.api.JIRMethod

interface Analyzer<Fact, out Event> {
    val flowFunctions: FlowFunctions<Fact>

    fun isSkipped(method: JIRMethod): Boolean = false

    fun handleNewEdge(
        edge: Edge<Fact>,
    ): List<Event>

    fun handleCrossUnitCall(
        caller: Vertex<Fact>,
        callee: Vertex<Fact>,
    ): List<Event>
}
