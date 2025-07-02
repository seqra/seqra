package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.opentaint.dataflow.jvm.ap.ifds.TaintAnalysisUnitRunnerManager.WorkListEmptyEvent
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.concurrentReadSafeForEach
import java.util.concurrent.atomic.LongAdder

class TaintAnalysisUnitRunner(
    private val manager: TaintAnalysisUnitRunnerManager,
    private val unit: UnitType,
    override val graph: JIRApplicationGraph,
    override val unitResolver: JIRUnitResolver,
    override val taintConfiguration: TaintRulesProvider,
    override val factTypeChecker: FactTypeChecker,
    override val sinkTracker: TaintSinkTracker
) : AnalysisRunner, SummaryEdgeSubscriptionManager.SummaryEdgeProcessingCtx {
    private val workList: Channel<Any> = Channel(Channel.UNLIMITED)

    private val queueIsEmpty = WorkListEmptyEvent(unit, isEmpty = true)
    private val queueIsNotEmpty = WorkListEmptyEvent(unit, isEmpty = false)

    private val analyzers = mutableListOf<MethodAnalyzerStorage>()
    private val methodAnalyzers = hashMapOf<JIRMethod, MethodAnalyzerStorage>()

    private val internalMethodSummarySubscriptions = SummaryEdgeSubscriptionManager(manager, this)
    private val externalMethodSummarySubscriptions = SummaryEdgeSubscriptionManager(manager, this)

    private val eventsProcessed = LongAdder()
    private val eventsEnqueued = LongAdder()

    fun stats() = UnitRunnerStats(eventsProcessed.sum(), eventsEnqueued.sum())

    fun collectMethodStats(stats: MethodStats) {
        analyzers.concurrentReadSafeForEach { _, methodAnalyzerStorage ->
            methodAnalyzerStorage.collectStats(stats)
        }
    }

    fun cancel() {
        workList.cancel()
    }

    suspend fun run(startMethods: List<JIRMethod>) {
        for (method in startMethods) {
            addStart(method)
        }

        tabulationAlgorithm()
    }

    private fun addStart(method: JIRMethod) {
        require(unitResolver.resolve(method) == unit)
        for (start in graph.entryPoints(method)) {
            val methodAnalyzers = methodAnalyzers(start)
            methodAnalyzers.add(this, start)

            methodAnalyzers.getAnalyzer(start).addInitialZeroFact()
        }
    }

    fun submitExternalInitialZeroFact(methodEntryPoint: JIRInst) {
        addUnprocessedEvent(ExternalInputFact.InputZero(methodEntryPoint))
    }

    fun submitExternalInitialFact(methodEntryPoint: JIRInst, fact: Fact.TaintedTree) {
        addUnprocessedEvent(ExternalInputFact.InputFact(methodEntryPoint, fact))
    }

    sealed interface ExternalInputFact {
        val methodEntryPoint: JIRInst

        data class InputZero(override val methodEntryPoint: JIRInst) : ExternalInputFact

        data class InputFact(override val methodEntryPoint: JIRInst, val fact: Fact.TaintedTree) : ExternalInputFact
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val event = workList.tryReceive().getOrElse {
                manager.handleControlEvent(queueIsEmpty)
                workList.receive()
            }

            when (event) {
                is ExternalInputFact -> {
                    handleNewInputFact(event)
                }

                is SummaryEdgeSubscriptionManager.SummaryEvent -> {
                    event.processMethodSummary()
                }

                is Edge -> {
                    tabulationAlgorithmStep(event)
                }
            }

            eventsEnqueued.decrement()
            eventsProcessed.increment()
        }
    }

    private fun addUnprocessedEvent(event: ExternalInputFact) = addUnprocessedAnyEvent(event)
    private fun addUnprocessedEvent(edge: Edge) = addUnprocessedAnyEvent(edge)

    override fun addSummaryEdgeEvent(event: SummaryEdgeSubscriptionManager.SummaryEvent) {
        addUnprocessedAnyEvent(event)
    }

    private fun addUnprocessedAnyEvent(event: Any) {
        workList.trySend(event)
        eventsEnqueued.increment()
        manager.handleControlEvent(queueIsNotEmpty)
    }

    private fun handleNewInputFact(event: ExternalInputFact) {
        when (event) {
            is ExternalInputFact.InputFact -> submitMethodInitialFact(event.methodEntryPoint, event.fact)
            is ExternalInputFact.InputZero -> submitMethodInitialZeroFact(event.methodEntryPoint)
        }
    }

    private fun submitMethodInitialZeroFact(methodEntryPoint: JIRInst) {
        val methodRunner = methodAnalyzers(methodEntryPoint)
        methodRunner.add(this, methodEntryPoint)

        methodRunner.getAnalyzer(methodEntryPoint).addInitialZeroFact()
    }

    private fun submitMethodInitialFact(methodEntryPoint: JIRInst, fact: Fact.TaintedTree) {
        val methodRunner = methodAnalyzers(methodEntryPoint)
        methodRunner.add(this, methodEntryPoint)

        methodRunner.getAnalyzer(methodEntryPoint).addInitialFact(fact)
    }

    private fun methodAnalyzers(methodEntryPoint: JIRInst): MethodAnalyzerStorage =
        methodAnalyzers(methodEntryPoint.location.method)

    private fun methodAnalyzers(method: JIRMethod): MethodAnalyzerStorage =
        methodAnalyzers.computeIfAbsent(method) { MethodAnalyzerStorage().also { analyzers.add(it) } }

    private fun propagateNew(edge: Edge) {
        require(unitResolver.resolve(edge.initialStatement.location.method) == unit) {
            "Propagated edge must be in the same unit"
        }

        // Add edge to worklist:
        addUnprocessedEvent(edge)
    }

    private fun tabulationAlgorithmStep(currentEdge: Edge) {
        val methodRunners = methodAnalyzers(currentEdge.initialStatement)
        val runner = methodRunners.getAnalyzer(currentEdge.initialStatement)
        runner.tabulationAlgorithmStep(currentEdge)
    }

    override fun submitNewUnprocessedEdge(edge: Edge) {
        propagateNew(edge)
    }

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToZero,
        methodEntryPoint: JIRInst
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, edge) },
        submitThisUnitFact = { submitMethodInitialZeroFact(methodEntryPoint) },
        submitCrossUnitFact = { handleCrossUnitZeroCall(methodEntryPoint) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToFact,
        methodEntryPoint: JIRInst,
        methodFactBase: AccessPathBase
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.fact.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(methodEntryPoint, edge.fact.rebase(methodFactBase)) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.FactToFact,
        methodEntryPoint: JIRInst,
        methodFactBase: AccessPathBase
    ) = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.fact.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(methodEntryPoint, edge.fact.rebase(methodFactBase)) }
    )

    private inline fun subscribeOnMethodSummaries(
        methodEntryPoint: JIRInst,
        subscribe: SummaryEdgeSubscriptionManager.() -> Boolean,
        submitThisUnitFact: () -> Unit,
        submitCrossUnitFact: TaintAnalysisUnitRunnerManager.() -> Unit,
    ) {
        val method = methodEntryPoint.location.method
        if (method.isExtern) {
            // Subscribe on summary edges:
            if (externalMethodSummarySubscriptions.subscribe()) {
                // Initialize analysis of callee:
                manager.submitCrossUnitFact()
            }
        } else {
            // Save info about the call for summary edges that will be found later:
            if (internalMethodSummarySubscriptions.subscribe()) {
                // Initialize analysis of callee:
                submitThisUnitFact()
            }
        }
    }

    override fun addNewSummaryEdges(initialStatement: JIRInst, edges: List<Edge>) {
        manager.newSummaryEdges(initialStatement, edges)
    }

    override fun addNewSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath) {
        manager.newSinkRequirement(initialStatement, requirement)
    }

    override fun getMethodAnalyzer(methodEntryPoint: JIRInst): MethodAnalyzer =
        methodAnalyzers(methodEntryPoint).getAnalyzer(methodEntryPoint)
}
