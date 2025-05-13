package org.opentaint.ir.analysis.ifds

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import org.opentaint.ir.analysis.graph.JIRNoopInst
import org.opentaint.ir.analysis.taint.Zero
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.cfg.callExpr
import java.util.concurrent.ConcurrentHashMap

private val logger = mu.KotlinLogging.logger {}

interface Runner<Fact> {
    val unit: UnitType

    suspend fun run(startMethods: List<JIRMethod>)
    fun submitNewEdge(edge: Edge<Fact>)
    fun getAggregate(): Aggregate<Fact>
}

class UniRunner<Fact, Event>(
    private val graph: JIRApplicationGraph,
    private val analyzer: Analyzer<Fact, Event>,
    private val manager: Manager<Fact, Event>,
    private val unitResolver: UnitResolver,
    override val unit: UnitType,
) : Runner<Fact> {

    private val flowSpace: FlowFunctions<Fact> = analyzer.flowFunctions
    private val workList: Channel<Edge<Fact>> = Channel(Channel.UNLIMITED)
    private val reasons = ConcurrentHashMap<Edge<Fact>, MutableSet<Reason>>()
    internal val pathEdges: MutableSet<Edge<Fact>> = ConcurrentHashMap.newKeySet()

    private val summaryEdges: MutableMap<Vertex<Fact>, MutableSet<Vertex<Fact>>> = hashMapOf()
    private val callerPathEdgeOf: MutableMap<Vertex<Fact>, MutableSet<Edge<Fact>>> = hashMapOf()

    private val queueIsEmpty = QueueEmptinessChanged(runner = this, isEmpty = true)
    private val queueIsNotEmpty = QueueEmptinessChanged(runner = this, isEmpty = false)

    override suspend fun run(startMethods: List<JIRMethod>) {
        for (method in startMethods) {
            addStart(method)
        }

        tabulationAlgorithm()
    }

    private fun addStart(method: JIRMethod) {
        require(unitResolver.resolve(method) == unit)
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            for (start in graph.entryPoints(method)) {
                val vertex = Vertex(start, startFact)
                val edge = Edge(vertex, vertex) // loop
                val reason = Reason.Initial
                propagate(edge, reason)
            }
        }
    }

    override fun submitNewEdge(edge: Edge<Fact>) {
        // TODO: add default-argument 'reason = Reason.External' to 'submitNewEdge'
        propagate(edge, Reason.External)
    }

    private fun propagate(
        edge: Edge<Fact>,
        reason: Reason,
    ): Boolean {
        require(unitResolver.resolve(edge.method) == unit) {
            "Propagated edge must be in the same unit"
        }

        reasons.computeIfAbsent(edge) { ConcurrentHashMap.newKeySet() }.add(reason)

        // Handle only NEW edges:
        if (pathEdges.add(edge)) {
            val doPrintOnlyForward = true
            val doPrintZero = false
            if (!doPrintOnlyForward || edge.from.statement is JIRNoopInst) {
                if (doPrintZero || edge.to.fact != Zero) {
                    logger.trace { "Propagating edge=$edge in method=${edge.method.name} with reason=${reason}" }
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

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private fun tabulationAlgorithmStep(
        currentEdge: Edge<Fact>,
        scope: CoroutineScope,
    ) {
        val (startVertex, currentVertex) = currentEdge
        val (current, currentFact) = currentVertex

        val currentCallees = graph.callees(current).toList()
        val currentIsCall = current.callExpr != null
        val currentIsExit = current in graph.exitPoints(current.location.method)

        if (currentIsCall) {
            // Propagate through the call-to-return-site edge:
            for (returnSite in graph.successors(current)) {
                val factsAtReturnSite = flowSpace
                    .obtainCallToReturnSiteFlowFunction(current, returnSite)
                    .compute(currentFact)
                for (returnSiteFact in factsAtReturnSite) {
                    val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                    val newEdge = Edge(startVertex, returnSiteVertex)
                    val reason = Reason.Sequent(currentEdge)
                    propagate(newEdge, reason)
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
                                    logger.debug { "Skipping unsuitable summary edge: $summaryEdge" }
                                }
                            }
                        } else {
                            // Save info about the call for summary edges that will be found later:
                            callerPathEdgeOf.getOrPut(calleeStartVertex) { hashSetOf() }.add(currentEdge)

                            // Initialize analysis of callee:
                            run {
                                val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                val reason = Reason.CallToStart(currentEdge)
                                propagate(newEdge, reason)
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
                    val reason = Reason.Sequent(currentEdge)
                    propagate(newEdge, reason)
                }
            }
        }
    }

    private fun handleSummaryEdge(
        currentEdge: Edge<Fact>,
        summaryEdge: Edge<Fact>,
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
                val reason = Reason.ThroughSummary(currentEdge, summaryEdge)
                propagate(newEdge, reason)
            }
        }
    }

    private fun getFinalFacts(): Map<JIRInst, Set<Fact>> {
        val resultFacts: MutableMap<JIRInst, MutableSet<Fact>> = hashMapOf()
        for (edge in pathEdges) {
            resultFacts.getOrPut(edge.to.statement) { hashSetOf() }.add(edge.to.fact)
        }
        return resultFacts
    }

    override fun getAggregate(): Aggregate<Fact> {
        val facts = getFinalFacts()
        return Aggregate(pathEdges, facts, reasons)
    }
}
