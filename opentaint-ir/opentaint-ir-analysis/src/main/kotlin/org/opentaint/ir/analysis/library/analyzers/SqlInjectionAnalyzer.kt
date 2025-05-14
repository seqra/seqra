package org.opentaint.ir.analysis.library.analyzers

import org.opentaint.ir.analysis.engine.AnalyzerFactory
import org.opentaint.ir.analysis.engine.IfdsVertex
import org.opentaint.ir.analysis.sarif.SarifMessage
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.api.analysis.JIRApplicationGraph

class SqlInjectionAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int
) : TaintAnalyzer(graph, maxPathLength) {
    override val generates = isSourceMethodToGenerates(sqlSourceMatchers.asMethodMatchers)
    override val sanitizes = isSanitizeMethodToSanitizes(sqlSanitizeMatchers.asMethodMatchers)
    override val sinks = isSinkMethodToSinks(sqlSinkMatchers.asMethodMatchers)

    companion object {
        private const val ruleId: String = "SQL-injection"
        private val vulnerabilityMessage = SarifMessage("SQL query with unchecked injection")

        val vulnerabilityDescription = VulnerabilityDescription(vulnerabilityMessage, ruleId)
    }

    override fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription = vulnerabilityDescription
}

class SqlInjectionBackwardAnalyzer(
    graph: JIRApplicationGraph,
    maxPathLength: Int
) : TaintBackwardAnalyzer(graph, maxPathLength) {
    override val generates = isSourceMethodToGenerates(sqlSourceMatchers.asMethodMatchers)
    override val sinks = isSinkMethodToSinks(sqlSinkMatchers.asMethodMatchers)
}

fun SqlInjectionAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    SqlInjectionAnalyzer(graph, maxPathLength)
}

fun SqlInjectionBackwardAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    SqlInjectionBackwardAnalyzer(graph, maxPathLength)
}

private val sqlSourceMatchers: List<String> = listOf(
    "java\\.io.+", // TODO
    // "java\\.lang\\.System\\#getenv", // in config
    // "java\\.sql\\.ResultSet#get.+" // in config
)

private val sqlSanitizeMatchers: List<String> = listOf(
    // "java\\.sql\\.Statement#set.*", // Remove
    // "java\\.sql\\.PreparedStatement#set.*" // TODO
)

private val sqlSinkMatchers: List<String> = listOf(
    // "java\\.sql\\.Statement#execute.*", // in config
    // "java\\.sql\\.PreparedStatement#execute.*", // in config
)
