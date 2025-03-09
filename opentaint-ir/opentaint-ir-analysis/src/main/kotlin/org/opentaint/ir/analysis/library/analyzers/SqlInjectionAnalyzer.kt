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

private val sqlSourceMatchers = listOf(
    "java\\.io.+",
    "java\\.lang\\.System\\#getenv",
    "java\\.sql\\.ResultSet#get.+"
)

private val sqlSanitizeMatchers = listOf(
    "java\\.sql\\.Statement#set.*",
    "java\\.sql\\.PreparedStatement#set.*"
)

private val sqlSinkMatchers = listOf(
    "java\\.sql\\.Statement#execute.*",
    "java\\.sql\\.PreparedStatement#execute.*",
)