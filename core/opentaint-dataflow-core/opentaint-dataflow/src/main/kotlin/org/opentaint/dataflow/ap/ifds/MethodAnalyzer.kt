package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.Edge.NDFactToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroInitialEdge
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToZero
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.FactToFactSub
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallResolutionFailureHandler
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallSummaryHandler.SummaryEdge
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.ap.ifds.trace.MethodForwardTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.MethodForwardTraceResolver.RelevantFactFilter
import org.opentaint.dataflow.ap.ifds.trace.MethodForwardTraceResolver.TraceGraph
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.cartesianProductMapTo
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import kotlin.system.measureNanoTime

interface MethodAnalyzer {
    val methodEntryPoint: MethodEntryPoint

    fun addInitialZeroFact()

    fun addInitialFact(factAp: FinalFactAp)

    fun triggerSideEffectRequirement(sideEffectRequirement: InitialFactAp)

    fun updateFactDepthLimit(newLimit: Int)

    val containsUnprocessedEdges: Boolean

    val containsDelayedEdges: Boolean

    fun tabulationAlgorithmStep()

    fun handleZeroToZeroMethodSummaryEdge(currentEdge: ZeroToZero, methodSummaries: List<ZeroInitialEdge>)

    fun handleZeroToFactMethodSummaryEdge(summarySubs: List<ZeroToFactSub>, methodSummaries: List<FactToFact>)

    fun handleFactToFactMethodSummaryEdge(summarySubs: List<FactToFactSub>, methodSummaries: List<FactToFact>)

    fun handleNDFactToFactMethodSummaryEdge(summarySubs: List<NDFactToFactSub>, methodSummaries: List<FactToFact>)

    fun handleZeroToFactMethodNDSummaryEdge(summarySubs: List<ZeroToFactSub>, methodSummaries: List<NDFactToFact>)

    fun handleFactToFactMethodNDSummaryEdge(summarySubs: List<FactToFactSub>, methodSummaries: List<NDFactToFact>)

    fun handleNDFactToFactMethodNDSummaryEdge(summarySubs: List<NDFactToFactSub>, methodSummaries: List<NDFactToFact>)

    fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
    )

    fun handleZeroToZeroMethodSideEffectSummary(
        currentEdge: ZeroToZero,
        sideEffectSummaries: List<SideEffectSummary.ZeroSideEffectSummary>
    )

    fun handleZeroToFactMethodSideEffectSummary(
        summarySubs: List<ZeroToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    )

    fun handleFactToFactMethodSideEffectSummary(
        summarySubs: List<FactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    )

    fun handleNDFactToFactMethodSideEffectSummary(
        summarySubs: List<NDFactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    )

    val analyzerSteps: Long

    fun collectStats(stats: MethodStats)

    data class ZeroToFactSub(
        val currentEdge: ZeroToFact,
        val methodInitialFactBase: AccessPathBase
    )

    data class FactToFactSub(
        val currentEdge: FactToFact,
        val methodInitialFactBase: AccessPathBase
    )

    data class NDFactToFactSub(
        val currentEdge: NDFactToFact,
        val methodInitialFactBase: AccessPathBase
    )

    fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler)

    fun handleResolvedMethodCall(entryPoint: MethodEntryPoint, handler: MethodCallHandler)

    fun handleMethodCallResolutionFailure(
        callExpr: CommonCallExpr,
        handler: MethodCallResolutionFailureHandler
    )

    fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        facts: Set<InitialFactAp>,
        includeStatement: Boolean = false,
    ): List<MethodTraceResolver.SummaryTrace>

    fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace>

    fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: Cancellation,
        collapseUnchangedNodes: Boolean,
    ): List<MethodTraceResolver.FullTrace>

    fun resolveIntraProceduralForwardFullTrace(
        statement: CommonInst,
        fact: FinalFactAp,
        includeStatement: Boolean = false,
        relevantFactFilter: RelevantFactFilter,
    ): TraceGraph

    fun resolveCalleeFact(
        statement: CommonInst,
        factAp: FinalFactAp
    ): Set<FinalFactAp>

    fun allIntraProceduralFacts(): Map<CommonInst, Set<FinalFactAp>>

    sealed interface MethodCallHandler {
        data class ZeroToZeroHandler(val currentEdge: ZeroToZero) : MethodCallHandler
        data class ZeroToFactHandler(val currentEdge: ZeroToFact, val startFactBase: AccessPathBase) : MethodCallHandler
        data class FactToFactHandler(val currentEdge: FactToFact, val startFactBase: AccessPathBase) : MethodCallHandler
        data class NDFactToFactHandler(val currentEdge: NDFactToFact, val startFactBase: AccessPathBase) : MethodCallHandler
    }

    sealed interface MethodCallResolutionFailureHandler {
        val callerEdge: Edge

        data class ZeroToZeroHandler(override val callerEdge: ZeroToZero) : MethodCallResolutionFailureHandler
        data class ZeroToFactHandler(override val callerEdge: ZeroToFact, val startFactBase: AccessPathBase) : MethodCallResolutionFailureHandler
        data class FactToFactHandler(override val callerEdge: FactToFact, val startFactBase: AccessPathBase): MethodCallResolutionFailureHandler
        data class NDFactToFactHandler(override val callerEdge: NDFactToFact, val startFactBase: AccessPathBase): MethodCallResolutionFailureHandler
    }
}

class NormalMethodAnalyzer(
    private val runner: AnalysisRunner,
    override val methodEntryPoint: MethodEntryPoint,
    private val taintRulesStatsSamplingPeriod: Int?,
    emptyContextAnalyzer: MethodAnalyzer?,
) : MethodAnalyzer {
    private val apManager: ApManager get() = runner.apManager
    private val analysisManager get() = runner.analysisManager
    private val methodCallResolver get() = runner.methodCallResolver
    private val cancellation: Cancellation = runner.manager.cancellation

    private var zeroInitialFactProcessed: Boolean = false
    private val initialFacts = apManager.initialFactAbstraction(methodEntryPoint.statement)
    private val edges = MethodAnalyzerEdges(apManager, methodEntryPoint, analysisManager)
    private var pendingSummaryEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)
    private var pendingSideEffectRequirements = arrayListOf<InitialFactAp>()
    private var pendingSideEffectSummaries = arrayListOf<SideEffectSummary>()

    private val analysisContext: MethodAnalysisContext = analysisManager.getMethodAnalysisContext(
        methodEntryPoint, runner.graph, runner.methodCallResolver,
        (emptyContextAnalyzer as? NormalMethodAnalyzer)?.analysisContext
    )

    private val methodInstGraph = analysisManager.getMethodInstGraph(runner.graph, analysisContext, methodEntryPoint.method)

    private var analyzerEnqueued = false
    private var unprocessedEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)
    private var enqueuedUnchangedEdges = EdgeCollection.EdgeSet()

    override val containsUnprocessedEdges: Boolean
        get() = !unprocessedEdges.isEmpty

    override var analyzerSteps: Long = 0
        private set

    private val stepsForTaintMark: MutableMap<String, Long> = hashMapOf()

    private var summaryEdgesHandled: Long = 0
    private var traceResolverSteps: Long = 0

    private var factDepthLimit = INITIAL_ALLOWED_FACT_DEPTH
    private var delayedF2FInitialEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)
    private var delayedF2FSummaries = EdgeCollection.EdgeList(apManager, methodEntryPoint)

    override val containsDelayedEdges: Boolean
        get() = !delayedF2FInitialEdges.isEmpty || !delayedF2FSummaries.isEmpty

    init {
        loadSummariesFromRunner()
    }

    private fun loadSummariesFromRunner() {
        runner.getPrecalculatedSummaries(methodEntryPoint)?.let { (summaryEdges, requirements) ->
            runner.addNewSummaryEdges(methodEntryPoint, summaryEdges)
            runner.addNewSideEffectRequirement(methodEntryPoint, requirements)
            summaryEdges.forEach { edge ->
                when (edge) {
                    is FactToFact -> initialFacts.registerNewInitialFact(edge.initialFactAp, analysisManager.factTypeChecker)
                    is ZeroToFact -> zeroInitialFactProcessed = true
                    is ZeroToZero -> zeroInitialFactProcessed = true
                    is NDFactToFact -> edge.initialFacts.forEach {
                        initialFacts.registerNewInitialFact(it, analysisManager.factTypeChecker)
                    }
                }
            }
        }
    }

    override fun collectStats(stats: MethodStats) {
        stats.stats(methodEntryPoint.method).apply {
            steps += analyzerSteps
            handledSummaries += summaryEdgesHandled
            traceResolverSteps += this@NormalMethodAnalyzer.traceResolverSteps
            unprocessedEdges += this@NormalMethodAnalyzer.unprocessedEdges.size
            coveredInstructions.or(edges.reachedStatements())
            this@NormalMethodAnalyzer.stepsForTaintMark.forEach { (mark, count) ->
                stepsForTaintMark.compute(mark) { _, prev ->
                    prev?.let { it + count } ?: count
                }
            }
        }
    }

    override fun allIntraProceduralFacts(): Map<CommonInst, Set<FinalFactAp>> =
        edges.reachedStatementsWithFact(analysisManager)

    override fun addInitialZeroFact() {
        if (!zeroInitialFactProcessed) {
            zeroInitialFactProcessed = true
            val flowFunction = analysisManager.getMethodStartFlowFunction(apManager, analysisContext)
            flowFunction.propagateZero().forEach { fact ->
                when (fact) {
                    StartFact.Zero -> addInitialZeroEdge()
                    is StartFact.Fact -> addInitialZeroToFactEdge(fact.fact)
                }
            }
        }
    }

    override fun addInitialFact(factAp: FinalFactAp) {
        val flowFunction = analysisManager.getMethodStartFlowFunction(apManager, analysisContext)
        val startFacts = flowFunction.propagateFact(factAp)
        startFacts.forEach { startFact ->
            initialFacts.addAbstractedInitialFact(startFact.fact, analysisManager.factTypeChecker).forEach { (initialFact, finalFact) ->
                addInitialEdge(initialFact, finalFact)
            }
        }
    }

    override fun triggerSideEffectRequirement(sideEffectRequirement: InitialFactAp) {
        val curFact = sideEffectRequirement.replaceExclusions(ExclusionSet.Empty)
        addSideEffectRequirement(curFact, sideEffectRequirement)
    }

    override fun tabulationAlgorithmStep() {
        analyzerSteps++

        val edge = unprocessedEdges.removeLast()

        val finalEdgeFact = when (edge) {
            is ZeroToZero -> null
            is ZeroToFact -> edge.factAp
            is FactToFact -> edge.factAp
            is NDFactToFact -> edge.factAp
        }

        val edgeFactBase = finalEdgeFact?.base

        if (taintRulesStatsSamplingPeriod != null) {
            updateTaintRulesStats(finalEdgeFact, taintRulesStatsSamplingPeriod)
        }

        if (edgeFactBase == null || analysisManager.isReachable(apManager, analysisContext, edgeFactBase, edge.statement)) {
            analysisManager.onInstructionReached(edge.statement)

            val callExpr = analysisManager.getCallExpr(edge.statement)
            if (callExpr != null) {
                callStatementStep(callExpr, edge)
            } else {
                simpleStatementStep(edge)
            }
        }

        if (!unprocessedEdges.isEmpty) return

        analyzerEnqueued = false

        // Create new empty list to shrink internal array
        unprocessedEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)
        enqueuedUnchangedEdges = EdgeCollection.EdgeSet()

        flushPendingSummaryEdges()
        flushPendingSideEffectRequirements()
        flushPendingSideEffectSummaries()
    }

    private fun simpleStatementStep(edge: Edge) {
        // Simple (sequential) propagation to the next instruction:
        val flowFunction = analysisManager.getMethodSequentFlowFunction(apManager, analysisContext, edge.statement)
        val sequentialFacts = when (edge) {
            is ZeroToZero -> flowFunction.propagateZeroToZero()
            is ZeroToFact -> flowFunction.propagateZeroToFact(edge.factAp)
            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFactAp, edge.factAp)
            is NDFactToFact -> flowFunction.propagateNDFactToFact(edge.initialFacts, edge.factAp)
        }

        handleSequentFact(edge, sequentialFacts)
    }

    private fun handleSequentFact(edge: Edge, sf: Iterable<Sequent>) =
        sf.forEach { handleSequentFact(edge, it) }

    private fun handleSequentFact(edge: Edge, sf: Sequent) {
        val edgeAfterStatement = when (sf) {
            Sequent.Unchanged -> {
                handleUnchangedStatementEdge(edge)
                return
            }
            Sequent.ZeroToZero -> ZeroToZero(methodEntryPoint, edge.statement)
            is Sequent.ZeroToFact -> ZeroToFact(methodEntryPoint, edge.statement, sf.factAp)
            is Sequent.FactToFact -> FactToFact(methodEntryPoint, sf.initialFactAp, edge.statement, sf.factAp)
            is Sequent.NDFactToFact -> NDFactToFact(methodEntryPoint, sf.initialFacts, edge.statement, sf.factAp)
            is Sequent.SideEffectRequirement -> {
                check(edge is FactToFact) { "Initial fact required for side effect" }
                addSideEffectRequirement(edge, sf.initialFactAp)
                return
            }
            is Sequent.ZeroSideEffect -> {
                addZeroSideEffect(sf.kind)
                return
            }
            is Sequent.FactSideEffect -> {
                addFactSideEffect(edge, sf.initialFactAp, sf.kind)
                return
            }
        }

        handleStatementEdge(edge, edgeAfterStatement)
    }

    private fun callStatementStep(callExpr: CommonCallExpr, edge: Edge) {
        val returnValue: CommonValue? = (edge.statement as? CommonAssignInst)?.lhv

        val flowFunction = analysisManager.getMethodCallFlowFunction(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            edge.statement,
            generateTrace = false,
        )

        when (edge) {
            is ZeroInitialEdge -> {
                val callFacts = when (edge) {
                    is ZeroToZero -> flowFunction.propagateZeroToZero()
                    is ZeroToFact -> flowFunction.propagateZeroToFact(edge.factAp)
                }

                callFacts.forEach {
                    propagateZeroCallFact(callExpr, edge, it)
                }
            }

            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFactAp, edge.factAp).forEach {
                propagateFactCallFact(callExpr, edge, it)
            }

            is NDFactToFact -> flowFunction.propagateNDFactToFact(edge.initialFacts, edge.factAp).forEach {
                propagateNDFactCallFact(callExpr, edge, it)
            }
        }
    }

    private fun propagateZeroCallFact(
        callExpr: CommonCallExpr,
        edge: ZeroInitialEdge,
        fact: ZeroCallFact
    ) {
        when (fact) {
            is MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge)
            }

            is MethodCallFlowFunction.Drop -> {
                // do nothing
            }

            is MethodCallFlowFunction.CallToReturnZeroFact -> {
                handleStatementEdge(edge, ZeroToZero(methodEntryPoint, edge.statement))
            }

            is MethodCallFlowFunction.CallToReturnZFact -> {
                handleStatementEdge(edge, ZeroToFact(methodEntryPoint, edge.statement, fact.factAp))
            }

            is MethodCallFlowFunction.CallToStartZeroFact -> {
                val callerEdge = ZeroToZero(methodEntryPoint, edge.statement)

                val handler = MethodCallHandler.ZeroToZeroHandler(callerEdge)
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToZeroHandler(callerEdge)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.CallToStartZFact -> {
                val callerEdge = ZeroToFact(methodEntryPoint, edge.statement, fact.callerFactAp)
                val handler = MethodCallHandler.ZeroToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToFactHandler(callerEdge, fact.startFactBase)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.CallToReturnFFact -> {
                val edgeAfterStatement = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToReturnNonDistributiveFact -> {
                val edgeAfterStatement = NDFactToFact(
                    methodEntryPoint, fact.initialFacts, edge.statement, fact.factAp
                )
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.ZeroSideEffect -> {
                addZeroSideEffect(fact.kind)
            }
        }
    }

    private fun propagateFactCallFact(
        callExpr: CommonCallExpr,
        edge: FactToFact,
        fact: MethodCallFlowFunction.FactCallFact
    ) {
        when (fact) {
            is MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge)
            }

            is MethodCallFlowFunction.Drop -> {
                // do nothing
            }

            is MethodCallFlowFunction.CallToReturnFFact -> {
                val edgeAfterStatement = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToReturnZFact -> {
                val edgeAfterStatement = ZeroToFact(methodEntryPoint, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToStartFFact -> {
                val callerEdge = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.callerFactAp)

                handleInputFactChange(edge.initialFactAp, callerEdge.initialFactAp)

                val handler = MethodCallHandler.FactToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.FactToFactHandler(callerEdge, fact.startFactBase)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.SideEffectRequirement -> {
                addSideEffectRequirement(edge, fact.initialFactAp)
            }

            is MethodCallFlowFunction.FactSideEffect -> {
                addFactSideEffect(edge, fact.initialFactAp, fact.kind)
            }

            is MethodCallFlowFunction.CallToReturnNonDistributiveFact -> {
                val edgeAfterStatement = NDFactToFact(
                    methodEntryPoint, fact.initialFacts, edge.statement, fact.factAp
                )
                handleStatementEdge(edge, edgeAfterStatement)
            }
        }
    }

    private fun propagateNDFactCallFact(
        callExpr: CommonCallExpr,
        edge: NDFactToFact,
        fact: MethodCallFlowFunction.NDFactCallFact,
    ) {
        when (fact) {
            is MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge)
            }

            is MethodCallFlowFunction.Drop -> {
                // do nothing
            }

            is MethodCallFlowFunction.CallToReturnNonDistributiveFact -> {
                val edgeAfterStatement = NDFactToFact(
                    methodEntryPoint, fact.initialFacts, edge.statement, fact.factAp
                )
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToReturnZFact -> {
                val edgeAfterStatement = ZeroToFact(methodEntryPoint, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToStartNDFFact -> {
                val callerEdge = NDFactToFact(methodEntryPoint, fact.initialFacts, edge.statement, fact.callerFactAp)

                val handler = MethodCallHandler.NDFactToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.NDFactToFactHandler(callerEdge, fact.startFactBase)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }
        }
    }

    private fun addInitialZeroEdge() {
        val edge = ZeroToZero(methodEntryPoint, methodEntryPoint.statement)
        addSequentialEdge(edge)
    }

    private fun addInitialZeroToFactEdge(factAp: FinalFactAp) {
        val edge = ZeroToFact(methodEntryPoint, methodEntryPoint.statement, factAp)
        addSequentialEdge(edge)
    }

    private fun addInitialEdge(initialFactAp: InitialFactAp, factAp: FinalFactAp) {
        val edge = FactToFact(methodEntryPoint, initialFactAp, methodEntryPoint.statement, factAp)
        addInitialF2FEdge(edge)
    }

    private fun addInitialF2FEdge(edge: FactToFact) {
        if (edgeExceedLimit(edge)) {
            registerDelayed()
            delayedF2FInitialEdges.add(edge)
            return
        }

        addSequentialEdge(edge)
    }

    private fun registerDelayed() {
        if (containsDelayedEdges) return
        runner.registerDelayedAnalyzer(this)
    }

    override fun updateFactDepthLimit(newLimit: Int) {
        factDepthLimit = newLimit

        val currentDelayedInitialF2F = delayedF2FInitialEdges
        delayedF2FInitialEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)

        val currentDelayedSummaries = delayedF2FSummaries
        delayedF2FSummaries = EdgeCollection.EdgeList(apManager, methodEntryPoint)

        currentDelayedInitialF2F.toList().forEach {
            addInitialF2FEdge(it as FactToFact)
        }

        currentDelayedSummaries.toList().forEach {
            newSummaryEdge(it)
        }
    }

    private fun edgeExceedLimit(edge: FactToFact): Boolean {
        if (edge.initialFactAp.depth > factDepthLimit) return true
        if (edge.factAp.depth > factDepthLimit + 2) return true
        return false
    }

    private fun addSequentialEdge(edge: Edge) {
        edges.add(edge).forEach { newEdge ->
            enqueueNewEdge(newEdge)
        }
    }

    private fun enqueueNewEdge(edge: Edge) {
        unprocessedEdges.add(edge)

        if (!analyzerEnqueued) {
            runner.enqueueMethodAnalyzer(this)
            analyzerEnqueued = true
        }
    }

    private fun handleInputFactChange(originalInputFactAp: InitialFactAp, newInputFactAp: InitialFactAp) {
        if (originalInputFactAp == newInputFactAp) return
        initialFacts.registerNewInitialFact(newInputFactAp, analysisManager.factTypeChecker).forEach { (initialFact, finalFact) ->
            addInitialEdge(initialFact, finalFact)
        }
    }

    private fun handleStatementEdge(edgeBeforeStatement: Edge, edgeAfterStatement: Edge) {
        if (edgeBeforeStatement is FactToFact && edgeAfterStatement is FactToFact) {
            handleInputFactChange(edgeBeforeStatement.initialFactAp, edgeAfterStatement.initialFactAp)
        }

        val edgePostProcessor = analysisManager.getEdgePostProcessor(
            apManager,
            analysisContext,
            methodInstGraph,
            edgeAfterStatement.statement
        )
        val processedEdges = edgePostProcessor?.process(edgeAfterStatement) ?: listOf(edgeAfterStatement)

        processedEdges.forEach { edge ->
            propagateEdge(edge, edgeUnchanged = false)
        }
    }

    private fun handleUnchangedStatementEdge(edge: Edge) {
        val edgePostProcessor = analysisManager.getEdgePostProcessor(
            apManager,
            analysisContext,
            methodInstGraph,
            edge.statement
        )
        val processedEdges = edgePostProcessor?.process(edge) ?: listOf(edge)

        processedEdges.forEach { processedEdge ->
            val edgeUnchanged = processedEdge === edge
            propagateEdge(edge, edgeUnchanged)
        }
    }

    private fun propagateEdge(edge: Edge, edgeUnchanged: Boolean) {
        tryEmmitSummaryEdge(edge)
        propagateEdgeToSuccessors(edge, edgeUnchanged)
    }

    private fun propagateEdgeToSuccessors(edge: Edge, edgeUnchanged: Boolean) {
        methodInstGraph.forEachSuccessor(analysisManager, edge.statement) {
            val nextEdge = edge.replaceStatement(it)
            if (!edgeUnchanged) {
                addSequentialEdge(nextEdge)
            } else {
                if (enqueuedUnchangedEdges.add(nextEdge)) {
                    enqueueNewEdge(nextEdge)
                }
            }
        }
    }

    private fun tryEmmitSummaryEdge(edge: Edge) {
        if (!methodInstGraph.isExitPoint(analysisManager, edge.statement)) return

        val isValidSummaryEdge = when (edge) {
            is ZeroToZero -> true
            is ZeroToFact -> analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
            is FactToFact -> analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
            is NDFactToFact -> analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
        }

        if (isValidSummaryEdge) {
            newSummaryEdge(edge)
        }
    }

    private fun newSummaryEdge(edge: Edge) {
        if (edge is ZeroToZero) {
            runner.addNewSummaryEdges(methodEntryPoint, listOf(edge))
        } else {
            if (edge is FactToFact) {
                if (edgeExceedLimit(edge)) {
                    registerDelayed()
                    delayedF2FSummaries.add(edge)
                    return
                }
            }

            pendingSummaryEdges.add(edge)

            if (!analyzerEnqueued) {
                flushPendingSummaryEdges()
            }
        }
    }

    private fun flushPendingSummaryEdges() {
        if (!pendingSummaryEdges.isEmpty) {
            runner.addNewSummaryEdges(methodEntryPoint, pendingSummaryEdges.toList())
            pendingSummaryEdges = EdgeCollection.EdgeList(apManager, methodEntryPoint)
        }
    }

    private fun flushPendingSideEffectRequirements() {
        if (pendingSideEffectRequirements.isNotEmpty()) {
            runner.addNewSideEffectRequirement(methodEntryPoint, pendingSideEffectRequirements)
            pendingSideEffectRequirements = arrayListOf()
        }
    }

    private fun flushPendingSideEffectSummaries() {
        if (pendingSideEffectSummaries.isNotEmpty()) {
            runner.addNewSideEffectSummaries(methodEntryPoint, pendingSideEffectSummaries)
            pendingSideEffectSummaries = arrayListOf()
        }
    }

    private fun resolveMethodCall(
        callExpr: CommonCallExpr, statement: CommonInst,
        handler: MethodCallHandler, failureHandler: MethodCallResolutionFailureHandler
    ) {
        methodCallResolver.resolveMethodCall(analysisContext, callExpr, statement, handler, failureHandler)
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        for (ep in methodEntryPoints(method)) {
            handleMethodCall(handler, ep)
        }
    }

    override fun handleResolvedMethodCall(entryPoint: MethodEntryPoint, handler: MethodCallHandler) {
        handleMethodCall(handler, entryPoint)
    }

    private fun handleMethodCall(handler: MethodCallHandler, ep: MethodEntryPoint) = when (handler) {
        is MethodCallHandler.ZeroToZeroHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep)

        is MethodCallHandler.ZeroToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)

        is MethodCallHandler.FactToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)

        is MethodCallHandler.NDFactToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)
    }

    override fun handleMethodCallResolutionFailure(
        callExpr: CommonCallExpr,
        handler: MethodCallResolutionFailureHandler
    ) {
        val statement = handler.callerEdge.statement
        val returnValue: CommonValue? = (statement as? CommonAssignInst)?.lhv
        val flowFunction = analysisManager.getMethodCallFlowFunction(
            apManager,
            analysisContext,
            returnValue,
            callExpr,
            statement,
            generateTrace = false,
        )

        when (handler) {
            is MethodCallResolutionFailureHandler.ZeroToZeroHandler -> {
                flowFunction.propagateZeroToZeroResolutionFailure().forEach { fact ->
                    propagateZeroCallFact(callExpr, handler.callerEdge, fact)
                }
            }

            is MethodCallResolutionFailureHandler.ZeroToFactHandler -> {
                flowFunction.propagateZeroToFactResolutionFailure(handler.callerEdge.factAp, handler.startFactBase).forEach { fact ->
                    propagateZeroCallFact(callExpr, handler.callerEdge, fact)
                }
            }

            is MethodCallResolutionFailureHandler.FactToFactHandler -> {
                val edge = handler.callerEdge
                flowFunction.propagateFactToFactResolutionFailure(edge.initialFactAp, edge.factAp, handler.startFactBase).forEach { fact ->
                    propagateFactCallFact(callExpr, handler.callerEdge, fact)
                }
            }

            is MethodCallResolutionFailureHandler.NDFactToFactHandler -> {
                val edge = handler.callerEdge
                flowFunction.propagateNDFactToFactResolutionFailure(edge.initialFacts, edge.factAp, handler.startFactBase).forEach { fact ->
                    propagateNDFactCallFact(callExpr, handler.callerEdge, fact)
                }
            }
        }
    }

    private val methodEntryPointsCache = hashMapOf<MethodWithContext, Array<CommonInst>>()

    private fun methodEntryPoints(method: MethodWithContext): List<MethodEntryPoint> {
        val methodEntryPoints = methodEntryPointsCache.getOrPut(method) {
            val epResolver = analysisManager.getMethodEntrypointResolver(runner.graph)
            epResolver.resolveEntryPoints(method.method, method.ctx).toTypedArray()
        }
        return methodEntryPoints.map { MethodEntryPoint(method.ctx, it) }
    }

    private fun isApplicableExitToReturnEdge(edge: Edge): Boolean {
        return !analysisManager.producesExceptionalControlFlow(edge.statement)
    }

    override fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
    ) {
        val methodInitialFact = currentEdge.factAp.rebase(methodInitialFactBase)
        val exclusionRefinements = methodSideEffectRequirements.mapNotNull { methodSinkRequirement ->
            MethodSummaryEdgeApplicationUtils.emptyDeltaExclusionRefinementOrNull(
                methodInitialFact, methodSinkRequirement
            )
        }

        if (exclusionRefinements.isEmpty()) {
            return
        }

        val sinkRequirementExclusion = exclusionRefinements.fold(ExclusionSet.Empty, ExclusionSet::union)

        if (sinkRequirementExclusion !is ExclusionSet.Empty) {
            val requirement = currentEdge.initialFactAp.replaceExclusions(sinkRequirementExclusion)
            addSideEffectRequirement(currentEdge, requirement)
        }
    }

    override fun handleZeroToZeroMethodSideEffectSummary(
        currentEdge: ZeroToZero,
        sideEffectSummaries: List<SideEffectSummary.ZeroSideEffectSummary>
    ) {
        val handler = analysisManager.getMethodSideEffectSummaryHandler(
            apManager, analysisContext,
            currentEdge.statement,
            runner
        )

        handler.handleZeroToZero(sideEffectSummaries).forEach {
            handleSequentFact(currentEdge, it)
        }
    }

    override fun handleZeroToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val handler = analysisManager.getMethodSideEffectSummaryHandler(
                apManager, analysisContext,
                sub.currentEdge.statement,
                runner
            )

            applyMethodSideEffectSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                sideEffectSummaries = sideEffectSummaries,
                handleSideEffect = handler::handleZeroToFact
            )
        }
    }

    override fun handleFactToFactMethodSideEffectSummary(
        summarySubs: List<FactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val handler = analysisManager.getMethodSideEffectSummaryHandler(
                apManager, analysisContext,
                sub.currentEdge.statement,
                runner,
            )

            applyMethodSideEffectSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                sideEffectSummaries = sideEffectSummaries,
            ) { currentFactAp, summaryEffect, kind ->
                handler.handleFactToFact(sub.currentEdge.initialFactAp, currentFactAp, summaryEffect, kind)
            }
        }
    }

    override fun handleNDFactToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        TODO("ND-side effects are not supported")
    }

    private fun addSideEffectRequirement(currentEdge: FactToFact, sideEffectRequirement: InitialFactAp) {
        addSideEffectRequirement(currentEdge.initialFactAp, sideEffectRequirement)
    }

    private fun addSideEffectRequirement(curInitialFactAp: InitialFactAp, sideEffectRequirement: InitialFactAp) {
        handleInputFactChange(curInitialFactAp, sideEffectRequirement)

        pendingSideEffectRequirements.add(sideEffectRequirement)

        if (!analyzerEnqueued) {
            flushPendingSideEffectRequirements()
        }
    }

    private fun addFactSideEffect(
        currentEdge: Edge,
        initialFactAp: InitialFactAp,
        kind: SideEffectKind,
    ) {
        if (currentEdge is FactToFact) {
            handleInputFactChange(currentEdge.initialFactAp, initialFactAp)
        }

        addSideEffectSummary(SideEffectSummary.FactSideEffectSummary(initialFactAp, kind))
    }

    private fun addZeroSideEffect(kind: SideEffectKind) {
        addSideEffectSummary(SideEffectSummary.ZeroSideEffectSummary(kind))
    }

    private fun addSideEffectSummary(summary: SideEffectSummary) {
        pendingSideEffectSummaries.add(summary)

        if (!analyzerEnqueued) {
            flushPendingSideEffectSummaries()
        }
    }

    override fun handleZeroToZeroMethodSummaryEdge(
        currentEdge: ZeroToZero,
        methodSummaries: List<ZeroInitialEdge>
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }
        val handler = analysisManager.getMethodCallSummaryHandler(
            apManager, analysisContext, currentEdge.statement
        )

        for (methodSummary in applicableSummaries) {
            if (!cancellation.isActive()) return

            val sequentialFacts = when (methodSummary) {
                is ZeroToZero -> handler.handleZeroToZero(summaryFact = null)
                is ZeroToFact -> handler.handleZeroToZero(methodSummary.factAp)
            }
            handleSequentFact(currentEdge, sequentialFacts)
        }
    }

    override fun handleZeroToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, sub.currentEdge.statement
            )

            val summariesToApply = applicableSummaries.flatMap { handler.prepareFactToFactSummary(it) }

            applyMethodSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = summariesToApply,
                handleSummaryEdge = handler::handleZeroToFact
            )
        }
    }

    override fun handleZeroToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        handleMethodNDSummariesSub(
            summarySubs, methodSummaries,
            { currentEdge }, { currentEdge.factAp }, { methodInitialFactBase }
        )
    }

    override fun handleFactToFactMethodSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, sub.currentEdge.statement
            )

            applyMethodSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                handleSummaryEdge = { currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryEdge: SummaryEdge ->
                    handler.handleFactToFact(sub.currentEdge.initialFactAp, currentFactAp, summaryEffect, summaryEdge)
                }
            )
        }
    }

    override fun handleFactToFactMethodNDSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        handleMethodNDSummariesSub(
            summarySubs, methodSummaries,
            { currentEdge }, { currentEdge.factAp }, { methodInitialFactBase }
        )
    }

    override fun handleNDFactToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<FactToFact>,
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, sub.currentEdge.statement
            )

            applyMethodSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                handleSummaryEdge = { currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryEdge: SummaryEdge ->
                    handler.handleNDFactToFact(sub.currentEdge.initialFacts, currentFactAp, summaryEffect, summaryEdge)
                }
            )
        }
    }

    override fun handleNDFactToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        handleMethodNDSummariesSub(
            summarySubs, methodSummaries,
            { currentEdge }, { currentEdge.factAp }, { methodInitialFactBase }
        )
    }

    private fun applyMethodSummaries(
        currentEdge: Edge,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<FactToFact>,
        handleSummaryEdge: (currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryEdge: SummaryEdge) -> Set<Sequent>
    ) {
        applyMethodAnySummaries(
            currentEdge,
            currentEdgeFactAp,
            methodInitialFactBase,
            methodSummaries,
            { it.initialFactAp }
        ) { currentFactAp, summaryEdgeEffect, methodSummary ->
            handleSummaryEdge(currentFactAp, summaryEdgeEffect, methodSummary.summaryEdge())
        }
    }

    private fun applyMethodSideEffectSummaries(
        currentEdge: Edge,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>,
        handleSideEffect: (currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, kind: SideEffectKind) -> Set<Sequent>
    ) {
        applyMethodAnySummaries(
            currentEdge,
            currentEdgeFactAp,
            methodInitialFactBase,
            sideEffectSummaries,
            { it.initialFactAp }
        ) { currentFactAp, summaryEdgeEffect, methodSummary ->
            handleSideEffect(currentFactAp, summaryEdgeEffect, methodSummary.kind)
        }
    }

    private inline fun <S> applyMethodAnySummaries(
        currentEdge: Edge,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<S>,
        getSummaryInitialFact: (S) -> InitialFactAp,
        handleSummary: (currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, S) -> Set<Sequent>
    ) {
        val methodInitialFact = currentEdgeFactAp.rebase(methodInitialFactBase)

        val summaries = methodSummaries.groupByTo(hashMapOf()) { getSummaryInitialFact(it) }
        for ((summaryInitialFact, summaryEdges) in summaries) {
            if (!cancellation.isActive()) return

            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, summaryInitialFact
            )

            for (summaryEdgeEffect in summaryEdgeEffects) {
                for (methodSummary in summaryEdges) {
                    if (!cancellation.isActive()) return

                    val sf = handleSummary(currentEdgeFactAp, summaryEdgeEffect, methodSummary)
                    handleSequentFact(currentEdge, sf)
                }
            }
        }
    }

    private inline fun <Sub> handleMethodNDSummariesSub(
        summarySubs: List<Sub>,
        methodSummaries: List<NDFactToFact>,
        subEdge: Sub.() -> Edge,
        subFact: Sub.() -> FinalFactAp,
        subInitialFactBase: Sub.() -> AccessPathBase,
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            if (!cancellation.isActive()) return

            val currentEdge = sub.subEdge()

            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, currentEdge.statement
            )

            val summariesToApply = applicableSummaries.flatMap { handler.prepareNDFactToFactSummary(it) }

            applyMethodNDSummaries(
                summaryHandler = handler,
                currentEdge = currentEdge,
                currentEdgeFactAp = sub.subFact(),
                methodInitialFactBase = sub.subInitialFactBase(),
                methodSummaries = summariesToApply,
            )
        }
    }

    private fun applyMethodNDSummaries(
        summaryHandler: MethodCallSummaryHandler,
        currentEdge: Edge,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<NDFactToFact>,
    ) {
        val methodInitialFact = currentEdgeFactAp.rebase(methodInitialFactBase)

        nextSummary@for (summaryEdge in methodSummaries) {
            if (!cancellation.isActive()) return

            val requiredFacts = mutableListOf<InitialFactAp>()
            for (summaryInitialFact in summaryEdge.initialFacts) {
                if (!methodInitialFact.matchNDInitial(summaryInitialFact)) {
                    requiredFacts.add(summaryInitialFact)
                }
            }

            if (requiredFacts.size == summaryEdge.initialFacts.size) continue

            val requiredInitials = mutableListOf<List<Set<InitialFactAp>>>()
            for (requiredFact in requiredFacts) {

                val searcher = object : MethodAnalyzerEdgeSearcher(
                    edges, apManager, analysisManager, analysisContext, methodInstGraph
                ) {
                    override fun matchFact(factAtStatement: FinalFactAp, targetFactPattern: InitialFactAp): Boolean =
                        factAtStatement.rebase(requiredFact.base).matchNDInitial(requiredFact)
                }

                val mappedRequiredFacts = analysisContext.methodCallFactMapper.mapMethodExitToReturnFlowFact(
                    currentEdge.statement, requiredFact
                )

                val factInitials = mappedRequiredFacts.flatMapTo(hashSetOf()) {
                    searcher.findMatchingEdgesInitialFacts(currentEdge.statement, it)
                }

                if (factInitials.isEmpty()) {
                    continue@nextSummary
                }

                requiredInitials.add(factInitials.toList())
            }

            requiredInitials.cartesianProductMapTo { initialFactGroup ->
                if (!cancellation.isActive()) return

                val ndSummaryInitial = initialFactGroup.flatMapTo(hashSetOf()) { it }

                val sf = when (currentEdge) {
                    is ZeroToZero -> error("Impossible")

                    is ZeroToFact -> when {
                        ndSummaryInitial.isEmpty() -> {
                            summaryHandler.handleZeroToFact(
                                currentEdgeFactAp,
                                SummaryExclusionRefinement(ExclusionSet.Universe),
                                summaryEdge.summaryEdge()
                            )
                        }

                        ndSummaryInitial.size == 1 -> {
                            val initialFact = ndSummaryInitial.first()
                            summaryHandler.handleFactToFact(
                                initialFact,
                                currentEdgeFactAp,
                                SummaryExclusionRefinement(initialFact.exclusions),
                                summaryEdge.summaryEdge()
                            )
                        }

                        else -> {
                            summaryHandler.handleNDFactToFact(
                                ndSummaryInitial,
                                currentEdgeFactAp,
                                SummaryExclusionRefinement(ExclusionSet.Universe),
                                summaryEdge.summaryEdge()
                            )
                        }
                    }

                    is FactToFact -> {
                        ndSummaryInitial.add(currentEdge.initialFactAp)

                        when (ndSummaryInitial.size) {
                            1 -> {
                                summaryHandler.handleFactToFact(
                                    currentEdge.initialFactAp,
                                    currentEdgeFactAp,
                                    SummaryExclusionRefinement(currentEdge.initialFactAp.exclusions),
                                    summaryEdge.summaryEdge()
                                )
                            }

                            else -> {
                                summaryHandler.handleNDFactToFact(
                                    ndSummaryInitial,
                                    currentEdgeFactAp,
                                    SummaryExclusionRefinement(ExclusionSet.Universe),
                                    summaryEdge.summaryEdge()
                                )
                            }
                        }
                    }

                    is NDFactToFact -> {
                        summaryHandler.handleNDFactToFact(
                            ndSummaryInitial + currentEdge.initialFacts,
                            currentEdgeFactAp,
                            SummaryExclusionRefinement(ExclusionSet.Universe),
                            summaryEdge.summaryEdge()
                        )
                    }
                }

                val applicableSf = sf.filter { it !is Sequent.SideEffectRequirement }
                handleSequentFact(currentEdge, applicableSf)
            }
        }
    }

    private fun FinalFactAp.matchNDInitial(initialFactAp: InitialFactAp): Boolean {
        val exclusion = MethodSummaryEdgeApplicationUtils.emptyDeltaExclusionRefinementOrNull(this, initialFactAp)
            ?: return false

        check(exclusion is ExclusionSet.Universe) {
            "ND-summary with non-universe exclusion"
        }

        return true
    }

    override fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        facts: Set<InitialFactAp>,
        includeStatement: Boolean
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, analysisContext, edges, methodInstGraph)
        return resolver.resolveIntraProceduralTrace(statement, facts, includeStatement)
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, analysisContext, edges, methodInstGraph)
        return resolver.resolveIntraProceduralTraceFromCall(statement, calleeEntry)
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: Cancellation,
        collapseUnchangedNodes: Boolean,
    ): List<MethodTraceResolver.FullTrace> {
        val resolver = MethodTraceResolver(runner, analysisContext, edges, methodInstGraph)
        val (fullTrace, steps) = resolver.resolveIntraProceduralFullTrace(summaryTrace, cancellation, collapseUnchangedNodes)
        traceResolverSteps += steps
        return fullTrace
    }

    override fun resolveIntraProceduralForwardFullTrace(
        statement: CommonInst,
        fact: FinalFactAp,
        includeStatement: Boolean,
        relevantFactFilter: RelevantFactFilter
    ): TraceGraph {
        val resolver = MethodForwardTraceResolver(runner, analysisContext, methodInstGraph)
        return resolver.resolveForwardTrace(statement, fact, includeStatement, relevantFactFilter)
    }

    override fun resolveCalleeFact(statement: CommonInst, factAp: FinalFactAp): Set<FinalFactAp> =
        analysisContext.methodCallFactMapper.mapMethodExitToReturnFlowFact(
            statement, factAp, FactTypeChecker.Dummy
        ).toSet()

    private fun updateTaintRulesStats(
        finalEdgeFact: FinalFactAp?,
        taintRulesStatsSamplingPeriod: Int
    ) {
        if (finalEdgeFact == null) return
        if (analyzerSteps % taintRulesStatsSamplingPeriod.toLong() != 0L) return

        val taintMarks = finalEdgeFact.collectTaintMarks()
        taintMarks.forEach { taintMark ->
            stepsForTaintMark.compute(taintMark) { _, prev ->
                prev?.let { it + 1 } ?: 1
            }
        }
    }

    private fun FactToFact.summaryEdge() = SummaryEdge.F2F(initialFactAp, factAp)
    private fun NDFactToFact.summaryEdge() = SummaryEdge.NdF2F(initialFacts, factAp)

    companion object {
        const val INITIAL_ALLOWED_FACT_DEPTH = 3
        const val DEBUG_ANALYSIS_TIME = false
    }
}

class EmptyMethodAnalyzer(
    private val runner: AnalysisRunner,
    override val methodEntryPoint: MethodEntryPoint
) : MethodAnalyzer {
    private var zeroInitialFactProcessed: Boolean = false
    private val taintedInitialFacts = hashSetOf<AccessPathBase>()
    private val apManager: ApManager get() = runner.apManager

    override fun addInitialZeroFact() {
        if (!zeroInitialFactProcessed) {
            zeroInitialFactProcessed = true
            runner.addNewSummaryEdges(
                methodEntryPoint,
                listOf(ZeroToZero(methodEntryPoint, methodEntryPoint.statement))
            )
        }
    }

    override fun addInitialFact(factAp: FinalFactAp) {
        addSummary(factAp.base)
    }

    override fun triggerSideEffectRequirement(sideEffectRequirement: InitialFactAp) {
        // do nothing
    }

    private fun addSummary(base: AccessPathBase) {
        if (!taintedInitialFacts.add(base)) return

        val initialFactAp = apManager.mostAbstractInitialAp(base)
        val factAp = apManager.mostAbstractFinalAp(base)

        runner.addNewSummaryEdges(
            methodEntryPoint,
            listOf(FactToFact(methodEntryPoint, initialFactAp, methodEntryPoint.statement, factAp))
        )
    }

    override val analyzerSteps: Long = 0

    override fun collectStats(stats: MethodStats) {
        // No stats
    }

    override val containsUnprocessedEdges: Boolean
        get() = false

    override val containsDelayedEdges: Boolean
        get() = false

    override fun updateFactDepthLimit(newLimit: Int) {
        // do nothing
    }

    override fun tabulationAlgorithmStep() {
        error("Empty method should not perform steps")
    }

    override fun handleFactToFactMethodSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleZeroToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleZeroToZeroMethodSummaryEdge(
        currentEdge: ZeroToZero,
        methodSummaries: List<ZeroInitialEdge>
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleNDFactToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<FactToFact>,
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleZeroToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleFactToFactMethodNDSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleNDFactToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) {
        error("Empty method should not receive summary edges")
    }

    override fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
    ) {
        error("Empty method should not receive side effect requirements")
    }

    override fun handleZeroToZeroMethodSideEffectSummary(
        currentEdge: ZeroToZero,
        sideEffectSummaries: List<SideEffectSummary.ZeroSideEffectSummary>
    ) {
        error("Empty method should not receive side effects")
    }

    override fun handleZeroToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        error("Empty method should not receive side effects")
    }

    override fun handleFactToFactMethodSideEffectSummary(
        summarySubs: List<FactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        error("Empty method should not receive side effects")
    }

    override fun handleNDFactToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>
    ) {
        error("Empty method should not receive side effects")
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        error("Empty method should not method resolution results")
    }

    override fun handleResolvedMethodCall(entryPoint: MethodEntryPoint, handler: MethodCallHandler) {
        error("Empty method should not method resolution results")
    }

    override fun handleMethodCallResolutionFailure(callExpr: CommonCallExpr, handler: MethodCallResolutionFailureHandler) {
        error("Empty method should not method resolution results")
    }

    override fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        facts: Set<InitialFactAp>,
        includeStatement: Boolean
    ): List<MethodTraceResolver.SummaryTrace> {
        TODO("Not yet implemented")
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: Cancellation,
        collapseUnchangedNodes: Boolean,
    ): List<MethodTraceResolver.FullTrace> {
        TODO("Not yet implemented")
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        error("Empty method have no calls")
    }

    override fun resolveIntraProceduralForwardFullTrace(
        statement: CommonInst,
        fact: FinalFactAp,
        includeStatement: Boolean,
        relevantFactFilter: RelevantFactFilter
    ): TraceGraph {
        TODO("Not yet implemented")
    }

    override fun resolveCalleeFact(statement: CommonInst, factAp: FinalFactAp): Set<FinalFactAp> {
        TODO("Not yet implemented")
    }

    override fun allIntraProceduralFacts(): Map<CommonInst, Set<FinalFactAp>> = emptyMap()
}

class TimedMethodAnalyzer(
    private val base: MethodAnalyzer,
) : MethodAnalyzer {

    private enum class OpCategory {
        TRACE,
        SUMMARY,
        STEP,
        CALL,
        OTHER,
    }

    private var totalAnalysisTime: Long = 0
    private var traceResolutionTimeNanos: Long = 0
    private var summaryHandlingTimeNanos: Long = 0
    private var stepTimeNanos: Long = 0
    private var otherTimeNanos: Long = 0
    private var callTimeNanos: Long = 0

    private val operationTime = Object2LongOpenHashMap<String>()

    private inline fun <T> timeOperation(
        operation: String,
        category: OpCategory,
        addToTotalTime: Boolean = true,
        block: () -> T,
    ): T {
        var result: T
        val time = measureNanoTime { result = block() }

        operationTime.addTo(operation, time)

        when (category) {
            OpCategory.TRACE -> traceResolutionTimeNanos += time
            OpCategory.SUMMARY -> summaryHandlingTimeNanos += time
            OpCategory.STEP -> stepTimeNanos += time
            OpCategory.OTHER -> otherTimeNanos += time
            OpCategory.CALL -> callTimeNanos += time
        }

        if (addToTotalTime) {
            totalAnalysisTime += time
        }

        return result
    }

    override fun collectStats(stats: MethodStats) {
        base.collectStats(stats)

        stats.stats(methodEntryPoint.method).apply {
            analysisTime += totalAnalysisTime
            stepTime += stepTimeNanos
            summaryTime += summaryHandlingTimeNanos
            otherTime += otherTimeNanos
            callTime += callTimeNanos
        }
    }

    override val methodEntryPoint: MethodEntryPoint
        get() = base.methodEntryPoint

    override val containsUnprocessedEdges: Boolean
        get() = base.containsUnprocessedEdges

    override val containsDelayedEdges: Boolean
        get() = base.containsDelayedEdges

    override val analyzerSteps: Long
        get() = base.analyzerSteps

    override fun updateFactDepthLimit(newLimit: Int) {
        base.updateFactDepthLimit(newLimit)
    }

    override fun addInitialZeroFact() = timeOperation(
        operation = "addInitialZeroFact",
        category = OpCategory.OTHER,
    ) {
        base.addInitialZeroFact()
    }

    override fun addInitialFact(factAp: FinalFactAp) = timeOperation(
        operation = "addInitialFact",
        category = OpCategory.OTHER,
    ) {
        base.addInitialFact(factAp)
    }

    override fun triggerSideEffectRequirement(sideEffectRequirement: InitialFactAp) = timeOperation(
        operation = "triggerSideEffectRequirement",
        category = OpCategory.OTHER,
    ) {
        base.triggerSideEffectRequirement(sideEffectRequirement)
    }

    override fun tabulationAlgorithmStep() = timeOperation(
        operation = "tabulationAlgorithmStep",
        category = OpCategory.STEP,
    ) {
        base.tabulationAlgorithmStep()
    }

    override fun handleZeroToZeroMethodSummaryEdge(
        currentEdge: ZeroToZero,
        methodSummaries: List<ZeroInitialEdge>,
    ) = timeOperation(
        operation = "handleZeroToZeroMethodSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleZeroToZeroMethodSummaryEdge(currentEdge, methodSummaries)
    }

    override fun handleZeroToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<FactToFact>,
    ) = timeOperation(
        operation = "handleZeroToFactMethodSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleZeroToFactMethodSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleFactToFactMethodSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<FactToFact>,
    ) = timeOperation(
        operation = "handleFactToFactMethodSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleFactToFactMethodSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleNDFactToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<FactToFact>,
    ) = timeOperation(
        operation = "handleNDFactToFactMethodSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleNDFactToFactMethodSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleZeroToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) = timeOperation(
        operation = "handleZeroToFactMethodNDSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleZeroToFactMethodNDSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleFactToFactMethodNDSummaryEdge(
        summarySubs: List<FactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) = timeOperation(
        operation = "handleFactToFactMethodNDSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleFactToFactMethodNDSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleNDFactToFactMethodNDSummaryEdge(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        methodSummaries: List<NDFactToFact>,
    ) = timeOperation(
        operation = "handleNDFactToFactMethodNDSummaryEdge",
        category = OpCategory.SUMMARY,
    ) {
        base.handleNDFactToFactMethodNDSummaryEdge(summarySubs, methodSummaries)
    }

    override fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>,
    ) = timeOperation(
        operation = "handleMethodSideEffectRequirement",
        category = OpCategory.SUMMARY,
    ) {
        base.handleMethodSideEffectRequirement(currentEdge, methodInitialFactBase, methodSideEffectRequirements)
    }

    override fun handleZeroToZeroMethodSideEffectSummary(
        currentEdge: ZeroToZero,
        sideEffectSummaries: List<SideEffectSummary.ZeroSideEffectSummary>,
    ) = timeOperation(
        operation = "handleZeroToZeroMethodSideEffectSummary",
        category = OpCategory.SUMMARY,
    ) {
        base.handleZeroToZeroMethodSideEffectSummary(currentEdge, sideEffectSummaries)
    }

    override fun handleZeroToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>,
    ) = timeOperation(
        operation = "handleZeroToFactMethodSideEffectSummary",
        category = OpCategory.SUMMARY,
    ) {
        base.handleZeroToFactMethodSideEffectSummary(summarySubs, sideEffectSummaries)
    }

    override fun handleFactToFactMethodSideEffectSummary(
        summarySubs: List<FactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>,
    ) = timeOperation(
        operation = "handleFactToFactMethodSideEffectSummary",
        category = OpCategory.SUMMARY,
    ) {
        base.handleFactToFactMethodSideEffectSummary(summarySubs, sideEffectSummaries)
    }

    override fun handleNDFactToFactMethodSideEffectSummary(
        summarySubs: List<MethodAnalyzer.NDFactToFactSub>,
        sideEffectSummaries: List<SideEffectSummary.FactSideEffectSummary>,
    ) = timeOperation(
        operation = "handleNDFactToFactMethodSideEffectSummary",
        category = OpCategory.SUMMARY,
    ) {
        base.handleNDFactToFactMethodSideEffectSummary(summarySubs, sideEffectSummaries)
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) = timeOperation(
        operation = "handleResolvedMethodCall(method)",
        category = OpCategory.CALL,
    ) {
        base.handleResolvedMethodCall(method, handler)
    }

    override fun handleResolvedMethodCall(entryPoint: MethodEntryPoint, handler: MethodCallHandler) = timeOperation(
        operation = "handleResolvedMethodCall(entryPoint)",
        category = OpCategory.CALL,
    ) {
        base.handleResolvedMethodCall(entryPoint, handler)
    }

    override fun handleMethodCallResolutionFailure(
        callExpr: CommonCallExpr,
        handler: MethodCallResolutionFailureHandler,
    ) = timeOperation(
        operation = "handleMethodCallResolutionFailure",
        category = OpCategory.CALL,
    ) {
        base.handleMethodCallResolutionFailure(callExpr, handler)
    }

    override fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        facts: Set<InitialFactAp>,
        includeStatement: Boolean,
    ): List<MethodTraceResolver.SummaryTrace> = timeOperation(
        operation = "resolveIntraProceduralTraceSummary",
        category = OpCategory.TRACE,
        addToTotalTime = false,
    ) {
        base.resolveIntraProceduralTraceSummary(statement, facts, includeStatement)
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry,
    ): List<MethodTraceResolver.SummaryTrace> = timeOperation(
        operation = "resolveIntraProceduralTraceSummaryFromCall",
        category = OpCategory.TRACE,
        addToTotalTime = false,
    ) {
        base.resolveIntraProceduralTraceSummaryFromCall(statement, calleeEntry)
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: Cancellation,
        collapseUnchangedNodes: Boolean,
    ): List<MethodTraceResolver.FullTrace> = timeOperation(
        operation = "resolveIntraProceduralFullTrace",
        category = OpCategory.TRACE,
        addToTotalTime = false,
    ) {
        base.resolveIntraProceduralFullTrace(summaryTrace, cancellation, collapseUnchangedNodes)
    }

    override fun resolveIntraProceduralForwardFullTrace(
        statement: CommonInst,
        fact: FinalFactAp,
        includeStatement: Boolean,
        relevantFactFilter: RelevantFactFilter,
    ): TraceGraph = timeOperation(
        operation = "resolveIntraProceduralForwardFullTrace",
        category = OpCategory.TRACE,
        addToTotalTime = false,
    ) {
        base.resolveIntraProceduralForwardFullTrace(statement, fact, includeStatement, relevantFactFilter)
    }

    override fun resolveCalleeFact(
        statement: CommonInst,
        factAp: FinalFactAp,
    ): Set<FinalFactAp> = timeOperation(
        operation = "resolveCalleeFact",
        category = OpCategory.TRACE,
        addToTotalTime = false,
    ) {
        base.resolveCalleeFact(statement, factAp)
    }

    override fun allIntraProceduralFacts(): Map<CommonInst, Set<FinalFactAp>> = timeOperation(
        operation = "allIntraProceduralFacts",
        category = OpCategory.OTHER,
        addToTotalTime = false,
    ) {
        base.allIntraProceduralFacts()
    }
}

private fun FinalFactAp.collectTaintMarks(): Set<String> {
    val taintMarkGatherer = TaintMarkGatherer()
    filterFact(taintMarkGatherer)
    return taintMarkGatherer.marks
}

private class TaintMarkGatherer: FactTypeChecker.FactApFilter {
    private val visitedMarks = hashSetOf<String>()

    val marks: Set<String>
        get() = visitedMarks

    override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
        return when (accessor) {
            is TaintMarkAccessor -> FactTypeChecker.FilterResult.Reject.also {
                visitedMarks.add(accessor.mark)
            }
            is FinalAccessor -> FactTypeChecker.FilterResult.Reject
            else -> FactTypeChecker.FilterResult.FilterNext(this)
        }
    }
}