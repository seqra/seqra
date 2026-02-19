package org.opentaint.jvm.sast.sarif

import io.github.detekt.sarif4k.ArtifactLocation
import io.github.detekt.sarif4k.Location
import io.github.detekt.sarif4k.LogicalLocation
import io.github.detekt.sarif4k.Message
import io.github.detekt.sarif4k.PhysicalLocation
import io.github.detekt.sarif4k.Region
import io.github.detekt.sarif4k.ThreadFlowLocation
import mu.KLogging
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.dataflow.util.SarifTraits
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRRawInst
import org.opentaint.ir.api.jvm.cfg.JIRRawLineNumberInst
import org.opentaint.ir.impl.cfg.graphs.GraphDominators
import org.opentaint.ir.impl.features.classpaths.virtual.JIRVirtualClass
import org.opentaint.jvm.sast.ast.JavaAstSpanResolver
import org.opentaint.jvm.sast.mostOuterClass
import org.opentaint.jvm.sast.project.KotlinInlineFunctionScopeTransformer
import org.opentaint.jvm.sast.project.KotlinInlineFunctionScopeTransformer.LAMBDA_MARKER
import org.opentaint.jvm.sast.project.KotlinInlineFunctionScopeTransformer.ScopeDescriptor
import org.opentaint.jvm.sast.project.KotlinInlineFunctionScopeTransformer.ScopeManageEvent
import org.opentaint.jvm.sast.project.KotlinInlineFunctionScopeTransformer.ScopeManageType
import org.opentaint.jvm.sast.project.SarifGenerationOptions
import org.opentaint.jvm.sast.util.DebugInfo
import org.opentaint.jvm.sast.util.DebugInfoParser
import org.opentaint.jvm.sast.util.SourcePosition
import java.nio.file.Path

data class LocationSpan(
    val startLine: Int,
    val startColumn: Int?,
    val endLine: Int?,
    val endColumn: Int?,
)

data class InstructionInfo(
    val fullyQualified: String, val machineName: String, var lineNumber: Int,
    val noExtraResolve: Boolean = false
)

enum class LocationType {
    Simple, Multiple, RuleMethodEntry, WebInfoRelated,
}

data class IntermediateLocation(
    val inst: CommonInst,
    val info: InstructionInfo,
    val kind: String,
    val message: String?,
    val type: LocationType,
    val span: LocationSpan? = null,
    val node: TracePathNode? = null,
)

class LocationResolver(
    private val sourceFileResolver: SourceFileResolver<CommonInst>,
    private val traits: SarifTraits<CommonMethod, CommonInst>,
    private val spanResolver: JavaAstSpanResolver
) {
    fun resolve(locations: List<IntermediateLocation>): List<ThreadFlowLocation> {
        var currentIdx = 0
        var prevInlineStack: List<ScopeDescriptor> = emptyList()
        var prevMethod: CommonMethod? = null
        val result = mutableListOf<ThreadFlowLocation>()

        locations.forEach { loc ->
            val curMethod = loc.inst.location.method

            if (prevMethod != curMethod) {
                // if method was changed, the inline stack must've been reset
                prevInlineStack = emptyList()
            }

            val locResult = resolveLocation(loc, currentIdx, prevInlineStack)

            prevInlineStack = locResult.inlineStack
            currentIdx += locResult.flowLocations.size
            prevMethod = curMethod

            result.addAll(locResult.flowLocations)
        }

        return result
    }

    fun statementsLocationsAreRelative(a: CommonInst, b: CommonInst): Boolean {
        val aSource = sourceFileResolver.resolveByInst(a)
        val bSource = sourceFileResolver.resolveByInst(b)
        if (aSource == null || bSource == null) return false

        return traits.lineNumber(a) == traits.lineNumber(b) && aSource == bSource
    }

    fun generateSarifLocation(location: IntermediateLocation): Location {
        val realPosition = getCachedDebugInfo(location)?.findRealPosition(location.info.lineNumber)
        val source = if (realPosition != null) {
            location.info.lineNumber = realPosition.line
            sourceFileResolver.resolveByName(location.inst, realPosition.path, realPosition.file)
        }
        else {
            sourceFileResolver.resolveByInst(location.inst)
        }
        return generateSarifLocation(location, source)
    }

    private val debugInfoCache = hashMapOf<JIRClassOrInterface, DebugInfo?>()
    private fun getCachedDebugInfo(cls: JIRClassOrInterface): DebugInfo? =
        debugInfoCache.computeIfAbsent(cls.mostOuterClass()) {
            runCatching {
                DebugInfoParser.parseOrNull(it.withAsmNode { it.sourceDebug })
            }.onFailure { logger.error(it) { "Debug info extraction failed" } }
                .getOrNull()
        }

    private fun getCachedDebugInfo(location: IntermediateLocation): DebugInfo? {
        val method = location.inst.location.method
        check(method is JIRMethod)
        if (method.enclosingClass is JIRVirtualClass) return null
        return getCachedDebugInfo(method.enclosingClass)
    }

    private data class FileLocation(val lineNumber: Int, val sourceFile: Path)

    private data class ResolvedInlineCall(
        val callLocation: FileLocation,
        val methodLocation: FileLocation,
        val methodName: String
    )

    private fun DebugInfo.findRealPosition(lineNumber: Int): SourcePosition? {
        val range = findRange(lineNumber) ?: return null
        return range.mapDestToSource(lineNumber)
    }

    private fun getFileLocation(inst: CommonInst, sourcePosition: SourcePosition): FileLocation? {
        val sourceFile = sourceFileResolver.resolveByName(inst, sourcePosition.path, sourcePosition.file) ?: return null
        return FileLocation(
            sourcePosition.line,
            sourceFile
        )
    }

    private data class InlineEntry(
        val callLine: Int,
        val firstInlineLine: Int,
        val methodName: String,
        val descriptor: ScopeDescriptor,
    )

    private fun resolveInlineEntry(inst: CommonInst, debugInfo: DebugInfo, entry: InlineEntry): ResolvedInlineCall? {
        val callPosition = debugInfo.findRealPosition(entry.callLine) ?: return null
        val methodPosition = debugInfo.findRealPosition(entry.firstInlineLine) ?: return null
        val callLocation = getFileLocation(inst, callPosition) ?: return null
        val methodLocation = getFileLocation(inst, methodPosition) ?: return null
        return ResolvedInlineCall(
            callLocation,
            methodLocation,
            entry.methodName
        )
    }

    private fun restoreInlineCalls(inst: CommonInst): List<InlineEntry> {
        inst as JIRInst

        val method = inst.location.method
        val methodGraph = method.flowGraph()
        val methodDominators = GraphDominators(methodGraph).apply { find() }

        val instDominators = methodDominators.dominators(inst)
        val inlineEvents = instDominators.mapNotNull { i ->
            KotlinInlineFunctionScopeTransformer.findInlineFunctionScopeManageInst(i)?.let { i to it }
        }

        val stack = mutableListOf<Pair<JIRInst, ScopeDescriptor>>()
        for ((i, event) in inlineEvents) {
            when (event.type) {
                ScopeManageType.START -> {
                    stack.add(i to event.scope)
                }

                ScopeManageType.END -> {
                    if (event.scope == stack.lastOrNull()?.second) {
                        stack.removeLast()
                    }
                }
            }
        }

        val rawInstList = method.rawInstList.toList()
        val result = stack.map { (i, descriptor) ->
            val event = ScopeManageEvent(ScopeManageType.START, descriptor)
            val scopeEnterIdx = rawInstList.indexOfFirst {
                KotlinInlineFunctionScopeTransformer.isInlineFunctionScopeEvent(it, event)
            }

            /*
            * usual instruction layout:
            * -2. label
            * -1. line number
            *  0. scope descriptor
            * */
            val prevLineNumber = findPrevLineNumber(scopeEnterIdx - 2, rawInstList)

            val methodName = KotlinInlineFunctionScopeTransformer.inlinedMethodName(descriptor)

            InlineEntry(
                callLine = prevLineNumber ?: -1,
                firstInlineLine = i.lineNumber,
                methodName = methodName,
                descriptor,
            )
        }

        return result
    }

    private fun findPrevLineNumber(startIdx: Int, instList: List<JIRRawInst>): Int? {
        var idx = startIdx
        while (idx >= 0) {
            val inst = instList[idx]
            idx--
            if (inst is JIRRawLineNumberInst) return inst.lineNumber
        }
        return null
    }

    private fun fallbackPhysicalLocation(location: IntermediateLocation) =
        location.info.fullyQualified.split('#').firstOrNull()?.replace('.', '/')
            ?: "<#[unresolved]#>"

    private fun computeSpan(location: IntermediateLocation, sourceFile: Path): LocationSpan? {
        if (location.inst !is JIRInst || location.type == LocationType.WebInfoRelated) return null
        return spanResolver.computeSpan(sourceFile, location)
    }

    private fun generateSarifLocation(
        location: IntermediateLocation,
        sourceFile: Path?
    ): Location {
        val span = location.span ?: sourceFile?.let { src ->
            computeSpan(location, src)
        }
        val region = if (span != null) {
            Region(
                startLine = span.startLine.toLong(),
                startColumn = span.startColumn?.toLong(),
                endLine = (span.endLine ?: span.startLine).toLong(),
                endColumn = span.endColumn?.toLong(),
            )
        } else {
            Region(
                startLine = location.info.lineNumber.toLong()
            )
        }

        val fileLocation = sourceFile?.let { sourceFileResolver.relativeToRoot(it) }
            ?: fallbackPhysicalLocation(location)

        return Location(
            physicalLocation = PhysicalLocation(
                artifactLocation = ArtifactLocation(
                    uri = fileLocation,
                    uriBaseID = SarifGenerationOptions.LOCATION_URI
                ),
                region = region
            ),
            logicalLocations = listOf(
                LogicalLocation(
                    fullyQualifiedName = location.info.fullyQualified,
                    decoratedName = location.info.machineName
                )
            ),
            message = location.message?.let { Message(text = it.capitalize()) }
        )
    }

    private fun generateThreadFlowLocation(
        location: IntermediateLocation,
        sourceFile: Path?,
        idx: Int,
    ): ThreadFlowLocation = ThreadFlowLocation(
        executionOrder = idx.toLong(),
        kinds = listOf(location.kind),
        location = generateSarifLocation(location, sourceFile)
    )

    private fun generateInlineCallLocation(
        call: ResolvedInlineCall,
        initialLocation: IntermediateLocation,
        idx: Int
    ): List<ThreadFlowLocation> {
        val callFlow = IntermediateLocation(
            inst = initialLocation.inst,
            info = initialLocation.info.copy(lineNumber = call.callLocation.lineNumber),
            kind = "call",
            message = "Inline ${call.methodName} inserted",
            type = LocationType.Simple,
        ).let { generateThreadFlowLocation(it, call.callLocation.sourceFile, idx) }
        // if it's lambda, keep the original line; otherwise, try to highlight method declaration,
        // just as with MethodEntry case
        val methodStartLineFix = if (call.methodName == LAMBDA_MARKER) 0 else -1
        val methodFlow = IntermediateLocation(
            inst = initialLocation.inst,
            info = initialLocation.info.copy(lineNumber = call.methodLocation.lineNumber + methodStartLineFix),
            kind = "unknown",
            message = "Inlined body of ${call.methodName} entered",
            type = LocationType.Simple,
        ).let { generateThreadFlowLocation(it, call.methodLocation.sourceFile, idx + 1)}
        return listOf(callFlow, methodFlow)
    }

    private fun removeSamePrefix(prevStack: List<ScopeDescriptor>, curStack: List<InlineEntry>): List<InlineEntry> {
        var prevIdx = 0
        var curIdx = 0
        val prevSize = prevStack.size
        val curSize = curStack.size
        while (prevIdx < prevSize && curIdx < curSize && prevStack[prevIdx] == curStack[curIdx].descriptor) {
            prevIdx++
            curIdx++
        }
        return curStack.drop(curIdx)
    }

    private data class LocationResolutionResult(
        val flowLocations: List<ThreadFlowLocation>,
        val inlineStack: List<ScopeDescriptor>,
    )

    private fun resolveLocation(
        location: IntermediateLocation,
        startIdx: Int,
        prevInlineStack: List<ScopeDescriptor>
    ): LocationResolutionResult {
        val debugInfo = getCachedDebugInfo(location)
        val debugRange = debugInfo?.findRange(location.info.lineNumber)

        if (debugRange == null || location.info.noExtraResolve) {
            val source = sourceFileResolver.resolveByInst(location.inst)
            if (source == null) {
                logger.warn { "Source file for ${location.info.fullyQualified} not found!" }
            }
            return LocationResolutionResult(
                listOf(generateThreadFlowLocation(location, source, startIdx)),
                emptyList(),
            )
        }

        val restoredInlines = restoreInlineCalls(location.inst)
        val curStack = restoredInlines.map { it.descriptor }
        val updatedInlines = removeSamePrefix(prevInlineStack, restoredInlines)

        val actualPosition = debugRange.mapDestToSource(location.info.lineNumber)
        val callSource = sourceFileResolver.resolveByName(location.inst, actualPosition.path, actualPosition.file)
        // cannot find source of inlined code: skipping the location as we have nothing to bind it to
            ?: return LocationResolutionResult(emptyList(), curStack)

        val flowLocations = mutableListOf<ThreadFlowLocation>()
        var currentIdx = startIdx
        for (inlineCall in updatedInlines.mapNotNull { resolveInlineEntry(location.inst, debugInfo, it) }) {
            flowLocations.addAll(generateInlineCallLocation(inlineCall, location, currentIdx))
            currentIdx += 2
        }

        val callLocation = location.copy(
            info = location.info.copy(lineNumber = actualPosition.line),
        )
        val remappedLoc = generateThreadFlowLocation(callLocation, callSource, currentIdx)
        flowLocations.add(remappedLoc)

        return LocationResolutionResult(flowLocations, curStack)
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
