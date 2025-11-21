package org.opentaint.dataflow.ap.ifds.trace

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.AnalysisManager
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPrecondition
import org.opentaint.dataflow.ap.ifds.trace.MethodCallPrecondition.CallPreconditionFact
import org.opentaint.dataflow.ap.ifds.trace.MethodSequentPrecondition.SequentPrecondition
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.util.add
import org.opentaint.dataflow.util.bitSetOf
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import java.util.BitSet
import java.util.LinkedList
import java.util.Objects

class MethodTraceResolver(
    private val runner: AnalysisRunner,
    private val analysisContext: MethodAnalysisContext,
    private val edges: MethodAnalyzerEdges
) {
    private val methodEntryPoint: MethodEntryPoint = analysisContext.methodEntryPoint
    private val graph: ApplicationGraph<CommonMethod, CommonInst> get() = runner.graph
    private val analysisManager: AnalysisManager get() = runner.analysisManager
    private val manager: AnalysisUnitRunnerManager get() = runner.manager
    private val methodCallFactMapper: MethodCallFactMapper get() = analysisContext.methodCallFactMapper
    private val apManager: ApManager get() = runner.apManager

    sealed interface FullTrace {
        val method: MethodEntryPoint
        val initial: Set<TraceEntry>
        val final: TraceEntry.Final
        val successors: Map<TraceEntry, Set<TraceEntry>>
    }

    data class MethodFullTrace(
        override val method: MethodEntryPoint,
        override val initial: Set<TraceEntry.MethodEntry>,
        override val final: TraceEntry.Final,
        override val successors: Map<TraceEntry, Set<TraceEntry>>
    ) : FullTrace {
        override fun hashCode(): Int = Objects.hash(method, final)
    }

    data class SourceFullTrace(
        override val method: MethodEntryPoint,
        override val initial: Set<TraceEntry.SourceStartEntry>,
        override val final: TraceEntry.Final,
        override val successors: Map<TraceEntry, Set<TraceEntry>>
    ) : FullTrace {
        override fun hashCode(): Int = Objects.hash(method, final)
    }

    enum class TraceFinalNodeKind {
        TraceToFactFinalNode, // Trace ends within the method
        SummaryTraceFinalNode, // Trace summarizes method behaviour
    }

    sealed interface SummaryTrace {
        val method: MethodEntryPoint
        val final: TraceEntry.Final
    }

    data class MethodSummaryTrace(
        val initial: TraceEntry.MethodEntry,
        override val final: TraceEntry.Final,
    ) : SummaryTrace {
        override val method: MethodEntryPoint get() = initial.entryPoint
    }

    data class SourceSummaryTrace(
        override val method: MethodEntryPoint,
        override val final: TraceEntry.Final,
    ) : SummaryTrace

    sealed interface TraceEntry {
        val fact: InitialFactAp
        val statement: CommonInst

        sealed interface TraceStartEntry : TraceEntry

        sealed interface SourceStartEntry : TraceStartEntry

        sealed interface CallTraceEntry : TraceEntry

        data class Final(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val kind: TraceFinalNodeKind,
        ) : TraceEntry

        data class Sequential(
            override val fact: InitialFactAp,
            override val statement: CommonInst
        ) : TraceEntry

        data class Unchanged(
            override val fact: InitialFactAp,
            override val statement: CommonInst
        ) : TraceEntry

        data class MethodEntry(
            override val fact: InitialFactAp,
            val entryPoint: MethodEntryPoint
        ) : TraceStartEntry {
            override val statement: CommonInst
                get() = entryPoint.statement
        }

        data class CallSourceRule(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val rule: CommonTaintConfigurationSource,
            val action: CommonTaintAssignAction
        ) : SourceStartEntry, CallTraceEntry

        data class EntryPointSourceRule(
            override val fact: InitialFactAp,
            val entryPoint: MethodEntryPoint,
            val rule: CommonTaintConfigurationSource,
            val action: CommonTaintAssignAction
        ) : SourceStartEntry {
            override val statement: CommonInst
                get() = entryPoint.statement
        }

        data class CallRule(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val rule: CommonTaintConfigurationItem,
            val action: CommonTaintAction
        ) : TraceEntry, CallTraceEntry

        data class CallSummary(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val summaryTrace: MethodSummaryTrace,
            val factDelta: InitialFactAp.Delta,
        ) : TraceEntry, CallTraceEntry

        data class CallSourceSummary(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val summaryTrace: SourceSummaryTrace
        ) : SourceStartEntry, CallTraceEntry

        data class UnresolvedCallSkip(
            override val fact: InitialFactAp,
            override val statement: CommonInst
        ) : TraceEntry, CallTraceEntry
    }

    private class EntryManager {
        private val entries = arrayListOf<TraceEntry>()
        private val entryId = Object2IntOpenHashMap<TraceEntry>().apply { defaultReturnValue(NO_ENTRY) }

        fun entryId(entry: TraceEntry): Int {
            val currentId = entryId.getInt(entry)
            if (currentId != NO_ENTRY) return currentId

            val id = entries.size
            entries.add(entry)
            entryId.put(entry, id)
            return id
        }

        fun entryById(id: Int): TraceEntry = entries[id]

        companion object {
            private const val NO_ENTRY = -1
        }
    }

    private val entryManager = EntryManager()

    private inner class TraceBuilder(val finalEntryId: Int, val cancellation: TraceResolverCancellation) {
        val startEntryIds = BitSet()
        val processedEntryIds = BitSet().also { it.set(finalEntryId) }
        val unprocessedEntryIds = IntArrayList().also { it.add(finalEntryId) }
        val predecessors = Int2ObjectOpenHashMap<BitSet>()
        val successors = Int2ObjectOpenHashMap<BitSet>()

        fun addPredecessor(current: TraceEntry, predecessor: TraceEntry) {
            val currentId = entryManager.entryId(current)
            val predecessorId = entryManager.entryId(predecessor)

            var currentPredecessors = predecessors.get(currentId)
            if (currentPredecessors == null) {
                currentPredecessors = BitSet().also { predecessors.put(currentId, it) }
            }
            currentPredecessors.set(predecessorId)

            var currentSuccessors = successors.get(predecessorId)
            if (currentSuccessors == null) {
                currentSuccessors = BitSet().also { successors.put(predecessorId, it) }
            }
            currentSuccessors.set(currentId)

            if (!processedEntryIds.get(predecessorId)) {
                processedEntryIds.set(predecessorId)
                unprocessedEntryIds.add(predecessorId)
            }
        }

        fun addStartEntry(entry: TraceEntry) {
            startEntryIds.set(entryManager.entryId(entry))
        }
    }

    fun resolveIntraProceduralTrace(statement: CommonInst, fact: InitialFactAp): List<SummaryTrace> {
        val matchingInitialFacts = hashSetOf<InitialFactAp?>()

        val visitedStatements = hashSetOf<CommonInst>()
        val unprocessedStatements = mutableListOf(statement)

        while (unprocessedStatements.isNotEmpty()) {
            val stmt = unprocessedStatements.removeLast()
            if (!visitedStatements.add(stmt)) continue

            if (!checkFactStoredAtStatement(unprocessedStatements, stmt, fact)) continue

            if (edges.allZeroToFactFactsAtStatement(stmt, fact).any { it.contains(fact) }) {
                matchingInitialFacts.add(null)
            }

            edges.allFactToFactFactsAtStatement(stmt, fact).forEach { (initialFact, finalFact) ->
                if (finalFact.contains(fact)) {
                    matchingInitialFacts.add(initialFact)
                }
            }
        }

        return matchingInitialFacts.map { initialFact ->
            val startEntry = TraceEntry.Final(fact, statement, kind = TraceFinalNodeKind.TraceToFactFinalNode)
            if (initialFact != null) {
                MethodSummaryTrace(
                    TraceEntry.MethodEntry(initialFact, methodEntryPoint),
                    startEntry
                )
            } else {
                SourceSummaryTrace(methodEntryPoint, startEntry)
            }
        }
    }

    private fun checkFactStoredAtStatement(
        unprocessed: MutableList<CommonInst>,
        statement: CommonInst,
        fact: InitialFactAp
    ): Boolean {
        var predecessorsIsEmpty = true
        var predecessorProduceFact = false

        for (predecessor in graph.predecessors(statement)) {
            predecessorsIsEmpty = false

            if (preconditionIsUnchanged(predecessor, fact)) {
                unprocessed.add(predecessor)
            } else {
                predecessorProduceFact = true
            }
        }

        return predecessorProduceFact || predecessorsIsEmpty
    }

    fun resolveIntraProceduralTraceFromCall(
        statement: CommonInst,
        calleeEntry: TraceEntry.MethodEntry
    ): List<SummaryTrace> =
        methodCallFactMapper
            .mapMethodExitToReturnFlowFact(statement, calleeEntry.fact)
            .flatMap { resolveIntraProceduralTrace(statement, it) }

    fun resolveIntraProceduralFullTrace(
        summaryTrace: SummaryTrace,
        cancellation: TraceResolverCancellation
    ): FullTrace {
        check(summaryTrace.method == methodEntryPoint) { "Incorrect summary trace" }

        return when (summaryTrace) {
            is MethodSummaryTrace -> {
                val builder = TraceBuilder(entryManager.entryId(summaryTrace.final), cancellation)
                builder.resolveTrace(summaryTrace.initial.fact)
                builder.removeUnreachableNodes()
                builder.collapseUnchangedNodes()
                builder.methodTrace()
            }

            is SourceSummaryTrace -> {
                val builder = TraceBuilder(entryManager.entryId(summaryTrace.final), cancellation)
                builder.resolveTrace(initialFact = null)
                builder.removeUnreachableNodes()
                builder.collapseUnchangedNodes()
                builder.sourceTrace()
            }
        }
    }

    private fun TraceBuilder.removeUnreachableNodes() {
        val reachableFromStart = BitSet()
        val reachableFromFinish = BitSet()

        traverseReachableNodes(reachableFromStart, startEntryIds) { successors.get(it) ?: BitSet() }
        traverseReachableNodes(reachableFromFinish, bitSetOf(finalEntryId)) { predecessors.get(it) ?: BitSet() }

        val reachableNodes = reachableFromStart
        reachableNodes.and(reachableFromFinish)

        val unreachableNodes = successors.keys.toBitSet()
        unreachableNodes.andNot(reachableNodes)

        unreachableNodes.forEach { unreachableEntry ->
            removeUnreachableEntry(unreachableEntry)
        }
    }

    private fun TraceBuilder.removeUnreachableEntry(entryId: Int) {
        val entryPredecessorIds = predecessors.remove(entryId) ?: BitSet()
        val entrySuccessorIds = successors.remove(entryId) ?: BitSet()
        entryPredecessorIds.clear(entryId)
        entrySuccessorIds.clear(entryId)

        entryPredecessorIds.forEach { predecessorId: Int ->
            successors.get(predecessorId)?.clear(entryId)
        }

        entrySuccessorIds.forEach { successorId: Int ->
            predecessors.get(successorId)?.clear(entryId)
        }
    }

    private inline fun TraceBuilder.traverseReachableNodes(reachable: BitSet, initial: BitSet, next: (Int) -> BitSet) {
        initial.forEach { unprocessedEntryIds.add(it) }

        while (unprocessedEntryIds.isNotEmpty()) {
            val entryId = unprocessedEntryIds.removeInt(unprocessedEntryIds.lastIndex)

            if (!reachable.add(entryId)) continue

            next(entryId).forEach { unprocessedEntryIds.add(it) }
        }
    }

    private fun TraceBuilder.collapseUnchangedNodes() {
        processedEntryIds.clear()
        unprocessedEntryIds.add(finalEntryId)

        while (unprocessedEntryIds.isNotEmpty()) {
            val entryId = unprocessedEntryIds.removeInt(unprocessedEntryIds.lastIndex)

            if (!processedEntryIds.add(entryId)) continue

            if (startEntryIds.get(entryId)) continue

            val entryPredecessorIds = predecessors.get(entryId) ?: continue

            val entry = entryManager.entryById(entryId)
            if (entry is TraceEntry.Unchanged) {
                predecessors.remove(entryId)
                entryPredecessorIds.clear(entryId)

                val entrySuccessorIds = successors.remove(entryId) ?: BitSet()
                entrySuccessorIds.clear(entryId)

                entryPredecessorIds.forEach { predecessorId: Int ->
                    val predSuccessors = successors.get(predecessorId)
                    predSuccessors?.clear(entryId)
                    predSuccessors?.or(entrySuccessorIds)
                }

                entrySuccessorIds.forEach { successorId: Int ->
                    val succPredecessors = predecessors.get(successorId)
                    succPredecessors?.clear(entryId)
                    succPredecessors?.or(entryPredecessorIds)
                }
            }

            entryPredecessorIds.forEach { predecessorId: Int ->
                unprocessedEntryIds.add(predecessorId)
            }
        }
    }

    private fun TraceBuilder.sourceTrace(): FullTrace {
        val initial = hashSetOf<TraceEntry.SourceStartEntry>()
        startEntryIds.forEach { entryId: Int ->
            val entry = entryManager.entryById(entryId)
            if (entry is TraceEntry.SourceStartEntry) {
                initial.add(entry)
            }
        }

        check(initial.size == startEntryIds.cardinality()) {
            "Incorrect trace for $methodEntryPoint"
        }

        val finalEntry = entryManager.entryById(finalEntryId) as TraceEntry.Final
        return SourceFullTrace(methodEntryPoint, initial, finalEntry, successors())
    }

    private fun TraceBuilder.methodTrace(): FullTrace {
        val initial = hashSetOf<TraceEntry.MethodEntry>()
        startEntryIds.forEach { entryId: Int ->
            val entry = entryManager.entryById(entryId)
            if (entry is TraceEntry.MethodEntry) {
                initial.add(entry)
            }
        }

        check(initial.size == startEntryIds.cardinality()) {
            "Incorrect trace for $methodEntryPoint"
        }

        val finalEntry = entryManager.entryById(finalEntryId) as TraceEntry.Final
        return MethodFullTrace(methodEntryPoint, initial, finalEntry, successors())
    }

    private fun TraceBuilder.successors(): Map<TraceEntry, Set<TraceEntry>> {
        val allSuccessors = hashMapOf<TraceEntry, MutableSet<TraceEntry>>()
        for ((entryId, entryPredecessorIds) in predecessors) {
            val entry = entryManager.entryById(entryId)

            entryPredecessorIds.forEach { predecessorId: Int ->
                val predecessor = entryManager.entryById(predecessorId)
                val successors = allSuccessors.getOrPut(predecessor, ::hashSetOf)
                successors.add(entry)
            }
        }
        return allSuccessors
    }

    private fun TraceBuilder.resolveTrace(initialFact: InitialFactAp?) {
        while (unprocessedEntryIds.isNotEmpty() && cancellation.isActive) {
            val entryId = unprocessedEntryIds.removeInt(unprocessedEntryIds.lastIndex)
            val entry = entryManager.entryById(entryId)
            processTraceEntry(entry, initialFact)
        }
    }

    private fun TraceBuilder.processTraceEntry(entry: TraceEntry, initialFact: InitialFactAp?) {
        if (entry is TraceEntry.TraceStartEntry) {
            addStartEntry(entry)
            return
        }

        if (entry is TraceEntry.Final) {
            when (entry.kind) {
                TraceFinalNodeKind.TraceToFactFinalNode -> {
                    // We have fact BEFORE entry.statement, no need to propagate
                }

                TraceFinalNodeKind.SummaryTraceFinalNode -> {
                    // We have fact AFTER entry.statement
                    propagateEntry(entry.statement, entry, initialFact, skipFactCheck = true)
                    return
                }
            }
        }

        if (entry.statement == methodEntryPoint.statement) {
            propagateEntryToMethodEntryPoint(initialFact, entry)
            return
        }

        graph.predecessors(entry.statement).forEach {
            propagateEntry(it, entry, initialFact)
        }
    }

    private fun TraceBuilder.propagateEntryToMethodEntryPoint(
        initialFact: InitialFactAp?,
        entry: TraceEntry
    ) {
        // We always have fact before entry point
        if (!containsEntryFact(entry, initialFact)) return

        if (initialFact != null) {
            addPredecessor(entry, TraceEntry.MethodEntry(initialFact, methodEntryPoint))
        } else {
            val preconditionFunction = analysisManager.getMethodStartPrecondition(apManager, analysisContext)
            preconditionFunction.factPrecondition(entry.fact).forEach {
                val predEntry = TraceEntry.EntryPointSourceRule(entry.fact, methodEntryPoint, it.rule, it.action)
                addPredecessor(entry, predEntry)
            }
        }
    }

    private fun TraceBuilder.propagateEntry(
        statement: CommonInst,
        entry: TraceEntry,
        initialFact: InitialFactAp?,
        skipFactCheck: Boolean = false,
    ) {
        val statementCall = analysisManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            val preconditionFunction = analysisManager.getMethodCallPrecondition(
                apManager, analysisContext, returnValue, statementCall, statement
            )
            val precondition = preconditionFunction.factPrecondition(entry.fact)

            when (precondition) {
                CallPrecondition.Unchanged -> {
                    addPredecessor(entry, TraceEntry.Unchanged(entry.fact, statement))
                    return
                }

                is CallPrecondition.Facts -> {
                    if (!skipFactCheck && !containsEntryFact(entry, initialFact)) {
                        return
                    }

                    propagateCall(entry, initialFact, precondition.facts, statement, statementCall)
                }
            }
        } else {
            val preconditionFunction = analysisManager.getMethodSequentPrecondition(
                apManager, analysisContext, statement
            )
            val precondition = preconditionFunction.factPrecondition(entry.fact)

            when (precondition) {
                SequentPrecondition.Unchanged -> {
                    addPredecessor(entry, TraceEntry.Unchanged(entry.fact, statement))
                }

                is SequentPrecondition.Facts -> {
                    if (!skipFactCheck && !containsEntryFact(entry, initialFact)) {
                        return
                    }

                    precondition.facts.forEach {
                        addPredecessor(entry, TraceEntry.Sequential(it, statement))
                    }
                }
            }
        }
    }

    private fun preconditionIsUnchanged(statement: CommonInst, fact: InitialFactAp): Boolean {
        val statementCall = analysisManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            val preconditionFunction = analysisManager.getMethodCallPrecondition(
                apManager, analysisContext, returnValue, statementCall, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return precondition is CallPrecondition.Unchanged
        } else {
            val preconditionFunction = analysisManager.getMethodSequentPrecondition(
                apManager, analysisContext, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return precondition is SequentPrecondition.Unchanged
        }
    }

    private fun TraceBuilder.propagateCall(
        entry: TraceEntry,
        initialFact: InitialFactAp?,
        preconditionFacts: List<CallPreconditionFact>,
        statement: CommonInst,
        statementCall: CommonCallExpr
    ) {
        val callToStart = mutableListOf<CallPreconditionFact.CallToStart>()

        for (fact in preconditionFacts) {
            when (fact) {
                is CallPreconditionFact.CallToReturnTaintRule -> {
                    val preconditionEntry = when (val p = fact.precondition) {
                        is TaintRulePrecondition.Source -> {
                            TraceEntry.CallSourceRule(entry.fact, statement, p.rule, p.action)
                        }

                        is TaintRulePrecondition.Pass -> {
                            TraceEntry.CallRule(p.fact, statement, p.rule, p.action)
                        }
                    }

                    addPredecessor(entry, preconditionEntry)
                }

                is CallPreconditionFact.CallToStart -> {
                    callToStart.add(fact)
                }
            }
        }

        if (callToStart.isEmpty()) return

        val callees = runner.methodCallResolver.resolvedMethodCalls(methodEntryPoint, statementCall, statement)
        if (callees.isEmpty()) {

            // Drop fact if it is mapped to the method return value
            if (callToStart.any { it.startFactBase != AccessPathBase.Return }) {
                addPredecessor(entry, TraceEntry.UnresolvedCallSkip(entry.fact, statement))
            }

            return
        }

        resolveCallSummary(statement, initialFact, callees, callToStart, entry)
    }

    private fun TraceBuilder.resolveCallSummary(
        statement: CommonInst,
        initialFact: InitialFactAp?,
        callees: List<MethodWithContext>,
        startFacts: List<CallPreconditionFact.CallToStart>,
        entry: TraceEntry
    ) {
        val calleeEntryPoints = callees.flatMap { methodEntryPoints(it) }
        val predecessorEntries = mutableListOf<TraceEntry>()

        if (initialFact == null) {
            predecessorEntries.resolveCallSourceSummary(calleeEntryPoints, startFacts, statement)
        }

        predecessorEntries.resolveCallPassSummary(calleeEntryPoints, startFacts, statement)

        predecessorEntries.forEach { addPredecessor(entry, it) }
    }

    private fun MutableList<TraceEntry>.resolveCallPassSummary(
        calleeEntryPoints: List<MethodEntryPoint>,
        startFacts: List<CallPreconditionFact.CallToStart>,
        statement: CommonInst
    ) {
        val resolvedCallSummaries = mutableListOf<TraceEntry.CallSummary>()

        for (callee in calleeEntryPoints) {
            for (startFact in startFacts) {
                val methodSummaries = manager.findFactToFactSummaryEdges(callee, startFact.startFactBase)

                val callerFact = startFact.callerFact
                for (summaryEdge in methodSummaries) {
                    val mappedSummaryFact = summaryEdge.factAp.rebase(callerFact.base)
                    val deltas = callerFact.splitDelta(mappedSummaryFact)

                    if (deltas.isEmpty()) continue

                    // it is ok to map call arguments via exit2return
                    val mappedSummaryInitial = methodCallFactMapper.mapMethodExitToReturnFlowFact(
                        statement, summaryEdge.initialFactAp
                    )

                    for ((matchedEntryFact, delta) in deltas) {
                        // todo: remove this check?
                        if (!mappedSummaryFact.contains(matchedEntryFact)) continue

                        for (mappedSummaryInitialFact in mappedSummaryInitial) {
                            val precondition = mappedSummaryInitialFact
                                .concat(delta)
                                .replaceExclusions(callerFact.exclusions)

                            resolvedCallSummaries.addCallSummaryEntry(
                                statement = statement,
                                precondition = precondition,
                                preconditionDelta = delta,
                                callee = callee,
                                summaryFinalFact = matchedEntryFact,
                                summaryEdge = summaryEdge,
                            )
                        }
                    }
                }
            }
        }

        val weakestCallSummaries = selectWeakestEntries(resolvedCallSummaries)
        this += weakestCallSummaries
    }

    private fun MutableList<TraceEntry>.resolveCallSourceSummary(
        calleeEntryPoints: List<MethodEntryPoint>,
        startFacts: List<CallPreconditionFact.CallToStart>,
        statement: CommonInst
    ) {
        for (callee in calleeEntryPoints) {
            for (fact in startFacts) {
                val relevantSummaryEdges = manager.findZeroToFactSummaryEdges(callee, fact.startFactBase)
                for (summaryEdge in relevantSummaryEdges) {
                    val mappedSummaryFact = summaryEdge.factAp.rebase(fact.callerFact.base)
                    if (!mappedSummaryFact.contains(fact.callerFact)) continue

                    val summaryTrace = SourceSummaryTrace(
                        method = callee,
                        final = TraceEntry.Final(
                            kind = TraceFinalNodeKind.SummaryTraceFinalNode,
                            fact = fact.callerFact.rebase(fact.startFactBase),
                            statement = summaryEdge.statement
                        )
                    )

                    this += TraceEntry.CallSourceSummary(fact.callerFact, statement, summaryTrace)
                }
            }
        }
    }

    private fun <E : TraceEntry> selectWeakestEntries(entries: List<E>): List<E> {
        val selectedEntries = LinkedList<E>()
        for (summary in entries) {
            addWeakestEntry(summary, selectedEntries)
        }
        return selectedEntries
    }

    private fun <E : TraceEntry> addWeakestEntry(entry: E, selectedEntries: LinkedList<E>) {
        val entryFact = entry.fact
        val iter = selectedEntries.listIterator()

        while (iter.hasNext()) {
            val selectedEntry = iter.next()
            val selectedFact = selectedEntry.fact

            // Entry fact is stronger than already added selected fact
            if (entryFact.contains(selectedFact)) {
                return
            }

            // Selected fact is stronger
            if (selectedFact.contains(entryFact)) {
                iter.remove()
            }
        }

        selectedEntries.add(entry)
    }

    private fun MutableList<TraceEntry.CallSummary>.addCallSummaryEntry(
        statement: CommonInst,
        precondition: InitialFactAp,
        preconditionDelta: InitialFactAp.Delta,
        callee: MethodEntryPoint,
        summaryFinalFact: InitialFactAp,
        summaryEdge: FactToFact,
    ) {
        val mappedFinalFact = summaryFinalFact
            .rebase(summaryEdge.factAp.base)
            .replaceExclusions(summaryEdge.factAp.exclusions)

        val calleeTrace = MethodSummaryTrace(
            initial = TraceEntry.MethodEntry(summaryEdge.initialFactAp, callee),
            final = TraceEntry.Final(
                kind = TraceFinalNodeKind.SummaryTraceFinalNode,
                fact = mappedFinalFact,
                statement = summaryEdge.statement
            )
        )

        this += TraceEntry.CallSummary(precondition, statement, calleeTrace, preconditionDelta)
    }

    private fun methodEntryPoints(method: MethodWithContext): Sequence<MethodEntryPoint> =
        graph.entryPoints(method.method).map { MethodEntryPoint(method.ctx, it) }

    private fun containsEntryFact(entry: TraceEntry, initialFact: InitialFactAp?): Boolean {
        val entryFacts = if (initialFact == null) {
            edges.allZeroToFactFactsAtStatement(entry.statement, entry.fact)
        } else {
            edges.allFactToFactFactsAtStatement(entry.statement, initialFact, entry.fact)
        }
        return entryFacts.any { statementFact -> statementFact.contains(entry.fact) }
    }
}
