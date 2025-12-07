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
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction.SourceOtherAction
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver.TraceEntryAction.TraceSummaryEdge
import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.util.add
import org.opentaint.dataflow.util.bitSetOf
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.toBitSet
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet
import java.util.LinkedList
import java.util.Objects

class MethodTraceResolver(
    private val runner: AnalysisRunner,
    private val analysisContext: org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext,
    private val edges: MethodAnalyzerEdges
) {
    private val methodEntryPoint: MethodEntryPoint = analysisContext.methodEntryPoint
    private val graph: ApplicationGraph<CommonMethod, CommonInst> get() = runner.graph
    private val analysisManager: AnalysisManager get() = runner.analysisManager
    private val manager: AnalysisUnitRunnerManager get() = runner.manager
    private val methodCallFactMapper: org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper get() = analysisContext.methodCallFactMapper
    private val apManager: ApManager get() = runner.apManager

    enum class TraceKind {
        TraceToFact, // Trace ends within the method
        SummaryTrace, // Trace summarizes method behaviour
    }

    @Suppress("EqualsOrHashCode")
    data class FullTrace(
        val method: MethodEntryPoint,
        val startEntry: TraceEntry.StartTraceEntry,
        val final: TraceEntry.Final,
        val successors: Map<TraceEntry, Set<TraceEntry>>,
        val traceKind: TraceKind,
    ) {
        override fun hashCode(): Int = Objects.hash(method, final)
    }

    data class SummaryTrace(
        val method: MethodEntryPoint,
        val final: TraceEntry.Final,
        val traceKind: TraceKind,
    )

    sealed interface TraceEdge {
        val fact: InitialFactAp

        fun replaceFact(newFact: InitialFactAp): TraceEdge

        data class SourceTraceEdge(override val fact: InitialFactAp) : TraceEdge {
            override fun replaceFact(newFact: InitialFactAp): SourceTraceEdge = copy(fact = newFact)
        }

        data class MethodTraceEdge(val initialFact: InitialFactAp, override val fact: InitialFactAp) : TraceEdge {
            override fun replaceFact(newFact: InitialFactAp): MethodTraceEdge = copy(fact = newFact)
        }
    }

    sealed interface TraceEntryAction {
        val edges: Set<TraceEdge>

        sealed interface PrimaryAction : TraceEntryAction

        sealed interface OtherAction : TraceEntryAction

        sealed interface CallAction : TraceEntryAction

        sealed interface CallRuleAction : CallAction {
            val rule: CommonTaintConfigurationItem
            val action: CommonTaintAction
        }

        sealed interface SourceAction : TraceEntryAction {
            override val edges: Set<TraceEdge.SourceTraceEdge>
        }

        sealed interface SourcePrimaryAction : SourceAction, PrimaryAction

        sealed interface SourceOtherAction : SourceAction, OtherAction

        data class Sequential(
            override val edges: Set<TraceEdge>,
        ) : TraceEntryAction, PrimaryAction

        data class CallSourceRule(
            override val edges: Set<TraceEdge.SourceTraceEdge>,
            override val rule: CommonTaintConfigurationSource,
            override val action: CommonTaintAssignAction
        ) : SourceOtherAction, CallRuleAction

        data class EntryPointSourceRule(
            override val edges: Set<TraceEdge.SourceTraceEdge>,
            val entryPoint: MethodEntryPoint,
            override val rule: CommonTaintConfigurationSource,
            override val action: CommonTaintAssignAction
        ) : SourceOtherAction, CallRuleAction

        data class CallRule(
            override val edges: Set<TraceEdge>,
            override val rule: CommonTaintConfigurationItem,
            override val action: CommonTaintAction
        ) :  CallRuleAction, OtherAction

        sealed interface TraceSummaryEdge {
            val edge: TraceEdge

            data class SourceSummary(
                override val edge: TraceEdge.SourceTraceEdge
            ) : TraceSummaryEdge

            data class MethodSummary(
                override val edge: TraceEdge,
                val delta: InitialFactAp.Delta
            ) : TraceSummaryEdge
        }

        data class CallSummary(
            val summaryEdges: Set<TraceSummaryEdge>,
            val summaryTrace: SummaryTrace,
        ) : CallAction, PrimaryAction {
            override val edges: Set<TraceEdge>
                get() = summaryEdges.mapTo(hashSetOf()) { it.edge }
        }

        data class CallSourceSummary(
            val summaryEdges: Set<TraceSummaryEdge.SourceSummary>,
            val summaryTrace: SummaryTrace,
        ) : CallAction, SourcePrimaryAction {
            override val edges: Set<TraceEdge.SourceTraceEdge>
                get() = summaryEdges.mapTo(hashSetOf()) { it.edge }
        }

        data class UnresolvedCallSkip(
            override val edges: Set<TraceEdge>,
        ) : CallAction, PrimaryAction
    }

    sealed interface TraceEntry {
        val edges: Set<TraceEdge>
        val statement: CommonInst

        data class Action(
            val primaryAction: TraceEntryAction.PrimaryAction?,
            val otherActions: Set<TraceEntryAction.OtherAction>,
            val unchanged: Set<TraceEdge>,
            override val statement: CommonInst,
        ) : TraceEntry {
            override val edges: Set<TraceEdge> get() = buildSet {
                addAll(unchanged)
                primaryAction?.let { addAll(it.edges) }
                otherActions.forEach { addAll(it.edges) }
            }
        }

        data class Unchanged(
            override val edges: Set<TraceEdge>,
            override val statement: CommonInst,
        ) : TraceEntry

        data class Final(
            override val edges: Set<TraceEdge>,
            override val statement: CommonInst
        ) : TraceEntry

        sealed interface StartTraceEntry: TraceEntry

        data class MethodEntry(
            val facts: Set<InitialFactAp>,
            val entryPoint: MethodEntryPoint,
        ) : StartTraceEntry {
            override val edges: Set<TraceEdge> get() = facts.mapTo(hashSetOf()) {
                TraceEdge.MethodTraceEdge(it, it)
            }

            override val statement: CommonInst
                get() = entryPoint.statement
        }

        data class SourceStartEntry(
            val sourcePrimaryAction: TraceEntryAction.SourcePrimaryAction?,
            val sourceOtherActions: Set<SourceOtherAction>,
            override val statement: CommonInst,
        ) : StartTraceEntry {
            override val edges: Set<TraceEdge.SourceTraceEdge> get() = buildSet {
                sourcePrimaryAction?.let { addAll(it.edges) }
                sourceOtherActions.forEach { addAll(it.edges) }
            }
        }
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

        fun addPredecessor(current: TraceEntry, predecessor: TraceEntry, enqueue: Boolean = true) {
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

                if (enqueue) {
                    unprocessedEntryIds.add(predecessorId)
                }
            }
        }

        fun addStartEntry(entry: TraceEntry) {
            startEntryIds.set(entryManager.entryId(entry))
        }
    }

    fun resolveIntraProceduralTrace(statement: CommonInst, facts: Set<InitialFactAp>): List<SummaryTrace> {
        val edges = facts.map { resolveIntraProceduralTraceEdge(statement, it) }
        return edges.traceToFactSummaryEdges(statement)
    }

    private fun List<List<TraceEdge>>.traceToFactSummaryEdges(statement: CommonInst): List<SummaryTrace> {
        val result = mutableListOf<SummaryTrace>()
        this.cartesianProductMapTo {
            val finalEntry = TraceEntry.Final(it.toHashSet(), statement)
            result += SummaryTrace(methodEntryPoint, finalEntry, traceKind = TraceKind.TraceToFact)
        }
        return result
    }

    private fun resolveIntraProceduralTraceEdge(statement: CommonInst, fact: InitialFactAp): List<TraceEdge> {
        val matchingInitialFacts = hashSetOf<InitialFactAp?>()

        val visitedStatements = hashSetOf<CommonInst>()
        val unprocessedStatements = mutableListOf(statement)

        while (unprocessedStatements.isNotEmpty()) {
            val stmt = unprocessedStatements.removeLast()
            if (!visitedStatements.add(stmt)) continue

            factsStoredAtStatement(unprocessedStatements, stmt, fact).forEach { storedFact ->
                if (edges.allZeroToFactFactsAtStatement(stmt, storedFact).any { it.contains(storedFact) }) {
                    matchingInitialFacts.add(null)
                }

                edges.allFactToFactFactsAtStatement(stmt, storedFact).forEach { (initialFact, finalFact) ->
                    if (finalFact.contains(storedFact)) {
                        matchingInitialFacts.add(initialFact)
                    }
                }
            }
        }

        return matchingInitialFacts.map { initialFact ->
            if (initialFact != null) {
                TraceEdge.MethodTraceEdge(initialFact, fact)
            } else {
                TraceEdge.SourceTraceEdge(fact)
            }
        }
    }

    private fun factsStoredAtStatement(
        unprocessed: MutableList<CommonInst>,
        statement: CommonInst,
        fact: InitialFactAp,
    ): List<InitialFactAp> {
        var predecessorsIsEmpty = true
        val result = mutableListOf<InitialFactAp>()

        for (predecessor in graph.predecessors(statement)) {
            predecessorsIsEmpty = false

            result.addAll(
                factsForPrecondition(predecessor, fact).also {
                    if (it.isEmpty()) {
                        unprocessed.add(predecessor)
                    }
                }
            )
        }

        if (predecessorsIsEmpty) {
            result.add(fact)
        }

        return result
    }

    fun resolveIntraProceduralTraceFromCall(
        statement: CommonInst,
        calleeEntry: TraceEntry.MethodEntry
    ): List<SummaryTrace> {
        val traceEdges = calleeEntry.facts.flatMap { fact ->
            val mappedFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(statement, fact)
            mappedFacts.map { resolveIntraProceduralTraceEdge(statement, it) }
        }

        return traceEdges.traceToFactSummaryEdges(statement)
    }

    fun resolveIntraProceduralFullTrace(
        summaryTrace: SummaryTrace,
        cancellation: TraceResolverCancellation
    ): List<FullTrace> {
        check(summaryTrace.method == methodEntryPoint) { "Incorrect summary trace" }

        val builder = TraceBuilder(entryManager.entryId(summaryTrace.final), cancellation)
        builder.resolveTrace(summaryTrace.traceKind)
        builder.removeUnreachableNodes()
        builder.collapseUnchangedNodes()
        return builder.fullTrace(summaryTrace.traceKind)
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

    private fun TraceBuilder.fullTrace(traceKind: TraceKind): List<FullTrace> {
        val finalEntry = entryManager.entryById(finalEntryId) as TraceEntry.Final
        val successors = successors()

        val result = mutableListOf<FullTrace>()
        startEntryIds.forEach { entryId: Int ->
            val entry = entryManager.entryById(entryId)
            check(entry is TraceEntry.StartTraceEntry)

            result += FullTrace(methodEntryPoint, entry, finalEntry, successors, traceKind)
        }

        return result
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

    private fun TraceBuilder.resolveTrace(traceKind: TraceKind) {
        while (unprocessedEntryIds.isNotEmpty() && cancellation.isActive) {
            val entryId = unprocessedEntryIds.removeInt(unprocessedEntryIds.lastIndex)
            val entry = entryManager.entryById(entryId)
            processTraceEntry(entry, traceKind)
        }
    }

    private fun TraceBuilder.processTraceEntry(entry: TraceEntry, traceKind: TraceKind) {
        if (entry is TraceEntry.StartTraceEntry) {
            addStartEntry(entry)
            return
        }

        if (entry is TraceEntry.Final) {
            when (traceKind) {
                TraceKind.TraceToFact -> {
                    // We have fact BEFORE entry.statement, no need to propagate
                }

                TraceKind.SummaryTrace -> {
                    // We have fact AFTER entry.statement
                    propagateEntryNew(entry.statement, entry, skipFactCheck = true)
                    return
                }
            }
        }

        if (entry.statement == methodEntryPoint.statement) {
            propagateEntryToMethodEntryPoint(entry)
            return
        }

        graph.predecessors(entry.statement).forEach {
            propagateEntryNew(it, entry)
        }
    }

    private fun TraceBuilder.propagateEntryToMethodEntryPoint(
        entry: TraceEntry
    ) {
        val entryEdges = hashSetOf<TraceEdge.MethodTraceEdge>()
        val sources = hashSetOf<SourceOtherAction>()

        for (edge in entry.edges) {
            // We always have fact before entry point
            if (!containsEntryEdge(entry.statement, edge)) return

            when (edge) {
                is TraceEdge.MethodTraceEdge -> {
                    entryEdges.add(edge)
                }

                is TraceEdge.SourceTraceEdge -> {
                    val preconditionFunction = analysisManager.getMethodStartPrecondition(apManager, analysisContext)
                    preconditionFunction.factPrecondition(edge.fact).forEach {
                        val source = TraceEntryAction.EntryPointSourceRule(
                            setOf(edge), methodEntryPoint, it.rule, it.action
                        )
                        sources.add(source)
                    }
                }
            }
        }

        if (entryEdges.isEmpty()) {
            if (sources.isEmpty()) return

            addPredecessor(
                entry,
                TraceEntry.SourceStartEntry(sourcePrimaryAction = null, sources, methodEntryPoint.statement)
            )
        } else {
            val preStartEntry = if (sources.isNotEmpty()) {
                TraceEntry.Action(primaryAction = null, sources, entryEdges, methodEntryPoint.statement)
                    .also { addPredecessor(entry, it, enqueue = false) }
            } else {
                entry
            }

            val startEntry = TraceEntry.MethodEntry(
                entryEdges.mapTo(hashSetOf()) { it.initialFact },
                methodEntryPoint
            )
            addPredecessor(preStartEntry, startEntry)
        }
    }

    private fun TraceBuilder.propagateEntryNew(
        statement: CommonInst,
        entry: TraceEntry,
        skipFactCheck: Boolean = false,
    ) {
        val statementCall = analysisManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            val preconditionFunction = analysisManager.getMethodCallPrecondition(
                apManager, analysisContext, returnValue, statementCall, statement
            )

            val unchangedEdges = hashSetOf<TraceEdge>()
            val callEdges = mutableListOf<List<TraceEntryAction.CallAction>>()

            for (edge in entry.edges) {
                val precondition = preconditionFunction.factPrecondition(edge.fact)
                when (precondition) {
                    CallPrecondition.Unchanged -> {
                        unchangedEdges.add(edge)
                        continue
                    }

                    is CallPrecondition.Facts -> {
                        val callActions = mutableListOf<TraceEntryAction.CallAction>()
                        precondition.facts.forEach {
                            val initialEdge = edge.replaceFact(it.initialFact)
                            if (!skipFactCheck && !containsEntryEdge(entry.statement, initialEdge)) {
                                return@forEach
                            }

                            callActions.propagateCall(edge, it.preconditionFacts, statement, statementCall)
                        }

                        if (callActions.isEmpty()) {
                            // fact has no preconditions
                            return
                        }

                        callEdges.add(callActions)
                    }
                }
            }

            if (callEdges.isEmpty()) {
                addPredecessor(entry, TraceEntry.Unchanged(unchangedEdges, statement))
                return
            }

            val callActions = mergeCallActionsCombinations(callEdges)
            for ((callActionPrimary, callActionOther) in callActions) {
                val action = TraceEntry.Action(callActionPrimary, callActionOther, unchangedEdges, statement)
                addPredecessorAction(entry, action)
            }
        } else {
            val preconditionFunction = analysisManager.getMethodSequentPrecondition(
                apManager, analysisContext, statement
            )

            for (edge in entry.edges) {
                val precondition = preconditionFunction.factPrecondition(edge.fact)

                val unchangedEdges = hashSetOf<TraceEdge>()
                val sequentEdges = mutableListOf<List<TraceEdge>>()

                when (precondition) {
                    SequentPrecondition.Unchanged -> {
                        unchangedEdges.add(edge)
                    }

                    is SequentPrecondition.Facts -> {
                        val edges = mutableListOf<TraceEdge>()

                        precondition.facts.forEach {
                            val initialEdge = edge.replaceFact(it.initialFact)
                            if (!skipFactCheck && !containsEntryEdge(entry.statement, initialEdge)) {
                                return@forEach
                            }

                            it.preconditionFacts.forEach { fact ->
                                edges.add(edge.replaceFact(fact))
                            }
                        }

                        if (edges.isEmpty()) {
                            // fact has no preconditions
                            return
                        }

                        sequentEdges.add(edges)
                    }
                }

                if (sequentEdges.isEmpty()) {
                    addPredecessor(entry, TraceEntry.Unchanged(unchangedEdges, statement))
                    return
                }

                val sequentActionsCombination = mergeSequentEdgeCombinations(sequentEdges)
                for (action in sequentActionsCombination) {
                    addPredecessorAction(
                        entry,
                        TraceEntry.Action(action, otherActions = emptySet(), unchangedEdges, statement)
                    )
                }
            }
        }
    }

    private fun TraceBuilder.addPredecessorAction(entry: TraceEntry, action: TraceEntry.Action) {
        val startOrAction = tryCreateSourceStart(action) ?: action
        addPredecessor(entry, startOrAction)
    }

    private fun tryCreateSourceStart(action: TraceEntry.Action): TraceEntry.SourceStartEntry? {
        if (action.unchanged.isNotEmpty()) return null

        val primary = action.primaryAction
        if (primary !is TraceEntryAction.SourcePrimaryAction?) return null

        val sourceOther = action.otherActions.filterIsInstanceTo<SourceOtherAction, _>(hashSetOf())
        if (sourceOther.size != action.otherActions.size) return null

        return TraceEntry.SourceStartEntry(primary, sourceOther, action.statement)
    }

    private fun mergeSequentEdgeCombinations(edges: List<List<TraceEdge>>): List<TraceEntryAction.Sequential> {
        val result = mutableListOf<TraceEntryAction.Sequential>()
        edges.cartesianProductMapTo { edgeCombination ->
            result += TraceEntryAction.Sequential(edgeCombination.toHashSet())
        }
        return result
    }

    private fun mergeCallActionsCombinations(
        callActions: List<List<TraceEntryAction.CallAction>>,
    ): List<Pair<TraceEntryAction.PrimaryAction?, Set<TraceEntryAction.OtherAction>>> {
        // todo: check call summary method here
        val result = mutableListOf<Pair<TraceEntryAction.PrimaryAction?, Set<TraceEntryAction.OtherAction>>>()
        callActions.cartesianProductMapTo { actions ->
            val mergedActions = mergeCallActions(actions) ?: return@cartesianProductMapTo
            result.add(mergedActions)
        }
        return result
    }

    private fun mergeCallActions(callActions: Array<TraceEntryAction.CallAction>): Pair<TraceEntryAction.PrimaryAction?, Set<TraceEntryAction.OtherAction>>? {
        val other = hashSetOf<TraceEntryAction.OtherAction>()
        val unresolved = mutableListOf<TraceEntryAction.UnresolvedCallSkip>()
        val summary = mutableListOf<TraceEntryAction.CallSummary>()

        for (action in callActions) {
            when (action) {
                is TraceEntryAction.CallRule -> other.add(action)
                is TraceEntryAction.CallSourceRule -> other.add(action)
                is TraceEntryAction.UnresolvedCallSkip -> unresolved.add(action)
                is TraceEntryAction.CallSummary -> summary.add(action)
                is TraceEntryAction.CallSourceSummary,
                is TraceEntryAction.EntryPointSourceRule -> error("Unexpected action")
            }
        }

        if (unresolved.isNotEmpty()) {
            check(summary.isEmpty()) {
                "call is resolved and unresolved at the same time"
            }

            val mergedUnresolved = TraceEntryAction.UnresolvedCallSkip(
                unresolved.flatMapTo(hashSetOf()) { it.edges }
            )

            return mergedUnresolved to other
        }

        if (summary.isEmpty()) {
            return null to other
        }

        val mergedCallSummaries = mergeCallSummaries(summary) ?: return null
        return mergedCallSummaries to other
    }

    private fun mergeCallSummaries(callSummaries: List<TraceEntryAction.CallSummary>): TraceEntryAction.PrimaryAction? {
        check(callSummaries.all { it.summaryTrace.traceKind == TraceKind.SummaryTrace })

        val callee = callSummaries.first().summaryTrace.method
        if (callSummaries.any { it.summaryTrace.method != callee }) return null

        val exitStatement = callSummaries.first().summaryTrace.final.statement
        if (callSummaries.any { it.summaryTrace.final.statement != exitStatement }) return null

        val finalEdges = hashSetOf<TraceEdge>()
        val summaryEdges = hashSetOf<TraceSummaryEdge>()

        for (summary in callSummaries) {
            summaryEdges += summary.summaryEdges
            finalEdges += summary.summaryTrace.final.edges
        }

        val summaryTraceFinal = TraceEntry.Final(finalEdges, exitStatement)
        val summaryTrace = SummaryTrace(callee, summaryTraceFinal, TraceKind.SummaryTrace)

        val sourceSummaryEdges = summaryEdges.filterIsInstanceTo<TraceSummaryEdge.SourceSummary, _>(hashSetOf())
        val summaryAction = if (sourceSummaryEdges.size == summaryEdges.size) {
            TraceEntryAction.CallSourceSummary(sourceSummaryEdges, summaryTrace)
        } else {
            TraceEntryAction.CallSummary(summaryEdges, summaryTrace)
        }

        return summaryAction
    }

    private fun factsForPrecondition(statement: CommonInst, fact: InitialFactAp): List<InitialFactAp> {
        val statementCall = analysisManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            val preconditionFunction = analysisManager.getMethodCallPrecondition(
                apManager, analysisContext, returnValue, statementCall, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return when (precondition) {
                is CallPrecondition.Facts -> precondition.facts.map { it.initialFact }
                CallPrecondition.Unchanged -> emptyList()
            }
        } else {
            val preconditionFunction = analysisManager.getMethodSequentPrecondition(
                apManager, analysisContext, statement
            )
            val precondition = preconditionFunction.factPrecondition(fact)
            return when (precondition) {
                is SequentPrecondition.Facts -> precondition.facts.map { it.initialFact }
                SequentPrecondition.Unchanged -> emptyList()
            }
        }
    }

    private fun MutableList<TraceEntryAction.CallAction>.propagateCall(
        currentEdge: TraceEdge,
        preconditionFacts: List<CallPreconditionFact>,
        statement: CommonInst,
        statementCall: CommonCallExpr
    ) {
        val callToStart = mutableListOf<CallPreconditionFact.CallToStart>()

        for (fact in preconditionFacts) {
            when (fact) {
                is CallPreconditionFact.CallToReturnTaintRule -> {
                    val preconditionAction = when (val p = fact.precondition) {
                        is TaintRulePrecondition.Source -> {
                            val edge = TraceEdge.SourceTraceEdge(currentEdge.fact)
                            TraceEntryAction.CallSourceRule(setOf(edge), p.rule, p.action)
                        }

                        is TaintRulePrecondition.Pass -> {
                            val edge = currentEdge.replaceFact(p.fact)
                            TraceEntryAction.CallRule(setOf(edge), p.rule, p.action)
                        }
                    }

                    this += preconditionAction
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
                this += TraceEntryAction.UnresolvedCallSkip(setOf(currentEdge))
            }

            return
        }

        resolveCallSummary(statement, currentEdge, callees, callToStart)
    }

    private fun MutableList<TraceEntryAction.CallAction>.resolveCallSummary(
        statement: CommonInst,
        currentEdge: TraceEdge,
        callees: List<MethodWithContext>,
        startFacts: List<CallPreconditionFact.CallToStart>,
    ) {
        val calleeEntryPoints = callees.flatMap { methodEntryPoints(it) }

        if (currentEdge is TraceEdge.SourceTraceEdge) {
            resolveCallSourceSummary(currentEdge, calleeEntryPoints, startFacts)
        }

        resolveCallPassSummary(currentEdge, calleeEntryPoints, startFacts, statement)
    }

    private fun MutableList<TraceEntryAction.CallAction>.resolveCallPassSummary(
        currentEdge: TraceEdge,
        calleeEntryPoints: List<MethodEntryPoint>,
        startFacts: List<CallPreconditionFact.CallToStart>,
        statement: CommonInst
    ) {
        val resolvedCallSummaries = mutableListOf<TraceEntryAction.CallSummary>()

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
                                currentTraceEdge = currentEdge,
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

    private fun MutableList<TraceEntryAction.CallAction>.resolveCallSourceSummary(
        currentEdge: TraceEdge.SourceTraceEdge,
        calleeEntryPoints: List<MethodEntryPoint>,
        startFacts: List<CallPreconditionFact.CallToStart>
    ) {
        for (callee in calleeEntryPoints) {
            for (fact in startFacts) {
                val relevantSummaryEdges = manager.findZeroToFactSummaryEdges(callee, fact.startFactBase)
                for (summaryEdge in relevantSummaryEdges) {
                    val mappedSummaryFact = summaryEdge.factAp.rebase(fact.callerFact.base)
                    if (!mappedSummaryFact.contains(fact.callerFact)) continue

                    val summaryEdgeTrace = TraceEdge.SourceTraceEdge(fact.callerFact.rebase(fact.startFactBase))
                    val summaryTrace = SummaryTrace(
                        method = callee,
                        final = TraceEntry.Final(setOf(summaryEdgeTrace), summaryEdge.statement),
                        traceKind = TraceKind.SummaryTrace
                    )

                    val callSummary = TraceSummaryEdge.SourceSummary(currentEdge)
                    this += TraceEntryAction.CallSummary(setOf(callSummary), summaryTrace)
                }
            }
        }
    }

    private fun selectWeakestEntries(entries: List<TraceEntryAction.CallSummary>): List<TraceEntryAction.CallSummary> {
        val selectedEntries = LinkedList<TraceEntryAction.CallSummary>()
        for (summary in entries) {
            addWeakestEntry(summary, selectedEntries)
        }
        return selectedEntries
    }

    private fun addWeakestEntry(entry: TraceEntryAction.CallSummary, selectedEntries: LinkedList<TraceEntryAction.CallSummary>) {
        val entryFact = entry.edges.single().fact
        val iter = selectedEntries.listIterator()

        while (iter.hasNext()) {
            val selectedEntry = iter.next()
            val selectedFact = selectedEntry.edges.single().fact

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

    private fun MutableList<TraceEntryAction.CallSummary>.addCallSummaryEntry(
        currentTraceEdge: TraceEdge,
        precondition: InitialFactAp,
        preconditionDelta: InitialFactAp.Delta,
        callee: MethodEntryPoint,
        summaryFinalFact: InitialFactAp,
        summaryEdge: FactToFact,
    ) {
        val mappedFinalFact = summaryFinalFact
            .rebase(summaryEdge.factAp.base)
            .replaceExclusions(summaryEdge.factAp.exclusions)

        val traceSummaryEdge = TraceEdge.MethodTraceEdge(summaryEdge.initialFactAp, mappedFinalFact)
        val calleeTrace = SummaryTrace(
            final = TraceEntry.Final(setOf(traceSummaryEdge), summaryEdge.statement),
            method = callee,
            traceKind = TraceKind.SummaryTrace,
        )

        val callSummary = TraceSummaryEdge.MethodSummary(
            currentTraceEdge.replaceFact(precondition),
            preconditionDelta
        )

        this += TraceEntryAction.CallSummary(setOf(callSummary), calleeTrace)
    }

    private fun methodEntryPoints(method: MethodWithContext): Sequence<MethodEntryPoint> =
        graph.entryPoints(method.method).map { MethodEntryPoint(method.ctx, it) }

    private fun containsEntryEdge(entryStatement: CommonInst, entryEdge: TraceEdge): Boolean {
        when (entryEdge) {
            is TraceEdge.SourceTraceEdge -> {
                val entryFacts = edges.allZeroToFactFactsAtStatement(entryStatement, entryEdge.fact)
                return entryFacts.any { statementFact -> statementFact.contains(entryEdge.fact) }
            }
            is TraceEdge.MethodTraceEdge -> {
                val entryFacts = edges.allFactToFactFactsAtStatement(entryStatement, entryEdge.initialFact, entryEdge.fact)
                return entryFacts.any { statementFact -> statementFact.contains(entryEdge.fact) }
            }
        }
    }
}
