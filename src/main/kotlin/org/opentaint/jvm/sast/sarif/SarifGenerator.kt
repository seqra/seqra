package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Level
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.Result
import io.github.detekt.sarif4k.ThreadFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.api.jvm.cfg.JIRRef
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import org.opentaint.jvm.sast.project.spring.annotateSarifWithSpringRelatedInformation
import org.opentaint.semgrep.pattern.RuleMetadata
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class SarifGenerator(
    private val options: SarifGenerationOptions,
    private val sourceRoot: Path?,
    sourceFileResolver: SourceFileResolver<CommonInst>,
    private val traits: SarifTraits<CommonMethod, CommonInst>
) {
    private val locationResolver = LocationResolver(sourceFileResolver, traits)

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
        traces: Sequence<VulnerabilityWithTrace>,
        metadatas: List<RuleMetadata>
    ) {
        val sarifResults = traces.map { generateSarifResult(it.vulnerability, it.trace) }
        val sourceUri = sourceRoot?.let {
            val loc = ArtifactLocation(uri = it.absolutePathString())
            mapOf(SarifGenerationOptions.LOCATION_URI to loc)
        }

        val run = LazyToolRunReport(
            tool = generateSarifAnalyzerToolDescription(metadatas, options),
            originalURIBaseIDS = sourceUri,
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

        val codeFlow = generateCodeFlow(trace, vulnerabilityRule.meta.message)

        var result = Result(
            ruleID = options.formatRuleId(ruleId),
            message = ruleMessage,
            level = level,
            locations = listOfNotNull(sinkLocation),
            codeFlows = listOfNotNull(codeFlow)
        )
        result = annotateSarifWithSpringRelatedInformation(result, vulnerability, trace) { s ->
            statementLocation(s)
        }
        return result
    }

    private fun generateCodeFlow(trace: TraceResolver.Trace?, sinkMessage: String): CodeFlow? {
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

        val threadFlows = paths.map { generateThreadFlow(it, sinkMessage) }

        var limitedThreadFlow = threadFlows
        if (options.sarifThreadFlowLimit != null) {
            limitedThreadFlow = limitedThreadFlow.take(options.sarifThreadFlowLimit)
        }

        return CodeFlow(threadFlows = limitedThreadFlow)
    }

    private fun areTracesRelative(a: TracePathNode, b: TracePathNode): Boolean {
        // indexes are also an important part of being relative
        // it's checked in groupRelativeTraces by only comparing neighbouring traces
        return locationResolver.statementsLocationsAreRelative(a.statement, b.statement)
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

    private fun MethodTraceResolver.TraceEntry?.isSimpleAssign(): Boolean =
        this is MethodTraceResolver.TraceEntry.Action
                && primaryAction is MethodTraceResolver.TraceEntryAction.Sequential
                && otherActions.isEmpty()

    private fun isRepetitionOfAssign(a: List<TracePathNode>, b: List<TracePathNode>): Boolean {
        if (a.size != 1 || b.size != 1) return false
        val aNode = a[0]
        val bNode = b[0]

        if (!aNode.entry.isSimpleAssign() || !bNode.entry.isSimpleAssign())
            return false

        val aAssignee = traits.getReadableAssignee(aNode.statement) ?: return false
        val bAssignee = traits.getReadableAssignee(bNode.statement) ?: return false
        return aAssignee == bAssignee
    }

    private fun isFieldReassign(fst: TracePathNode, snd: TracePathNode): Boolean {
        if (!fst.entry.isSimpleAssign() || !snd.entry.isSimpleAssign())
            return false
        if (fst.statement !is CommonAssignInst || snd.statement !is CommonAssignInst)
            return false
        val base = fst.statement.lhv
        val field = snd.statement.rhv
        if (base !is JIRValue || field !is JIRRef)
            return false
        return when (field) {
            is JIRFieldRef -> base == field.instance
            is JIRArrayAccess -> base == field.array
            else -> false
        }
    }

    private fun removeFieldReassigns(group: List<TracePathNode>): List<TracePathNode> {
        if (group.size < 2) return group
        val result = mutableListOf<TracePathNode>()
        var prev = group[0]
        for (cur in group.drop(1)) {
            if (!isFieldReassign(prev, cur))
                result.add(prev)
            prev = cur
        }
        result.add(prev)
        return result
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

    private fun TracePathNode.isRewriteAllowed(builder: TraceMessageBuilder) = with (builder) {
        !isInsideLambda() && (entry is MethodTraceResolver.TraceEntry.MethodEntry || entry.isPureEntryPoint())
    }

    private fun generateThreadFlow(path: List<TracePathNode>, sinkMessage: String): ThreadFlow {
        val messageBuilder = TraceMessageBuilder(traits, sinkMessage, path)
        val filteredLocations = path.filter { messageBuilder.isGoodTrace(it) }
        val groupedLocations = groupRelativeTraces(filteredLocations)
        val noReassigns = groupedLocations.map { removeFieldReassigns(it) }
        val filteredGroups = removeRepetitiveAssigns(noReassigns)
        val groupsWithMsges = messageBuilder.createGroupTraceMessages(filteredGroups)
        val flowLocations = groupsWithMsges.map { groupNode ->
            val inst = groupNode.node.statement
            val rewriteLine = groupNode.node.isRewriteAllowed(messageBuilder)

            IntermediateLocation(
                inst = inst,
                info = getInstructionInfo(inst, rewriteLine),
                kind = groupNode.kind,
                isMultiple = groupNode.isMultiple,
                message = groupNode.message,
                node = if (!groupNode.isMultiple) groupNode.node else null,
            )
        }

        return ThreadFlow(locations = locationResolver.resolve(flowLocations))
    }

    private fun getInstructionInfo(statement: CommonInst, rewriteLine: Boolean = false): InstructionInfo = with(traits) {
        InstructionInfo(
            fullyQualified = locationFQN(statement),
            machineName = locationMachineName(statement),
            lineNumber = lineNumber(statement),
            noExtraResolve = rewriteLine
        )
    }

    private fun statementLocation(statement: CommonInst): Location {
        val loc = IntermediateLocation(
            inst = statement,
            info = getInstructionInfo(statement),
            kind = "",
            isMultiple = false,
            message = null
        )
        return locationResolver.generateSarifLocation(loc)
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
