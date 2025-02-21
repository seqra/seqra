package org.opentaint.ir.analysis
import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.ir.analysis.analyzers.AliasAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.NpeAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.NpePrecalcBackwardAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.SqlInjectionAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.SqlInjectionBackwardAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.analyzers.TaintAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.TaintBackwardAnalyzerFactory
import org.opentaint.ir.analysis.analyzers.TaintNode
import org.opentaint.ir.analysis.analyzers.UnusedVariableAnalyzerFactory
import org.opentaint.ir.analysis.engine.IfdsBaseUnitRunner
import org.opentaint.ir.analysis.engine.SequentialBidiIfdsUnitRunner
import org.opentaint.ir.analysis.engine.TraceGraph
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.cfg.JIRExpr
import org.opentaint.ir.api.cfg.JIRInst

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

fun newSqlInjectionRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(SqlInjectionAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(SqlInjectionBackwardAnalyzerFactory(maxPathLength)),
)

fun newNpeRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(NpeAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(NpePrecalcBackwardAnalyzerFactory(maxPathLength)),
)

fun newAliasRunner(
    generates: (JIRInst) -> List<TaintAnalysisNode>,
    sanitizes: (JIRExpr, TaintNode) -> Boolean,
    sinks: (JIRInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = IfdsBaseUnitRunner(AliasAnalyzerFactory(generates, sanitizes, sinks, maxPathLength))

fun newTaintRunner(
    isSourceMethod: (JIRMethod) -> Boolean,
    isSanitizeMethod: (JIRMethod) -> Boolean,
    isSinkMethod: (JIRMethod) -> Boolean,
    maxPathLength: Int = 5
) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(TaintAnalyzerFactory(isSourceMethod, isSanitizeMethod, isSinkMethod, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(isSourceMethod, isSinkMethod, maxPathLength))
)

fun newTaintRunner(
    sourceMethodMatchers: List<String>,
    sanitizeMethodMatchers: List<String>,
    sinkMethodMatchers: List<String>,
    maxPathLength: Int = 5
) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(TaintAnalyzerFactory(sourceMethodMatchers, sanitizeMethodMatchers, sinkMethodMatchers, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(sourceMethodMatchers, sinkMethodMatchers, maxPathLength))
)

internal val logger = object : KLogging() {}.logger