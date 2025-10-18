package org.opentaint.dataflow.ap.ifds.trace

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonType
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.LanguageManager
import org.opentaint.dataflow.ap.ifds.MethodAnalyzerEdges
import org.opentaint.dataflow.ap.ifds.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.graph.statementFilteredTraverse
import org.opentaint.dataflow.util.forEach
import org.opentaint.util.onSome
import java.util.BitSet
import java.util.LinkedList
import java.util.Objects

class MethodTraceResolver(
    private val runner: AnalysisRunner,
    private val methodEntryPoint: MethodEntryPoint,
    private val edges: MethodAnalyzerEdges
) {
    private val graph: ApplicationGraph<CommonMethod, CommonInst> get() = runner.graph
    private val languageManager: LanguageManager get() = runner.languageManager
    private val manager: AnalysisUnitRunnerManager get() = runner.manager
    private val methodCallFactMapper: MethodCallFactMapper get() = languageManager.methodCallFactMapper
    private val apManager: ApManager get() = runner.apManager
    private val taintConfiguration: TaintRulesProvider get() = runner.taintConfiguration

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

        data class Sequential(override val fact: InitialFactAp, override val statement: CommonInst) : TraceEntry

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
            val rule: TaintConfigurationItem,
            val action: AssignMark
        ) : SourceStartEntry, CallTraceEntry

        data class EntryPointSourceRule(
            override val fact: InitialFactAp,
            val entryPoint: MethodEntryPoint,
            val rule: TaintConfigurationItem,
            val action: AssignMark
        ) : SourceStartEntry {
            override val statement: CommonInst
                get() = entryPoint.statement
        }

        data class CallRule(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val rule: TaintConfigurationItem,
            val action: Action
        ) : TraceEntry, CallTraceEntry

        data class CallSummary(
            override val fact: InitialFactAp,
            override val statement: CommonInst,
            val summaryTrace: MethodSummaryTrace
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

        if (edges.allZeroToFactFactsAtStatement(statement).any { it.contains(fact) }) {
            matchingInitialFacts.add(null)
        }

        edges.allFactToFactFactsAtStatement(statement).forEach { (initialFact, finalFact) ->
            if (finalFact.contains(fact)) {
                matchingInitialFacts.add(initialFact)
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
                builder.collapseSequentialPredecessors()
                builder.methodTrace()
            }

            is SourceSummaryTrace -> {
                val builder = TraceBuilder(entryManager.entryId(summaryTrace.final), cancellation)
                builder.resolveTrace(initialFact = null)
                builder.collapseSequentialPredecessors()
                builder.sourceTrace()
            }
        }
    }

    private fun TraceBuilder.collapseSequentialPredecessors() {
        processedEntryIds.clear()
        unprocessedEntryIds.add(finalEntryId)

        while (unprocessedEntryIds.isNotEmpty()) {
            val entryId = unprocessedEntryIds.removeInt(unprocessedEntryIds.lastIndex)

            if (processedEntryIds.get(entryId)) continue
            processedEntryIds.set(entryId)

            if (startEntryIds.get(entryId)) continue

            val entryPredecessorIds = predecessors.get(entryId) ?: continue

            val entry = entryManager.entryById(entryId)
            if (canRemoveEntry(entry, entryPredecessorIds)) {
                entryPredecessorIds.clear(entryId)
                predecessors.remove(entryId)

                val entrySuccessors = successors.remove(entryId) ?: BitSet()
                entrySuccessors.clear(entryId)

                entryPredecessorIds.forEach { predecessorId: Int ->
                    val predSuccessors = successors.get(predecessorId)
                    predSuccessors?.clear(entryId)
                    predSuccessors?.or(entrySuccessors)
                }

                entrySuccessors.forEach { successorId: Int ->
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

    private fun canRemoveEntry(entry: TraceEntry, predecessorIds: BitSet): Boolean {
        if (entry !is TraceEntry.Sequential) return false

        predecessorIds.forEach { predecessorId: Int ->
            val predecessor = entryManager.entryById(predecessorId)

            if (predecessor.fact != entry.fact) return false
        }

        return true
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
                    propagateEntry(entry.statement, entry, initialFact)
                    return
                }
            }
        }

        if (entry.statement == methodEntryPoint.statement) {
            propagateEntryToMethodEntryPoint(initialFact, entry)
            return
        }

        statementFilteredTraverse(
            languageManager, entry.statement, graph::predecessors,
            predicate = { languageManager.isRelevantInstruction(it) || it == methodEntryPoint.statement },
            body = { propagateEntry(it, entry, initialFact) }
        )
    }

    private fun TraceBuilder.propagateEntryToMethodEntryPoint(
        initialFact: InitialFactAp?,
        entry: TraceEntry
    ) {
        if (initialFact != null) {
            addPredecessor(entry, TraceEntry.MethodEntry(initialFact, methodEntryPoint))
        } else {
            val entryPointPrecondition = languageManager.getEntryPointPrecondition(
                apManager, taintConfiguration, methodEntryPoint.method, entry.fact
            )
            entryPointPrecondition.onSome { preconditions ->
                for ((rule, action) in preconditions) {
                    addPredecessor(entry, TraceEntry.EntryPointSourceRule(entry.fact, methodEntryPoint, rule, action))
                }
            }
        }
    }

    private fun TraceBuilder.propagateEntry(
        statement: CommonInst,
        entry: TraceEntry,
        initialFact: InitialFactAp?
    ) {
        val statementCall = languageManager.getCallExpr(statement)
        if (statementCall != null) {
            val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv

            if (!methodCallFactMapper.factCanBeModifiedByMethodCall(returnValue, statementCall, entry.fact)) {
                addPredecessor(entry, TraceEntry.Sequential(entry.fact, statement))
                return
            }

            val factsAtStatement = factsAtStatement(statement, initialFact)

            applyCallRules(
                statement,
                factsAtStatement,
                statementCall,
                returnValue,
                initialFact,
                entry
            )

            val callees = runner.methodCallResolver.resolvedMethodCalls(methodEntryPoint, statementCall, statement)
            if (callees.isEmpty()) {
                if (statementFactsContainsFact(factsAtStatement, entry.fact)) {
                    addPredecessor(entry, TraceEntry.UnresolvedCallSkip(entry.fact, statement))
                }
                return
            }

            val calleeExitBases = methodRelevantBases(
                statementCall,
                returnValue,
                entry.fact
            )
            if (calleeExitBases.isEmpty()) {
                return // todo: ???
            }

            resolveCallSummary(
                statement,
                factsAtStatement,
                statementCall,
                returnValue,
                initialFact,
                callees,
                calleeExitBases,
                entry
            )

        } else {
            val preconditionFunction = languageManager.getMethodSequentPrecondition(statement)
            val preconditions = preconditionFunction.factPrecondition(entry.fact)

            if (preconditions == null) {
                addPredecessor(entry, TraceEntry.Sequential(entry.fact, statement))
                return
            }

            val factsAtStatement = factsAtStatement(statement, initialFact)

            for (precondition in preconditions) {
                if (statementFactsContainsFact(factsAtStatement, precondition)) {
                    addPredecessor(entry, TraceEntry.Sequential(precondition, statement))
                }
            }
        }
    }

    private fun TraceBuilder.applyCallRules(
        statement: CommonInst,
        factsAtStatement: List<FinalFactAp>,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        initialFact: InitialFactAp?,
        entry: TraceEntry
    ) {
        val preconditionFunction = languageManager.getMethodCallPrecondition(
            apManager = apManager,
            config = taintConfiguration,
            returnValue = returnValue,
            callExpr = callExpr,
            statement = statement,
            factsAtStatement = factsAtStatement,
        )

        val passRulePreconditions = preconditionFunction.factPassRulePrecondition(entry.fact)
        passRulePreconditions.onSome { preconditions ->
            for (precondition in preconditions) {
                if (statementFactsContainsFact(factsAtStatement, precondition.fact)) {
                    addPredecessor(
                        entry,
                        TraceEntry.CallRule(precondition.fact, statement, precondition.rule, precondition.action)
                    )
                }
            }
        }

        if (initialFact == null) {
            val sourceRulePreconditions = preconditionFunction.factSourceRulePrecondition(entry.fact)
            sourceRulePreconditions.onSome { preconditions ->
                for ((rule, action) in preconditions) {
                    addPredecessor(entry, TraceEntry.CallSourceRule(entry.fact, statement, rule, action))
                }
            }
        }
    }

    private fun TraceBuilder.resolveCallSummary(
        statement: CommonInst,
        factsAtStatement: List<FinalFactAp>,
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        initialFact: InitialFactAp?,
        callees: List<MethodWithContext>,
        calleeExitBases: Set<AccessPathBase>,
        entry: TraceEntry
    ) {
        val calleeEntryPoints = callees.flatMap { method ->
            methodEntryPoints(method).map { it to calleeExitBases }
        }

        if (initialFact == null) {
            val resolvedSourceSummaries = hashSetOf<TraceEntry.CallSourceSummary>()

            for ((callee, calleeBases) in calleeEntryPoints) {
                for (calleeBase in calleeBases) {
                    val relevantSummaryEdges = manager.findZeroToFactSummaryEdges(callee, calleeBase)
                    for (summaryEdge in relevantSummaryEdges) {
                        val mappedSummaryFact = summaryEdge.factAp.rebase(entry.fact.base)
                        if (!mappedSummaryFact.contains(entry.fact)) continue

                        val summaryTrace = SourceSummaryTrace(
                            method = callee,
                            final = TraceEntry.Final(
                                kind = TraceFinalNodeKind.SummaryTraceFinalNode,
                                fact = entry.fact.rebase(summaryEdge.factAp.base),
                                statement = summaryEdge.statement
                            )
                        )

                        resolvedSourceSummaries += TraceEntry.CallSourceSummary(entry.fact, statement, summaryTrace)
                    }
                }
            }

            resolvedSourceSummaries.forEach { addPredecessor(entry, it) }
        }

        val resolvedCallSummaries = mutableListOf<TraceEntry.CallSummary>()

        val startFacts = factsAtStatement.flatMap { statementFact ->
            methodStartFacts(statementFact, returnValue, callExpr)
        }

        for ((callee, calleeBases) in calleeEntryPoints) {
            for (calleeBase in calleeBases) {
                for ((callerFact, calleeInitialBase) in startFacts) {
                    val calleeInitialFact = callerFact.rebase(calleeInitialBase)
                    val methodSummaries = manager.findFactToFactSummaryEdges(
                        callee, calleeInitialFact, calleeBase
                    )

                    for (summaryEdge in methodSummaries) {
                        resolvedCallSummaries.resolveCallSummaryEdge(
                            summaryEdge = summaryEdge,
                            calleeInitialFact = calleeInitialFact,
                            currentEntryFact = entry.fact,
                            callerFact = callerFact,
                            statement = statement,
                            callee = callee
                        )
                    }
                }
            }
        }

        val weakestCallSummaries = selectWeakestEntries(resolvedCallSummaries)

        weakestCallSummaries.forEach { addPredecessor(entry, it) }
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

    private fun MutableList<TraceEntry.CallSummary>.resolveCallSummaryEdge(
        summaryEdge: FactToFact,
        calleeInitialFact: FinalFactAp,
        currentEntryFact: InitialFactAp,
        callerFact: FinalFactAp,
        statement: CommonInst,
        callee: MethodEntryPoint
    ) {
        val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
            calleeInitialFact, summaryEdge.initialFactAp
        )

        for (effect in summaryEdgeEffects) {
            when (effect) {
                is SummaryApRefinement -> {
                    val mappedSummaryFact = summaryEdge.factAp.rebase(currentEntryFact.base)

                    val summaryFact = mappedSummaryFact.concat(DummyFactChecker, effect.delta)
                        ?.replaceExclusions(currentEntryFact.exclusions) ?: continue

                    if (!summaryFact.contains(currentEntryFact)) continue

                    val deltas = currentEntryFact.splitDelta(mappedSummaryFact)
                    for ((matchedEntryFact, delta) in deltas) {
                        if (!mappedSummaryFact.contains(matchedEntryFact)) continue

                        val precondition = summaryEdge.initialFactAp.concat(delta).rebase(callerFact.base)
                            .replaceExclusions(currentEntryFact.exclusions)

                        addCallSummaryEntry(
                            statement = statement,
                            precondition = precondition,
                            callee = callee,
                            summaryFinalFact = matchedEntryFact,
                            summaryEdge = summaryEdge,
                        )
                    }
                }

                is SummaryExclusionRefinement -> {
                    val summaryFact = summaryEdge.factAp
                        .rebase(currentEntryFact.base)
                        .replaceExclusions(currentEntryFact.exclusions)

                    if (!summaryFact.contains(currentEntryFact)) continue

                    val precondition = summaryEdge.initialFactAp
                        .rebase(callerFact.base)
                        .replaceExclusions(currentEntryFact.exclusions)

                    addCallSummaryEntry(
                        statement = statement,
                        precondition = precondition,
                        callee = callee,
                        summaryFinalFact = currentEntryFact,
                        summaryEdge = summaryEdge
                    )
                }
            }
        }
    }

    private fun MutableList<TraceEntry.CallSummary>.addCallSummaryEntry(
        statement: CommonInst,
        precondition: InitialFactAp,
        callee: MethodEntryPoint,
        summaryFinalFact: InitialFactAp,
        summaryEdge: FactToFact
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

        this += TraceEntry.CallSummary(precondition, statement, calleeTrace)
    }

    private fun methodRelevantBases(
        callExpr: CommonCallExpr,
        returnValue: CommonValue?,
        fact: InitialFactAp
    ): Set<AccessPathBase> {
        val calleeBases = hashSetOf<AccessPathBase>()
        methodCallFactMapper.mapMethodCallToStartFlowFact(
            languageManager.getCalleeMethod(callExpr), callExpr, fact
        ) { _, calleeExitBase ->
            calleeBases += calleeExitBase
        }

        if (returnValue != null) {
            val returnValueBase = languageManager.accessPathBase(returnValue)
            if (returnValueBase == fact.base) {
                calleeBases += AccessPathBase.Return
            }
        }

        return calleeBases
    }

    private fun methodStartFacts(
        fact: FinalFactAp,
        returnValue: CommonValue?,
        callExpr: CommonCallExpr
    ): List<Pair<FinalFactAp, AccessPathBase>> {
        if (!methodCallFactMapper.factCanBeModifiedByMethodCall(returnValue, callExpr, fact)) return emptyList()

        val method = languageManager.getCalleeMethod(callExpr)
        return buildList {
            methodCallFactMapper.mapMethodCallToStartFlowFact(
                method,
                callExpr,
                fact,
                DummyFactChecker
            ) { f, base -> this.add(f to base) }
        }
    }

    private fun methodEntryPoints(method: MethodWithContext): Sequence<MethodEntryPoint> =
        graph.entryPoints(method.method).map { MethodEntryPoint(method.ctx, it) }

    private fun factsAtStatement(statement: CommonInst, initialFact: InitialFactAp?): List<FinalFactAp> =
        if (initialFact == null) {
            edges.allZeroToFactFactsAtStatement(statement)
        } else {
            edges.allFactToFactFactsAtStatement(statement, initialFact)
        }

    private fun statementFactsContainsFact(statementFacts: List<FinalFactAp>, fact: InitialFactAp): Boolean =
        statementFacts.any { statementFact -> statementFact.contains(fact) }

    private object DummyFactChecker : FactTypeChecker {
        override fun filterFactByLocalType(actualType: CommonType?, factAp: FinalFactAp): FinalFactAp = factAp
        override fun accessPathFilter(accessPath: List<Accessor>): FactTypeChecker.FactApFilter =
            FactTypeChecker.AlwaysAcceptFilter
    }
}
