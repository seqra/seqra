package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroInitialEdge
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToZero
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodTraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolverCancellation
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.ap.ifds.MethodAnalyzer.MethodCallResolutionFailureHandler
import org.opentaint.dataflow.ap.ifds.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.graph.statementFilteredTraverse
import org.opentaint.util.onSome

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
    private val methodEntryPoint: MethodEntryPoint
) : MethodAnalyzer {
    private val graph: ApplicationGraph<CommonMethod, CommonInst> get() = runner.graph
    private val taintConfig: TaintRulesProvider get() = runner.taintConfiguration
    private val taintSinkTracker: TaintSinkTracker get() = runner.sinkTracker
    private val apManager: ApManager get() = runner.apManager
    private val languageManager get() = runner.languageManager
    private val methodCallResolver get() = runner.methodCallResolver
    private val methodCallFactMapper get() = languageManager.methodCallFactMapper

    private var zeroInitialFactProcessed: Boolean = false
    private val initialFacts = apManager.initialFactAbstraction()
    private val edges = MethodAnalyzerEdges(apManager, methodEntryPoint, languageManager)
    private var pendingSummaryEdges = arrayListOf<Edge>()
    private var pendingSideEffectRequirements = arrayListOf<InitialFactAp>()

    private val reachability = languageManager.getLocalVariableReachability(methodEntryPoint.method, graph)

    private var analyzerEnqueued = false
    private var unprocessedEdges = arrayListOf<Edge>()

    override val containsUnprocessedEdges: Boolean
        get() = unprocessedEdges.isNotEmpty()

    private var analyzerSteps: Long = 0
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
        }
    }

    override fun addInitialZeroFact() {
        if (!zeroInitialFactProcessed) {
            zeroInitialFactProcessed = true
            propagateStartFacts()
        }
    }

    override fun addInitialFact(factAp: FinalFactAp) {
        val checkedFact = languageManager.checkInitialFactTypes(methodEntryPoint, factAp) ?: return
        initialFacts.addAbstractedInitialFact(checkedFact).forEach { (initialFact, finalFact) ->
            addInitialEdge(initialFact, finalFact)
        }
    }

    private fun propagateStartFacts() {
        addInitialZeroEdge()

        languageManager.applyEntryPointConfigDefault(apManager, taintConfig, methodEntryPoint.method)
            .onSome { facts ->
                facts.forEach { fact -> addInitialZeroToFactEdge(fact) }
            }
    }

    override fun tabulationAlgorithmStep() {
        analyzerSteps++

        val edge = unprocessedEdges.removeLast()

        val edgeFactBase = when (edge) {
            is ZeroToZero -> null
            is ZeroToFact -> edge.factAp.base
            is FactToFact -> edge.factAp.base
        }

        if (edgeFactBase == null || reachability.isReachable(edgeFactBase, edge.statement)) {
            languageManager.onInstructionReached(edge.statement)

            val callExpr = languageManager.getCallExpr(edge.statement)
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

        flushPendingSummaryEdges()
        flushPendingSideEffectRequirements()
    }

    private fun simpleStatementStep(edge: Edge) {
        // Simple (sequential) propagation to the next instruction:
        val flowFunction = languageManager.getMethodSequentFlowFunction(apManager, edge.statement)
        val sequentialFacts = when (edge) {
            is ZeroToZero -> flowFunction.propagateZeroToZero()
            is ZeroToFact -> flowFunction.propagateZeroToFact(edge.factAp)
            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFactAp, edge.factAp)
        }

        for (sf in sequentialFacts) {
            val edgeAfterStatement = when (sf) {
                Sequent.ZeroToZero -> ZeroToZero(methodEntryPoint, edge.statement)
                is Sequent.ZeroToFact -> ZeroToFact(methodEntryPoint, edge.statement, sf.factAp)
                is Sequent.FactToFact -> FactToFact(methodEntryPoint, sf.initialFactAp, edge.statement, sf.factAp)
            }

            handleStatementEdge(edge, edgeAfterStatement)
        }
    }

    private fun callStatementStep(callExpr: CommonCallExpr, edge: Edge) {
        val returnValue: CommonValue? = (edge.statement as? CommonAssignInst)?.lhv

        val flowFunction = languageManager.getMethodCallFlowFunction(
            apManager,
            taintConfig,
            returnValue,
            callExpr,
            languageManager.factTypeChecker,
            edge.statement,
            taintSinkTracker,
            methodEntryPoint,
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
                methodCallResolver.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.CallToStartZFact -> {
                val callerEdge = ZeroToFact(methodEntryPoint, edge.statement, fact.callerFactAp)
                val handler = MethodCallHandler.ZeroToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToFactHandler(edge, fact.callerFactAp)
                methodCallResolver.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
            }
        }
    }

    private fun propagateFactCallFact(
        callExpr: CommonCallExpr,
        edge: FactToFact,
        fact: MethodCallFlowFunction.FactCallFact
    ) {
        when (fact) {
            is MethodCallFlowFunction.CallToReturnFFact -> {
                val edgeAfterStatement = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.factAp)
                handleStatementEdge(edge, edgeAfterStatement)
            }

            is MethodCallFlowFunction.CallToStartFFact -> {
                val callerEdge = FactToFact(methodEntryPoint, fact.initialFactAp, edge.statement, fact.callerFactAp)

                handleInputFactChange(edge.initialFactAp, callerEdge.initialFactAp)

                val handler = MethodCallHandler.FactToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.FactToFactHandler(callerEdge, fact.callerFactAp)
                methodCallResolver.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
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

        propagateEdgeToSuccessors(edgeAfterStatement)
    }

    private fun propagateEdgeToSuccessors(edge: Edge) {
        if (edge.statement in methodExitPoints) {
            val isValidSummaryEdge = when (edge) {
                is ZeroToZero -> true
                is ZeroToFact -> methodCallFactMapper.isValidMethodExitFact(edge.factAp)
                is FactToFact -> methodCallFactMapper.isValidMethodExitFact(edge.factAp)
            }

            if (isValidSummaryEdge) {
                newSummaryEdge(edge)
            }
        }

        statementFilteredTraverse(
            languageManager, edge.statement, graph::successors,
            predicate = { languageManager.isRelevantInstruction(it) || it in methodExitPoints },
            body = { addSequentialEdge(edge.replaceStatement(it)) }
        )
    }

    private fun newSummaryEdge(edge: Edge) {
        if (edge is ZeroToZero) {
            runner.addNewSummaryEdges(methodEntryPoint, listOf(edge))
        } else {
            pendingSummaryEdges.add(edge)
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
        return !languageManager.producesExceptionalControlFlow(edge.statement)
    }

    override fun handleMethodSideEffectRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSideEffectRequirements: List<InitialFactAp>
    ) {
        var sinkRequirementExclusion: ExclusionSet = ExclusionSet.Empty

        val methodInitialFact = currentEdge.factAp.rebase(methodInitialFactBase)
        for (methodSinkRequirement in methodSideEffectRequirements) {
            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, methodSinkRequirement
            )

            if (summaryEdgeEffects.any { it is SummaryExclusionRefinement }) {
                sinkRequirementExclusion = sinkRequirementExclusion.union(methodSinkRequirement.exclusions)
            }
        }

        if (sinkRequirementExclusion !is ExclusionSet.Empty) {
            addSideEffectRequirement(currentEdge, currentEdge.initialFactAp.replaceExclusions(sinkRequirementExclusion))
        }
    }

    private fun addSideEffectRequirement(currentEdge: Edge, sideEffectRequirement: InitialFactAp) {
        if (currentEdge is FactToFact) {
            handleInputFactChange(currentEdge.initialFactAp, sideEffectRequirement)
        }

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

        for (methodSummary in applicableSummaries) {
            when (methodSummary) {
                is ZeroToZero -> {
                    handleStatementEdge(currentEdge, ZeroToZero(methodEntryPoint, currentEdge.statement))
                }

                is ZeroToFact -> {
                    val summaryExitFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(
                        currentEdge.statement, methodSummary.factAp, languageManager.factTypeChecker
                    )

                    for (summaryExitFact in summaryExitFacts) {
                        val edgeAfterStatement = ZeroToFact(methodEntryPoint, currentEdge.statement, summaryExitFact)
                        handleStatementEdge(currentEdge, edgeAfterStatement)
                    }
                }
            }
        }
    }

    override fun handleZeroToFactMethodSummaryEdge(
        summarySubs: List<MethodAnalyzer.ZeroToFactSub>,
        methodSummaries: List<FactToFact>
    ) {
        summaryEdgesHandled++

        val applicableSummaries = methodSummaries.filter { isApplicableExitToReturnEdge(it) }

        for (sub in summarySubs) {
            applyMethodSummaries(
                currentEdgeInitialFact = null,
                currentEdgeStatement = sub.currentEdge.statement,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                refineInitialFact = { _, refinement ->
                    check(refinement is ExclusionSet.Universe) { "Incorrect refinement" }
                    null
                },
                handleSummaryEdge = { _, summaryFact ->
                    handleZeroToFactMethodSummaryEdge(summaryFact, sub.currentEdge)
                }
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
            applyMethodSummaries(
                currentEdgeInitialFact = sub.currentEdge.initialFactAp,
                currentEdgeStatement = sub.currentEdge.statement,
                currentEdgeFactAp = sub.currentEdge.factAp,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                refineInitialFact = { initialFactAp: InitialFactAp, refinement: ExclusionSet ->
                    initialFactAp.replaceExclusions(refinement)
                },
                handleSummaryEdge = { initialFactAp: InitialFactAp, summaryFactAp: FinalFactAp ->
                    handleFactToFactMethodSummaryEdge(initialFactAp, summaryFactAp, sub.currentEdge)
                }
            )
        }
    }

    private fun <IF> applyMethodSummaries(
        currentEdgeInitialFact: IF,
        currentEdgeStatement: CommonInst,
        currentEdgeFactAp: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<FactToFact>,
        refineInitialFact: (fact: IF, refinement: ExclusionSet) -> IF,
        handleSummaryEdge: (initialFact: IF, summaryFactAp: FinalFactAp) -> Unit
    ) {
        val summaries = methodSummaries.groupByTo(hashMapOf()) { it.initialFactAp }
        for ((summaryInitialFact, summaryEdges) in summaries) {
            val methodInitialFact = currentEdgeFactAp.rebase(methodInitialFactBase)
            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, summaryInitialFact
            )

            for (summaryEdgeEffect in summaryEdgeEffects) {
                when (summaryEdgeEffect) {
                    is SummaryApRefinement -> {
                        for (methodSummary in summaryEdges) {
                            val mappedSummaryFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(
                                currentEdgeStatement, methodSummary.factAp, languageManager.factTypeChecker
                            )

                            for (mappedSummaryFact in mappedSummaryFacts) {
                                // todo: filter exclusions
                                val summaryFactAp = mappedSummaryFact
                                    .concat(languageManager.factTypeChecker, summaryEdgeEffect.delta)
                                    ?.replaceExclusions(currentEdgeFactAp.exclusions)
                                    ?: continue

                                handleSummaryEdge(currentEdgeInitialFact, summaryFactAp)
                            }
                        }
                    }

                    is SummaryExclusionRefinement -> {
                        val initialFact = refineInitialFact(currentEdgeInitialFact, summaryEdgeEffect.exclusion)

                        for (methodSummary in summaryEdges) {
                            val mappedSummaryFacts = methodCallFactMapper.mapMethodExitToReturnFlowFact(
                                currentEdgeStatement, methodSummary.factAp, languageManager.factTypeChecker
                            )

                            for (mappedSummaryFact in mappedSummaryFacts) {
                                // todo: filter exclusions
                                val summaryFactAp = mappedSummaryFact.replaceExclusions(summaryEdgeEffect.exclusion)

                                handleSummaryEdge(initialFact, summaryFactAp)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleFactToFactMethodSummaryEdge(
        initialFactAp: InitialFactAp,
        summaryFactAp: FinalFactAp,
        currentEdge: FactToFact
    ) {
        val edgeAfterStatement = FactToFact(methodEntryPoint, initialFactAp, currentEdge.statement, summaryFactAp)
        handleStatementEdge(currentEdge, edgeAfterStatement)
    }

    private fun handleZeroToFactMethodSummaryEdge(summaryFactAp: FinalFactAp, currentEdge: ZeroToFact) {
        val edgeAfterStatement = ZeroToFact(methodEntryPoint, currentEdge.statement, summaryFactAp)
        handleStatementEdge(currentEdge, edgeAfterStatement)
    }

    override fun resolveIntraProceduralTraceSummary(
        statement: CommonInst,
        factAp: InitialFactAp
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, methodEntryPoint, edges)
        return resolver.resolveIntraProceduralTrace(statement, factAp)
    }

    override fun resolveIntraProceduralTraceSummaryFromCall(
        statement: CommonInst,
        calleeEntry: MethodTraceResolver.TraceEntry.MethodEntry
    ): List<MethodTraceResolver.SummaryTrace> {
        val resolver = MethodTraceResolver(runner, methodEntryPoint, edges)
        return resolver.resolveIntraProceduralTraceFromCall(statement, calleeEntry)
    }

    override fun resolveIntraProceduralFullTrace(
        summaryTrace: MethodTraceResolver.SummaryTrace,
        cancellation: TraceResolverCancellation
    ): MethodTraceResolver.FullTrace {
        val resolver = MethodTraceResolver(runner, methodEntryPoint, edges)
        return resolver.resolveIntraProceduralFullTrace(summaryTrace, cancellation)
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
