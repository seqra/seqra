package org.opentaint.dataflow.ap.ifds

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ap.ifds.SummaryEdgeSubscriptionManager.MethodEntryPointCaller
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.serialization.MethodSummariesSerializer
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolverCancellation
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.util.concurrentReadSafeForEach
import java.util.concurrent.atomic.LongAdder

class TaintAnalysisUnitRunner(
    override val manager: TaintAnalysisUnitRunnerManager,
    private val unit: UnitType,
    override val graph: ApplicationGraph<CommonMethod, CommonInst>,
    private val unitResolver: UnitResolver<CommonMethod>,
    override val taintConfiguration: TaintRulesProvider,
    override val sinkTracker: TaintSinkTracker,
    private val summarySerializationContext: SummarySerializationContext
) : AnalysisRunner, SummaryEdgeSubscriptionManager.SummaryEdgeProcessingCtx {
    override val apManager: ApManager
        get() = manager.apManager

    override val languageManager: LanguageManager
        get() = manager.languageManager

    override val methodCallResolver: MethodCallResolver = languageManager.getMethodCallResolver(
        graph = graph,
        unitResolver = unitResolver,
        runner = this
    )

    private val workList: Channel<Any> = Channel(Channel.UNLIMITED)

    private val analyzers = mutableListOf<MethodAnalyzerStorage>()
    private val methodAnalyzers = hashMapOf<CommonMethod, MethodAnalyzerStorage>()
    private val loadedSummaries = hashMapOf<MethodEntryPoint, Pair<List<Edge>, List<InitialFactAp>>>()

    private val internalMethodSummarySubscriptions = SummaryEdgeSubscriptionManager(manager, this)
    private val externalMethodSummarySubscriptions = SummaryEdgeSubscriptionManager(manager, this)

    private val eventsProcessed = LongAdder()
    private val eventsEnqueued = LongAdder()

    private val methodSummariesSerializer = MethodSummariesSerializer(
        summarySerializationContext,
        languageManager,
        apManager
    )

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

    fun submitStartMethods(startMethods: List<CommonMethod>) {
        for (method in startMethods) {
            addStart(method)
        }
    }

    private fun addStart(method: CommonMethod) {
        require(unitResolver.resolve(method) == unit)
        addStartMethodEvent(method)
    }

    override fun submitExternalInitialZeroFact(methodEntryPoint: MethodEntryPoint) {
        addUnprocessedEvent(ExternalInputFact.InputZero(methodEntryPoint))
    }

    override fun submitExternalInitialFact(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp) {
        addUnprocessedEvent(ExternalInputFact.InputFact(methodEntryPoint, factAp))
    }

    sealed interface ExternalInputFact {
        val methodEntryPoint: MethodEntryPoint

        data class InputZero(override val methodEntryPoint: MethodEntryPoint) : ExternalInputFact

        data class InputFact(override val methodEntryPoint: MethodEntryPoint, val factAp: FinalFactAp) :
            ExternalInputFact
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val event = workList.receive()

            when (event) {
                is MethodAnalyzer -> {
                    while (event.containsUnprocessedEdges && isActive) {
                        event.tabulationAlgorithmStep()
                    }
                }

                is ExternalInputFact -> {
                    handleNewInputFact(event)
                }

                is SummaryEdgeSubscriptionManager.SummaryEvent -> {
                    event.processMethodSummary()
                }

                is CommonMethod -> {
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

    private fun addStartMethodEvent(method: CommonMethod) = addUnprocessedAnyEvent(method)

    private fun addUnprocessedEvent(event: ExternalInputFact) = addUnprocessedAnyEvent(event)
    private fun addUnprocessedEvent(edge: MethodAnalyzer) = addUnprocessedAnyEvent(edge)

    override fun addSummaryEdgeEvent(event: SummaryEdgeSubscriptionManager.SummaryEvent) {
        addUnprocessedAnyEvent(event)
    }

    fun addResolvedLambdaEvent(event: LambdaResolvedEvent) = addUnprocessedAnyEvent(event)

    private fun addUnprocessedAnyEvent(event: Any) {
        eventsEnqueued.increment()
        manager.handleEventEnqueued()
        workList.trySend(event)
    }

    private fun handleStartMethodEvent(method: CommonMethod) {
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
        submitMethodInitialFact(methodEntryPoint) {
            it.addInitialZeroFact()
        }
    }

    private fun submitMethodInitialFact(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp) {
        submitMethodInitialFact(methodEntryPoint) {
            it.addInitialFact(factAp)
        }
    }

    private inline fun submitMethodInitialFact(methodEntryPoint: MethodEntryPoint, body: (MethodAnalyzer) -> Unit) {
        val methodRunner = methodAnalyzers(methodEntryPoint)
        methodRunner.add(this, methodEntryPoint)

        val analyzer = methodRunner.getAnalyzer(methodEntryPoint)
        body(analyzer)
    }

    private fun methodAnalyzers(methodEntryPoint: MethodEntryPoint): MethodAnalyzerStorage =
        methodAnalyzers(methodEntryPoint.method)

    private fun methodAnalyzers(method: CommonMethod): MethodAnalyzerStorage =
        methodAnalyzers.computeIfAbsent(method) { MethodAnalyzerStorage(languageManager).also { analyzers.add(it) } }

    override fun enqueueMethodAnalyzer(analyzer: MethodAnalyzer) {
        addUnprocessedEvent(analyzer)
    }

    private val CommonMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToZero,
        methodEntryPoint: MethodEntryPoint
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, edge) },
        submitThisUnitFact = { submitMethodInitialZeroFact(methodEntryPoint) },
        submitCrossUnitFact = { handleCrossUnitZeroCall(unit, methodEntryPoint) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.ZeroToFact,
        methodEntryPoint: MethodEntryPoint,
        methodFactBase: AccessPathBase
    )  = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.factAp.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(unit, methodEntryPoint, edge.factAp.rebase(methodFactBase)) }
    )

    override fun subscribeOnMethodSummaries(
        edge: Edge.FactToFact,
        methodEntryPoint: MethodEntryPoint,
        methodFactBase: AccessPathBase
    ) = subscribeOnMethodSummaries(
        methodEntryPoint = methodEntryPoint,
        subscribe = { subscribeOnMethodSummary(methodEntryPoint, methodFactBase, edge) },
        submitThisUnitFact = { submitMethodInitialFact(methodEntryPoint, edge.factAp.rebase(methodFactBase)) },
        submitCrossUnitFact = { handleCrossUnitFactCall(unit, methodEntryPoint, edge.factAp.rebase(methodFactBase)) }
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

    override fun getPrecalculatedSummaries(methodEntryPoint: MethodEntryPoint): Pair<List<Edge>, List<InitialFactAp>>? {
        loadedSummaries[methodEntryPoint]?.let {
            return it
        }

        val serializedSummaries = summarySerializationContext.loadSummaries(methodEntryPoint.method) ?: return null
        val methodSummaries = methodSummariesSerializer.deserializeMethodSummaries(serializedSummaries)

        methodSummaries.forEach { (methodEntryPoint, edges, requirements) ->
            loadedSummaries[methodEntryPoint] = edges to requirements
        }

        return loadedSummaries[methodEntryPoint]
    }

    override fun addNewSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>) {
        manager.newSideEffectRequirement(methodEntryPoint, requirements)
    }

    override fun getMethodAnalyzer(methodEntryPoint: MethodEntryPoint): MethodAnalyzer =
        methodAnalyzers(methodEntryPoint).getAnalyzer(methodEntryPoint)

    data class LambdaResolvedEvent(
        val callerEntryPoint: MethodEntryPoint,
        val handler: MethodAnalyzer.MethodCallHandler,
        val resolvedLambdaMethod: MethodWithContext
    )

    private fun handleLambdaResolvedEvent(event: LambdaResolvedEvent) {
        val analyzer = getMethodAnalyzer(event.callerEntryPoint)
        analyzer.handleResolvedMethodCall(event.resolvedLambdaMethod, event.handler)
    }

    fun methodCallers(
        methodEntryPoint: MethodEntryPoint,
        collectZeroCallsOnly: Boolean,
        callers: MutableSet<MethodEntryPointCaller>,
    ) {
        if (methodEntryPoint.method.isExtern) {
            externalMethodSummarySubscriptions.methodEntryPointCallers(methodEntryPoint, collectZeroCallsOnly, callers)
        } else {
            internalMethodSummarySubscriptions.methodEntryPointCallers(methodEntryPoint, collectZeroCallsOnly, callers)
        }
    }

    fun resolveIntraProceduralTraceSummary(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        factAp: InitialFactAp
    ): List<MethodTraceResolver.SummaryTrace> {
        val methodRunners = methodAnalyzers(methodEntryPoint)
        val runner = methodRunners.getAnalyzer(methodEntryPoint)
        return runner.resolveIntraProceduralTraceSummary(statement, factAp)
    }

    fun resolveIntraProceduralTraceSummaryFromCall(
        methodEntryPoint: MethodEntryPoint,
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        val methodRunners = methodAnalyzers(methodEntryPoint)
        val runner = methodRunners.getAnalyzer(methodEntryPoint)
        return runner.resolveIntraProceduralTraceSummaryFromCall(statement, calleeEntry)
    }

    fun resolveIntraProceduralFullTrace(
        methodEntryPoint: MethodEntryPoint,
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: TraceResolverCancellation
    ): MethodTraceResolver.FullTrace {
        val methodRunners = methodAnalyzers(methodEntryPoint)
        val runner = methodRunners.getAnalyzer(methodEntryPoint)
        return runner.resolveIntraProceduralFullTrace(summaryTrace, cancellation)
    }
}
