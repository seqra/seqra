package org.opentaint.dataflow.ap.ifds

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroInitialEdge
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToZero
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallResolutionFailureHandler
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.ap.ifds.analysis.MethodStartFlowFunction.StartFact
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolverCancellation
import org.opentaint.dataflow.graph.ApplicationGraph

interface MethodAnalyzer {
    fun addInitialZeroFact()

    fun addInitialFact(factAp: FinalFactAp)

    val containsUnprocessedEdges: Boolean

    fun tabulationAlgorithmStep()

    fun handleZeroToZeroMethodSummaryEdge(currentEdge: ZeroToZero, methodSummaries: List<ZeroInitialEdge>)

    fun handleZeroToFactMethodSummaryEdge(summarySubs: List<ZeroToFactSub>, methodSummaries: List<FactToFact>)

    fun handleFactToFactMethodSummaryEdge(summarySubs: List<FactToFactSub>, methodSummaries: List<FactToFact>)

    fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
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

    fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler)

    fun handleMethodCallResolutionFailure(
        callExpr: CommonCallExpr,
        handler: MethodCallResolutionFailureHandler
    )

    fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        factAp: InitialFactAp
    ): List<MethodTraceResolver.SummaryTrace>

    fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace>

    fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: TraceResolverCancellation
    ): MethodTraceResolver.FullTrace

    sealed interface MethodCallHandler {
        data class ZeroToZeroHandler(val currentEdge: ZeroToZero) : MethodCallHandler
        data class ZeroToFactHandler(val currentEdge: ZeroToFact, val startFactBase: AccessPathBase) : MethodCallHandler
        data class FactToFactHandler(val currentEdge: FactToFact, val startFactBase: AccessPathBase) : MethodCallHandler
    }

    sealed interface MethodCallResolutionFailureHandler {
        data class ZeroToZeroHandler(val edge: ZeroToZero) : MethodCallResolutionFailureHandler
        data class ZeroToFactHandler(val edge: ZeroInitialEdge, val callerFactAp: FinalFactAp) : MethodCallResolutionFailureHandler
        data class FactToFactHandler(val callerEdge: FactToFact, val callerFactAp: FinalFactAp): MethodCallResolutionFailureHandler
    }
}

class NormalMethodAnalyzer(
    private val runner: AnalysisRunner,
    private val methodEntryPoint: MethodEntryPoint,
    private val taintRulesStatsSamplingPeriod: Int?
) : MethodAnalyzer {
    private val graph: ApplicationGraph<CommonMethod, CommonInst> get() = runner.graph
    private val apManager: ApManager get() = runner.apManager
    private val analysisManager get() = runner.analysisManager
    private val methodCallResolver get() = runner.methodCallResolver

    private var zeroInitialFactProcessed: Boolean = false
    private val initialFacts = apManager.initialFactAbstraction(methodEntryPoint.statement)
    private val edges = MethodAnalyzerEdges(apManager, methodEntryPoint, analysisManager)
    private var pendingSummaryEdges = arrayListOf<Edge>()
    private var pendingSideEffectRequirements = arrayListOf<InitialFactAp>()

    private val analysisContext = analysisManager.getMethodAnalysisContext(methodEntryPoint, graph)

    private var analyzerEnqueued = false
    private var unprocessedEdges = arrayListOf<Edge>()
    private var enqueuedUnchangedEdges = ObjectOpenHashSet<Edge>()

    override val containsUnprocessedEdges: Boolean
        get() = unprocessedEdges.isNotEmpty()

    override var analyzerSteps: Long = 0
        private set

    private val stepsForTaintMark: MutableMap<String, Long> = hashMapOf()

    private var summaryEdgesHandled: Long = 0

    private val methodExitPoints by lazy { graph.exitPoints(methodEntryPoint.method).toHashSet() }

    init {
        loadSummariesFromRunner()
    }

    private fun loadSummariesFromRunner() {
        runner.getPrecalculatedSummaries(methodEntryPoint)?.let { (summaryEdges, requirements) ->
            runner.addNewSummaryEdges(methodEntryPoint, summaryEdges)
            runner.addNewSideEffectRequirement(methodEntryPoint, requirements)
            summaryEdges.forEach { edge ->
                when (edge) {
                    is FactToFact -> initialFacts.registerNewInitialFact(edge.initialFactAp)
                    is ZeroToFact -> zeroInitialFactProcessed = true
                    is ZeroToZero -> zeroInitialFactProcessed = true
                }
            }
        }
    }

    override fun collectStats(stats: MethodStats) {
        stats.stats(methodEntryPoint.method).apply {
            steps += analyzerSteps
            handledSummaries += summaryEdgesHandled
            unprocessedEdges += this@NormalMethodAnalyzer.unprocessedEdges.size
            coveredInstructions.or(edges.reachedStatements())
            this@NormalMethodAnalyzer.stepsForTaintMark.forEach { (mark, count) ->
                stepsForTaintMark.compute(mark) { _, prev ->
                    prev?.let { it + count } ?: count
                }
            }
        }
    }

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
            initialFacts.addAbstractedInitialFact(startFact.fact).forEach { (initialFact, finalFact) ->
                addInitialEdge(initialFact, finalFact)
            }
        }
    }

    override fun tabulationAlgorithmStep() {
        analyzerSteps++

        val edge = unprocessedEdges.removeLast()

        val finalEdgeFact = when (edge) {
            is ZeroToZero -> null
            is ZeroToFact -> edge.factAp
            is FactToFact -> edge.factAp
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

        if (unprocessedEdges.isNotEmpty()) return

        analyzerEnqueued = false

        // Create new empty list to shrink internal array
        unprocessedEdges = arrayListOf()
        enqueuedUnchangedEdges = ObjectOpenHashSet()

        flushPendingSummaryEdges()
        flushPendingSideEffectRequirements()
    }

    private fun simpleStatementStep(edge: Edge) {
        // Simple (sequential) propagation to the next instruction:
        val flowFunction = analysisManager.getMethodSequentFlowFunction(apManager, analysisContext, edge.statement)
        val sequentialFacts = when (edge) {
            is ZeroToZero -> flowFunction.propagateZeroToZero()
            is ZeroToFact -> flowFunction.propagateZeroToFact(edge.factAp)
            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFactAp, edge.factAp)
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
            is Sequent.SideEffectRequirement -> {
                check(edge is FactToFact) { "Initial fact required for side effect" }
                addSideEffectRequirement(edge, sf.initialFactAp)
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
        }
    }

    private fun propagateZeroCallFact(
        callExpr: CommonCallExpr,
        edge: ZeroInitialEdge,
        fact: ZeroCallFact
    ) {
        when (fact) {
            MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge)
            }

            MethodCallFlowFunction.CallToReturnZeroFact -> {
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
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToFactHandler(edge, fact.callerFactAp)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }
        }
    }

    private fun propagateFactCallFact(
        callExpr: CommonCallExpr,
        edge: FactToFact,
        fact: MethodCallFlowFunction.FactCallFact
    ) {
        when (fact) {
            MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge)
            }

            is MethodCallFlowFunction.CallToReturnFFact -> {
                val edgeAfterStatement = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToStartFFact -> {
                val callerEdge = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.callerFactAp)

                handleInputFactChange(edge.initialFactAp, callerEdge.initialFactAp)

                val handler = MethodCallHandler.FactToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.FactToFactHandler(callerEdge, fact.callerFactAp)
                resolveMethodCall(callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.SideEffectRequirement -> {
                addSideEffectRequirement(edge, fact.initialFactAp)
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
        addSequentialEdge(edge)
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
        initialFacts.registerNewInitialFact(newInputFactAp).forEach { (initialFact, finalFact) ->
            addInitialEdge(initialFact, finalFact)
        }
    }

    private fun handleStatementEdge(edgeBeforeStatement: Edge, edgeAfterStatement: Edge) {
        if (edgeBeforeStatement is FactToFact && edgeAfterStatement is FactToFact) {
            handleInputFactChange(edgeBeforeStatement.initialFactAp, edgeAfterStatement.initialFactAp)
        }

        tryEmmitSummaryEdge(edgeAfterStatement)
        propagateEdgeToSuccessors(edgeAfterStatement, edgeUnchanged = false)
    }

    private fun handleUnchangedStatementEdge(edge: Edge) {
        tryEmmitSummaryEdge(edge)
        propagateEdgeToSuccessors(edge, edgeUnchanged = true)
    }

    private fun propagateEdgeToSuccessors(edge: Edge, edgeUnchanged: Boolean) {
        graph.successors(edge.statement).forEach {
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
        if (edge.statement !in methodExitPoints) return

        val isValidSummaryEdge = when (edge) {
            is ZeroToZero -> true
            is ZeroToFact -> analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
            is FactToFact -> analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
        }

        if (isValidSummaryEdge) {
            newSummaryEdge(edge)
        }
    }

    private fun newSummaryEdge(edge: Edge) {
        if (edge is ZeroToZero) {
            runner.addNewSummaryEdges(methodEntryPoint, listOf(edge))
        } else {
            pendingSummaryEdges.add(edge)

            if (!analyzerEnqueued) {
                flushPendingSummaryEdges()
            }
        }
    }

    private fun flushPendingSummaryEdges() {
        if (pendingSummaryEdges.isNotEmpty()) {
            runner.addNewSummaryEdges(methodEntryPoint, pendingSummaryEdges)
            pendingSummaryEdges = arrayListOf()
        }
    }

    private fun flushPendingSideEffectRequirements() {
        if (pendingSideEffectRequirements.isNotEmpty()) {
            runner.addNewSideEffectRequirement(methodEntryPoint, pendingSideEffectRequirements)
            pendingSideEffectRequirements = arrayListOf()
        }
    }

    private fun resolveMethodCall(
        callExpr: CommonCallExpr, statement: CommonInst,
        handler: MethodCallHandler, failureHandler: MethodCallResolutionFailureHandler
    ) {
        methodCallResolver.resolveMethodCall(methodEntryPoint, callExpr, statement, handler, failureHandler)
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        for (ep in methodEntryPoints(method)) {
            handleMethodCall(handler, ep)
        }
    }

    private fun handleMethodCall(handler: MethodCallHandler, ep: MethodEntryPoint) = when (handler) {
        is MethodCallHandler.ZeroToZeroHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep)

        is MethodCallHandler.ZeroToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)

        is MethodCallHandler.FactToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)
    }

    override fun handleMethodCallResolutionFailure(
        callExpr: CommonCallExpr,
        handler: MethodCallResolutionFailureHandler
    ) = when (handler) {
        is MethodCallResolutionFailureHandler.ZeroToZeroHandler -> {
            // If no callees resolved propagate as call-to-return
            handleStatementEdge(handler.edge, ZeroToZero(methodEntryPoint, handler.edge.statement))
        }

        is MethodCallResolutionFailureHandler.ZeroToFactHandler -> {
            // If no callees resolved propagate as call-to-return
            val stubFact = MethodCallFlowFunction.CallToReturnZFact(handler.callerFactAp)
            propagateZeroCallFact(callExpr, handler.edge, stubFact)
        }

        is MethodCallResolutionFailureHandler.FactToFactHandler -> {
            // If no callees resolved propagate as call-to-return
            val stubFact = MethodCallFlowFunction.CallToReturnFFact(handler.callerEdge.initialFactAp, handler.callerFactAp)
            propagateFactCallFact(callExpr, handler.callerEdge, stubFact)
        }
    }

    private fun methodEntryPoints(method: MethodWithContext): Sequence<MethodEntryPoint> =
        graph.entryPoints(method.method).map { MethodEntryPoint(method.ctx, it) }

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

    private fun addSideEffectRequirement(currentEdge: FactToFact, sideEffectRequirement: InitialFactAp) {
        handleInputFactChange(currentEdge.initialFactAp, sideEffectRequirement)

        pendingSideEffectRequirements.add(sideEffectRequirement)

        if (!analyzerEnqueued) {
            flushPendingSideEffectRequirements()
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
            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, sub.currentEdge.statement
            )

            applyMethodSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                handleSummaryEdge = handler::handleZeroToFact
            )
        }
    }

    override fun handleFactToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.FactToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            val handler = analysisManager.getMethodCallSummaryHandler(
                apManager, analysisContext, sub.currentEdge.statement
            )

            applyMethodSummaries(
                currentEdge = sub.currentEdge,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                handleSummaryEdge = { currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryFact: FinalFactAp ->
                    handler.handleFactToFact(sub.currentEdge.initialFactAp, currentFactAp, summaryEffect, summaryFact)
                }
            )
        }
    }

    private fun applyMethodSummaries(
        currentEdge: Edge,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<FactToFact>,
        handleSummaryEdge: (currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryFact: FinalFactAp) -> Set<Sequent>
    ) {
        val summaries = methodSummaries.groupByTo(hashMapOf()) { it.initialFactAp }
        for ((summaryInitialFact, summaryEdges) in summaries) {
            val methodInitialFact = currentEdgeFactAp.rebase(methodInitialFactBase)
            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, summaryInitialFact
            )

            for (summaryEdgeEffect in summaryEdgeEffects) {
                for (methodSummary in summaryEdges) {
                    val sf = handleSummaryEdge(currentEdgeFactAp, summaryEdgeEffect, methodSummary.factAp)
                    handleSequentFact(currentEdge, sf)
                }
            }
        }
    }

    override fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        factAp: InitialFactAp
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, analysisContext, edges)
        return resolver.resolveIntraProceduralTrace(statement, factAp)
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, analysisContext, edges)
        return resolver.resolveIntraProceduralTraceFromCall(statement, calleeEntry)
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: TraceResolverCancellation
    ): MethodTraceResolver.FullTrace {
        val resolver = MethodTraceResolver(runner, analysisContext, edges)
        return resolver.resolveIntraProceduralFullTrace(summaryTrace, cancellation)
    }

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
}

class EmptyMethodAnalyzer(
    private val runner: AnalysisRunner,
    private val methodEntryPoint: MethodEntryPoint
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

    override fun tabulationAlgorithmStep() {
        error("Empty method should not perform steps")
    }

    override fun handleFactToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.FactToFactSub>,
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

    override fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
    ) {
        error("Empty method should not receive sink requirements")
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        error("Empty method should not method resolution results")
    }

    override fun handleMethodCallResolutionFailure(callExpr: CommonCallExpr, handler: MethodCallResolutionFailureHandler) {
        error("Empty method should not method resolution results")
    }

    override fun resolveIntraProceduralTraceSummary(statement: CommonInst, factAp: InitialFactAp): List<MethodTraceResolver.SummaryTrace> {
        TODO("Not yet implemented")
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: TraceResolverCancellation
    ): MethodTraceResolver.FullTrace {
        TODO("Not yet implemented")
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        error("Empty method have no calls")
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