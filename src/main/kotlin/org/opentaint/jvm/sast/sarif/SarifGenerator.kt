package org.opentaint.jvm.sast.sarif

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
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.util.SarifTraits
import java.io.OutputStream

class SarifGenerator(
    private val sourceFileResolver: org.opentaint.dataflow.sarif.SourceFileResolver<CommonInst>,
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

        val codeFlow = generateCodeFlow(trace, vulnerabilityRule.meta.message, ruleId)

        return Result(
            ruleID = ruleId,
            message = ruleMessage,
            level = level,
            locations = listOfNotNull(sinkLocation),
            codeFlows = listOfNotNull(codeFlow)
        )
    }

    private fun generateCodeFlow(trace: TraceResolver.Trace?, sinkMessage: String, ruleId: String): CodeFlow? {
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

        // create a better choosing algorithm
        return paths.firstOrNull()?.let { CodeFlow(threadFlows = listOf(generateThreadFlow(it, sinkMessage, ruleId))) }
    }

    private fun areTracesRelative(a: TracePathNode, b: TracePathNode): Boolean {
        val aSource = getCachedSourceLocation(a.statement, sourceFileResolver)
        val bSource = getCachedSourceLocation(b.statement, sourceFileResolver)
        if (aSource == null || bSource == null) return false
        // indexes are also an important part of being relative
        // it's checked in groupRelativeTraces by only comparing neighbouring traces
        return traits.lineNumber(a.statement) == traits.lineNumber(b.statement)
                && aSource == bSource
    }

    private fun groupRelativeTraces(traces: List<TracePathNode>): List<List<TracePathNode>> {
        val result = mutableListOf<List<TracePathNode>>()
        var curList = mutableListOf<TracePathNode>()
        var prev: TracePathNode? = null
        for (trace in traces) {
            if (prev != null && areTracesRelative(prev, trace)) {
                curList.add(trace)
            }
            else {
                if (prev != null) result.add(curList)
                curList = mutableListOf()
                curList.add(trace)
            }
            prev = trace
        }
        result.add(curList)
        return result
    }

    private fun isRepetitionOfAssign(a: List<TracePathNode>, b: List<TracePathNode>): Boolean {
        if (a.size != 1 || b.size != 1) return false
        val aNode = a[0]
        val bNode = a[0]
        if (aNode.entry !is MethodTraceResolver.TraceEntry.Action
            || bNode.entry !is MethodTraceResolver.TraceEntry.Action)
            return false
        val aAssignee = traits.getReadableAssignee(aNode.statement)
        val bAssignee = traits.getReadableAssignee(bNode.statement)
        return aAssignee == bAssignee
    }

    private fun removeRepetitiveAssigns(groups: List<List<TracePathNode>>): List<List<TracePathNode>> {
        val result = mutableListOf<List<TracePathNode>>()

        val reversed = groups.asReversed()
        var prevNode: List<TracePathNode>? = null
        for (curNode in reversed) {
            if (prevNode == null) {
                prevNode = curNode
                result += curNode
                continue
            }
            if (isRepetitionOfAssign(curNode, prevNode)) {
                continue
            }
            prevNode = null
            result += curNode
        }

        return result.reversed()
    }

    private fun generateThreadFlow(path: List<TracePathNode>, sinkMessage: String, ruleId: String): ThreadFlow {
        val messageBuilder = TraceMessageBuilder(traits, sinkMessage, ruleId)
        val filteredLocations = path.filter { messageBuilder.isGoodTrace(it) }
        val groupedLocations = groupRelativeTraces(filteredLocations)
        val filteredGroups = removeRepetitiveAssigns(groupedLocations)
        val groupsWithMsges = filteredGroups.flatMap { group ->
            if (group.isEmpty())
                return@flatMap listOf<TracePathNodeWithMsg>()

            messageBuilder.createGroupTraceMessage(group)
        }
        val flowLocations = groupsWithMsges.mapIndexed { idx, groupNode ->
            val insnLoc =
                if (groupNode.node.entry is MethodTraceResolver.TraceEntry.MethodEntry

                    // todo
                    /*|| groupNode.node.entry is MethodTraceResolver.TraceEntry.EntryPointSourceRule*/

                    ) {
                    // this is an attempt to highlight the method signature instead of its first bytecode instruction
                    // for the MethodEntry traces
                    // will fail if the source has extra lines between method declaration and its body
                    // (i.e. blank lines, extra parameter indentation, or comments)
                    val firstInsn = groupNode.node.statement.location.method.flowGraph().entries.firstOrNull()
                    checkNotNull(firstInsn)
                    instToSarifLocation(firstInsn, -1)
                }
                else instToSarifLocation(groupNode.node.statement, 0)

            ThreadFlowLocation(
                index = idx.toLong(),
                executionOrder = idx.toLong(),
                kinds = listOf(groupNode.kind),
                location = insnLoc.copy(message = Message(text = groupNode.message)),
            )
        }

        return ThreadFlow(locations = flowLocations)
    }

    private fun statementLocation(statement: CommonInst, message: String? = null): Location =
        instToSarifLocation(statement)
            .copy(message = message?.let { Message(text = it )})

    private val locationsCache = hashMapOf<CommonInst, String?>()
    private fun <Statement : CommonInst> getCachedSourceLocation(
        inst: Statement,
        sourceFileResolver: org.opentaint.dataflow.sarif.SourceFileResolver<Statement>,
    ): String? =
        locationsCache.computeIfAbsent(inst) {
            sourceFileResolver.resolve(inst)
        }

    private fun <Statement : CommonInst> instToSarifLocation(
        inst: Statement,
        offset: Int = 0
    ): Location = with(traits) {
        val sourceLocation = getCachedSourceLocation(inst, sourceFileResolver)
        return Location(
            physicalLocation = sourceLocation?.let {
                PhysicalLocation(
                    artifactLocation = ArtifactLocation(uri = it),
                    region = Region(
                        startLine = lineNumber(inst).toLong() + offset
                    )
                )
            },
            logicalLocations = listOf(
                LogicalLocation(
                    fullyQualifiedName = locationFQN(inst),
                    decoratedName = locationMachineName(inst)
                )
            ),
        )
    }
}
