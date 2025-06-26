package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.opentaint.ir.analysis.graph.JIRNoopInst
import org.opentaint.ir.analysis.taint.TaintZeroFact
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.common.ext.callExpr
import java.util.concurrent.ConcurrentHashMap

private val logger = mu.KotlinLogging.logger {}

interface Runner<Fact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    val graph: ApplicationGraph<Method, Statement>
    val unit: UnitType

    suspend fun run(startMethods: List<Method>)
    fun submitNewEdge(edge: Edge<Fact, Statement>, reason: Reason<Fact, Statement>)
    fun getIfdsResult(): IfdsResult<Fact, Statement>
}

class UniRunner<Fact, Event, Method, Statement>(
    private val manager: Manager<Fact, Event, Method, Statement>,
    override val graph: ApplicationGraph<Method, Statement>,
    private val analyzer: Analyzer<Fact, Event, Method, Statement>,
    private val unitResolver: UnitResolver<Method>,
    override val unit: UnitType,
    private val zeroFact: Fact?,
) : Runner<Fact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    private val flowSpace: FlowFunctions<Fact, Method, Statement> = analyzer.flowFunctions
    private val workList: Channel<Edge<Fact, Statement>> = Channel(Channel.UNLIMITED)
    internal val pathEdges: MutableSet<Edge<Fact, Statement>> = ConcurrentHashMap.newKeySet()
    private val reasons =
        ConcurrentHashMap<Edge<Fact, Statement>, MutableSet<Reason<Fact, Statement>>>()

    private val summaryEdges: MutableMap<Vertex<Fact, Statement>, MutableSet<Vertex<Fact, Statement>>> =
        hashMapOf()
    private val callerPathEdgeOf: MutableMap<Vertex<Fact, Statement>, MutableSet<Edge<Fact, Statement>>> =
        hashMapOf()

    private val queueIsEmpty = QueueEmptinessChanged(runner = this, isEmpty = true)
    private val queueIsNotEmpty = QueueEmptinessChanged(runner = this, isEmpty = false)

    override suspend fun run(startMethods: List<Method>) {
        for (method in startMethods) {
            addStart(method)
        }

        tabulationAlgorithm()
    }

    private fun addStart(method: Method) {
        require(unitResolver.resolve(method) == unit)
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            for (start in graph.entryPoints(method)) {
                val vertex = Vertex(start, startFact)
                val edge = Edge(vertex, vertex) // loop
                propagate(edge, Reason.Initial)
            }
        }
    }

    override fun submitNewEdge(edge: Edge<Fact, Statement>, reason: Reason<Fact, Statement>) {
        propagate(edge, reason)
    }

    private fun propagate(
        edge: Edge<Fact, Statement>,
        reason: Reason<Fact, Statement>,
    ): Boolean {
        val method = graph.methodOf(edge.from.statement)
        require(unitResolver.resolve(method) == unit) {
            "Propagated edge must be in the same unit"
        }

        reasons.computeIfAbsent(edge) { ConcurrentHashMap.newKeySet() }.add(reason)

        // Handle only NEW edges:
        if (pathEdges.add(edge)) {
            val doPrintOnlyForward = true
            val doPrintZero = false
            if (!doPrintOnlyForward || edge.from.statement is JIRNoopInst) {
                if (doPrintZero || edge.to.fact != TaintZeroFact) {
                    logger.trace {
                        "Propagating edge=${edge} in method=${method.name} with reason=${reason}"
                    }
                }
            }

            // Send edge to analyzer/manager:
            for (event in analyzer.handleNewEdge(edge)) {
                manager.handleEvent(event)
            }

            // Add edge to worklist:
            workList.trySend(edge).getOrThrow()

            return true
        }

        return false
    }

    private suspend fun tabulationAlgorithm() = coroutineScope {
        while (isActive) {
            val edge = workList.tryReceive().getOrElse {
                manager.handleControlEvent(queueIsEmpty)
                val edge = workList.receive()
                manager.handleControlEvent(queueIsNotEmpty)
                edge
            }
            tabulationAlgorithmStep(edge, this@coroutineScope)
        }
    }

    private val Method.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private fun tabulationAlgorithmStep(
        currentEdge: Edge<Fact, Statement>,
        scope: CoroutineScope,
    ) {
        val (startVertex, currentVertex) = currentEdge
        val (current, currentFact) = currentVertex

        val currentCallees = graph.callees(current).toList()
        val currentIsCall = current.callExpr != null
        val currentIsExit = current in graph.exitPoints(graph.methodOf(current))

        if (currentIsCall) {
            // Propagate through the call-to-return-site edge:
            for (returnSite in graph.successors(current)) {
                val factsAtReturnSite = flowSpace
                    .obtainCallToReturnSiteFlowFunction(current, returnSite)
                    .compute(currentFact)
                for (returnSiteFact in factsAtReturnSite) {
                    val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                    val newEdge = Edge(startVertex, returnSiteVertex)
                    propagate(newEdge, Reason.Sequent(currentEdge))
                }
            }

            // Propagate through the call:
            for (callee in currentCallees) {
                for (calleeStart in graph.entryPoints(callee)) {
                    val factsAtCalleeStart = flowSpace
                        .obtainCallToStartFlowFunction(current, calleeStart)
                        .compute(currentFact)
                    for (calleeStartFact in factsAtCalleeStart) {
                        val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                        if (callee.isExtern) {
                            // Initialize analysis of callee:
                            for (event in analyzer.handleCrossUnitCall(currentVertex, calleeStartVertex)) {
                                manager.handleEvent(event)
                            }

                            // Subscribe on summary edges:
                            manager.subscribeOnSummaryEdges(callee, scope) { summaryEdge ->
                                if (summaryEdge.from == calleeStartVertex) {
                                    handleSummaryEdge(currentEdge, summaryEdge)
                                } else {
                                    logger.trace { "Skipping unsuitable summary edge: $summaryEdge" }
                                }
                            }
                        } else {
                            // Save info about the call for summary edges that will be found later:
                            callerPathEdgeOf.getOrPut(calleeStartVertex) { hashSetOf() }.add(currentEdge)

                            // Initialize analysis of callee:
                            run {
                                val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                propagate(newEdge, Reason.CallToStart(currentEdge))
                            }

                            // Handle already-found summary edges:
                            for (exitVertex in summaryEdges[calleeStartVertex].orEmpty()) {
                                val summaryEdge = Edge(calleeStartVertex, exitVertex)
                                handleSummaryEdge(currentEdge, summaryEdge)
                            }
                        }
                    }
                }
            }
        } else {
            if (currentIsExit) {
                // Propagate through the summary edge:
                for (callerPathEdge in callerPathEdgeOf[startVertex].orEmpty()) {
                    handleSummaryEdge(currentEdge = callerPathEdge, summaryEdge = currentEdge)
                }

                // Add new summary edge:
                summaryEdges.getOrPut(startVertex) { hashSetOf() }.add(currentVertex)
            }

            // Simple (sequential) propagation to the next instruction:
            for (next in graph.successors(current)) {
                val factsAtNext = flowSpace
                    .obtainSequentFlowFunction(current, next)
                    .compute(currentFact)
                for (nextFact in factsAtNext) {
                    val nextVertex = Vertex(next, nextFact)
                    val newEdge = Edge(startVertex, nextVertex)
                    propagate(newEdge, Reason.Sequent(currentEdge))
                }
            }
        }
    }

    private fun handleSummaryEdge(
        currentEdge: Edge<Fact, Statement>,
        summaryEdge: Edge<Fact, Statement>,
    ) {
        val (startVertex, currentVertex) = currentEdge
        val caller = currentVertex.statement
        for (returnSite in graph.successors(caller)) {
            val (exit, exitFact) = summaryEdge.to
            val finalFacts = flowSpace
                .obtainExitToReturnSiteFlowFunction(caller, returnSite, exit)
                .compute(exitFact)
            for (returnSiteFact in finalFacts) {
                val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                val newEdge = Edge(startVertex, returnSiteVertex)
                propagate(newEdge, Reason.ThroughSummary(currentEdge, summaryEdge))
            }
        }
    }

    private fun getFinalFacts(): Map<Statement, Set<Fact>> {
        val resultFacts: MutableMap<Statement, MutableSet<Fact>> = hashMapOf()
        for (edge in pathEdges) {
            resultFacts.getOrPut(edge.to.statement) { hashSetOf() }.add(edge.to.fact)
        }
        return resultFacts
    }

    override fun getIfdsResult(): IfdsResult<Fact, Statement> {
        val facts = getFinalFacts()
        return IfdsResult(pathEdges, facts, reasons, zeroFact)
    }
}
