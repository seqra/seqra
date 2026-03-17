package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.CodeFlow
import io.github.detekt.sarif4k.Level
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
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRArrayAccess
import org.opentaint.ir.api.jvm.cfg.JIRFieldRef
import org.opentaint.ir.api.jvm.cfg.JIRInstLocation
import org.opentaint.ir.api.jvm.cfg.JIRRef
import org.opentaint.ir.api.jvm.cfg.JIRValue
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.ast.AstSpanResolverProvider
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import org.opentaint.jvm.sast.project.servlet.ServletAnnotator
import org.opentaint.jvm.sast.project.spring.SpringAnnotator
import org.opentaint.semgrep.pattern.RuleMetadata
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.path.absolutePathString

class SarifGenerator(
    private val options: SarifGenerationOptions,
    private val sourceRoot: Path?,
    sourceFileResolver: JIRSourceFileResolver,
    private val traits: SarifTraits<CommonMethod, CommonInst>
) {
    private val spanResolver = AstSpanResolverProvider(traits as JIRSarifTraits)
    private val locationResolver = LocationResolver(sourceFileResolver, traits, spanResolver)
    private val annotators = listOf(
        SpringAnnotator(sourceFileResolver, spanResolver),
        ServletAnnotator(sourceFileResolver, spanResolver),
    )

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
        val sarifReport = generateSarif(traces, metadatas)
        json.encodeToStream(sarifReport, output)
    }

    fun generateSarif(
        traces: Sequence<VulnerabilityWithTrace>,
        metadatas: List<RuleMetadata>
    ): LazySarifReport {
        val sarifResults = traces.mapNotNull { generateSarifResult(it.vulnerability, it.trace) }

        val uriBase = options.uriBase ?: sourceRoot?.absolutePathString()
        val sourceUri = uriBase?.let {
            mapOf(SarifGenerationOptions.LOCATION_URI to ArtifactLocation(uri = it))
        }

        val run = LazyToolRunReport(
            tool = generateSarifAnalyzerToolDescription(metadatas, options),
            originalURIBaseIDS = sourceUri,
            results = sarifResults,
        )

        val sarifReport = LazySarifReport.fromRuns(listOf(run))
        return sarifReport
    }

    private fun generateSarifResult(
        vulnerability: TaintSinkTracker.TaintVulnerability,
        trace: TraceResolver.Trace?
    ): Result? {
        val vulnerabilityRule = vulnerability.rule
        val ruleId = vulnerabilityRule.id
        val ruleMessage = Message(text = vulnerabilityRule.meta.message)
        val level = when (vulnerabilityRule.meta.severity) {
            Severity.Note -> Level.Note
            Severity.Warning -> Level.Warning
            Severity.Error -> Level.Error
        }

        val sinkType = if (vulnerabilityRule is TaintMethodEntrySink) LocationType.RuleMethodEntry else LocationType.Simple

        val tracePaths = generateTracePaths(trace)
        val threadFlows = tracePaths?.map { generateThreadFlow(it, vulnerabilityRule.meta.message) }

        val sinkLocation = statementLocation(vulnerability.statement, sinkType, threadFlows)
            ?: run {
                logger.error("Invalid vulnerability location: $vulnerability")
                return null
            }

        var partialFingerPrints: Map<String, String>? = null

        if (options.generateFingerprint) {
            val fingerprint = computeFingerprint(ruleId, sinkLocation, threadFlows)
            partialFingerPrints = mapOf(
                "vulnerabilityWithTraceHash/v1" to fingerprint
            )
        }

        val resolvedSinkLocation = locationResolver.generateSarifLocation(sinkLocation)
        val resolvedThreadFlows = threadFlows?.map {
            ThreadFlow(locations = locationResolver.resolve(it))
        }

        var result = Result(
            ruleID = options.formatRuleId(ruleId),
            message = ruleMessage,
            level = level,
            partialFingerprints = partialFingerPrints,
            locations = listOf(resolvedSinkLocation),
            codeFlows = resolvedThreadFlows.orEmpty().map { tf -> CodeFlow(threadFlows = listOf(tf)) }
        )

        for (annotator in annotators) {
            result = annotator.annotateSarif(result, vulnerability, trace, tracePaths.orEmpty()) { s ->
                val loc = statementLocation(s, LocationType.WebInfoRelated, relevantLocations = null)
                    ?: return@annotateSarif null

                locationResolver.generateSarifLocation(loc)
            }
        }

        return result
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun computeFingerprint(
        ruleId: String,
        vulnerabilityLocation: IntermediateLocation,
        traces: List<List<IntermediateLocation>>?
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ruleId.toByteArray())
        digest.addLocationFingerprint(vulnerabilityLocation)
        traces?.forEach { trace ->
            trace.forEach { digest.addLocationFingerprint(it) }
        }
        val digestData = digest.digest()
        return Base64.encode(digestData)
    }

    private fun MessageDigest.addLocationFingerprint(loc: IntermediateLocation) {
        val instLoc = loc.inst.location as? JIRInstLocation ?: return
        update(instLoc.method.enclosingClass.name.toByteArray())
        update(instLoc.method.name.toByteArray())
        update(instLoc.index.toString().toByteArray())
    }

    private fun generateTracePaths(trace: TraceResolver.Trace?): List<List<TracePathNode>>? {
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

        var limitedTracePaths = paths
        if (options.sarifCodeFlowLimit != null) {
            limitedTracePaths = paths.take(options.sarifCodeFlowLimit)
        }

        return limitedTracePaths
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

    private fun generateThreadFlow(path: List<TracePathNode>, sinkMessage: String): List<IntermediateLocation> {
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
                type = if (groupNode.isMultiple) LocationType.Multiple else LocationType.Simple,
                message = groupNode.message,
                node = if (!groupNode.isMultiple) groupNode.node else null,
            )
        }
        return flowLocations
    }

    private fun getInstructionInfo(statement: CommonInst, rewriteLine: Boolean = false): InstructionInfo = with(traits) {
        InstructionInfo(
            fullyQualified = locationFQN(statement),
            machineName = locationMachineName(statement),
            lineNumber = lineNumber(statement),
            noExtraResolve = rewriteLine
        )
    }

    private fun statementLocation(
        statement: CommonInst,
        type: LocationType,
        relevantLocations: List<List<IntermediateLocation>>?
    ): IntermediateLocation? {
        if (TraceMessageBuilder.isGeneratedLocation(statement)) {
            val normalLocation = TraceMessageBuilder.tryResolveNormalGeneratedLocation(statement)
                ?: return null
            return statementLocation(normalLocation, type, relevantLocations)
        }

        if (TraceMessageBuilder.isAbnormalLocation(statement)) {
            val normalLocation = TraceMessageBuilder.tryResolveNormalLocation(statement, relevantLocations)
                ?: return null
            return statementLocation(normalLocation, type, relevantLocations)
        }

        return IntermediateLocation(
            inst = statement,
            info = getInstructionInfo(statement),
            kind = "",
            message = null,
            type = type,
        )
    }

    companion object {
        val logger = object : KLogging() {}.logger
    }
}
