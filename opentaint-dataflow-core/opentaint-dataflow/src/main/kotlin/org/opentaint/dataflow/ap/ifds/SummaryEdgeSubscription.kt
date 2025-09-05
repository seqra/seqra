package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.concurrentReadSafeMapIndexed
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

    data class MethodEntryPointCaller(val callerEp: MethodEntryPoint, val statement: CommonInst)

    fun methodEntryPointCallers(
        entryPoint: MethodEntryPoint,
        collectZeroCallsOnly: Boolean,
        callers: MutableSet<MethodEntryPointCaller>
    ) {
        val subscribers = methodSummarySubscriptions[entryPoint] ?: return
        return subscribers.collectCallers(collectZeroCallsOnly, callers)
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        callerPathEdge: Edge.ZeroToZero
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        if (!methodSubscriptions.addZeroToZero(callerPathEdge)) return false

        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)
        val summaries = manager.findZeroSummaryEdges(methodEntryPoint)
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

        val calleeInitialFactAp = addedSubscription.callerPathEdge.factAp.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFactAp)

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

        val calleeInitialFactAp = addedSubscription.callerPathEdge.factAp.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFactAp)

        if (summaries.isNotEmpty()) {
            val sub = MethodAnalyzer.FactToFactSub(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase
            )
            callerAnalyzer.handleFactToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        val sideEffectRequirements = manager.findSideEffectRequirements(methodEntryPoint, calleeInitialFactAp)
        if (sideEffectRequirements.isNotEmpty()) {
            callerAnalyzer.handleMethodSideEffectRequirement(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase,
                sideEffectRequirements
            )
        }

        return true
    }

    private class MethodSummarySubscription(
        apManager: ApManager
    ) {
        private val zeroFactSubscriptions = MethodZeroFactSubscription()
        private val taintedFactSubscriptions = MethodTaintedFactSubscription(apManager)

        fun addZeroToZero(callerPathEdge: Edge.ZeroToZero): Boolean =
            zeroFactSubscriptions.add(callerPathEdge)

        fun addFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.FactToFact
        ): FactEdgeSummarySubscription? =
            taintedFactSubscriptions
                .addFactToFact(calleeInitialFactBase, callerPathEdge)

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.ZeroToFact
        ): ZeroEdgeSummarySubscription? =
            taintedFactSubscriptions
                .addZeroToFact(
                    calleeInitialFactBase,
                    callerPathEdge.methodEntryPoint,
                    callerPathEdge.statement,
                    callerPathEdge.factAp
                )

        fun zeroFactSubscriptions() = zeroFactSubscriptions.subscriptions()

        fun findFactEdgeSub(
            summaryInitialFactAp: InitialFactAp
        ): Sequence<Pair<MethodEntryPoint, Sequence<FactEdgeSummarySubscription>>> {
            return taintedFactSubscriptions.findFactEdge(summaryInitialFactAp)
        }

        fun findZeroEdgeSub(
            summaryInitialFactAp: InitialFactAp
        ): Sequence<Pair<MethodEntryPoint, Sequence<ZeroEdgeSummarySubscription>>> {
            return taintedFactSubscriptions.findZeroEdge(summaryInitialFactAp)
        }

        fun collectCallers(collectZeroCallsOnly: Boolean, callers: MutableSet<MethodEntryPointCaller>) {
            zeroFactSubscriptions.collectCallers(callers)

            if (collectZeroCallsOnly) return

            taintedFactSubscriptions.collectCallers(callers)
        }
    }

    private class MethodZeroFactSubscription {
        private val subscriptions = Object2ObjectOpenHashMap<MethodEntryPoint, MutableSet<Edge.ZeroToZero>>()

        fun add(callerPathEdge: Edge.ZeroToZero): Boolean =
            subscriptions.getOrPut(callerPathEdge.methodEntryPoint) {
                ObjectOpenHashSet()
            }.add(callerPathEdge)

        fun subscriptions(): Map<MethodEntryPoint, Set<Edge.ZeroToZero>> = subscriptions

        fun collectCallers(callers: MutableSet<MethodEntryPointCaller>) {
            subscriptions.forEach { (methodEp, sub) ->
                sub.forEach { callers += MethodEntryPointCaller(methodEp, it.statement) }
            }
        }
    }

    private class MethodTaintedFactSubscription(
        private val apManager: ApManager
    ) {
        private val subscriptions =
            Object2ObjectOpenHashMap<MethodEntryPoint, MutableMap<CommonInst, MethodAccessPathSubscription>>()

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerEntryPoint: MethodEntryPoint,
            callerExitStatement: CommonInst,
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
            }.addFactToFact(calleeInitialFactBase, callerPathEdge.initialFactAp, callerPathEdge.factAp)
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

        fun collectCallers(callers: MutableSet<MethodEntryPointCaller>) {
            subscriptions.forEach { (methodEp, sub) ->
                sub.keys.forEach { callers += MethodEntryPointCaller(methodEp, it) }
            }
        }
    }

    data class FactEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerEntryPoint: MethodEntryPoint? = null,
        private var callerInitialFactAp: InitialFactAp? = null,
        private var callerStatement: CommonInst? = null,
        private var callerFactAp: FinalFactAp? = null,
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.FactToFact
            get() = Edge.FactToFact(
                callerEntryPoint!!,
                callerInitialFactAp!!,
                callerStatement!!,
                callerFactAp!!
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

        fun setStatements(callerEntryPoint: MethodEntryPoint, callerExitStmt: CommonInst) = this.also {
            this.callerEntryPoint = callerEntryPoint
            callerStatement = callerExitStmt
        }
    }

    data class ZeroEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerPathEdgeEntryPoint: MethodEntryPoint? = null,
        private var callerPathEdgeExitStatement: CommonInst? = null,
        private var callerPathEdgeFactAp: FinalFactAp? = null
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.ZeroToFact
            get() = Edge.ZeroToFact(
                callerPathEdgeEntryPoint!!,
                callerPathEdgeExitStatement!!,
                callerPathEdgeFactAp!!
            )

        fun setStatements(callerEntryPoint: MethodEntryPoint, callerExitStmt: CommonInst) = this.also {
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
            val sameInitialFactEdges = summaryEdges.groupBy { it.initialFactAp }
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

    private inner class NewSideEffectRequirementEvent(
        private val methodEntryPoint: MethodEntryPoint,
        private val sideEffectRequirements: List<InitialFactAp>
    ) : SummaryEvent {
        override fun processMethodSummary() {
            val subscriptions = methodSummarySubscriptions[methodEntryPoint] ?: return

            sideEffectRequirements.forEach { sideEffectRequirement ->
                subscriptions.findFactEdgeSub(sideEffectRequirement).forEach { (ep, subscriptions) ->
                    val analyzer = processingCtx.getMethodAnalyzer(ep)
                    for (subscription in subscriptions) {
                        analyzer.handleMethodSideEffectRequirement(
                            subscription.callerPathEdge, subscription.calleeInitialFactBase,
                            listOf(sideEffectRequirement)
                        )
                    }
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

        override fun newSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>) {
            processingCtx.addSummaryEdgeEvent(NewSideEffectRequirementEvent(methodEntryPoint, requirements))
        }
    }
}

class SummaryEdgeStorageWithSubscribers(
    apManager: ApManager,
    private val methodEntryPoint: MethodEntryPoint
) {
    interface Subscriber {
        fun newSummaryEdges(edges: List<Edge>)
        fun newSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>)
    }

    private val subscribers = ConcurrentLinkedQueue<Subscriber>()

    private val zeroToZeroSummaryEdges = ArrayList<CommonInst>()
    private val zeroToFactSummaryEdges = apManager.methodFinalApSummariesStorage(methodEntryPoint.statement)
    private val taintedFactSummaryEdges = apManager.methodInitialToFinalApSummariesStorage(methodEntryPoint.statement)

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

    private val sideEffectRequirement = apManager.sideEffectRequirementApStorage()

    fun sideEffectRequirement(requirements: List<InitialFactAp>) {
        val addedRequirements = sideEffectRequirement.add(requirements)

        for (subscriber in subscribers) {
            subscriber.newSideEffectRequirement(methodEntryPoint, addedRequirements)
        }
    }

    private fun addZeroToZeroEdges(edges: List<Edge.ZeroToZero>, added: MutableList<Edge>) {
        edges.mapTo(zeroToZeroSummaryEdges) { it.statement }
        added += edges
    }

    private fun addZeroToFactEdges(edges: List<Edge.ZeroToFact>, added: MutableList<Edge>) {
        val summariesStorage = zeroToFactSummaryEdges
        synchronized(summariesStorage) {
            val addedEdgeBuilders = mutableListOf<ZeroToFactEdgeBuilder>()
            summariesStorage.add(edges, addedEdgeBuilders)
            addedEdgeBuilders.mapTo(added) {
                it.setEntryPoint(methodEntryPoint).build()
            }
        }
    }

    private fun addFactToFactEdges(edges: List<Edge.FactToFact>, added: MutableList<Edge>) {
        if (edges.isEmpty()) return

        val summariesStorage = taintedFactSummaryEdges

        synchronized(summariesStorage) {
            val addedEdgeBuilders = mutableListOf<FactToFactEdgeBuilder>()
            summariesStorage.add(edges, addedEdgeBuilders)
            addedEdgeBuilders.mapTo(added) {
                it.setEntryPoint(methodEntryPoint).build()
            }
        }
    }

    fun subscribeOnEdges(handler: Subscriber) {
        subscribers.add(handler)
    }

    fun zeroEdges(): List<Edge.ZeroInitialEdge> {
        val result = mutableListOf<Edge.ZeroInitialEdge>()
        collectAllZeroToZeroSummariesTo(result)
        collectAllZeroToFactSummariesTo(result)
        return result
    }

    fun zeroToFactEdges(factBase: AccessPathBase): List<Edge.ZeroToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            zeroToFactSummaryEdges.filterEdgesTo(it, factBase)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    private fun collectAllZeroToZeroSummariesTo(dst: MutableList<Edge.ZeroInitialEdge>) {
        zeroToZeroSummaryEdges.concurrentReadSafeForEach { _, inst ->
            dst.add(Edge.ZeroToZero(methodEntryPoint, inst))
        }
    }

    private fun collectAllZeroToFactSummariesTo(dst: MutableList<Edge.ZeroInitialEdge>) {
        collectToListWithPostProcess(dst, {
            zeroToFactSummaryEdges.collectAllEdgesTo(it)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })
    }

    fun factEdges(initialFactAp: FinalFactAp): List<Edge.FactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            taintedFactSummaryEdges.filterEdgesTo(it, initialFactAp, finalFactBase = null)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    fun factToFactEdges(
        initialFactAp: FinalFactAp,
        finalFactBase: AccessPathBase
    ): List<Edge.FactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            taintedFactSummaryEdges.filterEdgesTo(it, initialFactAp, finalFactBase)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    private fun collectAllFactToFactSummariesTo(dst: MutableList<Edge.FactToFact>) {
        collectToListWithPostProcess(dst, {
            taintedFactSummaryEdges.collectAllEdgesTo(it)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })
    }

    fun sideEffectRequirement(initialFactAp: FinalFactAp): List<InitialFactAp> {
        val result = mutableListOf<InitialFactAp>()
        sideEffectRequirement.filterTo(result, initialFactAp)
        return result
    }

    fun collectStats(stats: MethodStats) {
        val sourceEdges = mutableListOf<Edge.ZeroInitialEdge>()
        collectAllZeroToFactSummariesTo(sourceEdges)
        val sourceSummaries = sourceEdges.sumOf { (it as? Edge.ZeroToFact)?.factAp?.size ?: 0 }

        val passEdges = mutableListOf<Edge.FactToFact>()
        collectAllFactToFactSummariesTo(passEdges)
        val passSummaries = passEdges.sumOf { it.factAp.size }

        stats.stats(methodEntryPoint.method).sourceSummaries += sourceSummaries
        stats.stats(methodEntryPoint.method).passSummaries += passSummaries
    }
}

sealed interface EdgeBuilder<B : EdgeBuilder<B>> {
    fun setEntryPoint(entryPoint: MethodEntryPoint): B
    fun setExitStatement(statement: CommonInst): B
}

data class ZeroToFactEdgeBuilder(
    private var entryPoint: MethodEntryPoint? = null,
    private var exitStatement: CommonInst? = null,
    private var exitFactAp: FinalFactAp? = null,
) : EdgeBuilder<ZeroToFactEdgeBuilder> {
    fun build(): Edge.ZeroToFact = Edge.ZeroToFact(
        entryPoint!!, exitStatement!!,
        exitFactAp!!
    )

    override fun setEntryPoint(entryPoint: MethodEntryPoint) = this.also {
        this.entryPoint = entryPoint
    }

    override fun setExitStatement(statement: CommonInst) = this.also {
        exitStatement = statement
    }

    fun setExitAp(ap: FinalFactAp) = this.also {
        exitFactAp = ap
    }
}

data class FactToFactEdgeBuilder(
    private var entryPoint: MethodEntryPoint? = null,
    private var exitStatement: CommonInst? = null,
    private var initialAp: InitialFactAp? = null,
    private var exitAp: FinalFactAp? = null,
) : EdgeBuilder<FactToFactEdgeBuilder> {
    fun build(): Edge.FactToFact = Edge.FactToFact(
        entryPoint!!,
        initialAp!!,
        exitStatement!!,
        exitAp!!
    )

    override fun setEntryPoint(entryPoint: MethodEntryPoint) = this.also {
        this.entryPoint = entryPoint
    }

    override fun setExitStatement(statement: CommonInst) = this.also {
        exitStatement = statement
    }

    fun setInitialAp(ap: InitialFactAp) = this.also {
        initialAp = ap
    }

    fun setExitAp(ap: FinalFactAp) = this.also {
        exitAp = ap
    }
}

abstract class SummaryFactStorage<Storage : Any>(methodEntryPoint: CommonInst) :
    AccessPathBaseStorage<Storage>(methodEntryPoint) {
    private val locals = ConcurrentHashMap<Int, Storage>()
    private var constants: ConcurrentHashMap<AccessPathBase.Constant, Storage>? = null
    private var statics: ConcurrentHashMap<AccessPathBase.ClassStatic, Storage>? = null

    override fun getOrCreateLocal(idx: Int): Storage =
        locals.computeIfAbsent(idx) { createStorage() }

    override fun findLocal(idx: Int): Storage? = locals[idx]

    override fun forEachLocalValue(body: (AccessPathBase, Storage) -> Unit) {
        locals.forEach { localVarIdx, storage -> body(AccessPathBase.LocalVar(localVarIdx), storage) }
    }

    override fun getOrCreateConstant(base: AccessPathBase.Constant): Storage {
        val summaries = constants ?: ConcurrentHashMap<AccessPathBase.Constant, Storage>()
            .also { constants = it }

        return summaries.computeIfAbsent(base) { createStorage() }
    }

    override fun findConstant(base: AccessPathBase.Constant): Storage? =
        constants?.get(base)

    override fun forEachConstantValue(body: (AccessPathBase, Storage) -> Unit) {
        constants?.forEach { body(it.key, it.value) }
    }

    override fun getOrCreateClassStatic(base: AccessPathBase.ClassStatic): Storage {
        val summaries = statics ?: ConcurrentHashMap<AccessPathBase.ClassStatic, Storage>()
            .also { statics = it }

        return summaries.computeIfAbsent(base) { createStorage() }
    }

    override fun findClassStatic(base: AccessPathBase.ClassStatic): Storage? =
        statics?.get(base)

    override fun forEachClassStaticValue(body: (AccessPathBase, Storage) -> Unit) {
        statics?.forEach { body(it.key, it.value) }
    }
}

typealias MethodSummaryZeroEdgesForExitPoint<Storage, Pattern> =
        MethodSummaryEdgesForExitPoint<Edge.ZeroToFact, ZeroToFactEdgeBuilder, Storage, Pattern>

typealias MethodSummaryFactEdgesForExitPoint<Storage, Pattern> =
        MethodSummaryEdgesForExitPoint<Edge.FactToFact, FactToFactEdgeBuilder, Storage, Pattern>

abstract class MethodSummaryEdgesForExitPoint<E : Edge, B : EdgeBuilder<B>, Storage, Pattern>(
    val methodEntryPoint: CommonInst
) {
    abstract fun createStorage(): Storage
    abstract fun storageAdd(storage: Storage, edges: List<E>, added: MutableList<B>)

    abstract fun storageCollectAllEdgesTo(dst: MutableList<B>, storage: Storage)

    abstract fun storageFilterEdgesTo(dst: MutableList<B>, storage: Storage, containsPattern: Pattern)

    private val exitPoints = arrayListOf<CommonInst>()
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

    fun collectAllEdgesTo(dst: MutableList<B>) {
        processStorageEdges(dst) { storage, result ->
            storageCollectAllEdgesTo(result, storage)
        }
    }

    fun filterEdgesTo(dst: MutableList<B>, containsPattern: Pattern) {
        processStorageEdges(dst) { storage, result ->
            storageFilterEdgesTo(result, storage, containsPattern)
        }
    }

    private inline fun processStorageEdges(dst: MutableList<B>, storageEdges: (Storage, MutableList<B>) -> Unit) {
        exitPointsStorage.concurrentReadSafeMapIndexed { idx, storage ->
            val exitPoint = exitPoints[idx]

            collectToListWithPostProcess(dst, {
                storageEdges(storage, it)
            }, {
                it.setExitStatement(exitPoint)
            })
        }
    }

    override fun toString(): String =
        exitPointsStorage.concurrentReadSafeMapIndexed { idx, storage ->
            val exitPoint = exitPoints[idx]
            "($exitPoint: $storage)"
        }.joinToString("\n")
}
