package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.util.concurrentReadSafeForEach
import java.util.concurrent.atomic.LongAdder

class TaintAnalysisUnitRunner(
    private val manager: TaintAnalysisUnitRunnerManager,
    private val unit: UnitType,
    override val graph: JIRApplicationGraph,
    private val callResolver: JIRCallResolver,
    private val unitResolver: JIRUnitResolver,
    override val taintConfiguration: TaintRulesProvider,
    override val factTypeChecker: FactTypeChecker,
    override val sinkTracker: TaintSinkTracker
) : AnalysisRunner, SummaryEdgeSubscriptionManager.SummaryEdgeProcessingCtx {
    override val lambdaTracker: JIRLambdaTracker
        get() = manager.lambdaTracker

    override val apManager: ApManager
        get() = manager.apManager

    private val workList: Channel<Any> = Channel(Channel.UNLIMITED)

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

    suspend fun runLoop() {
        tabulationAlgorithm()
    }

    fun submitStartMethods(startMethods: List<JIRMethod>) {
        for (method in startMethods) {
            addStart(method)
        }
    }

    private fun addStart(method: JIRMethod) {
        require(unitResolver.resolve(method) == unit)
        addStartMethodEvent(method)
    }

    fun submitExternalInitialZeroFact(methodEntryPoint: MethodEntryPoint) {
        addUnprocessedEvent(ExternalInputFact.InputZero(methodEntryPoint))
    }

    fun submitExternalInitialFact(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp) {
        addUnprocessedEvent(ExternalInputFact.InputFact(methodEntryPoint, factAp))
    }

    sealed interface ExternalInputFact {
        val methodEntryPoint: MethodEntryPoint

        data class InputZero(override val methodEntryPoint: MethodEntryPoint) : ExternalInputFact

        data class InputFact(override val methodEntryPoint: MethodEntryPoint, val factAp: FinalFactAp) : ExternalInputFact
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val event = workList.receive()

            when (event) {
                is Edge -> {
                    tabulationAlgorithmStep(event)
                }

                is ExternalInputFact -> {
                    handleNewInputFact(event)
                }

                is SummaryEdgeSubscriptionManager.SummaryEvent -> {
                    event.processMethodSummary()
                }

                is JIRMethod -> {
                    handleStartMethodEvent(event)
                }

                is LambdaResolvedEvent -> {
                    handleLambdaResolvedEvent(event)
                }
            }

            eventsEnqueued.decrement()
            eventsProcessed.increment()
            manager.handleEventProcessed()
        }
    }

    private fun addStartMethodEvent(method: JIRMethod) = addUnprocessedAnyEvent(method)

    private fun addUnprocessedEvent(event: ExternalInputFact) = addUnprocessedAnyEvent(event)
    private fun addUnprocessedEvent(edge: Edge) = addUnprocessedAnyEvent(edge)

    override fun addSummaryEdgeEvent(event: SummaryEdgeSubscriptionManager.SummaryEvent) {
        addUnprocessedAnyEvent(event)
    }

    private fun addResolvedLambdaEvent(event: LambdaResolvedEvent) = addUnprocessedAnyEvent(event)

    private fun addUnprocessedAnyEvent(event: Any) {
        eventsEnqueued.increment()
        manager.handleEventEnqueued()
        workList.trySend(event)
    }

    private fun handleStartMethodEvent(method: JIRMethod) {
        for (start in graph.entryPoints(method)) {
            val methodEntryPoint = MethodEntryPoint(EmptyMethodContext, start)
            val methodAnalyzers = methodAnalyzers(methodEntryPoint)
            methodAnalyzers.add(this, methodEntryPoint)

            methodAnalyzers.getAnalyzer(methodEntryPoint).addInitialZeroFact()
        }
    }

    private fun handleNewInputFact(event: ExternalInputFact) {
        when (event) {
            is ExternalInputFact.InputFact -> submitMethodInitialFact(event.methodEntryPoint, event.factAp)
            is ExternalInputFact.InputZero -> submitMethodInitialZeroFact(event.methodEntryPoint)
        }
    }

    private fun submitMethodInitialZeroFact(methodEntryPoint: MethodEntryPoint) {
        val methodRunner = methodAnalyzers(methodEntryPoint)
        methodRunner.add(this, methodEntryPoint)

        methodRunner.getAnalyzer(methodEntryPoint).addInitialZeroFact()
    }

    private fun submitMethodInitialFact(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp) {
        val methodRunner = methodAnalyzers(methodEntryPoint)
        methodRunner.add(this, methodEntryPoint)

        methodRunner.getAnalyzer(methodEntryPoint).addInitialFact(factAp)
    }

    private fun methodAnalyzers(methodEntryPoint: MethodEntryPoint): MethodAnalyzerStorage =
        methodAnalyzers(methodEntryPoint.method)

    private fun methodAnalyzers(method: JIRMethod): MethodAnalyzerStorage =
        methodAnalyzers.computeIfAbsent(method) { MethodAnalyzerStorage().also { analyzers.add(it) } }

    private fun propagateNew(edge: Edge) {
        require(unitResolver.resolve(edge.methodEntryPoint.method) == unit) {
            "Propagated edge must be in the same unit"
        }

        // Add edge to worklist:
        addUnprocessedEvent(edge)
    }

    private fun tabulationAlgorithmStep(currentEdge: Edge) {
        val methodRunners = methodAnalyzers(currentEdge.methodEntryPoint)
        val runner = methodRunners.getAnalyzer(currentEdge.methodEntryPoint)
        runner.tabulationAlgorithmStep(currentEdge)
    }

    override fun submitNewUnprocessedEdge(edge: Edge) {
        propagateNew(edge)
    }

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToZero,
        methodEntryPoint: MethodEntryPoint
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, edge) },
        submitThisUnitFact = { submitMethodInitialZeroFact(methodEntryPoint) },
        submitCrossUnitFact = { handleCrossUnitZeroCall(methodEntryPoint) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToFact,
        methodEntryPoint: MethodEntryPoint,
        methodFactBase: AccessPathBase
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.factAp.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(methodEntryPoint, edge.factAp.rebase(methodFactBase)) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.FactToFact,
        methodEntryPoint: MethodEntryPoint,
        methodFactBase: AccessPathBase
    ) = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.factAp.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(methodEntryPoint, edge.factAp.rebase(methodFactBase)) }
    )

    private inline fun subscribeOnMethodSummaries(
        methodEntryPoint: MethodEntryPoint,
        subscribe: SummaryEdgeSubscriptionManager.() -> Boolean,
        submitThisUnitFact: () -> Unit,
        submitCrossUnitFact: TaintAnalysisUnitRunnerManager.() -> Unit,
    ) {
        val method = methodEntryPoint.method
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

    override fun addNewSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>) {
        manager.newSummaryEdges(methodEntryPoint, edges)
    }

    override fun addNewSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: InitialFactAp) {
        manager.newSinkRequirement(methodEntryPoint, requirement)
    }

    override fun getMethodAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer =
        methodAnalyzers(methodEntryPoint).getAnalyzer(methodEntryPoint)

    override fun resolveMethodCall(
        callerEntryPoint: MethodEntryPoint,
        callExpr: JIRCallExpr,
        location: JIRInst,
        handler: MethodAnalyzer.MethodCallHandler,
        failureHandler: MethodAnalyzer.MethodCallResolutionFailureHandler
    ) {
        val callees = callResolver.resolve(callExpr, location, callerEntryPoint.context)

        val analyzer = getMethodAnalyzer(callerEntryPoint)
        for (resolvedCallee in callees) {
            when (resolvedCallee) {
                JIRCallResolver.MethodResolutionResult.MethodResolutionFailed -> {
                    analyzer.handleMethodCallResolutionFailure(callExpr, failureHandler)
                }

                is JIRCallResolver.MethodResolutionResult.ConcreteMethod -> {
                    analyzer.handleResolvedMethodCall(resolvedCallee.method, handler)
                }

                is JIRCallResolver.MethodResolutionResult.Lambda -> {
                    val subscription = LambdaSubscription(this, callerEntryPoint, handler)
                    lambdaTracker.subscribeOnLambda(resolvedCallee.method, subscription)
                }
            }
        }
    }

    private data class LambdaSubscription(
        private val runner: TaintAnalysisUnitRunner,
        private val callerEntryPoint: MethodEntryPoint,
        private val handler: MethodAnalyzer.MethodCallHandler
    ) : JIRLambdaTracker.LambdaSubscriber {
        override fun newLambda(method: JIRMethod, lambdaClass: LambdaAnonymousClassFeature.JIRLambdaClass) {
            val methodImpl = lambdaClass.findMethodOrNull(method.name, method.description)
                ?: error("Lambda class $lambdaClass has no lambda method $method")

            val lambdaMethodWithContext = MethodWithContext(methodImpl, EmptyMethodContext)
            runner.addResolvedLambdaEvent(LambdaResolvedEvent(callerEntryPoint, handler, lambdaMethodWithContext))
        }
    }

    private data class LambdaResolvedEvent(
        val callerEntryPoint: MethodEntryPoint,
        val handler: MethodAnalyzer.MethodCallHandler,
        val resolvedLambdaMethod: MethodWithContext
    )

    private fun handleLambdaResolvedEvent(event: LambdaResolvedEvent) {
        val analyzer = getMethodAnalyzer(event.callerEntryPoint)
        analyzer.handleResolvedMethodCall(event.resolvedLambdaMethod, event.handler)
    }
}
