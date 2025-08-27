package org.opentaint.dataflow.jvm.ap.ifds.sarif

import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.ThreadFlow
import io.github.detekt.sarif4k.ThreadFlowLocation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ap.ifds.TaintSinkTracker
import org.opentaint.dataflow.jvm.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.dataflow.sarif.instToSarifLocation
import java.io.OutputStream

class SarifGenerator(
    cp: JIRClasspath,
    private val sourceFileResolver: SourceFileResolver<JIRInst>
) {
    private val json = Json {
        prettyPrint = true
    }

    private val jirTraits = JIRTraits(cp)

    data class TraceGenerationStats(
        var total: Int = 0,
        var simple: Int = 0,
        var generatedSuccess: Int = 0,
        var generationFailed: Int = 0,
    )

    val traceGenerationStats = TraceGenerationStats()

    @OptIn(ExperimentalSerializationApi::class)
    fun generateSarif(
        output: OutputStream,
        traces: Sequence<Pair<TaintSinkTracker.TaintVulnerability, TraceResolver.Trace>>
    ) {
        val sarifResults = traces.mapNotNull { generateSarifResult(it.first, it.second) }
        val run = LazyToolRunReport(
            tool = generateSarifAnalyzerToolDescription(),
            results = sarifResults,
        )

        val sarifReport = LazySarifReport.fromRuns(listOf(run))
        json.encodeToStream(sarifReport, output)
    }

    private fun generateSarifResult(
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace
    ): Result? {
        val ruleId = vulnerability.rule.cwe.firstOrNull()?.let { "CWE-$it" } ?: return null
        val ruleMessage = Message(text = "${vulnerability.rule.ruleNote} ${vulnerability.rule.cwe}")
        val level = Level.Warning

        val sinkLocation = statementLocation(vulnerability.statement)

        val codeFlow = generateCodeFlow(trace)

        return Result(
            ruleID = ruleId,
            message = ruleMessage,
            level = level,
            locations = listOfNotNull(sinkLocation),
            codeFlows = listOfNotNull(codeFlow)
        )
    }

    private fun generateCodeFlow(trace: TraceResolver.Trace): CodeFlow? {
        traceGenerationStats.total++

        val generatedTracePaths = generateTracePath(trace)
        val paths = when (generatedTracePaths) {
            TracePathGenerationResult.Failure -> {
                traceGenerationStats.generationFailed++
                return null
            }

            TracePathGenerationResult.Simple -> {
                traceGenerationStats.simple++
                return null
            }

            is TracePathGenerationResult.Path -> {
                traceGenerationStats.generatedSuccess++
                generatedTracePaths.path
            }
        }

        val flows = paths.map { generateThreadFlow(it) }
        return CodeFlow(threadFlows = flows)
    }

    private fun generateThreadFlow(path: List<TracePathNode>): ThreadFlow {
        val flowLocations = path.mapIndexed { idx, node ->
            val kind = when (node.kind) {
                TracePathNodeKind.SOURCE -> "taint"
                TracePathNodeKind.SINK -> "taint"
                TracePathNodeKind.CALL -> "call"
                TracePathNodeKind.RETURN -> "return"
                TracePathNodeKind.OTHER -> "unknown"
            }

            ThreadFlowLocation(
                index = idx.toLong(),
                executionOrder = idx.toLong(),
                kinds = listOf(kind),
                location = statementLocation(node.statement)
            )
        }

        return ThreadFlow(locations = flowLocations)
    }

    private val locationsCache = hashMapOf<JIRInst, Location?>()
    private fun statementLocation(statement: JIRInst): Location? = with(jirTraits) {
        locationsCache.computeIfAbsent(statement) {
            instToSarifLocation(statement, sourceFileResolver)
        }
    }
}
