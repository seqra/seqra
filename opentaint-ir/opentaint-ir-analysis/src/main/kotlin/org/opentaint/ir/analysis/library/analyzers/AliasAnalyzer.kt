package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst

fun AliasAnalyzerFactory(
    generates: (JIRInst) -> List<DomainFact>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    AliasAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private class AliasAnalyzer(
    graph: JIRApplicationGraph,
    override val generates: (JIRInst) -> List<DomainFact>,
    override val sanitizes: (JIRExpr, TaintNode) -> Boolean,
    override val sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer(graph, maxPathLength) {
    override fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription = TODO()

    override fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent> = TODO()
}