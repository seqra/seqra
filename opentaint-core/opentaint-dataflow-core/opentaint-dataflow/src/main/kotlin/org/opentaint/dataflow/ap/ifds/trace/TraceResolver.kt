package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry.MethodEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntry.SourceStartEntry
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

class TraceResolver(
    private val entryPointMethods: Set<CommonMethod>,
    private val manager: TaintAnalysisUnitRunnerManager,
    private val params: Params,
    private val cancellation: ProcessingCancellation
) {
    data class Params(
        val resolveEntryPointToStartTrace: Boolean = true,
        val startToSourceTraceResolutionLimit: Int? = null,
        val startToSinkTraceResolutionLimit: Int? = null,
        val sourceToSinkInnerTraceResolutionLimit: Int? = null,
        val innerCallTraceResolveStrategy: InnerCallTraceResolveStrategy = InnerCallTraceResolveStrategy.Default,
    )

    interface InnerCallTraceResolveStrategy {
        fun innerCallTraceIsRelevant(callSummary: TraceEntryAction.CallSummary): Boolean =
            callSummary.summaryEdges.any { innerCallSummaryEdgeIsRelevant(it) }

        fun innerCallSummaryEdgeIsRelevant(summaryEdge: TraceEntryAction.TraceSummaryEdge): Boolean =
            when (summaryEdge) {
                is TraceEntryAction.TraceSummaryEdge.SourceSummary -> true
                is TraceEntryAction.TraceSummaryEdge.MethodSummary -> summaryEdge.edge.fact != summaryEdge.edgeAfter.fact
            }

        object Default: InnerCallTraceResolveStrategy
    }

    data class Trace(
        val entryPointToStart: EntryPointToStartTrace?,
        val sourceToSinkTrace: SourceToSinkTrace,
    )

    data class EntryPointToStartTrace(
        val entryPoints: Set<EntryPointTraceNode>,
        val successors: Map<TraceNode, Set<TraceNode>>
    )

    data class SourceToSinkTrace(
        val startNodes: Set<SourceToSinkTraceNode>,
        val sinkNodes: Set<SourceToSinkTraceNode>,
        val successors: Map<InterProceduralTraceNode, Set<InterProceduralCall>>
    ) {
        fun findSuccessors(
            node: InterProceduralTraceNode, kind: CallKind, statement: CommonInst
        ) = successors[node]?.filter { it.kind == kind && it.statement == statement }.orEmpty()

        fun findSuccessors(
            node: InterProceduralTraceNode, kind: CallKind, statement: CommonInst, trace: MethodTraceResolver.SummaryTrace
        ) = successors[node]?.filter { it.kind == kind && it.statement == statement && it.summary == trace }.orEmpty()
    }

    sealed interface TraceNode

    sealed interface EntryPointToStartTraceNode : TraceNode

    data class CallTraceNode(val statement: CommonInst, val methodEntryPoint: MethodEntryPoint) :
        EntryPointToStartTraceNode

    data class EntryPointTraceNode(val method: CommonMethod) : EntryPointToStartTraceNode

    sealed interface SourceToSinkTraceNode : TraceNode {
        val methodEntryPoint: MethodEntryPoint
    }

    data class SimpleTraceNode(
        val statement: CommonInst,
        override val methodEntryPoint: MethodEntryPoint
    ) : SourceToSinkTraceNode

    sealed interface InterProceduralTraceNode: SourceToSinkTraceNode

    data class InterProceduralFullTraceNode(
        val trace: MethodTraceResolver.FullTrace
    ) : InterProceduralTraceNode {
        override val methodEntryPoint: MethodEntryPoint
            get() = trace.method
    }

    data class InterProceduralSummaryTraceNode(
        val trace: MethodTraceResolver.SummaryTrace
    ) : InterProceduralTraceNode {
        override val methodEntryPoint: MethodEntryPoint
            get() = trace.method
    }

    // Enum can give non-determinacy as its entries have new hash code on every JVM run.
    // Override hashcode() and equals() when using enum as a field in classes whose objects
    // can be stored in sets etc.
    enum class CallKind {
        CallToSource, CallToSink, CallInnerTrace
    }

    @Suppress("EqualsOrHashCode")
    data class InterProceduralCall(
        val kind: CallKind,
        val statement: CommonInst,
        val summary: MethodTraceResolver.SummaryTrace,
        val node: InterProceduralTraceNode
    ) {
        override fun hashCode(): Int {
            var result = kind.ordinal.hashCode()
            result = 31 * result + statement.hashCode()
            result = 31 * result + summary.hashCode()
            result = 31 * result + node.hashCode()
            return result
        }
    }

    fun resolveTrace(vulnerability: TaintVulnerability): Trace {
        when (vulnerability) {
            is TaintSinkTracker.TaintVulnerabilityWithEndFactRequirement -> {
                return resolveTrace(vulnerability.vulnerability)
            }

            is TaintSinkTracker.TaintVulnerabilityUnconditional -> {
                val node = SimpleTraceNode(vulnerability.statement, vulnerability.methodEntryPoint)
                val entryPointToStart = resolveEntryPointToStartTrace(setOf(node))
                val sourceToSinkTrace = SourceToSinkTrace(setOf(node), setOf(node), emptyMap())
                return Trace(entryPointToStart, sourceToSinkTrace)
            }

            is TaintSinkTracker.TaintVulnerabilityWithFact -> {
                val builder = InterProceduralTraceGraphBuilder()

                manager.withMethodRunner(vulnerability.methodEntryPoint) {
                    val traces = resolveIntraProceduralTraceSummary(
                        vulnerability.methodEntryPoint,
                        vulnerability.statement,
                        vulnerability.factAp,
                        includeStatement = when (vulnerability.vulnerabilityTriggerPosition) {
                            TaintSinkTracker.VulnerabilityTriggerPosition.BEFORE_INST -> false
                            TaintSinkTracker.VulnerabilityTriggerPosition.AFTER_INST -> true
                        }
                    )

                    for (trace in traces) {
                        builder.createSinkNode(trace)
                    }
                }

                val sourceToSinkTrace = builder.build()

                val entryPointToStart = resolveEntryPointToStartTrace(sourceToSinkTrace.startNodes)
                return Trace(entryPointToStart, sourceToSinkTrace)
            }
        }
    }

    private fun resolveEntryPointToStartTrace(startNodes: Set<SourceToSinkTraceNode>): EntryPointToStartTrace? {
        if (!params.resolveEntryPointToStartTrace) return null
        return EntryPointToStartTraceBuilder().build(startNodes)
    }

    @Suppress("EqualsOrHashCode")
    private data class BuilderUnprocessedTrace(
        val trace: MethodTraceResolver.SummaryTrace,
        val kind: CallKind,
        val depth: Int,
        val predecessor: InterProceduralCall? = null,
        val successor: InterProceduralCall? = null
    ) {
        override fun hashCode(): Int {
            var result = trace.hashCode()
            result = 31 * result + kind.ordinal.hashCode()
            result = 31 * result + (predecessor?.hashCode() ?: 0)
            result = 31 * result + (successor?.hashCode() ?: 0)
            return result
        }
    }

    private inner class InterProceduralTraceGraphBuilder {
        val fullNodes =
            hashMapOf<MethodEntryPoint, MutableMap<Pair<MethodTraceResolver.FullTrace, CallKind>, InterProceduralTraceNode>>()
        val summaryNodes =
            hashMapOf<MethodEntryPoint, MutableMap<Pair<MethodTraceResolver.SummaryTrace, CallKind>, List<InterProceduralTraceNode>>>()

        val sinkNodes = hashSetOf<InterProceduralTraceNode>()
        val sourceNodes = hashSetOf<InterProceduralTraceNode>()
        val rootNodes = hashSetOf<InterProceduralTraceNode>()
        val successors = hashMapOf<InterProceduralTraceNode, MutableSet<InterProceduralCall>>()

        val requestedInnerTraces = hashSetOf<InterProceduralCall>()

        val unprocessedCall2Source = mutableListOf<BuilderUnprocessedTrace>()
        val unprocessedCall2Sink = mutableListOf<BuilderUnprocessedTrace>()
        val unprocessedInner = mutableListOf<BuilderUnprocessedTrace>()

        private var startToSourceTraceResolutionStat = 0
        private var startToSinkTraceResolutionStat = 0

        fun createSinkNode(trace: MethodTraceResolver.SummaryTrace) {
            val nodes = resolveNode(trace, CallKind.CallToSink, depth = 0)
            sinkNodes.addAll(nodes)
        }

        fun build(): SourceToSinkTrace {
            process()
            removeUnresolvedInnerCalls()
            return createSource2SinkTrace()
        }

        private fun pollUnprocessedEvent(): BuilderUnprocessedTrace? {
            unprocessedCall2Sink.removeLastOrNull()?.let { return it }
            unprocessedCall2Source.removeLastOrNull()?.let { return it }
            return unprocessedInner.removeLastOrNull()
        }

        private fun addUnprocessedEvent(event: BuilderUnprocessedTrace) {
            when (event.kind) {
                CallKind.CallToSource -> unprocessedCall2Source.add(event)
                CallKind.CallToSink -> unprocessedCall2Sink.add(event)
                CallKind.CallInnerTrace -> {
                    event.predecessor?.let { requestedInnerTraces.add(it) }
                    unprocessedInner.add(event)
                }
            }
        }

        private fun process() {
            while (cancellation.isActive) {
                val event = pollUnprocessedEvent() ?: break
                val resolvedNodes = resolveNode(event.trace, event.kind, event.depth)

                for (resolved in resolvedNodes) {
                    event.predecessor?.let { predecessor ->
                        val predSucc = successors.getOrPut(predecessor.node, ::hashSetOf)
                        predSucc.add(predecessor.copy(node = resolved))
                    }

                    event.successor?.let { successor ->
                        val nodeSucc = successors.getOrPut(resolved, ::hashSetOf)
                        nodeSucc.add(successor)
                    }
                }
            }
        }

        private fun createSource2SinkTrace(): SourceToSinkTrace {
            val rootsWithReachableSources = rootNodes.filter { node ->
                entriesReachableFrom(successors, node, sourceNodes) { edge ->
                    edge.takeIf { it.kind == CallKind.CallToSource }?.node
                }
            }

            val rootsWithReachableSinks = rootsWithReachableSources.filterTo(hashSetOf()) { node ->
                entriesReachableFrom(successors, node, sinkNodes) { edge ->
                    edge.takeIf { it.kind == CallKind.CallToSink }?.node
                }
            }

            if (rootsWithReachableSinks.isEmpty()) return SourceToSinkTrace(emptySet(), emptySet(), emptyMap())

            return SourceToSinkTrace(rootsWithReachableSinks, sinkNodes, successors)
        }

        private fun removeUnresolvedInnerCalls() {
            while (cancellation.isActive) {
                val unresolvedNodes = hashMapOf<InterProceduralFullTraceNode, MutableList<InterProceduralCall>>()

                for (r in requestedInnerTraces) {
                    val node = r.node
                    if (node !is InterProceduralFullTraceNode) continue

                    val callResolved = successors[node].orEmpty()
                        .any { it.kind == r.kind && it.statement == r.statement && it.summary == r.summary }

                    if (callResolved) continue

                    unresolvedNodes.getOrPut(node, ::mutableListOf).add(r)
                }

                if (unresolvedNodes.isEmpty()) return

                for ((node, calls) in unresolvedNodes) {
                    removeUnresolvedCallsFromNode(node, calls)
                }
            }
        }

        private fun removeUnresolvedCallsFromNode(
            node: InterProceduralFullTraceNode,
            calls: List<InterProceduralCall>
        ) {
            val actions = calls.map { it.statement to it.summary }
            calls.forEach { requestedInnerTraces.remove(it) }

            val filteredTrace = removeCallActions(node.trace, actions)
            val nodeReplacement = filteredTrace?.let { InterProceduralFullTraceNode(it) }

            replaceNode(node, nodeReplacement)
        }

        private fun replaceNode(node: InterProceduralFullTraceNode, replacement: InterProceduralFullTraceNode?) {
            if (sinkNodes.remove(node)) {
                replacement?.let { sinkNodes.add(it) }
            }

            if (rootNodes.remove(node)) {
                replacement?.let { rootNodes.add(it) }
            }

            successors.remove(node)?.let { s -> replacement?.let { successors[it] = s } }

            for (nodeSuccessors in successors.values) {
                val dependentSuccessors = nodeSuccessors.filterTo(hashSetOf()) { it.node == node }
                val successorsReplacement = replacement?.let {
                    dependentSuccessors.map { s -> s.copy(node = it) }
                }

                nodeSuccessors.removeAll(dependentSuccessors)
                successorsReplacement?.let { nodeSuccessors.addAll(it) }
            }

            val dependentRequests = requestedInnerTraces.filterTo(hashSetOf()) { it.node == node }
            val requestsReplacement = replacement?.let {
                dependentRequests.map { s -> s.copy(node = it) }
            }

            requestedInnerTraces.removeAll(dependentRequests)
            requestsReplacement?.let { requestedInnerTraces.addAll(it) }
        }

        private fun removeCallActions(
            trace: MethodTraceResolver.FullTrace,
            calls: List<Pair<CommonInst, MethodTraceResolver.SummaryTrace>>
        ): MethodTraceResolver.FullTrace? = trace.filter { entry ->
            if (entry !is MethodTraceResolver.TraceEntry.Action) return@filter true

            val action = entry.primaryAction
            if (action !is TraceEntryAction.CallSummary) return@filter true

            val entryContainsCall = calls.any { call ->
                entry.statement == call.first && action.summaryTrace == call.second
            }

            !entryContainsCall
        }

        private fun resolveNode(
            trace: MethodTraceResolver.SummaryTrace,
            kind: CallKind,
            depth: Int
        ): List<InterProceduralTraceNode> {
            val traceNodes = summaryNodes.getOrPut(trace.method, ::hashMapOf)
            val cacheKey = trace to kind
            val currentNode = traceNodes[cacheKey]
            if (currentNode != null) return currentNode

            val fullTraces = manager.withMethodRunner(trace.method) {
                resolveIntraProceduralFullTrace(trace.method, trace, cancellation)
            }

            val resultNodes = mutableListOf<InterProceduralTraceNode>()

            for (fullTrace in fullTraces) {
                val innerDepth = when (kind) {
                    CallKind.CallToSource,
                    CallKind.CallToSink -> 0
                    CallKind.CallInnerTrace -> depth
                }

                addInnerTraces(fullTrace, innerDepth)

                if (kind == CallKind.CallInnerTrace) {
                    resultNodes += InterProceduralFullTraceNode(fullTrace)
                    continue
                }

                when (val start = fullTrace.startEntry) {
                    is SourceStartEntry -> {
                        resultNodes += resolveNode(fullTrace, kind, depth)
                    }

                    is MethodEntry -> {
                        check(kind != CallKind.CallToSource) { "Unexpected trace: $trace" }

                        val node = InterProceduralFullTraceNode(fullTrace)
                        resultNodes += node

                        val callerTraces = resolveMethodEntry(start)
                        for ((callerStatement, callerTrace) in callerTraces) {
                            if (params.startToSinkTraceResolutionLimit != null) {
                                if (startToSinkTraceResolutionStat++ > params.startToSinkTraceResolutionLimit) continue
                            }

                            addUnprocessedEvent(
                                BuilderUnprocessedTrace(
                                    trace = callerTrace,
                                    kind = kind,
                                    depth = depth + 1,
                                    successor = InterProceduralCall(kind, callerStatement, trace, node)
                                )
                            )
                        }
                    }
                }
            }

            traceNodes[cacheKey] = resultNodes
            return resultNodes
        }

        private fun resolveNode(trace: MethodTraceResolver.FullTrace, kind: CallKind, depth: Int): InterProceduralTraceNode {
            val traceNodes = fullNodes.getOrPut(trace.method, ::hashMapOf)
            val cacheKey = trace to kind
            val currentNode = traceNodes[cacheKey]
            if (currentNode != null) return currentNode

            when (val start = trace.startEntry) {
                is MethodEntry -> {
                    TODO("Method full traces not used in inter-procedural graph builder (yet)")
                }

                is SourceStartEntry -> {
                    val node = InterProceduralFullTraceNode(trace)
                    traceNodes[cacheKey] = node

                    if (kind == CallKind.CallToSink) {
                        rootNodes.add(node)
                    }

                    val callSummary = start.sourcePrimaryAction as? TraceEntryAction.CallSourceSummary
                    if (callSummary == null) {
                        sourceNodes.add(node)
                        return node
                    }

                    if (params.startToSourceTraceResolutionLimit != null) {
                        if (startToSourceTraceResolutionStat++ > params.startToSourceTraceResolutionLimit) {
                            sourceNodes.add(node)
                            return node
                        }
                    }

                    addUnprocessedEvent(
                        BuilderUnprocessedTrace(
                            trace = callSummary.summaryTrace,
                            kind = CallKind.CallToSource,
                            depth = depth + 1,
                            predecessor = InterProceduralCall(
                                CallKind.CallToSource,
                                start.statement,
                                callSummary.summaryTrace,
                                node
                            )
                        )
                    )

                    return node
                }
            }
        }

        private fun addInnerTraces(trace: MethodTraceResolver.FullTrace, depth: Int) {
            if (params.sourceToSinkInnerTraceResolutionLimit != null) {
                if (depth > params.sourceToSinkInnerTraceResolutionLimit) {
                    return
                }
            }

            val node = InterProceduralFullTraceNode(trace)
            val allActions = trace.successors.keys
                .filterIsInstance<MethodTraceResolver.TraceEntry.Action>()

            for (entry in allActions) {
                if (!cancellation.isActive) return

                val action = entry.primaryAction
                if (action !is TraceEntryAction.CallSummary) continue
                if (!params.innerCallTraceResolveStrategy.innerCallTraceIsRelevant(action)) continue

                val summary = action.summaryTrace
                addUnprocessedEvent(
                    BuilderUnprocessedTrace(
                        trace = summary,
                        kind = CallKind.CallInnerTrace,
                        depth = depth + 1,
                        predecessor = InterProceduralCall(
                            CallKind.CallInnerTrace,
                            entry.statement,
                            summary,
                            node
                        )
                    )
                )
            }
        }

        private fun resolveMethodEntry(
            methodEntry: MethodEntry
        ): List<Pair<CommonInst, MethodTraceResolver.SummaryTrace>> {
            val callers = manager.findMethodCallers(methodEntry.entryPoint)
            return callers.flatMap { caller ->
                manager.withMethodRunner(caller.callerEp) {
                    resolveIntraProceduralTraceSummaryFromCall(caller.callerEp, caller.statement, methodEntry)
                }.map { caller.statement to it }
            }
        }
    }

    inner class EntryPointToStartTraceBuilder {
        private val entryPointNodes = hashSetOf<EntryPointTraceNode>()
        private val nodeSuccessors = hashMapOf<TraceNode, MutableSet<TraceNode>>()

        fun build(startNodes: Set<SourceToSinkTraceNode>): EntryPointToStartTrace {
            val unprocessedMethods = mutableListOf<Pair<MethodEntryPoint, TraceNode>>()
            startNodes.mapTo(unprocessedMethods) { it.methodEntryPoint to it }

            val visitedEp = hashSetOf<Pair<MethodEntryPoint, TraceNode>>()
            while (unprocessedMethods.isNotEmpty() && cancellation.isActive) {
                val methodCall = unprocessedMethods.removeLast()
                if (!visitedEp.add(methodCall)) continue

                val (methodEp, methodCallNode) = methodCall
                if (methodEp.method in entryPointMethods) {
                    val epNode = EntryPointTraceNode(methodEp.method)
                    entryPointNodes.add(epNode)
                    nodeSuccessors.getOrPut(epNode, ::hashSetOf).add(methodCallNode)
                }

                val methodCallers = manager.findMethodCallers(methodEp)
                for (caller in methodCallers) {
                    val callNode = CallTraceNode(caller.statement, caller.callerEp)
                    nodeSuccessors.getOrPut(callNode, ::hashSetOf).add(methodCallNode)
                    unprocessedMethods += (caller.callerEp to callNode)
                }
            }

            return EntryPointToStartTrace(entryPointNodes, nodeSuccessors)
        }
    }
}
