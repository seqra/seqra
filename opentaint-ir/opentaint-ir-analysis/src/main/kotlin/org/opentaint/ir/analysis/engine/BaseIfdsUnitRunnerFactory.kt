package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a basic [IfdsUnitRunnerFactory], which creates one [BaseIfdsUnitRunner] for each [newRunner] call.
 *
 * @property analyzerFactory used to build [Analyzer] instance, which then will be used by launched [BaseIfdsUnitRunner].
 */
class BaseIfdsUnitRunnerFactory<Method, Location, Statement>(
    private val analyzerFactory: AnalyzerFactory<Method, Location, Statement>
) : IfdsUnitRunnerFactory<Method, Statement> where Location : CoreInstLocation<Method>,
                                                   Statement : CoreInst<Location, Method, *> {
    override fun <UnitType> newRunner(
        graph: ApplicationGraph<Method, Statement>,
        manager: IfdsUnitManager<UnitType, Method, Location, Statement>,
        unitResolver: UnitResolver<UnitType, Method>,
        unit: UnitType,
        startMethods: List<Method>
    ): IfdsUnitRunner<UnitType> {
        val analyzer = analyzerFactory.newAnalyzer(graph)
        return BaseIfdsUnitRunner(graph, analyzer, manager, unitResolver, unit, startMethods)
    }
}

/**
 * Encapsulates launch of tabulation algorithm, described in RHS95, for one unit
 */
private class BaseIfdsUnitRunner<UnitType, Method, Location, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    private val analyzer: Analyzer<Method, Location, Statement>,
    private val manager: IfdsUnitManager<UnitType>,
    private val unitResolver: UnitResolver<UnitType, Method>,
    unit: UnitType,
    private val startMethods: List<Method>
) : AbstractIfdsUnitRunner<UnitType>(unit) where Location : CoreInstLocation<Method>,
                                                 Statement : CoreInst<Location, Method, *> {
    private val pathEdges: MutableSet<IfdsEdge<Method, Location, Statement>> = ConcurrentHashMap.newKeySet()
    private val summaryEdges: MutableMap<IfdsVertex<Method, Location, Statement>, MutableSet<IfdsVertex<Method, Location, Statement>>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex<Method, Location, Statement>, MutableSet<IfdsEdge<Method, Location, Statement>>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge<Method, Location, Statement>, MutableSet<PathEdgePredecessor>> = ConcurrentHashMap()

    private val flowSpace = analyzer.flowFunctions

    /**
     * Queue containing all unprocessed path edges.
     */
    private val workList = Channel<IfdsEdge<Method, Location, Statement>>(Channel.UNLIMITED)

    /**
     * This method should be called each time new path edge is observed.
     * It will check if the edge is new and, if success, add it to [workList]
     * and summarize all [SummaryFact]s produces by the edge.
     *
     * @param edge the new path edge
     * @param pred the description of predecessor of the edge
     */
    private suspend fun propagate(edge: IfdsEdge<Method, Location, Statement>, pred: PathEdgePredecessor): Boolean {
        require(unitResolver.resolve(edge.method) == unit)

        pathEdgesPreds.computeIfAbsent(edge) {
            ConcurrentHashMap.newKeySet()
        }.add(pred)

        if (pathEdges.add(edge)) {
            workList.send(edge)
            analyzer.handleNewEdge(edge).forEach {
                manager.handleEvent(it, this)
            }
            return true
        }
        return false
    }

    private val Method.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    /**
     * Implementation of tabulation algorithm, based on RHS95. It slightly differs from the original in the following:
     *
     * - We do not analyze the whole supergraph (represented by [graph]), but only the methods that belong to our [unit];
     * - Path edges are added to [workList] not only by the main cycle, but they can also be obtained from [manager];
     * - By summary edge we understand the path edge from the start node of the method to its exit node;
     * - The supergraph is explored dynamically, and we do not inverse flow functions when new summary edge is found, i.e.
     * the extension from Chapter 4 of NLR10 is implemented.
     */
    private suspend fun runTabulationAlgorithm(): Unit = coroutineScope {
        while (isActive) {
            val curEdge = workList.tryReceive().getOrNull() ?: run {
                manager.handleEvent(QueueEmptinessChanged(true), this@BaseIfdsUnitRunner)
                workList.receive().also {
                    manager.handleEvent(QueueEmptinessChanged(false), this@BaseIfdsUnitRunner)
                }
            }

            val (u, v) = curEdge
            val (curVertex, curFact) = v

            val callees = graph.callees(curVertex).toList()
            val curVertexIsCall = callees.isNotEmpty()
            val curVertexIsExit = curVertex in graph.exitPoints(graph.methodOf(curVertex))

            if (curVertexIsCall) {
                for (returnSite in graph.successors(curVertex)) {
                    // Propagating through call-to-return-site edges (in RHS95 it is done in lines 17-19)
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(curVertex, returnSite).compute(curFact)) {
                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.Sequent))
                    }

                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(curVertex, callee).compute(curFact)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {
                                val sVertex = IfdsVertex(sPoint, sFact)

                                val handleExitVertex: suspend (IfdsVertex<Method, Location, Statement>) -> Unit =
                                    { (eStatement, eFact) ->
                                        val finalFacts = flowSpace
                                            .obtainExitToReturnSiteFlowFunction(curVertex, returnSite, eStatement)
                                            .compute(eFact)
                                        for (finalFact in finalFacts) {
                                            val summaryEdge =
                                                IfdsEdge(IfdsVertex(sPoint, sFact), IfdsVertex(eStatement, eFact))
                                            val newEdge = IfdsEdge(u, IfdsVertex(returnSite, finalFact))
                                            propagate(
                                                newEdge,
                                                PathEdgePredecessor(
                                                    curEdge,
                                                    PredecessorKind.ThroughSummary(summaryEdge)
                                                )
                                            )
                                        }
                                    }

                                if (callee.isExtern) {
                                    // Notify about cross-unit call
                                    analyzer.handleNewCrossUnitCall(CrossUnitCallFact(v, sVertex)).forEach {
                                        manager.handleEvent(it, this@BaseIfdsUnitRunner)
                                    }

                                    // Waiting for exit vertices and handling them
                                    val exitVertices = flow {
                                        manager.handleEvent(
                                            SubscriptionForSummaryEdges(callee, this@flow),
                                            this@BaseIfdsUnitRunner
                                        )
                                    }
                                    exitVertices
                                        .filter { it.u == sVertex }
                                        .map { it.v }
                                        .onEach(handleExitVertex)
                                        .launchIn(this)
                                } else {
                                    // Save info about call for summary-facts that will be found later
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)

                                    // Initiating analysis for callee
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PredecessorKind.CallToStart))

                                    // Handling already-found summary edges
                                    // .toList() is needed below to avoid ConcurrentModificationException
                                    for (exitVertex in summaryEdges[sVertex].orEmpty().toList()) {
                                        handleExitVertex(exitVertex)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (curVertexIsExit) {
                    // Propagating through newly found summary edge, similar to lines 22-31 of RHS95
                    // TODO: rewrite this in a more reactive way
                    for (callerEdge in callSitesOf[u].orEmpty()) {
                        val callerStatement = callerEdge.v.statement
                        for (returnSite in graph.successors(callerStatement)) {
                            for (returnSiteFact in flowSpace.obtainExitToReturnSiteFlowFunction(callerStatement, returnSite, curVertex).compute(curFact)) {
                                val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                                val newEdge = IfdsEdge(callerEdge.u, returnSiteVertex)
                                propagate(newEdge, PathEdgePredecessor(callerEdge, PredecessorKind.ThroughSummary(curEdge)))
                            }
                        }
                    }
                    summaryEdges.getOrPut(curEdge.u) { mutableSetOf() }.add(curEdge.v)
                }

                // Simple propagation through intraprocedural edge, as in lines 34-36 of RHS95
                // Note that generally speaking, exit vertices may have successors (in case of exceptional flow, etc.),
                // so this part should be done for exit vertices as well
                for (nextInst in graph.successors(curVertex)) {
                    val nextFacts = flowSpace.obtainSequentFlowFunction(curVertex, nextInst).compute(curFact)
                    for (nextFact in nextFacts) {
                        val newEdge = IfdsEdge(u, IfdsVertex(nextInst, nextFact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.Sequent))
                    }
                }
            }
        }
    }

    private val ifdsResult: IfdsResult by lazy {
        val allEdges = pathEdges.toList()

        val resultFacts = allEdges.groupBy({ it.v.statement }) {
            it.v.domainFact
        }.mapValues { (_, facts) -> facts.toSet() }

        IfdsResult(allEdges, resultFacts, pathEdgesPreds)
    }

    /**
     * Performs some initialization and runs tabulation algorithm, sending all relevant events to [manager].
     */
    override suspend fun run() = coroutineScope {
        try {
            // Adding initial facts to workList
            for (method in startMethods) {
                require(unitResolver.resolve(method) == unit)
                for (sPoint in graph.entryPoint(method)) {
                    for (sFact in flowSpace.obtainPossibleStartFacts(sPoint)) {
                        val vertex = IfdsVertex(sPoint, sFact)
                        val edge = IfdsEdge<Method, Location, Statement>(vertex, vertex)
                        propagate(edge, PathEdgePredecessor(edge, PredecessorKind.NoPredecessor))
                    }
                }
            }

            runTabulationAlgorithm()
        } finally {
            withContext(NonCancellable) {
                analyzer.handleIfdsResult(ifdsResult).forEach {
                    manager.handleEvent(it, this@BaseIfdsUnitRunner)
                }
            }
        }
    }

    override suspend fun submitNewEdge(edge: IfdsEdge) {
        propagate(edge, PathEdgePredecessor(edge, PredecessorKind.Unknown))
    }
}