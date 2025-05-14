package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Analyzer
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst

class UnusedVariableAnalyzer(
    private val graph: JIRApplicationGraph,
) : Analyzer<Fact, Event> {

    override val flowFunctions: UnusedVariableFlowFunctions by lazy {
        UnusedVariableFlowFunctions(graph)
    }

    private fun isExitPoint(statement: JIRInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(edge: Edge<Fact>): List<Event> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }
    }

    override fun handleCrossUnitCall(caller: Vertex<Fact>, callee: Vertex<Fact>): List<Event> {
        return emptyList()
    }
}
