package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.analysis.MethodSummaryEdgeProcessor

class JIRMethodSummaryEdgeProcessor(
    private val analysisContext: JIRMethodAnalysisContext
): MethodSummaryEdgeProcessor {
    override fun processSummaryEdge(edge: Edge): List<Edge> {
        if (edge !is Edge.FactToFact) return listOf(edge)

        val compatibilityFilter = edge.initialFactAp.compatibilityFilter(analysisContext.factTypeChecker)
        val compatibleFact = edge.factAp.filterFact(compatibilityFilter)
            ?: return emptyList()

        val compatibleEdge = Edge.FactToFact(edge.methodEntryPoint, edge.initialFactAp, edge.statement, compatibleFact)
        return listOf(compatibleEdge)
    }
}
