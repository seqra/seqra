package org.opentaint.dataflow.ap.ifds.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.ThreadFlow
import io.github.detekt.sarif4k.ThreadFlowLocation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.dataflow.util.SarifTraits
import java.io.OutputStream

class SarifGenerator(
    private val sourceFileResolver: SourceFileResolver<CommonInst>,
    private val traits: SarifTraits<CommonMethod, CommonInst>
) {
    private val json = Json {
        prettyPrint = true
    }

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
        traces: Sequence<VulnerabilityWithTrace>
    ) {
        val sarifResults = traces.map { generateSarifResult(it.vulnerability, it.trace) }
        val run = LazyToolRunReport(
            tool = generateSarifAnalyzerToolDescription(),
            results = sarifResults,
        )

        val sarifReport = LazySarifReport.fromRuns(listOf(run))
        json.encodeToStream(sarifReport, output)
    }

    private fun generateSarifResult(
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?
    ): Result {
        val vulnerabilityRule = vulnerability.rule
        val ruleId = vulnerabilityRule.id
        val ruleMessage = Message(text = vulnerabilityRule.meta.message)
        val level = when (vulnerabilityRule.meta.severity) {
            Severity.Note -> Level.Note
            Severity.Warning -> Level.Warning
            Severity.Error -> Level.Error
        }

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

    private fun generateCodeFlow(trace: TraceResolver.Trace?): CodeFlow? {
        traceGenerationStats.total++

        if (trace == null) {
            traceGenerationStats.generationFailed++
            return null
        }

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

    private val locationsCache = hashMapOf<CommonInst, Location>()
    private fun statementLocation(statement: CommonInst): Location =
        locationsCache.computeIfAbsent(statement) {
            instToSarifLocation(traits, statement, sourceFileResolver)
        }

    private fun <Statement : CommonInst> instToSarifLocation(
        traits: SarifTraits<*, Statement>,
        inst: Statement,
        sourceFileResolver: SourceFileResolver<Statement>,
    ): Location = with(traits) {
        val sourceLocation = sourceFileResolver.resolve(inst)
        return Location(
            physicalLocation = sourceLocation?.let {
                PhysicalLocation(
                    artifactLocation = ArtifactLocation(uri = it),
                    region = Region(
                        startLine = lineNumber(inst).toLong()
                    )
                )
            },
            logicalLocations = listOf(
                LogicalLocation(
                    fullyQualifiedName = locationFQN(inst),
                    decoratedName = locationMachineName(inst)
                )
            )
        )
    }
}
