package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodFinalApSummariesStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.MethodInitialToFinalApSummariesStorage
import org.opentaint.dataflow.jvm.ap.ifds.access.TaintSinkRequirementApStorage
import org.opentaint.dataflow.jvm.util.concurrentReadSafeIterator
import org.opentaint.dataflow.jvm.util.concurrentReadSafeMapIndexed
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SummaryEdgeSubscriptionManager(
    private val manager: TaintAnalysisUnitRunnerManager,
    private val processingCtx: SummaryEdgeProcessingCtx
) {
    private val methodSummarySubscriptions = Object2ObjectOpenHashMap<MethodEntryPoint, MethodSummarySubscription>()

    private fun methodSubscriptions(methodEntryPoint: MethodEntryPoint) =
        methodSummarySubscriptions.getOrPut(methodEntryPoint) {
            MethodSummarySubscription(manager.apManager).also {
                manager.subscribeOnMethodEntryPointSummaries(methodEntryPoint, methodSummaryHandler)
            }
        }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        callerPathEdge: Edge.ZeroToZero
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        if (!methodSubscriptions.addZeroToZero(callerPathEdge)) return false

        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)
        val summaries = manager.findZeroSummaryEdges(methodEntryPoint).asSequence().toList()
        if (summaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToZeroMethodSummaryEdge(callerPathEdge, summaries)
        }

        return true
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: Edge.ZeroToFact
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addZeroToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)

        val calleeInitialFact = addedSubscription.callerPathEdge.fact.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFact).asSequence().toList()

        if (summaries.isNotEmpty()) {
            val sub = MethodAnalyzer.ZeroToFactSub(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase
            )

            callerAnalyzer.handleZeroToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        return true
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: Edge.FactToFact
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addFactToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)

        val calleeInitialFact = addedSubscription.callerPathEdge.fact.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFact).asSequence().toList()

        if (summaries.isNotEmpty()) {
            val sub = MethodAnalyzer.FactToFactSub(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase
            )
            callerAnalyzer.handleFactToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        for (sinkRequirement in manager.findSinkRequirements(methodEntryPoint, calleeInitialFact)) {
            callerAnalyzer.handleMethodSinkRequirement(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase,
                sinkRequirement
            )
        }

        return true
    }

    private class MethodSummarySubscription(
        private val apManager: ApManager
    ) {
        private val zeroFactSubscriptions = MethodZeroFactSubscription()
        private val sameMarkTaintedFactSubscriptions =
            Object2ObjectOpenHashMap<TaintMark, MethodTaintedFactSubscription>()
        private val differentMarkTaintedFactSubscriptions =
            Object2ObjectOpenHashMap<TaintMark, MutableMap<TaintMark, MethodTaintedFactSubscription>>()

        fun addZeroToZero(callerPathEdge: Edge.ZeroToZero): Boolean =
            zeroFactSubscriptions.add(callerPathEdge)

        fun addFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.FactToFact
        ): FactEdgeSummarySubscription? =
            taintedStorage(callerPathEdge.fact.mark, callerPathEdge.initialFact.mark)
                .addFactToFact(calleeInitialFactBase, callerPathEdge)
                ?.setMarks(callerPathEdge.fact.mark, callerPathEdge.initialFact.mark)

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.ZeroToFact
        ): ZeroEdgeSummarySubscription? =
            taintedStorage(callerPathEdge.fact.mark)
                .addZeroToFact(
                    calleeInitialFactBase,
                    callerPathEdge.methodEntryPoint,
                    callerPathEdge.statement,
                    callerPathEdge.fact.ap
                )
                ?.setMarks(callerPathEdge.fact.mark)

        fun zeroFactSubscriptions() = zeroFactSubscriptions.subscriptions()

        fun findFactEdgeSub(
            summaryInitialFact: Fact.InitialFact
        ): Sequence<Pair<MethodEntryPoint, Sequence<FactEdgeSummarySubscription>>> {
            val m0 = summaryInitialFact.mark
            val same = sameMarkTaintedFactSubscriptions[m0]?.findFactEdge(summaryInitialFact.ap)?.map { (inst, subs) ->
                inst to subs.map { it.setMarks(m0, m0) }
            }

            val diff1 = differentMarkTaintedFactSubscriptions[m0]?.asSequence()?.flatMap { (m1, storage) ->
                storage.findFactEdge(summaryInitialFact.ap).map { (inst, subs) ->
                    inst to subs.map { it.setMarks(m0, m1) }
                }
            }

            return same.orEmpty() + diff1.orEmpty()
        }

        fun findZeroEdgeSub(
            summaryInitialFact: Fact.InitialFact
        ): Sequence<Pair<MethodEntryPoint, Sequence<ZeroEdgeSummarySubscription>>> {
            val m0 = summaryInitialFact.mark
            val same = sameMarkTaintedFactSubscriptions[m0]?.findZeroEdge(summaryInitialFact.ap)?.map { (inst, subs) ->
                inst to subs.map { it.setMarks(m0) }
            }
            return same.orEmpty()
        }

        private fun taintedStorage(
            mark0: TaintMark,
            mark1: TaintMark
        ): MethodTaintedFactSubscription {
            if (mark0 == mark1) {
                return sameMarkTaintedFactSubscriptions.getOrPut(mark0) { MethodTaintedFactSubscription(apManager) }
            }

            return differentMarkTaintedFactSubscriptions.getOrPut(mark0) {
                Object2ObjectOpenHashMap()
            }.getOrPut(mark1) {
                MethodTaintedFactSubscription(apManager)
            }
        }

        private fun taintedStorage(
            mark0: TaintMark
        ): MethodTaintedFactSubscription =
            sameMarkTaintedFactSubscriptions.getOrPut(mark0) { MethodTaintedFactSubscription(apManager) }
    }

    private class MethodZeroFactSubscription {
        private val subscriptions = Object2ObjectOpenHashMap<MethodEntryPoint, MutableSet<Edge.ZeroToZero>>()

        fun add(callerPathEdge: Edge.ZeroToZero): Boolean =
            subscriptions.getOrPut(callerPathEdge.methodEntryPoint) {
                ObjectOpenHashSet()
            }.add(callerPathEdge)

        fun subscriptions(): Map<MethodEntryPoint, Set<Edge.ZeroToZero>> = subscriptions
    }

    private class MethodTaintedFactSubscription(
        private val apManager: ApManager
    ) {
        private val subscriptions =
            Object2ObjectOpenHashMap<MethodEntryPoint, MutableMap<JIRInst, MethodAccessPathSubscription>>()

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerEntryPoint: MethodEntryPoint,
            callerExitStatement: JIRInst,
            callerFactAp: FinalFactAp
        ): ZeroEdgeSummarySubscription? =
            subscriptions.getOrPut(callerEntryPoint) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerExitStatement) {
                apManager.accessPathSubscription()
            }.addZeroToFact(calleeInitialFactBase, callerFactAp)
                ?.setStatements(callerEntryPoint, callerExitStatement)

        fun addFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.FactToFact
        ): FactEdgeSummarySubscription? =
            subscriptions.getOrPut(callerPathEdge.methodEntryPoint) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerPathEdge.statement) {
                apManager.accessPathSubscription()
            }.addFactToFact(calleeInitialFactBase, callerPathEdge.initialFact.ap, callerPathEdge.fact.ap)
                ?.setStatements(callerPathEdge.methodEntryPoint, callerPathEdge.statement)

        fun findFactEdge(
            summaryInitialFactAp: InitialFactAp
        ): Sequence<Pair<MethodEntryPoint, Sequence<FactEdgeSummarySubscription>>> =
            subscriptions.asSequence().map { (initialStmt, storage) ->
                initialStmt to storage.asSequence().flatMap { (exitStmt, subs) ->
                    subs.findFactEdge(summaryInitialFactAp).map {
                        it.setStatements(initialStmt, exitStmt)
                    }
                }
            }

        fun findZeroEdge(
            summaryInitialFactAp: InitialFactAp
        ): Sequence<Pair<MethodEntryPoint, Sequence<ZeroEdgeSummarySubscription>>> =
            subscriptions.asSequence().map { (initialStmt, storage) ->
                initialStmt to storage.asSequence().flatMap { (exitStmt, subs) ->
                    subs.findZeroEdge(summaryInitialFactAp).map {
                        it.setStatements(initialStmt, exitStmt)
                    }
                }
            }
    }

    data class FactEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerEntryPoint: MethodEntryPoint? = null,
        private var callerInitialFactMark: TaintMark? = null,
        private var callerInitialFactAp: InitialFactAp? = null,
        private var callerStatement: JIRInst? = null,
        private var callerFactMark: TaintMark? = null,
        private var callerFactAp: FinalFactAp? = null,
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.FactToFact
            get() = Edge.FactToFact(
                callerEntryPoint!!,
                Fact.InitialFact(callerInitialFactMark!!, callerInitialFactAp!!),
                callerStatement!!,
                Fact.FinalFact(
                    callerFactMark!!,
                    callerFactAp!!
                )
            )

        fun setCalleeBase(base: AccessPathBase) = this.also {
            calleeInitialFactApBase = base
        }

        fun setCallerInitialAp(ap: InitialFactAp) = this.also {
            callerInitialFactAp = ap
        }

        fun setCallerAp(ap: FinalFactAp) = this.also {
            callerFactAp = ap
        }

        fun setMarks(factMark: TaintMark, initialFactMark: TaintMark) = this.also {
            callerFactMark = factMark
            callerInitialFactMark = initialFactMark
        }

        fun setStatements(callerEntryPoint: MethodEntryPoint, callerExitStmt: JIRInst) = this.also {
            this.callerEntryPoint = callerEntryPoint
            callerStatement = callerExitStmt
        }
    }

    data class ZeroEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerPathEdgeEntryPoint: MethodEntryPoint? = null,
        private var callerPathEdgeExitStatement: JIRInst? = null,
        private var callerPathEdgeFactMark: TaintMark? = null,
        private var callerPathEdgeFactAp: FinalFactAp? = null
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.ZeroToFact
            get() = Edge.ZeroToFact(
                callerPathEdgeEntryPoint!!,
                callerPathEdgeExitStatement!!,
                Fact.FinalFact(
                    callerPathEdgeFactMark!!,
                    callerPathEdgeFactAp!!
                )
            )

        fun setMarks(mark: TaintMark) = this.also {
            callerPathEdgeFactMark = mark
        }

        fun setStatements(callerEntryPoint: MethodEntryPoint, callerExitStmt: JIRInst) = this.also {
            callerPathEdgeEntryPoint = callerEntryPoint
            callerPathEdgeExitStatement = callerExitStmt
        }

        fun setCalleeBase(base: AccessPathBase) = this.also {
            calleeInitialFactApBase = base
        }

        fun setCallerPathEdgeAp(ap: FinalFactAp) = this.also {
            callerPathEdgeFactAp = ap
        }
    }

    interface SummaryEvent {
        fun processMethodSummary()
    }

    private inner class NewSummaryEdgeEvent(private val summaryEdges: List<Edge>) : SummaryEvent {
        override fun processMethodSummary() {
            val sameInitialStatementEdges = summaryEdges.groupBy { it.methodEntryPoint }
            for ((initialStatement, edges) in sameInitialStatementEdges) {
                val subscriptions = methodSummarySubscriptions[initialStatement] ?: continue
                processMethodSummary(subscriptions, edges)
            }
        }

        fun processMethodSummary(subscriptions: MethodSummarySubscription, summaryEdges: List<Edge>) {
            val zeroEdges = summaryEdges.filterIsInstance<Edge.ZeroInitialEdge>()
            val factEdges = summaryEdges.filterIsInstance<Edge.FactToFact>()

            if (zeroEdges.isNotEmpty()) {
                processMethodZeroSummary(subscriptions, zeroEdges)
            }

            if (factEdges.isNotEmpty()) {
                processMethodFactSummary(subscriptions, factEdges)
            }
        }

        fun processMethodZeroSummary(
            subscriptions: MethodSummarySubscription,
            summaryEdges: List<Edge.ZeroInitialEdge>
        ) {
            subscriptions.zeroFactSubscriptions().forEach { (ep, callerPathEdges) ->
                val analyzer = processingCtx.getMethodAnalyzer(ep)
                for (callerPathEdge in callerPathEdges) {
                    analyzer.handleZeroToZeroMethodSummaryEdge(callerPathEdge, summaryEdges)
                }
            }
        }

        fun processMethodFactSummary(
            subscriptionStorage: MethodSummarySubscription,
            summaryEdges: List<Edge.FactToFact>
        ) {
            val sameInitialFactEdges = summaryEdges.groupBy { it.initialFact }
            for ((summaryInitialFact, summaries) in sameInitialFactEdges) {
                subscriptionStorage.findFactEdgeSub(summaryInitialFact).forEach { (ep, subscriptions) ->
                    val summarySubs = subscriptions.mapTo(mutableListOf()) {
                        MethodAnalyzer.FactToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                    }

                    if (summarySubs.isEmpty()) return@forEach

                    val analyzer = processingCtx.getMethodAnalyzer(ep)
                    analyzer.handleFactToFactMethodSummaryEdge(summarySubs, summaries)
                }

                subscriptionStorage.findZeroEdgeSub(summaryInitialFact).forEach { (ep, subscriptions) ->
                    val summarySubs = subscriptions.mapTo(mutableListOf()) {
                        MethodAnalyzer.ZeroToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                    }

                    if (summarySubs.isEmpty()) return@forEach

                    val analyzer = processingCtx.getMethodAnalyzer(ep)
                    analyzer.handleZeroToFactMethodSummaryEdge(summarySubs, summaries)
                }
            }
        }
    }

    private inner class NewSinkRequirementEvent(
        private val methodEntryPoint: MethodEntryPoint,
        private val sinkRequirement: Fact.InitialFact
    ) : SummaryEvent {
        override fun processMethodSummary() {
            val subscriptions = methodSummarySubscriptions[methodEntryPoint] ?: return

            subscriptions.findFactEdgeSub(sinkRequirement).forEach { (ep, subscriptions) ->
                val analyzer = processingCtx.getMethodAnalyzer(ep)
                for (subscription in subscriptions) {
                    analyzer.handleMethodSinkRequirement(
                        subscription.callerPathEdge, subscription.calleeInitialFactBase,
                        sinkRequirement
                    )
                }
            }
        }
    }

    interface SummaryEdgeProcessingCtx {
        fun addSummaryEdgeEvent(event: SummaryEvent)
        fun getMethodAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer
    }

    private val methodSummaryHandler = MethodSummaryEdgeHandler()

    private inner class MethodSummaryEdgeHandler : SummaryEdgeStorageWithSubscribers.Subscriber {
        override fun newSummaryEdges(edges: List<Edge>) {
            processingCtx.addSummaryEdgeEvent(NewSummaryEdgeEvent(edges))
        }

        override fun newSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: Fact.InitialFact) {
            processingCtx.addSummaryEdgeEvent(NewSinkRequirementEvent(methodEntryPoint, requirement))
        }
    }
}

class SummaryEdgeStorageWithSubscribers(
    private val apManager: ApManager,
    private val methodEntryPoint: MethodEntryPoint
) {
    interface Subscriber {
        fun newSummaryEdges(edges: List<Edge>)
        fun newSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: Fact.InitialFact)
    }

    private val subscribers = ConcurrentLinkedQueue<Subscriber>()

    private val zeroToZeroSummaryEdges = ArrayList<JIRInst>()
    private val zeroToFactSummaryEdges = ConcurrentHashMap<TaintMark, MethodFinalApSummariesStorage>()
    private val taintedFactSameMarkSummaryEdges = ConcurrentHashMap<TaintMark, MethodInitialToFinalApSummariesStorage>()
    private val taintedFactDifferentMarkSummaryEdges =
        ConcurrentHashMap<TaintMark, ConcurrentHashMap<TaintMark, MethodInitialToFinalApSummariesStorage>>()

    fun addEdges(edges: List<Edge>) {
        val addedEdges = mutableListOf<Edge>()
        val zeroToZeroEdges = edges.filterIsInstance<Edge.ZeroToZero>()
        val zeroToFactEdges = edges.filterIsInstance<Edge.ZeroToFact>()
        val factToFactEdges = edges.filterIsInstance<Edge.FactToFact>()

        addZeroToZeroEdges(zeroToZeroEdges, addedEdges)
        addZeroToFactEdges(zeroToFactEdges, addedEdges)
        addFactToFactEdges(factToFactEdges, addedEdges)

        for (subscriber in subscribers) {
            subscriber.newSummaryEdges(addedEdges)
        }
    }

    private val taintedSinkRequirement = ConcurrentHashMap<TaintMark, TaintSinkRequirementApStorage>()

    fun addSinkRequirement(requirement: Fact.InitialFact) {
        val markRequirements = taintedSinkRequirement.computeIfAbsent(requirement.mark) {
            apManager.taintSinkRequirementApStorage()
        }

        val addedAp = markRequirements.add(requirement.ap) ?: return
        val addedRequirement = Fact.InitialFact(requirement.mark, addedAp)

        for (subscriber in subscribers) {
            subscriber.newSinkRequirement(methodEntryPoint, addedRequirement)
        }
    }

    private fun addZeroToZeroEdges(edges: List<Edge.ZeroToZero>, added: MutableList<Edge>) {
        edges.mapTo(zeroToZeroSummaryEdges) { it.statement }
        added += edges
    }

    private fun addZeroToFactEdges(edges: List<Edge.ZeroToFact>, added: MutableList<Edge>) {
        val edgesWithSameMark = edges.groupBy { it.fact.mark }

        for ((mark, sameMarkEdges) in edgesWithSameMark) {
            val summariesStorage = zeroToFactSummaryEdges.computeIfAbsent(mark) {
                apManager.methodFinalApSummariesStorage(methodEntryPoint.statement)
            }

            synchronized(summariesStorage) {
                val addedEdgeBuilders = mutableListOf<ZeroToFactEdgeBuilder>()
                summariesStorage.add(sameMarkEdges, addedEdgeBuilders)
                addedEdgeBuilders.mapTo(added) {
                    it.setEntryPoint(methodEntryPoint).setMark(mark).build()
                }
            }
        }
    }

    private fun addFactToFactEdges(edges: List<Edge.FactToFact>, added: MutableList<Edge>) {
        if (edges.isEmpty()) return

        val edgesWithSameMark = edges.groupBy { it.initialFact.mark to it.fact.mark }

        for ((marks, sameMarkEdges) in edgesWithSameMark) {
            val (initialMark, resultMark) = marks

            val summariesStorage = if (initialMark == resultMark) {
                taintedFactSameMarkSummaryEdges.computeIfAbsent(initialMark) {
                    apManager.methodInitialToFinalApSummariesStorage(methodEntryPoint.statement)
                }
            } else {
                taintedFactDifferentMarkSummaryEdges.computeIfAbsent(initialMark) {
                    ConcurrentHashMap()
                }.computeIfAbsent(resultMark) {
                    apManager.methodInitialToFinalApSummariesStorage(methodEntryPoint.statement)
                }
            }

            synchronized(summariesStorage) {
                val addedEdgeBuilders = mutableListOf<FactToFactEdgeBuilder>()
                summariesStorage.add(sameMarkEdges, addedEdgeBuilders)
                addedEdgeBuilders.mapTo(added) {
                    it.setEntryPoint(methodEntryPoint).setMarks(initialMark, resultMark).build()
                }
            }
        }
    }

    fun subscribeOnEdges(handler: Subscriber) {
        subscribers.add(handler)
    }

    fun zeroEdgesIterator(): Iterator<Edge.ZeroInitialEdge> {
        val zeroToZero = allZeroToZeroSummaries()
        val zeroToFact = allZeroToFactSummaries()
        return (zeroToZero + zeroToFact).iterator()
    }

    private fun allZeroToZeroSummaries() =
        zeroToZeroSummaryEdges.concurrentReadSafeIterator().asSequence()
            .map { Edge.ZeroToZero(methodEntryPoint, it) }

    private fun allZeroToFactSummaries() = zeroToFactSummaryEdges.asSequence()
        .flatMap { (mark, storage) ->
            storage.allEdges().map { it.setEntryPoint(methodEntryPoint).setMark(mark).build() }
        }

    fun factEdgesIterator(initialFact: Fact.FinalFact): Iterator<Edge.FactToFact> {
        val sameFactEdges = taintedFactSameMarkSummaryEdges[initialFact.mark]
            ?.filterEdgesAndSetMarks(initialFact.mark, initialFact.mark, initialFact.ap)
            .orEmpty()

        val differentFactEdges = taintedFactDifferentMarkSummaryEdges[initialFact.mark]
            ?.asSequence()
            ?.flatMap { (exitMark, storage) ->
                storage.filterEdgesAndSetMarks(initialFact.mark, exitMark, initialFact.ap)
            }
            .orEmpty()

        return (sameFactEdges + differentFactEdges)
            .map { it.setEntryPoint(methodEntryPoint).build() }
            .iterator()
    }

    fun factEdgesIterator(): Iterator<Edge.FactToFact> = allFactToFactSummaries().iterator()

    private fun allFactToFactSummaries(): Sequence<Edge.FactToFact> {
        val sameFactEdges = taintedFactSameMarkSummaryEdges.asSequence().flatMap { (initialMark, storage) ->
            storage.takeAllEdgesAndSetMarks(initialMark, initialMark)
        }

        val differentFactEdges = taintedFactDifferentMarkSummaryEdges.asSequence().flatMap { (initialMark, other) ->
            other.asSequence().flatMap { (exitMark, storage) ->
                storage.takeAllEdgesAndSetMarks(initialMark, exitMark)
            }
        }

        return (sameFactEdges + differentFactEdges)
            .map { it.setEntryPoint(methodEntryPoint).build() }
    }

    private fun MethodInitialToFinalApSummariesStorage.filterEdgesAndSetMarks(
        initialMark: TaintMark,
        exitMark: TaintMark,
        filter: FinalFactAp,
    ): Sequence<FactToFactEdgeBuilder> = filterEdges(filter)
        .map { it.setMarks(initialMark, exitMark) }

    private fun MethodInitialToFinalApSummariesStorage.takeAllEdgesAndSetMarks(
        initialMark: TaintMark,
        exitMark: TaintMark,
    ): Sequence<FactToFactEdgeBuilder> = allEdges()
        .map { it.setMarks(initialMark, exitMark) }

    fun sinkRequirementIterator(initialFact: Fact.FinalFact): Iterator<Fact.InitialFact> =
        taintedSinkRequirement[initialFact.mark]
            ?.find(initialFact.ap)
            ?.map { Fact.InitialFact(initialFact.mark, it) }
            .orEmpty()
            .iterator()

    fun collectStats(stats: MethodStats) {
        val sourceSummaries = allZeroToFactSummaries().sumOf { it.fact.ap.size }
        val passSummaries = allFactToFactSummaries().sumOf { it.fact.ap.size }

        stats.stats(methodEntryPoint.method).sourceSummaries += sourceSummaries
        stats.stats(methodEntryPoint.method).passSummaries += passSummaries
    }
}

sealed interface EdgeBuilder<B : EdgeBuilder<B>> {
    fun setEntryPoint(entryPoint: MethodEntryPoint): B
    fun setExitStatement(statement: JIRInst): B
}

data class ZeroToFactEdgeBuilder(
    private var entryPoint: MethodEntryPoint? = null,
    private var exitStatement: JIRInst? = null,
    private var exitFactAp: FinalFactAp? = null,
    private var mark: TaintMark? = null
) : EdgeBuilder<ZeroToFactEdgeBuilder> {
    fun build(): Edge.ZeroToFact = Edge.ZeroToFact(
        entryPoint!!, exitStatement!!,
        Fact.FinalFact(
            mark!!,
            exitFactAp!!
        )
    )

    fun setMark(mark: TaintMark) = this.also {
        this.mark = mark
    }

    override fun setEntryPoint(entryPoint: MethodEntryPoint) = this.also {
        this.entryPoint = entryPoint
    }

    override fun setExitStatement(statement: JIRInst) = this.also {
        exitStatement = statement
    }

    fun setExitAp(ap: FinalFactAp) = this.also {
        exitFactAp = ap
    }
}

data class FactToFactEdgeBuilder(
    private var entryPoint: MethodEntryPoint? = null,
    private var exitStatement: JIRInst? = null,
    private var initialMark: TaintMark? = null,
    private var exitMark: TaintMark? = null,
    private var initialAp: InitialFactAp? = null,
    private var exitAp: FinalFactAp? = null,
) : EdgeBuilder<FactToFactEdgeBuilder> {
    fun build(): Edge.FactToFact = Edge.FactToFact(
        entryPoint!!,
        Fact.InitialFact(initialMark!!, initialAp!!),
        exitStatement!!,
        Fact.FinalFact(exitMark!!, exitAp!!)
    )

    override fun setEntryPoint(entryPoint: MethodEntryPoint) = this.also {
        this.entryPoint = entryPoint
    }

    override fun setExitStatement(statement: JIRInst) = this.also {
        exitStatement = statement
    }

    fun setMarks(initialMark: TaintMark, exitMark: TaintMark) = this.also {
        this.initialMark = initialMark
        this.exitMark = exitMark
    }

    fun setInitialAp(ap: InitialFactAp) = this.also {
        initialAp = ap
    }

    fun setExitAp(ap: FinalFactAp) = this.also {
        exitAp = ap
    }
}

abstract class SummaryFactStorage<Storage : Any>(methodEntryPoint: JIRInst) :
    AccessPathBaseStorage<Storage>(methodEntryPoint) {
    private val locals = ConcurrentHashMap<Int, Storage>()
    private var constants: ConcurrentHashMap<AccessPathBase.Constant, Storage>? = null
    private var statics: ConcurrentHashMap<AccessPathBase.ClassStatic, Storage>? = null

    override fun getOrCreateLocal(idx: Int): Storage =
        locals.computeIfAbsent(idx) { createStorage() }

    override fun findLocal(idx: Int): Storage? = locals[idx]

    override fun <R : Any> mapLocalValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
        locals.asSequence().map { (localVarIdx, storage) ->
            body(AccessPathBase.LocalVar(localVarIdx), storage)
        }

    override fun getOrCreateConstant(base: AccessPathBase.Constant): Storage {
        val summaries = constants ?: ConcurrentHashMap<AccessPathBase.Constant, Storage>()
            .also { constants = it }

        return summaries.computeIfAbsent(base) { createStorage() }
    }

    override fun findConstant(base: AccessPathBase.Constant): Storage? =
        constants?.get(base)

    override fun <R : Any> mapConstantValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
        constants?.asSequence()?.map { body(it.key, it.value) } ?: emptySequence()

    override fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): Storage {
        val summaries = statics ?: ConcurrentHashMap<AccessPathBase.ClassStatic, Storage>()
            .also { statics = it }

        return summaries.computeIfAbsent(base) { createStorage() }
    }

    override fun findClassStatic(base: AccessPathBase.ClassStatic): Storage? =
        statics?.get(base)

    override fun <R : Any> mapClassStaticValues(body: (AccessPathBase, Storage) -> R): Sequence<R> =
        statics?.asSequence()?.map { body(it.key, it.value) } ?: emptySequence()
}

abstract class MethodSummaryEdgesForExitPoint<E : Edge, B : EdgeBuilder<B>, Storage, Pattern>(
    val methodEntryPoint: JIRInst
) {
    abstract fun createStorage(): Storage
    abstract fun storageAdd(storage: Storage, edges: List<E>, added: MutableList<B>)

    abstract fun storageAllEdges(storage: Storage): Sequence<B>

    abstract fun storageFilterEdges(storage: Storage, containsPattern: Pattern): Sequence<B>

    private val exitPoints = arrayListOf<JIRInst>()
    private val exitPointsStorage = arrayListOf<Storage>()

    fun add(edges: List<E>, added: MutableList<B>) {
        val edgesWithSameExitPoint = edges.groupBy { it.statement }

        for ((exitPoint, exitPointEdges) in edgesWithSameExitPoint) {
            val epIdx = exitPoints.indexOf(exitPoint)
            val storage = if (epIdx != -1) {
                exitPointsStorage[epIdx]
            } else {
                createStorage().also {
                    exitPoints.add(exitPoint)
                    exitPointsStorage.add(it)
                }
            }

            val storageAdded = mutableListOf<B>()
            storageAdd(storage, exitPointEdges, storageAdded)
            storageAdded.mapTo(added) { it.setExitStatement(exitPoint) }
        }
    }

    fun allEdges(): Sequence<B> = processEdgeSequence { storageAllEdges(it) }

    fun filterEdges(containsPattern: Pattern): Sequence<B> = processEdgeSequence {
        storageFilterEdges(it, containsPattern)
    }

    private inline fun processEdgeSequence(storageEdges: (Storage) -> Sequence<B>): Sequence<B> =
        exitPointsStorage.concurrentReadSafeMapIndexed { idx, storage ->
            val exitPoint = exitPoints[idx]
            storageEdges(storage).map { it.setExitStatement(exitPoint) }
        }.asSequence().flatten()
}
