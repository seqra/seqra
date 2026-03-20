package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.analysis.MethodEdgePostProcessor
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRLanguageManager
import org.opentaint.ir.api.jvm.cfg.JIRInst

class JIRMethodSummaryEdgeProcessor(
    private val analysisContext: JIRMethodAnalysisContext,
    private val methodInstGraph: MethodInstGraph,
    private val languageManager: JIRLanguageManager,
    private val statement: JIRInst,
): MethodEdgePostProcessor {
    override fun process(edge: Edge): List<Edge> {
        if (edge !is Edge.FactToFact) return listOf(edge)
        if (!methodInstGraph.isExitPoint(languageManager, statement)) return listOf(edge)

        val compatibilityFilter = edge.initialFactAp.compatibilityFilter(analysisContext.factTypeChecker)
        val compatibleFact = edge.factAp.filterFact(compatibilityFilter)
            ?: return emptyList()

        val compatibleEdge = Edge.FactToFact(edge.methodEntryPoint, edge.initialFactAp, edge.statement, compatibleFact)
        return listOf(compatibleEdge)
    }
}
