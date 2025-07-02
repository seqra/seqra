package org.opentaint.dataflow.jvm.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.jvm.ap.ifds.AccessTree.AccessNode
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase
import org.opentaint.dataflow.jvm.util.concurrentReadSafeIterator
import org.opentaint.dataflow.jvm.util.concurrentReadSafeMapIndexed
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class SummaryEdgeSubscriptionManager(
    private val manager: TaintAnalysisUnitRunnerManager,
    private val processingCtx: SummaryEdgeProcessingCtx
) {
    private val methodSummarySubscriptions = Object2ObjectOpenHashMap<JIRInst, MethodSummarySubscription>()

    private fun methodSubscriptions(methodEntryPoint: JIRInst) =
        methodSummarySubscriptions.getOrPut(methodEntryPoint) {
            MethodSummarySubscription().also {
                manager.subscribeOnMethodEntryPointSummaries(methodEntryPoint, methodSummaryHandler)
            }
        }

    fun subscribeOnMethodSummary(
        methodEntryPoint: JIRInst,
        callerPathEdge: Edge.ZeroToZero
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        if (!methodSubscriptions.addZeroToZero(callerPathEdge)) return false

        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.initialStatement)
        val summaries = manager.findZeroSummaryEdges(methodEntryPoint).asSequence().toList()
        if (summaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToZeroMethodSummaryEdge(callerPathEdge, summaries)
        }

        return true
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: JIRInst,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: Edge.ZeroToFact
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addZeroToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.initialStatement)

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
        methodEntryPoint: JIRInst,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: Edge.FactToFact
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addFactToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.initialStatement)

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
                sinkRequirement,
                methodEntryPoint
            )
        }

        return true
    }

    private class MethodSummarySubscription {
        private val zeroFactSubscriptions = MethodZeroFactSubscription()
        private val sameMarkTaintedFactSubscriptions = Object2ObjectOpenHashMap<TaintMark, MethodTaintedFactSubscription>()
        private val differentMarkTaintedFactSubscriptions = Object2ObjectOpenHashMap<TaintMark, MutableMap<TaintMark, MethodTaintedFactSubscription>>()

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
                    callerPathEdge.initialStatement,
                    callerPathEdge.statement,
                    callerPathEdge.fact.ap
                )
                ?.setMarks(callerPathEdge.fact.mark)

        fun zeroFactSubscriptions() = zeroFactSubscriptions.subscriptions()

        fun findFactEdgeSub(
            summaryInitialFact: Fact.TaintedPath
        ): Sequence<Pair<JIRInst, Sequence<FactEdgeSummarySubscription>>> {
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
            summaryInitialFact: Fact.TaintedPath
        ) : Sequence<Pair<JIRInst, Sequence<ZeroEdgeSummarySubscription>>>{
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
                return sameMarkTaintedFactSubscriptions.getOrPut(mark0) { MethodTaintedFactSubscription() }
            }

            return differentMarkTaintedFactSubscriptions.getOrPut(mark0) {
                Object2ObjectOpenHashMap()
            }.getOrPut(mark1) {
                MethodTaintedFactSubscription()
            }
        }

        private fun taintedStorage(
            mark0: TaintMark
        ): MethodTaintedFactSubscription =
            sameMarkTaintedFactSubscriptions.getOrPut(mark0) { MethodTaintedFactSubscription() }
    }

    private class MethodZeroFactSubscription {
        private val subscriptions = Object2ObjectOpenHashMap<JIRInst, MutableSet<Edge.ZeroToZero>>()

        fun add(callerPathEdge: Edge.ZeroToZero): Boolean =
            subscriptions.getOrPut(callerPathEdge.initialStatement) {
                ObjectOpenHashSet()
            }.add(callerPathEdge)

        fun subscriptions(): Map<JIRInst, Set<Edge.ZeroToZero>> = subscriptions
    }

    private class MethodTaintedFactSubscription {
        private val subscriptions = Object2ObjectOpenHashMap<JIRInst, MutableMap<JIRInst, MethodAccessPathSubscription>>()

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerInitialStatement: JIRInst,
            callerExitStatement: JIRInst,
            callerFactAp: AccessTree
        ): ZeroEdgeSummarySubscription? =
            subscriptions.getOrPut(callerInitialStatement) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerExitStatement) {
                MethodAccessPathSubscription()
            }.addZeroToFact(calleeInitialFactBase, callerFactAp)
                ?.setStatements(callerInitialStatement, callerExitStatement)

        fun addFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.FactToFact
        ): FactEdgeSummarySubscription? =
            subscriptions.getOrPut(callerPathEdge.initialStatement) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerPathEdge.statement) {
                MethodAccessPathSubscription()
            }.addFactToFact(calleeInitialFactBase, callerPathEdge.initialFact.ap, callerPathEdge.fact.ap)
                ?.setStatements(callerPathEdge.initialStatement, callerPathEdge.statement)

        fun findFactEdge(
            summaryInitialFactAp: AccessPath
        ): Sequence<Pair<JIRInst, Sequence<FactEdgeSummarySubscription>>> =
            subscriptions.asSequence().map { (initialStmt, storage) ->
                initialStmt to storage.asSequence().flatMap { (exitStmt, subs) ->
                    subs.findFactEdge(summaryInitialFactAp).map {
                        it.setStatements(initialStmt, exitStmt)
                    }
                }
            }

        fun findZeroEdge(
            summaryInitialFactAp: AccessPath
        ): Sequence<Pair<JIRInst, Sequence<ZeroEdgeSummarySubscription>>> =
            subscriptions.asSequence().map { (initialStmt, storage) ->
                initialStmt to storage.asSequence().flatMap { (exitStmt, subs) ->
                    subs.findZeroEdge(summaryInitialFactAp).map {
                        it.setStatements(initialStmt, exitStmt)
                    }
                }
            }
    }

    private class MethodAccessPathSubscription {
        private val initialBaseSubscription = Object2ObjectOpenHashMap<AccessPathBase, FactEdgeSubscriptionStorage>()
        private val initialBaseTreeSubscription = Object2ObjectOpenHashMap<AccessPathBase, ZeroEdgeSubscriptionStorage>()

        fun addZeroToFact(
            calleeInitialFactBase: AccessPathBase,
            callerFactAp: AccessTree
        ): ZeroEdgeSummarySubscription? =
            initialBaseTreeSubscription.getOrPut(calleeInitialFactBase) {
                ZeroEdgeSubscriptionStorage()
            }.addZeroToFact(callerFactAp)?.setCalleeBase(calleeInitialFactBase)

        fun addFactToFact(
            calleeInitialBase: AccessPathBase,
            callerInitialAp: AccessPath,
            callerExitAp: AccessTree
        ): FactEdgeSummarySubscription? = initialBaseSubscription.getOrPut(calleeInitialBase) {
            FactEdgeSubscriptionStorage()
        }.add(callerInitialAp, callerExitAp)
            ?.setCalleeBase(calleeInitialBase)

        fun findFactEdge(summaryInitialFactAp: AccessPath): Sequence<FactEdgeSummarySubscription> =
            initialBaseSubscription[summaryInitialFactAp.base]
                ?.findFactEdge()
                ?.map { it.setCalleeBase(summaryInitialFactAp.base) }
                .orEmpty()

        fun findZeroEdge(summaryInitialFactAp: AccessPath): Sequence<ZeroEdgeSummarySubscription> =
            initialBaseTreeSubscription[summaryInitialFactAp.base]
                ?.findZeroEdge(summaryInitialFactAp.access)
                ?.map { it.setCalleeBase(summaryInitialFactAp.base) }
                .orEmpty()
    }

    private class FactEdgeSubscriptionStorage {
        private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactAbstractTreeSubscriptionStorage>()

        fun add(
            callerInitialAp: AccessPath,
            callerExitAp: AccessTree
        ): FactEdgeSummarySubscription? {
            val storage = subscriptions[callerExitAp.base]
                ?: SummaryEdgeFactAbstractTreeSubscriptionStorage().also {
                    subscriptions = subscriptions.put(callerExitAp.base, it)
                }

            return storage.add(callerInitialAp, callerExitAp.access, callerExitAp.exclusions)
                ?.setFactBase(callerExitAp.base)
        }

        fun findFactEdge(): Sequence<FactEdgeSummarySubscription> =
            subscriptions.asSequence().flatMap { (base, storage) ->
                storage.find().map { it.setFactBase(base) }
            }
    }

    private class SummaryEdgeFactAbstractTreeSubscriptionStorage {
        private val storage = Object2ObjectOpenHashMap<AccessPath, AccessNode>()

        fun add(
            callerInitialAp: AccessPath,
            callerExitAp: AccessNode,
            exclusion: ExclusionSet
        ): FactEdgeSummarySubscription? {
            check(exclusion == callerInitialAp.exclusions) { "Edge invariant" }

            val current = storage[callerInitialAp]
            if (current == null) {
                storage[callerInitialAp] = callerExitAp
                return FactEdgeSummarySubscription()
                    .setCallerAp(callerExitAp)
                    .setCallerInitialAp(callerInitialAp)
                    .setCallerExclusion(callerInitialAp.exclusions)
            }

            val (mergedExitAp, delta) = current.mergeAddDelta(callerExitAp)
            if (delta == null) return null

            storage[callerInitialAp] = mergedExitAp

            return FactEdgeSummarySubscription()
                .setCallerAp(delta)
                .setCallerInitialAp(callerInitialAp)
                .setCallerExclusion(callerInitialAp.exclusions)
        }

        // todo: filter
        fun find(): Sequence<FactEdgeSummarySubscription> = storage.asSequence()
            .map { (callerInitialAp, callerExitAp) ->
                FactEdgeSummarySubscription()
                    .setCallerAp(callerExitAp)
                    .setCallerInitialAp(callerInitialAp)
                    .setCallerExclusion(callerInitialAp.exclusions)
            }
    }

    private class ZeroEdgeSubscriptionStorage {
        private var subscriptions = persistentHashMapOf<AccessPathBase, SummaryEdgeFactTreeSubscriptionStorage>()

        fun addZeroToFact(callerFactAp: AccessTree): ZeroEdgeSummarySubscription? {
            val storage = subscriptions[callerFactAp.base]
                ?: SummaryEdgeFactTreeSubscriptionStorage().also {
                    subscriptions = subscriptions.put(callerFactAp.base, it)
                }

            return storage.add(callerFactAp.access)?.setFactBase(callerFactAp.base)
        }

        fun findZeroEdge(
            summaryInitialFact: AccessPath.AccessNode?
        ): Sequence<ZeroEdgeSummarySubscription> =
            subscriptions.asSequence().mapNotNull { (base, storage) ->
                storage.findForSummaryFact(summaryInitialFact)?.setFactBase(base)
            }
    }

    class SummaryEdgeFactTreeSubscriptionStorage {
        private var callerPathEdgeFactAp: AccessNode? = null

        fun findForSummaryFact(summaryFactAp: AccessPath.AccessNode?): ZeroEdgeSummarySubscription? =
            callerPathEdgeFactAp?.filterStartsWith(summaryFactAp)?.let {
                ZeroEdgeSummarySubscription().setFactAccessPath(it)
            }

        fun add(otherCallerPathEdgeFactAp: AccessNode): ZeroEdgeSummarySubscription? {
            if (callerPathEdgeFactAp == null) {
                callerPathEdgeFactAp = otherCallerPathEdgeFactAp
                return ZeroEdgeSummarySubscription().setFactAccessPath(otherCallerPathEdgeFactAp)
            }

            val (mergedAccess, mergeAccessDelta) = callerPathEdgeFactAp!!.mergeAddDelta(otherCallerPathEdgeFactAp)
            if (mergeAccessDelta == null) return null

            callerPathEdgeFactAp = mergedAccess

            return ZeroEdgeSummarySubscription().setFactAccessPath(mergeAccessDelta)
        }
    }

    data class FactEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerInitialStatement: JIRInst? = null,
        private var callerInitialFactMark: TaintMark? = null,
        private var callerInitialFactAp: AccessPath? = null,
        private var callerStatement: JIRInst? = null,
        private var callerFactMark: TaintMark? = null,
        private var callerFactApBase: AccessPathBase? = null,
        private var callerFactApAccess: AccessNode? = null,
        private var callerFactApExclusion: ExclusionSet? = null,
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.FactToFact
            get() = Edge.FactToFact(
                callerInitialStatement!!,
                Fact.TaintedPath(callerInitialFactMark!!, callerInitialFactAp!!),
                callerStatement!!,
                Fact.TaintedTree(
                    callerFactMark!!,
                    AccessTree(
                        callerFactApBase!!,
                        callerFactApAccess!!,
                        callerFactApExclusion!!
                    )
                )
            )

        fun setCallerAp(callerApAccess: AccessNode) = this.also {
            callerFactApAccess = callerApAccess
        }

        fun setCalleeBase(base: AccessPathBase) = this.also {
            calleeInitialFactApBase = base
        }

        fun setCallerInitialAp(ap: AccessPath) = this.also {
            callerInitialFactAp = ap
        }

        fun setCallerExclusion(exclusion: ExclusionSet) = this.also {
            callerFactApExclusion = exclusion
        }

        fun setFactBase(base: AccessPathBase) = this.also {
            callerFactApBase = base
        }

        fun setMarks(factMark: TaintMark, initialFactMark: TaintMark) = this.also {
            callerFactMark = factMark
            callerInitialFactMark = initialFactMark
        }

        fun setStatements(callerInitialStmt: JIRInst, callerExitStmt: JIRInst) = this.also {
            callerInitialStatement = callerInitialStmt
            callerStatement = callerExitStmt
        }
    }

    data class ZeroEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerPathEdgeInitialStatement: JIRInst? = null,
        private var callerPathEdgeExitStatement: JIRInst? = null,
        private var callerPathEdgeFactMark: TaintMark? = null,
        private var callerPathEdgeFactApBase: AccessPathBase? = null,
        private var callerPathEdgeFactApAccess: AccessNode? = null
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.ZeroToFact
            get() = Edge.ZeroToFact(
                callerPathEdgeInitialStatement!!,
                callerPathEdgeExitStatement!!,
                Fact.TaintedTree(
                    callerPathEdgeFactMark!!,
                    AccessTree(
                        callerPathEdgeFactApBase!!,
                        callerPathEdgeFactApAccess!!,
                        ExclusionSet.Universe
                    )
                )
            )

        fun setFactAccessPath(factAp: AccessNode) = this.also {
            callerPathEdgeFactApAccess = factAp
        }

        fun setMarks(mark: TaintMark) = this.also {
            callerPathEdgeFactMark = mark
        }

        fun setStatements(callerInitialStmt: JIRInst, callerExitStmt: JIRInst) = this.also {
            callerPathEdgeInitialStatement = callerInitialStmt
            callerPathEdgeExitStatement = callerExitStmt
        }

        fun setCalleeBase(base: AccessPathBase) = this.also {
            calleeInitialFactApBase = base
        }

        fun setFactBase(base: AccessPathBase) = this.also {
            callerPathEdgeFactApBase = base
        }
    }

    interface SummaryEvent {
        fun processMethodSummary()
    }

    private inner class NewSummaryEdgeEvent(private val summaryEdges: List<Edge>) : SummaryEvent {
        override fun processMethodSummary() {
            val sameInitialStatementEdges = summaryEdges.groupBy { it.initialStatement }
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

        fun processMethodFactSummary(subscriptionStorage: MethodSummarySubscription, summaryEdges: List<Edge.FactToFact>) {
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
        private val initialStatement: JIRInst,
        private val sinkRequirement: Fact.TaintedPath
    ) : SummaryEvent {
        override fun processMethodSummary() {
            val subscriptions = methodSummarySubscriptions[initialStatement] ?: return

            subscriptions.findFactEdgeSub(sinkRequirement).forEach { (ep, subscriptions) ->
                val analyzer = processingCtx.getMethodAnalyzer(ep)
                for (subscription in subscriptions) {
                    analyzer.handleMethodSinkRequirement(
                        subscription.callerPathEdge, subscription.calleeInitialFactBase,
                        sinkRequirement, initialStatement
                    )
                }
            }
        }
    }

    interface SummaryEdgeProcessingCtx {
        fun addSummaryEdgeEvent(event: SummaryEvent)
        fun getMethodAnalyzer(methodEntryPoint: JIRInst): MethodAnalyzer
    }

    private val methodSummaryHandler = MethodSummaryEdgeHandler()

    private inner class MethodSummaryEdgeHandler : SummaryEdgeStorageWithSubscribers.Subscriber {
        override fun newSummaryEdges(edges: List<Edge>) {
            processingCtx.addSummaryEdgeEvent(NewSummaryEdgeEvent(edges))
        }

        override fun newSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath) {
            processingCtx.addSummaryEdgeEvent(NewSinkRequirementEvent(initialStatement, requirement))
        }
    }
}

class SummaryEdgeStorageWithSubscribers(private val methodEntryPoint: JIRInst) {
    interface Subscriber {
        fun newSummaryEdges(edges: List<Edge>)
        fun newSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath)
    }

    private val subscribers = ConcurrentLinkedQueue<Subscriber>()

    private val zeroToZeroSummaryEdges = ArrayList<JIRInst>()
    private val zeroToFactSummaryEdges = ConcurrentHashMap<TaintMark, MethodZeroToFactSummariesStorage>()
    private val taintedFactSameMarkSummaryEdges = ConcurrentHashMap<TaintMark, MethodFactToFactSummaries>()
    private val taintedFactDifferentMarkSummaryEdges =
        ConcurrentHashMap<TaintMark, ConcurrentHashMap<TaintMark, MethodFactToFactSummaries>>()

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

    private val taintedSinkRequirement = ConcurrentHashMap<TaintMark, MutableMap<AccessPathBase, TaintSinkRequirementStorage>>()

    fun addSinkRequirement(requirement: Fact.TaintedPath) {
        val markRequirements = taintedSinkRequirement.computeIfAbsent(requirement.mark) {
            ConcurrentHashMap()
        }

        val baseRequirements = markRequirements.computeIfAbsent(requirement.ap.base) {
            TaintSinkRequirementStorage()
        }

        val addedRequirement = baseRequirements.mergeAdd(requirement) ?: return

        for (subscriber in subscribers) {
            subscriber.newSinkRequirement(methodEntryPoint, addedRequirement)
        }
    }

    private class TaintSinkRequirementStorage : AccessBasedStorage<TaintSinkRequirementStorage>() {
        private var requirement: Fact.TaintedPath? = null

        override fun createStorage() = TaintSinkRequirementStorage()

        fun mergeAdd(requirement: Fact.TaintedPath): Fact.TaintedPath? =
            getOrCreateNode(requirement.ap.access).mergeAddCurrent(requirement)

        fun findRequirements(access: AccessNode): Sequence<Fact.TaintedPath> =
            filterContains(access).mapNotNull { it.requirement }

        private fun mergeAddCurrent(requirement: Fact.TaintedPath): Fact.TaintedPath? {
            val current = this.requirement
            if (current == null) {
                this.requirement = requirement
                return requirement
            }

            val currentExclusion = current.ap.exclusions
            val mergedExclusion = currentExclusion.union(requirement.ap.exclusions)

            if (mergedExclusion === currentExclusion) return null

            val mergedFact = with(requirement.ap) {
                val mergedAp = AccessPath(base, access, mergedExclusion)
                requirement.changeAP(mergedAp)
            }

            this.requirement = mergedFact
            return mergedFact
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
                MethodZeroToFactSummariesStorage(methodEntryPoint)
            }

            synchronized(summariesStorage) {
                val addedEdgeBuilders = mutableListOf<ZeroToFactEdgeBuilder>()
                summariesStorage.add(sameMarkEdges, addedEdgeBuilders)
                addedEdgeBuilders.mapTo(added) {
                    it.setInitialStatement(methodEntryPoint).setMark(mark).build()
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
                    MethodFactToFactSummaries(methodEntryPoint)
                }
            } else {
                taintedFactDifferentMarkSummaryEdges.computeIfAbsent(initialMark) {
                    ConcurrentHashMap()
                }.computeIfAbsent(resultMark) {
                    MethodFactToFactSummaries(methodEntryPoint)
                }
            }

            synchronized(summariesStorage) {
                val addedEdgeBuilders = mutableListOf<FactToFactEdgeBuilder>()
                summariesStorage.add(sameMarkEdges, addedEdgeBuilders)
                addedEdgeBuilders.mapTo(added) {
                    it.setInitialStatement(methodEntryPoint).setMarks(initialMark, resultMark).build()
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
            storage.allEdges().map { it.setInitialStatement(methodEntryPoint).setMark(mark).build() }
        }

    fun factEdgesIterator(initialFact: Fact.TaintedTree): Iterator<Edge.FactToFact> {
        val sameFactEdges = taintedFactSameMarkSummaryEdges[initialFact.mark]
            ?.filterEdgesAndSetMarks(initialFact.mark, initialFact.mark, initialFact.ap.base, initialFact.ap.access)
            .orEmpty()

        val differentFactEdges = taintedFactDifferentMarkSummaryEdges[initialFact.mark]
            ?.asSequence()
            ?.flatMap { (exitMark, storage) ->
                storage.filterEdgesAndSetMarks(initialFact.mark, exitMark, initialFact.ap.base, initialFact.ap.access)
            }
            .orEmpty()

        return (sameFactEdges + differentFactEdges)
            .map { it.setInitialStatement(methodEntryPoint).build() }
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
            .map { it.setInitialStatement(methodEntryPoint).build() }
    }

    private fun MethodFactToFactSummaries.filterEdgesAndSetMarks(
        initialMark: TaintMark,
        exitMark: TaintMark,
        filterBase: AccessPathBase,
        filterAccess: AccessNode
    ): Sequence<FactToFactEdgeBuilder> = filterEdges(filterBase, filterAccess)
        .map { it.setMarks(initialMark, exitMark) }

    private fun MethodFactToFactSummaries.takeAllEdgesAndSetMarks(
        initialMark: TaintMark,
        exitMark: TaintMark,
    ): Sequence<FactToFactEdgeBuilder> = allEdges()
        .map { it.setMarks(initialMark, exitMark) }

    fun sinkRequirementIterator(initialFact: Fact.TaintedTree): Iterator<Fact.TaintedPath> =
        taintedSinkRequirement[initialFact.mark]
            ?.get(initialFact.ap.base)
            ?.findRequirements(initialFact.ap.access)
            .orEmpty()
            .iterator()

    fun collectStats(stats: MethodStats) {
        val sourceSummaries = allZeroToFactSummaries().sumOf { it.fact.ap.access.size }
        val passSummaries = allFactToFactSummaries().sumOf { it.fact.ap.access.size }

        stats.stats(methodEntryPoint.location.method).sourceSummaries += sourceSummaries
        stats.stats(methodEntryPoint.location.method).passSummaries += passSummaries
    }

    private class MethodZeroToFactSummaries(methodEntryPoint: JIRInst) :
        SummaryFactStorage<MethodZeroToFactSummaryEdgeStorage>(methodEntryPoint) {
        override fun createStorage() = MethodZeroToFactSummaryEdgeStorage()

        fun add(edges: List<Edge.ZeroToFact>, added: MutableList<ZeroToFactEdgeBuilder>) {
            val sameExitBaseEdges = edges.groupBy { it.fact.ap.base }
            for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {
                val storage = getOrCreate(exitBase)

                val addedEdge = sameBaseEdges.fold(null as ZeroToFactEdgeBuilder?) { addedEdge, edge ->
                    storage.add(edge.fact.ap.access) ?: addedEdge
                }

                if (addedEdge != null) {
                    added.add(addedEdge.setExitFactBase(exitBase))
                }
            }
        }

        fun edgeSequence() = mapValues { base, storage ->
            storage.summaryEdge()?.setExitFactBase(base)?.let { sequenceOf(it) }.orEmpty()
        }.flatten()
    }

    private class MethodZeroToFactSummaryEdgeStorage {
        private var summaryEdgeAccess: AccessNode? = null

        fun add(edgeAccess: AccessNode): ZeroToFactEdgeBuilder? {
            val summaryAccess = summaryEdgeAccess
            if (summaryAccess == null) {
                summaryEdgeAccess = edgeAccess
                return ZeroToFactEdgeBuilder().setExitAccess(edgeAccess)
            }

            val mergedAccess = summaryAccess.mergeAdd(edgeAccess)
            if (summaryAccess === mergedAccess) return null

            summaryEdgeAccess = mergedAccess
            return ZeroToFactEdgeBuilder().setExitAccess(mergedAccess)
        }

        fun summaryEdge(): ZeroToFactEdgeBuilder? = summaryEdgeAccess?.let {
            ZeroToFactEdgeBuilder().setExitAccess(it)
        }
    }

    private class MethodFactToFactSummaries(
        private val methodEntryPoint: JIRInst
    ) : SummaryFactStorage<MethodTaintedSummariesStorage>(methodEntryPoint) {
        override fun createStorage() = MethodTaintedSummariesStorage(methodEntryPoint)

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
            val sameInitialBaseEdges = edges.groupBy { it.initialFact.ap.base }
            for ((initialBase, sameBaseEdges) in sameInitialBaseEdges) {
                val baseAdded = mutableListOf<FactToFactEdgeBuilder>()
                getOrCreate(initialBase).add(sameBaseEdges, baseAdded)
                baseAdded.mapTo(added) { it.setInitialFactBase(initialBase) }
            }
        }

        fun filterEdges(base: AccessPathBase, containsPattern: AccessNode): Sequence<FactToFactEdgeBuilder> =
            find(base)
                ?.filterEdges(containsPattern)
                ?.map { it.setInitialFactBase(base) }
                .orEmpty()

        fun allEdges(): Sequence<FactToFactEdgeBuilder> = mapValues { base, storage ->
            storage.allEdges().map { it.setInitialFactBase(base) }
        }.flatten()
    }

    private class MethodTaintedSummariesGroupedByFact(methodEntryPoint: JIRInst) :
        SummaryFactStorage<MethodTaintedSummariesGroupedByFactStorage>(methodEntryPoint) {
        override fun createStorage() = MethodTaintedSummariesGroupedByFactStorage()

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
            val sameExitBaseEdges = edges.groupBy { it.fact.ap.base }
            for ((exitBase, sameBaseEdges) in sameExitBaseEdges) {

                val baseAdded = mutableListOf<FactToFactEdgeBuilder>()
                getOrCreate(exitBase).add(sameBaseEdges, baseAdded)
                baseAdded.mapTo(added) { it.setExitFactBase(exitBase) }
            }
        }

        fun filterEdges(containsPattern: AccessNode): Sequence<FactToFactEdgeBuilder> =
            mapValues { base, storage ->
                storage.findSummaries(containsPattern).map { it.setExitFactBase(base) }
            }.flatten()

        fun allEdges(): Sequence<FactToFactEdgeBuilder> =
            mapValues { base, storage ->
                storage.allSummaries().map { it.setExitFactBase(base) }
            }.flatten()
    }

    private class MethodZeroToFactSummariesStorage(methodEntryPoint: JIRInst) :
        MethodSummaryEdgesForExitPoint<Edge.ZeroToFact, ZeroToFactEdgeBuilder, MethodZeroToFactSummaries>(methodEntryPoint) {

        override fun createStorage(): MethodZeroToFactSummaries = MethodZeroToFactSummaries(methodEntryPoint)

        override fun storageAdd(
            storage: MethodZeroToFactSummaries,
            edges: List<Edge.ZeroToFact>,
            added: MutableList<ZeroToFactEdgeBuilder>
        ) = storage.add(edges, added)

        override fun storageAllEdges(storage: MethodZeroToFactSummaries): Sequence<ZeroToFactEdgeBuilder> =
            storage.edgeSequence()

        override fun storageFilterEdges(
            storage: MethodZeroToFactSummaries,
            containsPattern: AccessNode
        ): Sequence<ZeroToFactEdgeBuilder> {
            error("Can't filter edges")
        }
    }

    private class MethodTaintedSummariesStorage(methodEntryPoint: JIRInst) :
        MethodSummaryEdgesForExitPoint<Edge.FactToFact, FactToFactEdgeBuilder, MethodTaintedSummariesGroupedByFact>(methodEntryPoint) {

        override fun createStorage(): MethodTaintedSummariesGroupedByFact =
            MethodTaintedSummariesGroupedByFact(methodEntryPoint)

        override fun storageAdd(
            storage: MethodTaintedSummariesGroupedByFact,
            edges: List<Edge.FactToFact>,
            added: MutableList<FactToFactEdgeBuilder>
        ) = storage.add(edges, added)

        override fun storageAllEdges(storage: MethodTaintedSummariesGroupedByFact): Sequence<FactToFactEdgeBuilder> =
            storage.allEdges()

        override fun storageFilterEdges(
            storage: MethodTaintedSummariesGroupedByFact,
            containsPattern: AccessNode
        ): Sequence<FactToFactEdgeBuilder> = storage.filterEdges(containsPattern)
    }

    private class MethodTaintedSummariesInitialApStorage : AccessBasedStorage<MethodTaintedSummariesInitialApStorage>() {
        private var current: MethodTaintedSummariesMergingStorage? = null

        override fun createStorage() = MethodTaintedSummariesInitialApStorage()

        fun getOrCreate(initialAccess: AccessPath.AccessNode?): MethodTaintedSummariesMergingStorage =
            getOrCreateNode(initialAccess).getOrCreateCurrent(initialAccess)

        fun findSummaries(containsPattern: AccessNode): Sequence<FactToFactEdgeBuilder> =
            filterContains(containsPattern).flatMap { it.current?.summaries().orEmpty() }

        fun allSummaries(): Sequence<FactToFactEdgeBuilder> =
            allNodes().flatMap { it.current?.summaries().orEmpty() }

        private fun getOrCreateCurrent(access: AccessPath.AccessNode?) =
            current ?: MethodTaintedSummariesMergingStorage(access).also { current = it }
    }

    private class MethodTaintedSummariesGroupedByFactStorage {
        private val nonUniverseAccessPath = MethodTaintedSummariesInitialApStorage()

        fun add(edges: List<Edge.FactToFact>, added: MutableList<FactToFactEdgeBuilder>) {
            addNonUniverseEdges(edges, added)
        }

        private fun addNonUniverseEdges(
            edges: List<Edge.FactToFact>,
            added: MutableList<FactToFactEdgeBuilder>
        ) {
            val modifiedStorages = mutableListOf<MethodTaintedSummariesMergingStorage>()

            for (edge in edges) {
                // edges here are already separated by marks, statements and bases
                val initialAccess = edge.initialFact.ap.access
                val exitAccess = edge.fact.ap.access

                val exclusion = edge.initialFact.ap.exclusions
                check(exclusion == edge.fact.ap.exclusions) { "Edge invariant" }

                addNonUniverseEdge(initialAccess, exitAccess, exclusion, modifiedStorages)
            }

            modifiedStorages.flatMapTo(added) { it.getAndResetDelta() }
        }

        private fun addNonUniverseEdge(
            initialAccess: AccessPath.AccessNode?,
            exitAccess: AccessNode,
            exclusion: ExclusionSet,
            modifiedStorages: MutableList<MethodTaintedSummariesMergingStorage>
        ) {
            val storage = nonUniverseAccessPath.getOrCreate(initialAccess)
            val storageModified = storage.add(exitAccess, exclusion)

            if (storageModified) {
                modifiedStorages.add(storage)
            }
        }

        fun findSummaries(containsPattern: AccessNode): Sequence<FactToFactEdgeBuilder> =
            nonUniverseAccessPath.findSummaries(containsPattern)

        fun allSummaries(): Sequence<FactToFactEdgeBuilder> =
            nonUniverseAccessPath.allSummaries()
    }

    private class MethodTaintedSummariesMergingStorage(val initialAccess: AccessPath.AccessNode?) {
        private var emptyExclusionSummaries: EdgeMergeUtils.Storage? = null
        private var concreteExclusionSummaries = persistentHashMapOf<ExclusionSet.Concrete, EdgeMergeUtils.Storage>()

        fun add(exitAccess: AccessNode, addedEx: ExclusionSet): Boolean {
            var modified = false
            val queue = mutableListOf<Pair<AccessNode, ExclusionSet>>()
            queue.add(exitAccess to addedEx)

            while (queue.isNotEmpty()) {
                val (currentAccess, currentEx) = queue.removeLast()
                modified = modified or EdgeMergeUtils.mergeAdd(
                    access = currentAccess,
                    exclusion = currentEx,
                    allStorages = { summaryStorages().iterator() },
                    saveStorage = { ex, storage -> saveStorage(ex, storage) },
                    removeStorage = { ex -> removeStorage(ex) },
                    enqueue = { ex, access -> queue.add(access to ex) }
                )
            }

            return modified
        }

        fun getAndResetDelta(): Sequence<FactToFactEdgeBuilder> =
            summaryStorages().mapNotNull { storage ->
                storage.getAndResetDelta()?.let { delta ->
                    FactToFactEdgeBuilder()
                        .setInitialAp(initialAccess)
                        .setExitAp(delta)
                        .setExclusion(storage.exclusion)
                }
            }

        fun summaries(): Sequence<FactToFactEdgeBuilder> =
            summaryStorages().map { storage ->
                FactToFactEdgeBuilder()
                    .setInitialAp(initialAccess)
                    .setExitAp(storage.edges)
                    .setExclusion(storage.exclusion)
            }

        private fun saveStorage(exclusion: ExclusionSet, storage: EdgeMergeUtils.Storage) {
            when (exclusion) {
                ExclusionSet.Empty -> emptyExclusionSummaries = storage
                is ExclusionSet.Concrete -> concreteExclusionSummaries = concreteExclusionSummaries.put(exclusion, storage)
                ExclusionSet.Universe -> error("")
            }
        }

        private fun removeStorage(exclusion: ExclusionSet) {
            when (exclusion) {
                ExclusionSet.Empty -> emptyExclusionSummaries = null
                is ExclusionSet.Concrete -> concreteExclusionSummaries = concreteExclusionSummaries.remove(exclusion)
                ExclusionSet.Universe -> error("")
            }
        }

        private fun summaryStorages(): Sequence<EdgeMergeUtils.Storage> =
            emptyExclusionSummaries?.let { sequenceOf(it) }.orEmpty() + concreteExclusionSummaries.values.asSequence()
    }

    sealed interface EdgeBuilder<B : EdgeBuilder<B>> {
        fun setInitialStatement(statement: JIRInst): B
        fun setExitStatement(statement: JIRInst): B
    }

    private data class ZeroToFactEdgeBuilder(
        private var initialStatement: JIRInst? = null,
        private var exitStatement: JIRInst? = null,
        private var exitFactAccess: AccessNode? = null,
        private var exitFactBase: AccessPathBase? = null,
        private var mark: TaintMark? = null
    ) : EdgeBuilder<ZeroToFactEdgeBuilder> {
        fun build(): Edge.ZeroToFact  = Edge.ZeroToFact(
            initialStatement!!, exitStatement!!,
            Fact.TaintedTree(
                mark!!,
                AccessTree(
                    exitFactBase!!,
                    exitFactAccess!!,
                    ExclusionSet.Universe
                )
            )
        )

        fun setMark(mark: TaintMark) = this.also {
            this.mark = mark
        }

        override fun setInitialStatement(statement: JIRInst) = this.also {
            initialStatement = statement
        }

        override fun setExitStatement(statement: JIRInst) = this.also {
            exitStatement = statement
        }

        fun setExitAccess(access: AccessNode) = this.also {
            exitFactAccess = access
        }

        fun setExitFactBase(base: AccessPathBase) = this.also {
            exitFactBase = base
        }
    }

    private data class FactToFactEdgeBuilder(
        private var initialStatement: JIRInst? = null,
        private var exitStatement: JIRInst? = null,
        private var initialMark: TaintMark? = null,
        private var exitMark: TaintMark? = null,
        private var initialBase: AccessPathBase? = null,
        private var exitBase: AccessPathBase? = null,
        private var exclusion: ExclusionSet? = null,
        private var initialAp: AccessPath.AccessNode? = null,
        var exitAp: AccessNode? = null,
    ) : EdgeBuilder<FactToFactEdgeBuilder> {
        fun build(): Edge.FactToFact = Edge.FactToFact(
            initialStatement!!,
            Fact.TaintedPath(initialMark!!, AccessPath(initialBase!!, initialAp, exclusion!!)),
            exitStatement!!,
            Fact.TaintedTree(exitMark!!, AccessTree(exitBase!!, exitAp!!, exclusion!!))
        )

        override fun setInitialStatement(statement: JIRInst) = this.also {
            initialStatement = statement
        }

        override fun setExitStatement(statement: JIRInst) = this.also {
            exitStatement = statement
        }

        fun setMarks(initialMark: TaintMark, exitMark: TaintMark) = this.also {
            this.initialMark = initialMark
            this.exitMark = exitMark
        }

        fun setInitialFactBase(base: AccessPathBase) = this.also {
            initialBase = base
        }

        fun setExitFactBase(base: AccessPathBase) = this.also {
            exitBase = base
        }

        fun setExclusion(exclusion: ExclusionSet) = this.also {
            this.exclusion = exclusion
        }

        fun setInitialAp(ap: AccessPath.AccessNode?) = this.also {
            initialAp = ap
        }

        fun setExitAp(ap: AccessNode) = this.also {
            exitAp = ap
        }
    }

    private abstract class SummaryFactStorage<Storage : Any>(methodEntryPoint: JIRInst) :
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

    private abstract class MethodSummaryEdgesForExitPoint<E : Edge, B : EdgeBuilder<B>, Storage>(val methodEntryPoint: JIRInst) {
        abstract fun createStorage(): Storage
        abstract fun storageAdd(storage: Storage, edges: List<E>, added: MutableList<B>)

        abstract fun storageAllEdges(storage: Storage): Sequence<B>

        abstract fun storageFilterEdges(storage: Storage, containsPattern: AccessNode): Sequence<B>

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

        fun filterEdges(containsPattern: AccessNode): Sequence<B> = processEdgeSequence {
            storageFilterEdges(it, containsPattern)
        }

        private inline fun processEdgeSequence(storageEdges: (Storage) -> Sequence<B>): Sequence<B> =
            exitPointsStorage.concurrentReadSafeMapIndexed { idx, storage ->
                val exitPoint = exitPoints[idx]
                storageEdges(storage).map { it.setExitStatement(exitPoint) }
            }.asSequence().flatten()
    }
}

private abstract class AccessBasedStorage<S : AccessBasedStorage<S>> {
    private var children = persistentHashMapOf<Accessor, S>()

    abstract fun createStorage(): S

    fun getOrCreateNode(access: AccessPath.AccessNode?): S {
        if (access == null) {
            @Suppress("UNCHECKED_CAST")
            return this as S
        }

        var storage = this
        for (accessor in access) {
            storage = storage.getOrCreateChild(accessor)
        }

        @Suppress("UNCHECKED_CAST")
        return storage as S
    }

    fun filterContains(pattern: AccessNode): Sequence<S> {
        val nodes = mutableListOf<S>()
        collectNodesContains(pattern, nodes)
        return nodes.asSequence()
    }

    private fun collectNodesContains(pattern: AccessNode, nodes: MutableList<S>) {
        @Suppress("UNCHECKED_CAST")
        nodes.add(this as S)

        if (pattern.isFinal) {
            children[FinalAccessor]?.let { nodes.add(it) }
        }

        pattern.elementAccess?.let { elementAccessPattern ->
            children[ElementAccessor]?.collectNodesContains(elementAccessPattern, nodes)
        }

        pattern.forEachField { field, fieldPattern ->
            children[field]?.collectNodesContains(fieldPattern, nodes)
        }
    }

    fun allNodes(): Sequence<S> {
        val storages = mutableListOf<S>()

        val unprocessedStorages = mutableListOf(this)
        while (unprocessedStorages.isNotEmpty()) {
            val storage = unprocessedStorages.removeLast()
            @Suppress("UNCHECKED_CAST")
            storages.add(storage as S)
            unprocessedStorages.addAll(storage.children.values)
        }

        return storages.asSequence()
    }

    private fun getOrCreateChild(accessor: Accessor): S =
        children.getOrElse(accessor) {
            createStorage().also { children = children.put(accessor, it) }
        }
}
