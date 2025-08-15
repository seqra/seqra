package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRAssignInst
import org.opentaint.ir.api.jvm.cfg.JIRCallExpr
import org.opentaint.ir.api.jvm.cfg.JIRImmediate
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.api.jvm.ext.toType
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.dataflow.ifds.onSome
import org.opentaint.dataflow.jvm.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.jvm.ap.ifds.Edge.ZeroInitialEdge
import org.opentaint.dataflow.jvm.ap.ifds.Edge.ZeroToFact
import org.opentaint.dataflow.jvm.ap.ifds.Edge.ZeroToZero
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzer.MethodCallHandler
import org.opentaint.dataflow.jvm.ap.ifds.MethodAnalyzer.MethodCallResolutionFailureHandler
import org.opentaint.dataflow.jvm.ap.ifds.MethodCallFlowFunction.Companion.applyEntryPointConfigDefault
import org.opentaint.dataflow.jvm.ap.ifds.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.jvm.ap.ifds.MethodFlowFunctionUtils.rebase
import org.opentaint.dataflow.jvm.ap.ifds.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager

interface MethodAnalyzer {
    fun addInitialZeroFact()

    fun addInitialFact(fact: Fact.FinalFact)

    fun tabulationAlgorithmStep(edge: Edge)

    fun handleZeroToZeroMethodSummaryEdge(currentEdge: ZeroToZero, methodSummaries: List<ZeroInitialEdge>)

    fun handleZeroToFactMethodSummaryEdge(summarySubs: List<ZeroToFactSub>, methodSummaries: List<FactToFact>)

    fun handleFactToFactMethodSummaryEdge(summarySubs: List<FactToFactSub>, methodSummaries: List<FactToFact>)

    fun handleMethodSinkRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSinkRequirement: Fact.InitialFact
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

    sealed interface MethodCallHandler {
        data class ZeroToZeroHandler(val currentEdge: ZeroToZero) : MethodCallHandler
        data class ZeroToFactHandler(val currentEdge: ZeroToFact, val startFactBase: AccessPathBase) : MethodCallHandler
        data class FactToFactHandler(val currentEdge: FactToFact, val startFactBase: AccessPathBase) : MethodCallHandler
    }

    sealed interface MethodCallResolutionFailureHandler {
        object ZeroToZeroHandler : MethodCallResolutionFailureHandler
        data class ZeroToFactHandler(val edge: ZeroInitialEdge, val callerFact: Fact.FinalFact) : MethodCallResolutionFailureHandler
        data class FactToFactHandler(val callerEdge: FactToFact, val callerFact: Fact.FinalFact): MethodCallResolutionFailureHandler
    }

    fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler)

    fun handleMethodCallResolutionFailure(
        callExpr: JIRCallExpr,
        handler: MethodCallResolutionFailureHandler
    )
}

class NormalMethodAnalyzer(
    private val runner: AnalysisRunner,
    private val methodEntryPoint: MethodEntryPoint
) : MethodAnalyzer {
    private val graph: JIRApplicationGraph get() = runner.graph
    private val taintConfig: TaintRulesProvider get() = runner.taintConfiguration
    private val factTypeChecker: FactTypeChecker get() = runner.factTypeChecker
    private val taintSinkTracker: TaintSinkTracker get() = runner.sinkTracker
    private val lambdaTracker: JIRLambdaTracker get() = runner.lambdaTracker
    private val apManager: ApManager get() = runner.apManager

    private var zeroInitialFactProcessed: Boolean = false
    private val initialFacts = apManager.initialFactAbstraction()
    private val edges = MethodAnalyzerEdges(apManager, methodEntryPoint)
    private val pendingSummaryEdges = arrayListOf<Edge>()

    private val reachability = LocalVariableReachability(methodEntryPoint.method, graph)

    private var unprocessedEdgesCount: Int = 0
    private var analyzerSteps: Long = 0
    private var summaryEdgesHandled: Long = 0

    private val methodExitPoints by lazy { graph.exitPoints(methodEntryPoint.method).toHashSet() }

    override fun collectStats(stats: MethodStats) {
        stats.stats(methodEntryPoint.method).apply {
            steps += analyzerSteps
            handledSummaries += summaryEdgesHandled
            unprocessedEdges += unprocessedEdgesCount
        }
    }

    override fun addInitialZeroFact() {
        if (!zeroInitialFactProcessed) {
            zeroInitialFactProcessed = true
            propagateStartFacts()
        }
    }

    override fun addInitialFact(fact: Fact.FinalFact) {
        val checkedFact = checkInitialFactTypes(fact) ?: return
        initialFacts.addAbstractedInitialFact(checkedFact).forEach { (initialFact, finalFact) ->
            addInitialEdge(initialFact, finalFact)
        }
    }

    private fun propagateStartFacts() {
        addInitialZeroEdge()

        applyEntryPointConfigDefault(apManager, taintConfig, methodEntryPoint.method)
            .onSome { facts ->
                facts.forEach { fact -> addInitialZeroToFactEdge(fact) }
            }
    }

    private fun checkInitialFactTypes(fact: Fact.FinalFact): Fact.FinalFact? {
        if (fact.ap.base !is AccessPathBase.This) return fact

        val thisClass = when (methodEntryPoint.context) {
            EmptyMethodContext -> methodEntryPoint.method.enclosingClass
            is InstanceTypeMethodContext -> methodEntryPoint.context.type
        }

        val thisType = thisClass.toType()
        return factTypeChecker.filterFactByLocalType(thisType, fact)
    }

    override fun tabulationAlgorithmStep(edge: Edge) {
        analyzerSteps++
        unprocessedEdgesCount--

        val edgeFactBase = when (edge) {
            is ZeroToZero -> null
            is ZeroToFact -> edge.fact.ap.base
            is FactToFact -> edge.fact.ap.base
        }

        if (edgeFactBase == null || reachability.isReachable(edgeFactBase, edge.statement)) {
            registerLambdaAllocationSite(edge.statement)

            val callExpr = edge.statement.callExpr
            if (callExpr != null) {
                callStatementStep(callExpr, edge)
            } else {
                simpleStatementStep(edge)
            }
        }

        if (unprocessedEdgesCount == 0) {
            flushPendingSummaryEdges()
        }
    }

    private fun registerLambdaAllocationSite(statement: JIRInst) {
        val allocatedLambda = LambdaExpressionToAnonymousClassTransformerFeature.findLambdaAllocation(statement)
        if (allocatedLambda != null) {
            lambdaTracker.registerLambda(allocatedLambda)
        }
    }

    private fun simpleStatementStep(edge: Edge) {
        val currentIsExit = edge.statement in methodExitPoints
        if (currentIsExit) {
            when (edge) {
                is ZeroToZero -> newSummaryEdge(edge)

                is ZeroToFact -> if (isValidMethodExitFact(edge.statement, edge.fact)) {
                    newSummaryEdge(edge)
                }

                is FactToFact -> if (isValidMethodExitFact(edge.statement, edge.fact)) {
                    newSummaryEdge(edge)
                }
            }
        }

        // Simple (sequential) propagation to the next instruction:
        val flowFunction = MethodSequentFlowFunction(apManager, edge.statement, factTypeChecker)
        val sequentialFacts = when (edge) {
            is ZeroToZero -> flowFunction.propagateZeroToZero()
            is ZeroToFact -> flowFunction.propagateZeroToFact(edge.fact)
            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFact, edge.fact)
        }

        for (successor in graph.successors(edge.statement)) {
            for (sf in sequentialFacts) {
                val newEdge = when (sf) {
                    Sequent.ZeroToZero -> ZeroToZero(methodEntryPoint, successor)

                    is Sequent.ZeroToFact ->
                        ZeroToFact(methodEntryPoint, successor, sf.fact)

                    is Sequent.FactToFact ->
                        FactToFact(methodEntryPoint, sf.initialFact, successor, sf.fact)
                }

                addSequentialEdge(edge, newEdge)
            }
        }
    }

    private fun callStatementStep(callExpr: JIRCallExpr, edge: Edge) {
        val returnValue: JIRImmediate? = (edge.statement as? JIRAssignInst)?.lhv?.let {
            it as? JIRImmediate ?: error("Non simple return value: ${edge.statement}")
        }

        val flowFunction = MethodCallFlowFunction(
            apManager,
            taintConfig,
            returnValue,
            callExpr,
            factTypeChecker,
            edge.statement,
            taintSinkTracker
        )

        val returnSites = graph.successors(edge.statement).toList()

        when (edge) {
            is ZeroInitialEdge -> {
                val callFacts = when (edge) {
                    is ZeroToZero -> flowFunction.propagateZeroToZero()
                    is ZeroToFact -> flowFunction.propagateZeroToFact(edge.fact)
                }

                callFacts.forEach {
                    propagateZeroCallFact(callExpr, edge, it, returnSites)
                }
            }

            is FactToFact -> flowFunction.propagateFactToFact(edge.initialFact, edge.fact).forEach {
                propagateFactCallFact(callExpr, edge, it, returnSites)
            }
        }
    }

    private fun propagateZeroCallFact(
        callExpr: JIRCallExpr,
        edge: ZeroInitialEdge,
        fact: ZeroCallFact,
        returnSites: List<JIRInst>
    ) {
        when (fact) {
            MethodCallFlowFunction.CallToReturnZeroFact -> {
                for (returnSite in returnSites) {
                    val newEdge = ZeroToZero(methodEntryPoint, returnSite)
                    addSequentialEdge(edge, newEdge)
                }
            }

            is MethodCallFlowFunction.CallToReturnZFact -> {
                for (returnSite in returnSites) {
                    val newEdge = ZeroToFact(methodEntryPoint, returnSite, fact.fact)
                    addSequentialEdge(edge, newEdge)
                }
            }

            is MethodCallFlowFunction.CallToStartZeroFact -> {
                val callerEdge = ZeroToZero(methodEntryPoint, edge.statement)

                val handler = MethodCallHandler.ZeroToZeroHandler(callerEdge)
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToZeroHandler
                runner.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.CallToStartZFact -> {
                val callerEdge = ZeroToFact(methodEntryPoint, edge.statement, fact.callerFact)
                val handler = MethodCallHandler.ZeroToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.ZeroToFactHandler(edge, fact.callerFact)
                runner.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
            }
        }
    }

    private fun propagateFactCallFact(
        callExpr: JIRCallExpr,
        edge: FactToFact,
        fact: MethodCallFlowFunction.FactCallFact,
        returnSites: List<JIRInst>
    ) {
        when (fact) {
            is MethodCallFlowFunction.CallToReturnFFact -> {
                for (returnSite in returnSites) {
                    val newEdge = FactToFact(methodEntryPoint, fact.initialFact, returnSite, fact.fact)
                    addSequentialEdge(edge, newEdge)
                }
            }
            is MethodCallFlowFunction.CallToStartFFact -> {
                val callerEdge = FactToFact(methodEntryPoint, fact.initialFact, edge.statement, fact.callerFact)

                handleInputFactChange(edge.initialFact, callerEdge.initialFact)

                val handler = MethodCallHandler.FactToFactHandler(callerEdge, fact.startFactBase)
                val failureHandler = MethodCallResolutionFailureHandler.FactToFactHandler(callerEdge, fact.callerFact)
                runner.resolveMethodCall(methodEntryPoint, callExpr, edge.statement, handler, failureHandler)
            }

            is MethodCallFlowFunction.SinkRequirement -> {
                addSinkRequirement(edge, fact.initialFact)
            }
        }
    }

    private fun addInitialZeroEdge() {
        val edge = ZeroToZero(methodEntryPoint, methodEntryPoint.statement)
        edges.add(edge).forEach { newEdge ->
            enqueueNewEdge(newEdge)
        }
    }

    private fun addInitialZeroToFactEdge(fact: Fact.FinalFact) {
        val edge = ZeroToFact(methodEntryPoint, methodEntryPoint.statement, fact)
        edges.add(edge).forEach { newEdge ->
            enqueueNewEdge(newEdge)
        }
    }

    private fun addInitialEdge(initialFact: Fact.InitialFact, fact: Fact.FinalFact) {
        val edge = FactToFact(methodEntryPoint, initialFact, methodEntryPoint.statement, fact)
        edges.add(edge).forEach { newEdge ->
            enqueueNewEdge(newEdge)
        }
    }

    private fun addSequentialEdge(previousEdge: Edge, edge: Edge) {
        edges.add(edge).forEach { newEdge ->
            if (previousEdge is FactToFact && newEdge is FactToFact) {
                handleInputFactChange(previousEdge.initialFact, newEdge.initialFact)
            }

            enqueueNewEdge(newEdge)
        }
    }

    private fun enqueueNewEdge(edge: Edge) {
        unprocessedEdgesCount++
        runner.submitNewUnprocessedEdge(edge)
    }

    private fun handleInputFactChange(originalInputFact: Fact.InitialFact, newInputFact: Fact.InitialFact) {
        if (originalInputFact == newInputFact) return
        initialFacts.registerNewInitialFact(newInputFact).forEach { (initialFact, finalFact) ->
            addInitialEdge(initialFact, finalFact)
        }
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
            runner.addNewSummaryEdges(methodEntryPoint, pendingSummaryEdges.toList())
            pendingSummaryEdges.clear()
        }
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        for (ep in methodEntryPoints(method)) {
            handleMethodCall(handler, ep)
        }
    }

    private fun handleMethodCall(handler: MethodCallHandler, ep: MethodEntryPoint) = when(handler) {
        is MethodCallHandler.ZeroToZeroHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep)

        is MethodCallHandler.ZeroToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)

        is MethodCallHandler.FactToFactHandler ->
            runner.subscribeOnMethodSummaries(handler.currentEdge, ep, handler.startFactBase)
    }

    override fun handleMethodCallResolutionFailure(
        callExpr: JIRCallExpr,
        handler: MethodCallResolutionFailureHandler
    ) = when (handler) {
        MethodCallResolutionFailureHandler.ZeroToZeroHandler -> {
            // do nothing
        }

        is MethodCallResolutionFailureHandler.ZeroToFactHandler -> {
            // If no callees resolved propagate as call-to-return
            val stubFact = MethodCallFlowFunction.CallToReturnZFact(handler.callerFact)
            val returnSites = graph.successors(handler.edge.statement).toList()
            propagateZeroCallFact(callExpr, handler.edge, stubFact, returnSites)
        }

        is MethodCallResolutionFailureHandler.FactToFactHandler -> {
            // If no callees resolved propagate as call-to-return
            val stubFact = MethodCallFlowFunction.CallToReturnFFact(handler.callerEdge.initialFact, handler.callerFact)
            val returnSites = graph.successors(handler.callerEdge.statement).toList()
            propagateFactCallFact(callExpr, handler.callerEdge, stubFact, returnSites)
        }
    }

    private fun methodEntryPoints(method: MethodWithContext): Sequence<MethodEntryPoint> =
        graph.entryPoints(method.method).map { MethodEntryPoint(method.ctx, it) }

    private fun isApplicableExitToReturnEdge(edge: Edge): Boolean {
        return edge.statement !is JIRThrowInst
    }

    override fun handleMethodSinkRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSinkRequirement: Fact.InitialFact
    ) {
        val methodInitialFact = currentEdge.fact.rebase(methodInitialFactBase)
        val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
            methodInitialFact, methodSinkRequirement
        )

        for (summaryEdgeEffect in summaryEdgeEffects) {
            when (summaryEdgeEffect) {
                is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement -> continue
                is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement -> {
                    val refinedInitialFact = replaceFactExclusion(currentEdge.initialFact, summaryEdgeEffect.exclusion)
                    addSinkRequirement(currentEdge, refinedInitialFact)
                }
            }
        }
    }

    private fun addSinkRequirement(currentEdge: Edge, sinkRequirement: Fact.InitialFact) {
        if (currentEdge is FactToFact) {
            handleInputFactChange(currentEdge.initialFact, sinkRequirement)
        }
        runner.addNewSinkRequirement(methodEntryPoint, sinkRequirement)
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
                    for (returnSite in graph.successors(currentEdge.statement)) {
                        val newEdge = ZeroToZero(methodEntryPoint, returnSite)
                        addSequentialEdge(currentEdge, newEdge)
                    }
                }

                is ZeroToFact -> {
                    val summaryExitFact = mapMethodExitToReturnFlowFact(
                        currentEdge.statement, methodSummary.statement, methodSummary.fact, factTypeChecker
                    ) ?: continue

                    for (returnSite in graph.successors(currentEdge.statement)) {
                        val newEdge = ZeroToFact(methodEntryPoint, returnSite, summaryExitFact)
                        addSequentialEdge(currentEdge, newEdge)
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
                currentEdgeInitialFact = Fact.Zero,
                currentEdgeStatement = sub.currentEdge.statement,
                currentEdgeFact = sub.currentEdge.fact,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                refineInitialFact = { _, refinement ->
                    check(refinement is ExclusionSet.Universe) { "Incorrect refinement" }
                    Fact.Zero
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
                currentEdgeInitialFact = sub.currentEdge.initialFact,
                currentEdgeStatement = sub.currentEdge.statement,
                currentEdgeFact = sub.currentEdge.fact,
                methodInitialFactBase = sub.methodInitialFactBase,
                methodSummaries = applicableSummaries,
                refineInitialFact = { initialFact: Fact.InitialFact, refinement: ExclusionSet ->
                    replaceFactExclusion(initialFact, refinement)
                },
                handleSummaryEdge = { initialFact: Fact.InitialFact, summaryFact: Fact.FinalFact ->
                    handleFactToFactMethodSummaryEdge(initialFact, summaryFact, sub.currentEdge)
                }
            )
        }
    }

    private fun <IF: Fact> applyMethodSummaries(
        currentEdgeInitialFact: IF,
        currentEdgeStatement: JIRInst,
        currentEdgeFact: Fact.FinalFact,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<FactToFact>,
        refineInitialFact: (fact: IF, refinement: ExclusionSet) -> IF,
        handleSummaryEdge: (initialFact: IF, summaryFact: Fact.FinalFact) -> Unit
    ) {
        val summaries = methodSummaries.groupBy { it.initialFact }
        for ((summaryInitialFact, summaryEdges) in summaries) {
            val methodInitialFact = currentEdgeFact.rebase(methodInitialFactBase)
            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, summaryInitialFact
            )

            for (summaryEdgeEffect in summaryEdgeEffects) {
                when (summaryEdgeEffect) {
                    is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryApRefinement -> {
                        for (methodSummary in summaryEdges) {
                            val mappedSummaryFact = mapMethodExitToReturnFlowFact(
                                currentEdgeStatement, methodSummary.statement, methodSummary.fact, factTypeChecker
                            ) ?: continue

                            // todo: filter exclusions
                            val summaryFactAp = mappedSummaryFact.ap.concat(factTypeChecker, summaryEdgeEffect.delta)
                                ?.replaceExclusions(currentEdgeFact.ap.exclusions)
                                ?: continue
                            val summaryFact = mappedSummaryFact.changeAP(summaryFactAp)

                            handleSummaryEdge(currentEdgeInitialFact, summaryFact)
                        }
                    }

                    is MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication.SummaryExclusionRefinement -> {
                        val initialFact = refineInitialFact(currentEdgeInitialFact, summaryEdgeEffect.exclusion)

                        for (methodSummary in summaryEdges) {
                            val mappedSummaryFact = mapMethodExitToReturnFlowFact(
                                currentEdgeStatement, methodSummary.statement, methodSummary.fact, factTypeChecker
                            ) ?: continue

                            // todo: filter exclusions
                            val summaryFactAp = mappedSummaryFact.ap.replaceExclusions(summaryEdgeEffect.exclusion)
                            val summaryFact = mappedSummaryFact.changeAP(summaryFactAp)

                            handleSummaryEdge(initialFact, summaryFact)
                        }
                    }
                }
            }
        }
    }

    private fun handleFactToFactMethodSummaryEdge(
        initialFact: Fact.InitialFact,
        summaryFact: Fact.FinalFact,
        currentEdge: FactToFact
    ) {
        for (returnSite in graph.successors(currentEdge.statement)) {
            val newEdge = FactToFact(methodEntryPoint, initialFact, returnSite, summaryFact)
            addSequentialEdge(currentEdge, newEdge)
        }
    }

    private fun handleZeroToFactMethodSummaryEdge(summaryFact: Fact.FinalFact, currentEdge: ZeroToFact) {
        for (returnSite in graph.successors(currentEdge.statement)) {
            val newEdge = ZeroToFact(methodEntryPoint, returnSite, summaryFact)
            addSequentialEdge(currentEdge, newEdge)
        }
    }

    private fun replaceFactExclusion(
        fact: Fact.InitialFact,
        exclusions: ExclusionSet
    ): Fact.InitialFact = fact.changeAP(fact.ap.replaceExclusions(exclusions))
}

class EmptyMethodAnalyzer(
    private val runner: AnalysisRunner,
    private val methodEntryPoint: MethodEntryPoint
) : MethodAnalyzer {
    private var zeroInitialFactProcessed: Boolean = false
    private val taintedInitialFacts = hashMapOf<TaintMark, MutableSet<AccessPathBase>>()
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

    override fun addInitialFact(fact: Fact.FinalFact) {
        addSummary(fact.mark, fact.ap.base)
    }

    private fun addSummary(mark: TaintMark, base: AccessPathBase) {
        val facts = taintedInitialFacts.getOrPut(mark) { hashSetOf() }
        if (!facts.add(base)) return

        val initialFact = Fact.InitialFact(mark, apManager.mostAbstractInitialAp(base))
        val fact = Fact.FinalFact(mark, apManager.mostAbstractFinalAp(base))

        runner.addNewSummaryEdges(
            methodEntryPoint,
            listOf(FactToFact(methodEntryPoint, initialFact, methodEntryPoint.statement, fact))
        )
    }

    override fun collectStats(stats: MethodStats) {
        // No stats
    }

    override fun tabulationAlgorithmStep(edge: Edge) {
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

    override fun handleMethodSinkRequirement(
        currentEdge: FactToFact,
        methodInitialFactBase: AccessPathBase,
        methodSinkRequirement: Fact.InitialFact
    ) {
        error("Empty method should not receive sink requirements")
    }

    override fun handleResolvedMethodCall(method: MethodWithContext, handler: MethodCallHandler) {
        error("Empty method should not method resolution results")
    }

    override fun handleMethodCallResolutionFailure(callExpr: JIRCallExpr, handler: MethodCallResolutionFailureHandler) {
        error("Empty method should not method resolution results")
    }
}
