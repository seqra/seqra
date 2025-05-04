package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AnalysisDependentEvent
import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IfdsResult
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation

fun JIRAliasAnalyzerFactory(
    generates: (JIRInst) -> List<DomainFact>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    JIRAliasAnalyzer(graph as JIRApplicationGraph, generates, sanitizes, sinks, maxPathLength)
}

private class JIRAliasAnalyzer(
    graph: JIRApplicationGraph,
    override val generates: (JIRInst) -> List<DomainFact>,
    override val sanitizes: (JIRExpr, TaintNode) -> Boolean,
    override val sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer<JIRMethod, JIRInstLocation, JIRInst>(graph, maxPathLength) {
    override fun generateDescriptionForSink(sink: IfdsVertex<JIRMethod, JIRInstLocation, JIRInst>): VulnerabilityDescription =
        TODO()

    override fun handleIfdsResult(ifdsResult: IfdsResult<JIRMethod, JIRInstLocation, JIRInst>): List<AnalysisDependentEvent> =
        TODO()
}