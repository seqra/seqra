package org.opentaint.jvm.sast.sarif

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import mu.KLogging
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry.SourceStartEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver.CallKind.CallInnerTrace
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver.CallKind.CallToSink
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver.CallKind.CallToSource
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver.InterProceduralTraceNode
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver.SourceToSinkTrace

private val logger = object : KLogging() {}.logger

sealed interface TracePathGenerationResult {
    data class Path(val path: List<List<TracePathNode>>) : TracePathGenerationResult
    data object Simple : TracePathGenerationResult
    data object Failure : TracePathGenerationResult
}

fun generateTracePath(trace: TraceResolver.Trace): TracePathGenerationResult {
    try {
        val sourceToSinkTrace = trace.sourceToSinkTrace
        val startNodes = sourceToSinkTrace.startNodes
        if (startNodes.isEmpty()) {
            logger.error { "Trace has no start nodes" }
            return TracePathGenerationResult.Failure
        }

        val singleNode = startNodes.singleOrNull()
        if (singleNode != null && singleNode is TraceResolver.SimpleTraceNode) {
            // trace has no additional info
            return TracePathGenerationResult.Simple
        }

        val resolvedPaths = startNodes.mapNotNull {
            val node = it as? InterProceduralTraceNode ?: return@mapNotNull null
            val path = generateSourceToSinkPath(sourceToSinkTrace, node) ?: return@mapNotNull null
            node to path
        }.distinctBy { it.first.methodEntryPoint }.map { it.second }

        if (resolvedPaths.isEmpty()) {
            logger.error { "Trace has no resolved paths" }
            return TracePathGenerationResult.Failure
        }

        return TracePathGenerationResult.Path(resolvedPaths)
    } catch (ex: Throwable) {
        logger.error(ex) { "Failed to generate trace path" }
        return TracePathGenerationResult.Failure
    }
}

enum class TracePathNodeKind {
    SOURCE, SINK, CALL, RETURN, OTHER
}

data class TracePathNode(val statement: CommonInst, val kind: TracePathNodeKind, val entry: TraceEntry?)

private fun generateSourceToSinkPath(
    trace: SourceToSinkTrace,
    startNode: InterProceduralTraceNode
): List<TracePathNode>? {
    val callToSourceTrace = resolveStartToSource(trace, startNode, startNode.methodEntryPoint.statement)

    val startTraceNode = callToSourceTrace.firstOrNull()
        ?: return null

    val callToSinkTrace = resolveStartToSink(trace, startNode, startTraceNode)
        ?: return null

    val path = mutableListOf<TracePathNode>()

    var sourceNodeGenerated = false
    val callToSourceNoStart = callToSourceTrace.drop(1)

    for (call in callToSourceNoStart) {
        path += TracePathNode(call.callStatement, TracePathNodeKind.CALL, entry = null)
    }

    for (call in callToSourceNoStart.asReversed()) {
        var callPath = call.trace
        if (!sourceNodeGenerated) {
            val sourceNode = callPath.firstOrNull()
            if (sourceNode == null) {
                path.removeLast()
                continue
            }

            sourceNodeGenerated = true
            path += TracePathNode(sourceNode.statement, TracePathNodeKind.SOURCE, sourceNode)
            callPath = callPath.drop(1)
        }

        path += resolveCallPath(trace, call.node, callPath)

        path += TracePathNode(call.callStatement, TracePathNodeKind.RETURN, entry = null)
    }

    for ((idx, call) in callToSinkTrace.withIndex()) {
        var callPath = call.trace
        if (!sourceNodeGenerated) {
            val sourceNode = callPath.firstOrNull() ?: return null

            sourceNodeGenerated = true
            path += TracePathNode(sourceNode.statement, TracePathNodeKind.SOURCE, sourceNode)
            callPath = callPath.drop(1)
        }

        path += resolveCallPath(trace, call.node, callPath)

        if (idx == callToSinkTrace.lastIndex) {
            val sinkNode = callPath.lastOrNull() ?: return null

            path.removeLast()
            path += TracePathNode(sinkNode.statement, TracePathNodeKind.SINK, sinkNode)
        }
    }

    return path
}

private fun resolveCallPath(
    trace: SourceToSinkTrace,
    traceNode: InterProceduralTraceNode,
    callPath: List<TraceEntry>,
    stack: HashSet<CommonInst> = hashSetOf(),
): List<TracePathNode> {
    val path = mutableListOf<TracePathNode>()
    for (node in callPath) {
        if (node.statement !in stack) {
            val innerTraces = trace.findInnerCallEntries(traceNode, node)
            if (innerTraces != null) {
                val innerTrace = innerTraces.map { it.node }
                    .filterIsInstance<TraceResolver.InterProceduralFullTraceNode>()
                    .firstOrNull()

                if (innerTrace != null) {
                    path += TracePathNode(node.statement, TracePathNodeKind.CALL, node)
                    val innerPath = generateIntraProceduralPath(innerTrace.trace).orEmpty()
                    val newStack = HashSet<CommonInst>(stack)
                    newStack.add(node.statement)
                    path += resolveCallPath(trace, innerTrace, innerPath, newStack)
                }
            }
        }
        path += TracePathNode(node.statement, TracePathNodeKind.OTHER, node)
    }
    return path
}

private fun SourceToSinkTrace.findInnerCallEntries(
    traceNode: InterProceduralTraceNode,
    node: TraceEntry
): List<TraceResolver.InterProceduralCall>? {
    if (node !is TraceEntry.Action) return null

    val action = node.primaryAction
    if (action !is TraceEntryAction.CallSummary) return null

    return findSuccessors(traceNode, CallInnerTrace, node.statement, action.summaryTrace)
}

data class CallTrace(
    val callStatement: CommonInst,
    val trace: List<TraceEntry>,
    val node: InterProceduralTraceNode,
)

private fun resolveStartToSource(
    trace: SourceToSinkTrace,
    startNode: InterProceduralTraceNode,
    startStatement: CommonInst
): List<CallTrace> {
    val callTrace = mutableListOf<CallTrace>()
    val visitedNodes = hashSetOf<InterProceduralTraceNode>()

    var node = startNode
    var statement: CommonInst = startStatement

    while (true) {
        if (!visitedNodes.add(node)) return callTrace

        when (node) {
            is TraceResolver.InterProceduralFullTraceNode -> {
                val path = generateIntraProceduralPath(node.trace) ?: return callTrace
                callTrace += CallTrace(statement, path, node)

                val pathStart = path.firstOrNull() ?: return callTrace

                val pathStartSummary = (pathStart as? SourceStartEntry)?.sourcePrimaryAction as? TraceEntryAction.CallSourceSummary
                    ?: return callTrace

                val callNode = trace.findSuccessors(node, kind = CallToSource, pathStart.statement, pathStartSummary.summaryTrace)
                    .minByOrNull { it.priority() }
                    ?: return callTrace

                node = callNode.node
                statement = callNode.statement
            }

            is TraceResolver.InterProceduralSummaryTraceNode -> TODO()
        }
    }
}

private data class StartToSinkResolverState(
    val callTrace: PersistentList<CallTrace>,
    val node: InterProceduralTraceNode,
    val traceNode: CallTrace
)

private fun resolveStartToSink(
    trace: SourceToSinkTrace,
    startNode: InterProceduralTraceNode,
    startTraceNode: CallTrace
): List<CallTrace>? {
    val visitedNodes = hashSetOf<InterProceduralTraceNode>()

    val unprocessed = ArrayDeque<StartToSinkResolverState>()
    unprocessed.add(StartToSinkResolverState(persistentListOf(startTraceNode), startNode, startTraceNode))

    while (unprocessed.isNotEmpty()) {
        val (callTrace, node, traceNode) = unprocessed.removeFirst()

        if (node in trace.sinkNodes) return callTrace
        if (!visitedNodes.add(node)) continue

        val lastStatement = traceNode.trace.lastOrNull()?.statement ?: continue

        for (callNode in trace.findSuccessors(node, kind = CallToSink, lastStatement)) {
            val path = when (val nextNode = callNode.node) {
                is TraceResolver.InterProceduralFullTraceNode -> {
                    generateIntraProceduralPath(nextNode.trace) ?: continue
                }

                is TraceResolver.InterProceduralSummaryTraceNode -> {
                    listOf(nextNode.trace.final)
                }
            }

            val nextTraceNode = CallTrace(callNode.statement, path, callNode.node)
            unprocessed += StartToSinkResolverState(
                callTrace.add(nextTraceNode),
                callNode.node,
                nextTraceNode
            )
        }
    }

    return null
}

private fun generateIntraProceduralPath(
    trace: MethodTraceResolver.FullTrace
): PersistentList<TraceEntry>? {
    val unprocessed = ArrayDeque<Pair<TraceEntry, PersistentList<TraceEntry>>>()
    unprocessed.addFirst(trace.startEntry to persistentListOf<TraceEntry>(trace.startEntry))
    val visited = hashSetOf<TraceEntry>()

    while (unprocessed.isNotEmpty()) {
        val (entry, path) = unprocessed.removeFirst()

        if (entry == trace.final) {
            return path
        }

        if (!visited.add(entry)) continue

        trace.successors[entry]?.forEach {
            unprocessed.addLast(it to path.add(it))
        }
    }

    return null
}

private fun TraceResolver.InterProceduralCall.priority(): Int {
    return when (val n = node) {
        is TraceResolver.InterProceduralSummaryTraceNode -> -1
        is TraceResolver.InterProceduralFullTraceNode -> {
            when (val start = n.trace.startEntry) {
                is TraceEntry.MethodEntry -> 10
                is SourceStartEntry -> start.priority()
            }
        }
    }
}

private fun SourceStartEntry.priority(): Int {
    if (sourcePrimaryAction != null) return 2
    if (sourceOtherActions.any { it is TraceEntryAction.EntryPointSourceRule }) return 0
    if (sourceOtherActions.any { it is TraceEntryAction.CallSourceRule }) return 1
    return 3
}
