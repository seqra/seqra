package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.FactToFactSub
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.NDFactToFactSub
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.ZeroToFactSub
import org.opentaint.dataflow.ap.ifds.SideEffectSummary.FactSideEffectSummary
import org.opentaint.dataflow.ap.ifds.SideEffectSummary.ZeroSideEffectSummary
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.MethodAccessPathSubscription
import org.opentaint.dataflow.ap.ifds.serialization.MethodEntryPointSummaries
import org.opentaint.dataflow.util.collectToListWithPostProcess
import org.opentaint.dataflow.util.concurrentReadSafeForEach
import org.opentaint.dataflow.util.concurrentReadSafeMapIndexed
import org.opentaint.ir.api.common.cfg.CommonInst
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

        val sideEffectSummaries = manager.findZeroSideEffectSummaries(methodEntryPoint)
        if (sideEffectSummaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToZeroMethodSideEffectSummary(
                callerPathEdge,
                sideEffectSummaries
            )
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

        val sub = ZeroToFactSub(addedSubscription.callerPathEdge, addedSubscription.calleeInitialFactBase)

        if (summaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        val ndSummaries = manager.findFactNDSummaryEdges(methodEntryPoint, calleeInitialFactAp)
        if (ndSummaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToFactMethodNDSummaryEdge(listOf(sub), ndSummaries)
        }

        val sideEffectSummaries = manager.findFactSideEffectSummaries(methodEntryPoint, calleeInitialFactAp)
        if (sideEffectSummaries.isNotEmpty()) {
            callerAnalyzer.handleZeroToFactMethodSideEffectSummary(
                listOf(sub),
                sideEffectSummaries
            )
        }

        return true
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: FactToFact
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addFactToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)

        val calleeInitialFactAp = addedSubscription.callerPathEdge.factAp.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFactAp)

        val sub = FactToFactSub(addedSubscription.callerPathEdge, addedSubscription.calleeInitialFactBase)

        if (summaries.isNotEmpty()) {
            callerAnalyzer.handleFactToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        val ndSummaries = manager.findFactNDSummaryEdges(methodEntryPoint, calleeInitialFactAp)
        if (ndSummaries.isNotEmpty()) {
            callerAnalyzer.handleFactToFactMethodNDSummaryEdge(listOf(sub), ndSummaries)
        }

        val sideEffectRequirements = manager.findSideEffectRequirements(methodEntryPoint, calleeInitialFactAp)
        if (sideEffectRequirements.isNotEmpty()) {
            callerAnalyzer.handleMethodSideEffectRequirement(
                addedSubscription.callerPathEdge,
                addedSubscription.calleeInitialFactBase,
                sideEffectRequirements
            )
        }

        val sideEffectSummaries = manager.findFactSideEffectSummaries(methodEntryPoint, calleeInitialFactAp)
        if (sideEffectSummaries.isNotEmpty()) {
            callerAnalyzer.handleFactToFactMethodSideEffectSummary(
                listOf(sub),
                sideEffectSummaries
            )
        }

        return true
    }

    fun subscribeOnMethodSummary(
        methodEntryPoint: MethodEntryPoint,
        calleeInitialFactBase: AccessPathBase,
        callerPathEdge: Edge.NDFactToFact,
    ): Boolean {
        val methodSubscriptions = methodSubscriptions(methodEntryPoint)

        val addedSubscription = methodSubscriptions.addNDFactToFact(calleeInitialFactBase, callerPathEdge) ?: return false
        val callerAnalyzer = processingCtx.getMethodAnalyzer(callerPathEdge.methodEntryPoint)

        val calleeInitialFactAp = addedSubscription.callerPathEdge.factAp.rebase(addedSubscription.calleeInitialFactBase)
        val summaries = manager.findFactSummaryEdges(methodEntryPoint, calleeInitialFactAp)

        val sub = NDFactToFactSub(addedSubscription.callerPathEdge, addedSubscription.calleeInitialFactBase)

        if (summaries.isNotEmpty()) {
            callerAnalyzer.handleNDFactToFactMethodSummaryEdge(listOf(sub), summaries)
        }

        val ndSummaries = manager.findFactNDSummaryEdges(methodEntryPoint, calleeInitialFactAp)
        if (ndSummaries.isNotEmpty()) {
            callerAnalyzer.handleNDFactToFactMethodNDSummaryEdge(listOf(sub), ndSummaries)
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
            callerPathEdge: FactToFact
        ): FactEdgeSummarySubscription? =
            taintedFactSubscriptions
                .addFactToFact(calleeInitialFactBase, callerPathEdge)

        fun addNDFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.NDFactToFact
        ): FactNDEdgeSummarySubscription? =
            taintedFactSubscriptions
                .addNDFactToFact(calleeInitialFactBase, callerPathEdge)

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
            summaryInitialFactAp: InitialFactAp,
            emptyDeltaRequired: Boolean = false,
        ): List<Pair<MethodEntryPoint, List<FactEdgeSummarySubscription>>> {
            val result = mutableListOf<Pair<MethodEntryPoint, List<FactEdgeSummarySubscription>>>()
            taintedFactSubscriptions.collectFactEdge(result, summaryInitialFactAp, emptyDeltaRequired)
            return result
        }

        fun findZeroEdgeSub(
            summaryInitialFactAp: InitialFactAp
        ): List<Pair<MethodEntryPoint, List<ZeroEdgeSummarySubscription>>> {
            val result = mutableListOf<Pair<MethodEntryPoint, List<ZeroEdgeSummarySubscription>>>()
            taintedFactSubscriptions.collectZeroEdge(result, summaryInitialFactAp)
            return result
        }

        fun findFactNDEdgeSub(
            summaryInitialFactAp: InitialFactAp,
            emptyDeltaRequired: Boolean = false,
        ): List<Pair<MethodEntryPoint, List<FactNDEdgeSummarySubscription>>> {
            val result = mutableListOf<Pair<MethodEntryPoint, List<FactNDEdgeSummarySubscription>>>()
            taintedFactSubscriptions.collectFactNDEdge(result, summaryInitialFactAp, emptyDeltaRequired)
            return result
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
            }.addZeroToFact(callerEntryPoint.statement, calleeInitialFactBase, callerFactAp)
                ?.setStatements(callerEntryPoint, callerExitStatement)

        fun addFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: FactToFact
        ): FactEdgeSummarySubscription? =
            subscriptions.getOrPut(callerPathEdge.methodEntryPoint) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerPathEdge.statement) {
                apManager.accessPathSubscription()
            }.addFactToFact(
                callerPathEdge.methodEntryPoint.statement,
                calleeInitialFactBase, callerPathEdge.initialFactAp, callerPathEdge.factAp
            )?.setStatements(callerPathEdge.methodEntryPoint, callerPathEdge.statement)

        fun addNDFactToFact(
            calleeInitialFactBase: AccessPathBase,
            callerPathEdge: Edge.NDFactToFact,
        ): FactNDEdgeSummarySubscription? =
            subscriptions.getOrPut(callerPathEdge.methodEntryPoint) {
                Object2ObjectOpenHashMap()
            }.getOrPut(callerPathEdge.statement) {
                apManager.accessPathSubscription()
            }.addNDFactToFact(
                callerPathEdge.methodEntryPoint.statement,
                calleeInitialFactBase, callerPathEdge.initialFacts, callerPathEdge.factAp
            )?.setStatements(callerPathEdge.methodEntryPoint, callerPathEdge.statement)

        fun collectFactEdge(
            collection: MutableList<Pair<MethodEntryPoint, List<FactEdgeSummarySubscription>>>,
            summaryInitialFactAp: InitialFactAp,
            emptyDeltaRequired: Boolean,
        ) {
            for ((initialStmt, storage) in subscriptions) {
                val collectedSubs = mutableListOf<FactEdgeSummarySubscription>()
                for ((exitStmt, subs) in storage) {
                    collectToListWithPostProcess(
                        collectedSubs,
                        { subs.collectFactEdge(it, summaryInitialFactAp, emptyDeltaRequired) },
                        { it.setStatements(initialStmt, exitStmt) }
                    )
                }

                if (collectedSubs.isNotEmpty()) {
                    collection += initialStmt to collectedSubs
                }
            }
        }

        fun collectFactNDEdge(
            collection: MutableList<Pair<MethodEntryPoint, List<FactNDEdgeSummarySubscription>>>,
            summaryInitialFactAp: InitialFactAp,
            emptyDeltaRequired: Boolean,
        ) {
            for ((initialStmt, storage) in subscriptions) {
                val collectedSubs = mutableListOf<FactNDEdgeSummarySubscription>()
                for ((exitStmt, subs) in storage) {
                    collectToListWithPostProcess(
                        collectedSubs,
                        { subs.collectFactNDEdge(it, summaryInitialFactAp, emptyDeltaRequired) },
                        { it.setStatements(initialStmt, exitStmt) }
                    )
                }

                if (collectedSubs.isNotEmpty()) {
                    collection += initialStmt to collectedSubs
                }
            }
        }

        fun collectZeroEdge(
            collection: MutableList<Pair<MethodEntryPoint, List<ZeroEdgeSummarySubscription>>>,
            summaryInitialFactAp: InitialFactAp
        ) {
            for ((initialStmt, storage) in subscriptions) {
                val collectedSubs = mutableListOf<ZeroEdgeSummarySubscription>()
                for ((exitStmt, subs) in storage) {
                    collectToListWithPostProcess(
                        collectedSubs,
                        { subs.collectZeroEdge(it, summaryInitialFactAp) },
                        { it.setStatements(initialStmt, exitStmt) }
                    )
                }
                collection += initialStmt to collectedSubs
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

        val callerPathEdge: FactToFact
            get() = FactToFact(
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

    data class FactNDEdgeSummarySubscription(
        private var calleeInitialFactApBase: AccessPathBase? = null,
        private var callerEntryPoint: MethodEntryPoint? = null,
        private var callerInitialFact: Set<InitialFactAp>? = null,
        private var callerStatement: CommonInst? = null,
        private var callerFactAp: FinalFactAp? = null,
    ) {
        val calleeInitialFactBase: AccessPathBase
            get() = calleeInitialFactApBase!!

        val callerPathEdge: Edge.NDFactToFact
            get() = Edge.NDFactToFact(
                callerEntryPoint!!,
                callerInitialFact!!,
                callerStatement!!,
                callerFactAp!!
            )

        fun setCalleeBase(base: AccessPathBase) = this.also {
            calleeInitialFactApBase = base
        }

        fun setCallerInitial(ap: Set<InitialFactAp>) = this.also {
            callerInitialFact = ap
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
            val zeroEdges = mutableListOf<Edge.ZeroInitialEdge>()
            val factEdges = mutableListOf<FactToFact>()
            val ndFactEdges = mutableListOf<Edge.NDFactToFact>()

            for (edge in summaryEdges) {
                when (edge) {
                    is Edge.ZeroInitialEdge -> zeroEdges.add(edge)
                    is FactToFact -> factEdges.add(edge)
                    is Edge.NDFactToFact -> ndFactEdges.add(edge)
                }
            }

            if (zeroEdges.isNotEmpty()) {
                processMethodZeroSummary(subscriptions, zeroEdges)
            }

            if (factEdges.isNotEmpty()) {
                processMethodFactSummary(subscriptions, factEdges)
            }

            if (ndFactEdges.isNotEmpty()) {
                processMethodFactNDSummary(subscriptions, ndFactEdges)
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
            summaryEdges: List<FactToFact>
        ) {
            val sameInitialFactEdges = summaryEdges.groupBy { it.initialFactAp }
            for ((summaryInitialFact, summaries) in sameInitialFactEdges) {
                applySummaries(
                    subscriptionStorage, summaryInitialFact, summaries,
                    MethodAnalyzer::handleFactToFactMethodSummaryEdge,
                    MethodAnalyzer::handleZeroToFactMethodSummaryEdge,
                    MethodAnalyzer::handleNDFactToFactMethodSummaryEdge
                )
            }
        }

        private inline fun <S : Edge> applySummaries(
            subscriptionStorage: MethodSummarySubscription,
            summaryInitialFact: InitialFactAp,
            summaries: List<S>,
            handleF2F: MethodAnalyzer.(List<FactToFactSub>, List<S>) -> Unit,
            handleZ2F: MethodAnalyzer.(List<ZeroToFactSub>, List<S>) -> Unit,
            handleND2F: MethodAnalyzer.(List<NDFactToFactSub>, List<S>) -> Unit,
        ) {
            subscriptionStorage.findFactEdgeSub(summaryInitialFact).forEach { (ep, subscriptions) ->
                val summarySubs = subscriptions.mapTo(mutableListOf()) {
                    FactToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                }

                if (summarySubs.isEmpty()) return@forEach

                val analyzer = processingCtx.getMethodAnalyzer(ep)
                analyzer.handleF2F(summarySubs, summaries)
            }

            subscriptionStorage.findZeroEdgeSub(summaryInitialFact).forEach { (ep, subscriptions) ->
                val summarySubs = subscriptions.mapTo(mutableListOf()) {
                    ZeroToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                }

                if (summarySubs.isEmpty()) return@forEach

                val analyzer = processingCtx.getMethodAnalyzer(ep)
                analyzer.handleZ2F(summarySubs, summaries)
            }

            subscriptionStorage.findFactNDEdgeSub(summaryInitialFact).forEach { (ep, subscriptions) ->
                val summarySubs = subscriptions.mapTo(mutableListOf()) {
                    NDFactToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                }

                if (summarySubs.isEmpty()) return@forEach

                val analyzer = processingCtx.getMethodAnalyzer(ep)
                analyzer.handleND2F(summarySubs, summaries)
            }
        }

        fun processMethodFactNDSummary(
            subscriptionStorage: MethodSummarySubscription,
            summaryEdges: List<Edge.NDFactToFact>,
        ) {
            val sameInitialFactEdges = hashMapOf<InitialFactAp, MutableList<Edge.NDFactToFact>>()
            summaryEdges.forEach { edge ->
                edge.initialFacts.forEach { f ->
                    sameInitialFactEdges.getOrPut(f, ::mutableListOf).add(edge)
                }
            }

            for ((summaryInitialFact, summaries) in sameInitialFactEdges) {
                applySummaries(
                    subscriptionStorage, summaryInitialFact, summaries,
                    MethodAnalyzer::handleFactToFactMethodNDSummaryEdge,
                    MethodAnalyzer::handleZeroToFactMethodNDSummaryEdge,
                    MethodAnalyzer::handleNDFactToFactMethodNDSummaryEdge,
                )
            }
        }
    }

    private inner class NewSideEffectRequirementEvent(
        private val methodEntryPoint: MethodEntryPoint,
        private val sideEffectRequirements: List<InitialFactAp>
    ) : SummaryEvent {
        override fun processMethodSummary() {
            val methodSubscriptions = methodSummarySubscriptions[methodEntryPoint] ?: return

            sideEffectRequirements.forEach { sideEffectRequirement ->
                methodSubscriptions.findFactEdgeSub(sideEffectRequirement, emptyDeltaRequired = true).forEach { (ep, subscriptions) ->
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

    private inner class NewSideEffectSummaryEvent(
        private val methodEntryPoint: MethodEntryPoint,
        private val sideEffects: List<SideEffectSummary>,
    ) : SummaryEvent {
        override fun processMethodSummary() {
            val methodSubscriptions = methodSummarySubscriptions[methodEntryPoint] ?: return

            val zeroSideEffects = mutableListOf<ZeroSideEffectSummary>()
            val factSideEffects = mutableListOf<FactSideEffectSummary>()

            sideEffects.forEach { sideEffect ->
                when (sideEffect) {
                    is FactSideEffectSummary -> factSideEffects += sideEffect
                    is ZeroSideEffectSummary -> zeroSideEffects += sideEffect
                }
            }

            if (zeroSideEffects.isNotEmpty()) {
                processZeroSideEffectSummaries(methodSubscriptions, zeroSideEffects)
            }

            if (factSideEffects.isNotEmpty()) {
                processFactSideEffectSummaries(methodSubscriptions, factSideEffects)
            }
        }

        private fun processZeroSideEffectSummaries(subscriptions: MethodSummarySubscription, sideEffects: List<ZeroSideEffectSummary>) {
            subscriptions.zeroFactSubscriptions().forEach { (ep, callerPathEdges) ->
                val analyzer = processingCtx.getMethodAnalyzer(ep)
                for (callerPathEdge in callerPathEdges) {
                    analyzer.handleZeroToZeroMethodSideEffectSummary(callerPathEdge, sideEffects)
                }
            }
        }

        fun processFactSideEffectSummaries(
            subscriptionStorage: MethodSummarySubscription,
            sideEffects: List<FactSideEffectSummary>
        ) {
            val sameInitialFactSideEffects = sideEffects.groupBy { it.initialFactAp }
            for ((summaryInitialFact, summaries) in sameInitialFactSideEffects) {
                applyFactSideEffects(subscriptionStorage, summaryInitialFact, summaries)
            }
        }

        private fun applyFactSideEffects(
            subscriptionStorage: MethodSummarySubscription,
            seInitialFact: InitialFactAp,
            sideEffects: List<FactSideEffectSummary>,
        ) {
            subscriptionStorage.findFactEdgeSub(seInitialFact).forEach { (ep, subscriptions) ->
                val summarySubs = subscriptions.mapTo(mutableListOf()) {
                    FactToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                }

                if (summarySubs.isEmpty()) return@forEach

                val analyzer = processingCtx.getMethodAnalyzer(ep)
                analyzer.handleFactToFactMethodSideEffectSummary(summarySubs, sideEffects)
            }

            subscriptionStorage.findZeroEdgeSub(seInitialFact).forEach { (ep, subscriptions) ->
                val summarySubs = subscriptions.mapTo(mutableListOf()) {
                    ZeroToFactSub(it.callerPathEdge, it.calleeInitialFactBase)
                }

                if (summarySubs.isEmpty()) return@forEach

                val analyzer = processingCtx.getMethodAnalyzer(ep)
                analyzer.handleZeroToFactMethodSideEffectSummary(summarySubs, sideEffects)
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

        override fun newSideEffectSummaries(methodEntryPoint: MethodEntryPoint, sideEffects: List<SideEffectSummary>) {
            processingCtx.addSummaryEdgeEvent(NewSideEffectSummaryEvent(methodEntryPoint, sideEffects))
        }
    }
}

class SummaryEdgeStorageWithSubscribers(
    private val apManager: ApManager,
    private val methodEntryPoint: MethodEntryPoint,
) {
    interface Subscriber {
        fun newSummaryEdges(edges: List<Edge>)
        fun newSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>)
        fun newSideEffectSummaries(methodEntryPoint: MethodEntryPoint, sideEffects: List<SideEffectSummary>)
    }

    private val subscribers = ConcurrentLinkedQueue<Subscriber>()

    private val zeroToZeroSummaryEdges = ArrayList<CommonInst>()
    private val zeroToFactSummaryEdges = apManager.methodFinalApSummariesStorage(methodEntryPoint.statement)
    private val taintedFactSummaryEdges = apManager.methodInitialToFinalApSummariesStorage(methodEntryPoint.statement)
    private val ndF2FSummaryEdges = apManager.methodNDInitialToFinalApSummariesStorage(methodEntryPoint.statement)

    private val zeroSideEffectSummariesStorage by lazy {
        ConcurrentHashMap.newKeySet<SideEffectKind>()
    }

    private val factSideEffectSummariesStorage by lazy {
        apManager.factSideEffectSummariesApStorage(methodEntryPoint.statement)
    }

    fun addEdges(edges: List<Edge>) {
        val addedEdges = mutableListOf<Edge>()
        val zeroToZeroEdges = mutableListOf<Edge.ZeroToZero>()
        val zeroToFactEdges = mutableListOf<Edge.ZeroToFact>()
        val factToFactEdges = mutableListOf<FactToFact>()
        val ndFactToFactEdges = mutableListOf<Edge.NDFactToFact>()

        for (edge in edges) {
            when (edge) {
                is Edge.ZeroToZero -> zeroToZeroEdges.add(edge)
                is Edge.ZeroToFact -> zeroToFactEdges.add(edge)
                is FactToFact -> factToFactEdges.add(edge)
                is Edge.NDFactToFact -> ndFactToFactEdges.add(edge)
            }
        }

        addZeroToZeroEdges(zeroToZeroEdges, addedEdges)
        addZeroToFactEdges(zeroToFactEdges, addedEdges)
        addFactToFactEdges(factToFactEdges, addedEdges)
        addNDFactToFactEdges(ndFactToFactEdges, addedEdges)

        for (subscriber in subscribers) {
            subscriber.newSummaryEdges(addedEdges)
        }
    }

    fun getSummaries(): MethodEntryPointSummaries {
        val edges = mutableListOf<Edge>()
        collectAllZeroToZeroSummariesTo(edges)
        collectAllZeroToFactSummariesTo(edges)
        collectAllFactToFactSummariesTo(edges)

        val requirements = mutableListOf<InitialFactAp>()
        sideEffectRequirement.collectAllRequirementsTo(requirements)

        val sideEffects = mutableListOf<SideEffectSummary>()
        collectToListWithPostProcess(
            sideEffects,
            { dst -> zeroSideEffectSummariesStorage.mapTo(dst) { ZeroSideEffectSummary(it) } },
            { it }
        )
        collectToListWithPostProcess(
            sideEffects,
            { factSideEffectSummariesStorage.filterTaintedTo(it, pattern = null) },
            { it }
        )

        return MethodEntryPointSummaries(methodEntryPoint, edges, requirements, sideEffects)
    }

    private val sideEffectRequirement = apManager.sideEffectRequirementApStorage()

    fun sideEffectRequirement(requirements: List<InitialFactAp>) {
        val addedRequirements = sideEffectRequirement.add(requirements)

        for (subscriber in subscribers) {
            subscriber.newSideEffectRequirement(methodEntryPoint, addedRequirements)
        }
    }

    fun addSideEffectSummaries(sideEffects: List<SideEffectSummary>) {
        val zeroSideEffects = mutableListOf<ZeroSideEffectSummary>()
        val taintedSideEffects = mutableListOf<FactSideEffectSummary>()
        val addedZeroSideEffects = mutableListOf<ZeroSideEffectSummary>()
        val addedFactSideEffects = mutableListOf<FactSideEffectSummary>()

        sideEffects.forEach { sideEffect ->
            when (sideEffect) {
                is ZeroSideEffectSummary -> zeroSideEffects += sideEffect
                is FactSideEffectSummary -> taintedSideEffects += sideEffect
            }
        }

        zeroSideEffects.filterTo(addedZeroSideEffects) {
            zeroSideEffectSummariesStorage.add(it.kind)
        }

        synchronized(factSideEffectSummariesStorage) {
            factSideEffectSummariesStorage.add(taintedSideEffects, addedFactSideEffects)
        }

        val addedSideEffects = addedZeroSideEffects + addedFactSideEffects

        for (subscriber in subscribers) {
            subscriber.newSideEffectSummaries(methodEntryPoint, addedSideEffects)
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

    private fun addFactToFactEdges(edges: List<FactToFact>, added: MutableList<Edge>) {
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

    private fun addNDFactToFactEdges(edges: List<Edge.NDFactToFact>, added: MutableList<Edge>) {
        if (edges.isEmpty()) return

        val summariesStorage = ndF2FSummaryEdges

        synchronized(summariesStorage) {
            val addedEdgeBuilders = mutableListOf<NDFactToFactEdgeBuilder>()
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

    private fun collectAllZeroToZeroSummariesTo(dst: MutableList<in Edge.ZeroInitialEdge>) {
        zeroToZeroSummaryEdges.concurrentReadSafeForEach { _, inst ->
            dst.add(Edge.ZeroToZero(methodEntryPoint, inst))
        }
    }

    private fun collectAllZeroToFactSummariesTo(dst: MutableList<in Edge.ZeroInitialEdge>) {
        collectToListWithPostProcess(dst, {
            zeroToFactSummaryEdges.filterEdgesTo(it, finalFactBase = null)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })
    }

    fun factEdges(initialFactAp: FinalFactAp): List<FactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            taintedFactSummaryEdges.filterEdgesTo(it, initialFactAp, finalFactBase = null)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    fun factNDEdges(initialFactAp: FinalFactAp): List<Edge.NDFactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            ndF2FSummaryEdges.filterEdgesTo(it, initialFactAp, finalFactBase = null)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    fun factToFactEdges(
        finalFactBase: AccessPathBase
    ): List<FactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            taintedFactSummaryEdges.filterEdgesTo(it, initialFactPattern = null, finalFactBase)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    fun factNDEdges(finalFactBase: AccessPathBase): List<Edge.NDFactToFact> =
        collectToListWithPostProcess(mutableListOf(), {
            ndF2FSummaryEdges.filterEdgesTo(it, initialFactPattern = null, finalFactBase)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })

    private fun collectAllFactToFactSummariesTo(dst: MutableList<in FactToFact>) {
        collectToListWithPostProcess(dst, {
            taintedFactSummaryEdges.filterEdgesTo(it, initialFactPattern = null, finalFactBase = null)
        }, {
            it.setEntryPoint(methodEntryPoint).build()
        })
    }

    fun sideEffectRequirement(initialFactAp: FinalFactAp): List<InitialFactAp> {
        val result = mutableListOf<InitialFactAp>()
        sideEffectRequirement.filterTo(result, initialFactAp)
        return result
    }

    fun zeroSideEffectSummaries(): List<ZeroSideEffectSummary> =
        zeroSideEffectSummariesStorage.map { ZeroSideEffectSummary(it) }

    fun factSideEffectSummaries(initialFactAp: FinalFactAp): List<FactSideEffectSummary> =
        mutableListOf<FactSideEffectSummary>().also {
            factSideEffectSummariesStorage.filterTaintedTo(it, initialFactAp)
        }

    fun collectStats(stats: MethodStats) {
        val sourceEdges = mutableListOf<Edge.ZeroInitialEdge>()
        collectAllZeroToFactSummariesTo(sourceEdges)
        val sourceSummaries = sourceEdges.sumOf { (it as? Edge.ZeroToFact)?.factAp?.size ?: 0 }

        val passEdges = mutableListOf<FactToFact>()
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
    fun build(): FactToFact = FactToFact(
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

data class NDFactToFactEdgeBuilder(
    private var entryPoint: MethodEntryPoint? = null,
    private var exitStatement: CommonInst? = null,
    private var initial: Set<InitialFactAp>? = null,
    private var exitAp: FinalFactAp? = null,
) : EdgeBuilder<NDFactToFactEdgeBuilder> {
    fun build(): Edge.NDFactToFact = Edge.NDFactToFact(
        entryPoint!!,
        initial!!,
        exitStatement!!,
        exitAp!!
    )

    override fun setEntryPoint(entryPoint: MethodEntryPoint) = this.also {
        this.entryPoint = entryPoint
    }

    override fun setExitStatement(statement: CommonInst) = this.also {
        exitStatement = statement
    }

    fun setInitial(ap: Set<InitialFactAp>) = this.also {
        initial = ap
    }

    fun setExitAp(ap: FinalFactAp) = this.also {
        exitAp = ap
    }
}

abstract class SummaryFactStorage<Storage : Any>(methodEntryPoint: CommonInst) :
    AccessPathBaseStorage<Storage>(methodEntryPoint) {
    private var locals: ConcurrentHashMap<Int, Storage>? = null
    private var constants: ConcurrentHashMap<AccessPathBase.Constant, Storage>? = null
    private var statics: ConcurrentHashMap<AccessPathBase.ClassStatic, Storage>? = null

    override fun getOrCreateLocal(idx: Int): Storage {
        val summaries = locals ?: ConcurrentHashMap<Int, Storage>()
            .also { locals = it }

        return summaries.computeIfAbsent(idx) { createStorage() }
    }

    override fun findLocal(idx: Int): Storage? =
        locals?.get(idx)

    override fun forEachLocalValue(body: (AccessPathBase, Storage) -> Unit) {
        locals?.forEach { (localVarIdx, storage) -> body(AccessPathBase.LocalVar(localVarIdx), storage) }
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
        MethodSummaryEdgesForExitPoint<FactToFact, FactToFactEdgeBuilder, Storage, Pattern>

abstract class MethodSummaryEdgesForExitPoint<E : Edge, B : EdgeBuilder<B>, Storage, Pattern>(
    val methodEntryPoint: CommonInst
) {
    abstract fun createStorage(): Storage
    abstract fun storageAdd(storage: Storage, edges: List<E>, added: MutableList<B>)

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
