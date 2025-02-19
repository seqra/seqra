package org.opentaint.ir.analysis
import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.ir.analysis.analyzers.AliasAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.NpeAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.NpePrecalcBackwardAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.analyzers.TaintBackwardAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.UnusedVariableAnalyzerFactory
import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.IfdsBaseUnitRunner
import org.opentaint.ir.analysis.engine.ParallelBidiIfdsUnitRunner
import org.opentaint.ir.analysis.engine.SequentialBidiIfdsUnitRunner
import org.opentaint.ir.analysis.engine.TraceGraph
import org.opentaint.ir.analysis.graph.JIRApplicationGraphImpl
import org.opentaint.ir.analysis.graph.SimplifiedJIRApplicationGraph
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.impl.features.usagesExt

@Serializable
data class DumpableVulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val traces: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<DumpableVulnerabilityInstance>)

data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val traceGraph: TraceGraph
) {
    private fun JIRInst.prettyPrint(): String {
        return "${toString()} (${location.method}:${location.lineNumber})"
    }

    fun toDumpable(maxPathsCount: Int): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            traceGraph.sources.map { it.statement.prettyPrint() },
            traceGraph.sink.statement.prettyPrint(),
            traceGraph.getAllTraces().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.prettyPrint() }
            }.toList()
        )
    }
}

fun List<VulnerabilityInstance>.toDumpable(maxPathsCount: Int = 3): DumpableAnalysisResult {
    return DumpableAnalysisResult(map { it.toDumpable(maxPathsCount) })
}

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)

val UnusedVariableRunner = IfdsBaseUnitRunner(UnusedVariableAnalyzerFactory)

fun newNpeRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(NpeAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(NpePrecalcBackwardAnalyzerFactory(maxPathLength)),
)

fun newAliasRunner(
    generates: (JIRInst) -> List<TaintAnalysisNode>,
    isSink: (JIRInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) = ParallelBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(AliasAnalyzerFactory(generates, isSink, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(maxPathLength)),
)

suspend fun JIRClasspath.newApplicationGraph(bannedPackagePrefixes: List<String>? = null): JIRApplicationGraph {
    val mainGraph = JIRApplicationGraphImpl(this, usagesExt())
    return if (bannedPackagePrefixes != null) {
        SimplifiedJIRApplicationGraph(mainGraph, bannedPackagePrefixes)
    } else {
        SimplifiedJIRApplicationGraph(mainGraph)
    }
}

internal val logger = object : KLogging() {}.logger