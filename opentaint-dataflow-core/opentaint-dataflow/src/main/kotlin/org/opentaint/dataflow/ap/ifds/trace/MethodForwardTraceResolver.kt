package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.AnalysisRunner
import org.opentaint.dataflow.ap.ifds.AnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.Edge
import org.opentaint.dataflow.ap.ifds.Edge.FactToFact
import org.opentaint.dataflow.ap.ifds.Edge.ZeroToFact
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils
import org.opentaint.dataflow.ap.ifds.MethodSummaryEdgeApplicationUtils.SummaryEdgeApplication
import org.opentaint.dataflow.ap.ifds.MethodWithContext
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.analysis.AnalysisManager
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFlowFunction.ZeroCallFact
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction
import org.opentaint.dataflow.ap.ifds.analysis.MethodSequentFlowFunction.Sequent
import org.opentaint.dataflow.graph.MethodInstGraph
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonAssignInst
import org.opentaint.ir.api.common.cfg.CommonCallExpr
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.cfg.CommonValue

class MethodForwardTraceResolver(
    private val runner: AnalysisRunner,
    private val analysisContext: MethodAnalysisContext,
    private val graph: MethodInstGraph,
) {
    private val methodEntryPoint: MethodEntryPoint = analysisContext.methodEntryPoint
    private val analysisManager: AnalysisManager get() = runner.analysisManager
    private val manager: AnalysisUnitRunnerManager get() = runner.manager
    private val apManager: ApManager get() = runner.apManager

    interface RelevantFactFilter {
        fun factIsRelevant(statement: CommonInst, fact: FinalFactAp): Boolean

        object NoFilter : RelevantFactFilter {
            override fun factIsRelevant(statement: CommonInst, fact: FinalFactAp): Boolean = true
        }
    }

    data class TraceGraph(
        val initial: TraceNode,
        val successors: Map<TraceNode, Set<TraceEdge>>
    )

    data class TraceNode(val statement: CommonInst, val fact: FinalFactAp)

    sealed interface EdgeReason {
        data object Unchanged : EdgeReason
        data object Unknown : EdgeReason
        data class Sequential(val info: MethodSequentFlowFunction.TraceInfo) : EdgeReason

        sealed interface CallInfo {
            data class CallTraceInfo(
                val info: MethodCallFlowFunction.TraceInfo
            ): CallInfo

            data class CallSummaryInfo(
                val callEp: MethodEntryPoint,
                val startFactBase: AccessPathBase
            ) : CallInfo
        }

        data class Call(val info: CallInfo) : EdgeReason
    }

    sealed interface TraceEdge {
        val reason: EdgeReason

        data class Transition(val successor: TraceNode, override val reason: EdgeReason) : TraceEdge
        data class MethodEnd(val node: TraceNode, override val reason: EdgeReason) : TraceEdge
        data class MethodSummary(val node: TraceNode, override val reason: EdgeReason) : TraceEdge
        data class Drop(override val reason: EdgeReason) : TraceEdge
    }

    private class TraceBuilder(val relevantFactFilter: RelevantFactFilter) {
        val unprocessedEdges = mutableListOf<ZeroToFact>()
        val visited = hashSetOf<ZeroToFact>()
        val successors = hashMapOf<TraceNode, MutableSet<TraceEdge>>()

        fun addSuccessor(current: ZeroToFact, next: TraceEdge) {
            val currentNode = TraceNode(current.statement, current.factAp)
            successors.getOrPut(currentNode, ::hashSetOf).add(next)
        }

        fun isRelevant(edge: ZeroToFact): Boolean =
            relevantFactFilter.factIsRelevant(edge.statement, edge.factAp)

        fun enqueue(edge: ZeroToFact): Boolean {
            if (!isRelevant(edge)) return false
            unprocessedEdges.add(edge)
            return true
        }
    }

    fun resolveForwardTrace(
        initialStatement: CommonInst,
        initialFact: FinalFactAp,
        startAtStatement: Boolean,
        relevantFactFilter: RelevantFactFilter,
    ): TraceGraph {
        val initial = TraceNode(initialStatement, initialFact)
        val builder = TraceBuilder(relevantFactFilter)

        val initialEdge = ZeroToFact(methodEntryPoint, initialStatement, initialFact)
        if (!startAtStatement) {
            builder.propagateEdgeToSuccessors(initialEdge, initialEdge, EdgeReason.Unchanged)
        } else {
            builder.enqueue(initialEdge)
        }

        builder.buildGraph()
        val graph = TraceGraph(initial, builder.successors)
        return graph
    }

    private fun TraceBuilder.buildGraph() {
        while (unprocessedEdges.isNotEmpty()) {
            val edge = unprocessedEdges.removeLast()
            if (!visited.add(edge)) continue

            val callExpr = analysisManager.getCallExpr(edge.statement)
            if (callExpr != null) {
                callStatementStep(callExpr, edge)
            } else {
                simpleStatementStep(edge)
            }
        }
    }

    private fun TraceBuilder.simpleStatementStep(edge: ZeroToFact) {
        // Simple (sequential) propagation to the next instruction:
        val flowFunction = analysisManager.getMethodSequentFlowFunction(
            apManager, analysisContext, edge.statement, generateTrace = true
        )
        val sequentialFacts = flowFunction.propagateZeroToFact(edge.factAp)
        handleSequentFact(edge, sequentialFacts)
    }

    private fun TraceBuilder.handleSequentFact(prevEdge: ZeroToFact, sf: Iterable<Sequent>) =
        sf.forEach { handleSequentFact(prevEdge, it) }

    private fun TraceBuilder.handleSequentFact(prevEdge: ZeroToFact, sf: Sequent) {
        when (sf) {
            is Sequent.Unchanged -> {
                handleUnchangedStatementEdge(prevEdge, prevEdge)
            }

            is Sequent.ZeroToFact -> {
                val edgeAfterStatement = ZeroToFact(methodEntryPoint, prevEdge.statement, sf.factAp)
                val trace = sf.traceInfo?.let { EdgeReason.Sequential(it) } ?: EdgeReason.Unknown
                handleStatementEdge(prevEdge, edgeAfterStatement, trace)
            }

            is Sequent.ZeroToZero,
            is Sequent.FactToFact,
            is Sequent.NDFactToFact,
            is Sequent.SideEffect,
            is Sequent.SideEffectRequirement -> {
                // ignore
            }
        }
    }

    private fun TraceBuilder.callStatementStep(callExpr: CommonCallExpr, edge: ZeroToFact) {
        val returnValue: CommonValue? = (edge.statement as? CommonAssignInst)?.lhv

        val flowFunction = analysisManager.getMethodCallFlowFunction(
            apManager, analysisContext, returnValue, callExpr, edge.statement,
            generateTrace = true
        )

        val callFacts = flowFunction.propagateZeroToFact(edge.factAp)
        callFacts.forEach {
            propagateZeroCallFact(callExpr, edge, it)
        }
    }

    private fun TraceBuilder.propagateZeroCallFact(
        callExpr: CommonCallExpr,
        edge: ZeroToFact,
        fact: ZeroCallFact,
    ) {
        when (fact) {
            is MethodCallFlowFunction.Unchanged -> {
                handleUnchangedStatementEdge(edge, edge)
            }

            is MethodCallFlowFunction.Drop -> {
                val trace = fact.traceInfo?.let { EdgeReason.Call(EdgeReason.CallInfo.CallTraceInfo(it)) }
                    ?: EdgeReason.Unknown
                addSuccessor(edge, TraceEdge.Drop(trace))
            }

            is MethodCallFlowFunction.CallToReturnZFact -> {
                val trace = fact.traceInfo?.let { EdgeReason.Call(EdgeReason.CallInfo.CallTraceInfo(it)) }
                    ?: EdgeReason.Unknown
                val nextEdge = ZeroToFact(methodEntryPoint, edge.statement, fact.factAp)
                handleStatementEdge(edge, nextEdge, trace)
            }

            is MethodCallFlowFunction.CallToStartZFact -> {
                resolveMethodCall(callExpr, edge, fact.callerFactAp, fact.startFactBase)
            }

            is MethodCallFlowFunction.CallToReturnZeroFact,
            is MethodCallFlowFunction.CallToStartZeroFact,
            is MethodCallFlowFunction.CallToReturnFFact,
            is MethodCallFlowFunction.ZeroSideEffect,
            is MethodCallFlowFunction.CallToReturnNonDistributiveFact -> {
                // ignore
            }
        }
    }

    private fun TraceBuilder.resolveMethodCall(
        callExpr: CommonCallExpr,
        callerEdge: ZeroToFact,
        callerFact: FinalFactAp,
        startFactBase: AccessPathBase,
    ) {
        val methodCalls = runner.methodCallResolver.resolvedMethodCalls(
            methodEntryPoint, callExpr, callerEdge.statement
        )

        if (methodCalls.isEmpty()) {
            // If no callees resolved propagate as call-to-return
            val stubFact = MethodCallFlowFunction.CallToReturnZFact(callerFact, traceInfo = null)
            propagateZeroCallFact(callExpr, callerEdge, stubFact)
        } else {
            for (method in methodCalls) {
                for (ep in methodEntryPoints(method)) {
                    handleMethodCall(ep, callerEdge, callerFact, startFactBase)
                }
            }
        }
    }

    private val methodEntryPointsCache = hashMapOf<CommonMethod, Array<CommonInst>>()

    private fun methodEntryPoints(method: MethodWithContext): List<MethodEntryPoint> {
        val methodEntryPoints = methodEntryPointsCache.getOrPut(method.method) {
            runner.graph.methodGraph(method.method).entryPoints().toList().toTypedArray()
        }
        return methodEntryPoints.map { MethodEntryPoint(method.ctx, it) }
    }

    private fun TraceBuilder.handleMethodCall(
        ep: MethodEntryPoint,
        callerEdge: ZeroToFact,
        callerFact: FinalFactAp,
        startFactBase: AccessPathBase,
    ) {
        val calleeInitialFactAp = callerEdge.factAp.rebase(startFactBase)
        val summaries = manager.findFactSummaryEdges(ep, calleeInitialFactAp)

        val applicableSummaries = summaries.filter { isApplicableExitToReturnEdge(it) }

        val handler = analysisManager.getMethodCallSummaryHandler(
            apManager, analysisContext, callerEdge.statement
        )

        val summaryApplied = applyMethodSummaries(
            currentEdge = callerEdge,
            callerFact = callerFact,
            methodInitialFactBase = startFactBase,
            methodSummaries = applicableSummaries,
            handleSummaryEdge = handler::handleZeroToFact
        )

        if (!summaryApplied) {
            val callInfo = EdgeReason.CallInfo.CallSummaryInfo(ep, startFactBase)
            val reason = EdgeReason.Call(callInfo)
            addSuccessor(callerEdge, TraceEdge.Drop(reason))
        }
    }

    private fun isApplicableExitToReturnEdge(edge: Edge): Boolean {
        return !analysisManager.producesExceptionalControlFlow(edge.statement)
    }

    private fun TraceBuilder.applyMethodSummaries(
        currentEdge: ZeroToFact,
        callerFact: FinalFactAp,
        methodInitialFactBase: AccessPathBase,
        methodSummaries: List<FactToFact>,
        handleSummaryEdge: (currentFactAp: FinalFactAp, summaryEffect: SummaryEdgeApplication, summaryFact: FinalFactAp) -> Set<Sequent>,
    ): Boolean {
        var summaryApplied = false
        val methodInitialFact = callerFact.rebase(methodInitialFactBase)

        val summaries = methodSummaries.groupByTo(hashMapOf()) { it.initialFactAp }
        for ((summaryInitialFact, summaryEdges) in summaries) {
            val summaryEdgeEffects = MethodSummaryEdgeApplicationUtils.tryApplySummaryEdge(
                methodInitialFact, summaryInitialFact
            )

            for (summaryEdgeEffect in summaryEdgeEffects) {
                for (methodSummary in summaryEdges) {
                    val sf = handleSummaryEdge(callerFact, summaryEdgeEffect, methodSummary.factAp)
                    handleSequentFact(currentEdge, sf)
                    summaryApplied = true
                }
            }
        }
        return summaryApplied
    }

    private fun TraceBuilder.handleStatementEdge(
        prevEdge: ZeroToFact,
        edgeAfterStatement: ZeroToFact,
        trace: EdgeReason
    ) {
        handleEdgeToMethodEnd(edgeAfterStatement, prevEdge, trace)
        propagateEdgeToSuccessors(prevEdge, edgeAfterStatement, trace)
    }

    private fun TraceBuilder.handleUnchangedStatementEdge(prevEdge: ZeroToFact, edge: ZeroToFact) {
        handleEdgeToMethodEnd(edge, prevEdge, EdgeReason.Unchanged)
        propagateEdgeToSuccessors(prevEdge, edge, EdgeReason.Unchanged)
    }

    private fun TraceBuilder.propagateEdgeToSuccessors(prevEdge: ZeroToFact, edge: ZeroToFact, trace: EdgeReason) {
        graph.forEachSuccessor(analysisManager, edge.statement) {
            val nextEdge = edge.replaceStatement(it) as ZeroToFact
            addSequentialEdge(prevEdge, nextEdge, trace)
        }
    }

    private fun TraceBuilder.handleEdgeToMethodEnd(edge: ZeroToFact, prevEdge: ZeroToFact, trace: EdgeReason) {
        if (!graph.isExitPoint(analysisManager, edge.statement)) return

        val isValidSummaryEdge = analysisManager.isValidMethodExitFact(apManager, analysisContext, edge.factAp)
        if (isValidSummaryEdge) {
            saveSummaryFact(edge, prevEdge, trace)
        } else {
            saveEndFact(edge, prevEdge, trace)
        }
    }

    private fun TraceBuilder.addSequentialEdge(currentEdge: ZeroToFact, nextEdge: ZeroToFact, trace: EdgeReason) {
        if (!enqueue(nextEdge)) return

        val nextNode = TraceNode(nextEdge.statement, nextEdge.factAp)
        val transition = TraceEdge.Transition(nextNode, trace)
        addSuccessor(currentEdge, transition)
    }

    private fun TraceBuilder.saveEndFact(edge: ZeroToFact, prevEdge: ZeroToFact, trace: EdgeReason) {
        if (!isRelevant(edge)) return
        val node = TraceEdge.MethodEnd(TraceNode(edge.statement, edge.factAp), trace)
        addSuccessor(prevEdge, node)
    }

    private fun TraceBuilder.saveSummaryFact(edge: ZeroToFact, prevEdge: ZeroToFact, trace: EdgeReason) {
        if (!isRelevant(edge)) return
        val node = TraceEdge.MethodSummary(TraceNode(edge.statement, edge.factAp), trace)
        addSuccessor(prevEdge, node)
    }
}
