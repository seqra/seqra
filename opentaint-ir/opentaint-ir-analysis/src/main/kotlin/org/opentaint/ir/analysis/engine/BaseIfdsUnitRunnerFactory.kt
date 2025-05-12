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
import mu.KotlinLogging
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.ext.cfg.callExpr
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * This is a basic [IfdsUnitRunnerFactory], which creates one [BaseIfdsUnitRunner] for each [newRunner] call.
 *
 * @property analyzerFactory used to build [Analyzer] instance, which then will be used by launched [BaseIfdsUnitRunner].
 */
class BaseIfdsUnitRunnerFactory(
    private val analyzerFactory: AnalyzerFactory,
) : IfdsUnitRunnerFactory {
    override fun newRunner(
        graph: JIRApplicationGraph,
        manager: IfdsUnitManager,
        unitResolver: UnitResolver,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    ): IfdsUnitRunner {
        val analyzer = analyzerFactory.newAnalyzer(graph)
        return BaseIfdsUnitRunner(graph, analyzer, manager, unitResolver, unit, startMethods)
    }
}

/**
 * Encapsulates a launch of tabulation algorithm, described in RHS'95, for one unit.
 */
internal class BaseIfdsUnitRunner(
    private val graph: JIRApplicationGraph,
    private val analyzer: Analyzer,
    private val manager: IfdsUnitManager,
    private val unitResolver: UnitResolver,
    unit: UnitType,
    private val startMethods: List<JIRMethod>,
) : AbstractIfdsUnitRunner(unit) {

    internal val pathEdges: MutableSet<IfdsEdge> = ConcurrentHashMap.newKeySet()
    private val summaryEdges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = ConcurrentHashMap()

    private val flowSpace = analyzer.flowFunctions

    /**
     * Queue containing all unprocessed path edges.
     */
    private val workList = Channel<IfdsEdge>(Channel.UNLIMITED)

    /**
     * This method should be called each time new path edge is observed.
     * It will check if the edge is new and, if success, add it to [workList]
     * and summarize all [SummaryFact]s produces by the edge.
     *
     * @param edge the new path edge
     * @param pred the description of predecessor of the edge
     */
    private suspend fun propagate(
        edge: IfdsEdge,
        pred: PathEdgePredecessor,
    ): Boolean {
        require(unitResolver.resolve(edge.method) == unit)

        pathEdgesPreds.computeIfAbsent(edge) { ConcurrentHashMap.newKeySet() }.add(pred)

        // Update edge's reason:
        edge.reason = pred.predEdge

        if (pathEdges.add(edge)) {
            if (edge.from.statement.toString() == "noop" && edge.to.domainFact != ZEROFact) {
                logger.trace { "Propagating $edge in ${edge.method} via ${edge.reason}" }
            }
            workList.send(edge)
            analyzer.handleNewEdge(edge).forEach {
                manager.handleEvent(it, this)
            }
            return true
        }
        return false
    }

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    /**
     * Implementation of tabulation algorithm, based on RHS'95.
     *
     * It slightly differs from the original in the following:
     *
     * - We do not analyze the whole supergraph (represented by [graph]), but only the methods that belong to our [unit];
     * - Path edges are added to [workList] not only by the main cycle, but they can also be obtained from [manager];
     * - By "summary edge" we understand the path edge from the start node of the method to its exit node.
     * - The supergraph is explored dynamically, and we do not inverse flow functions when new summary edge is found,
     *   i.e. the extension from Chapter 4 of NLR'10 is implemented.
     */
    private suspend fun runTabulationAlgorithm(): Unit = coroutineScope {
        while (isActive) {
            val currentEdge = workList.tryReceive().getOrNull() ?: run {
                manager.handleEvent(QueueEmptinessChanged(true), this@BaseIfdsUnitRunner)
                workList.receive().also {
                    manager.handleEvent(QueueEmptinessChanged(false), this@BaseIfdsUnitRunner)
                }
            }

            val (startVertex, currentVertex) = currentEdge
            val (current, currentFact) = currentVertex

            val currentCallees = graph.callees(current).toList()
            // val currentIsCall = currentCallees.isNotEmpty()
            val currentIsCall = current.callExpr != null
            val currentIsExit = current in graph.exitPoints(graph.methodOf(current))

            if (currentIsCall) {
                // Propagating through the call-to-return-site edge (lines 17-19 in RHS'95).
                //
                //   START main :: (s, d1)
                //    |
                //    | (path edge)
                //    |
                //   CALL p :: (n, d2)
                //    :
                //    : (call-to-return-site edge)
                //    :
                //   RETURN FROM p :: (ret(n), d3)
                //
                // New path edge:
                //   (s -> n) + (n -> ret(n)) ==> (s -> ret(n))
                //
                // Below:
                //   startVertex == (s, d1)
                //   currentVertex = (current, currentFact) == (n, d2)
                //   returnSiteVertex = (returnSite, returnSiteFact) == (ret(n), d3)
                //   newEdge == ((s,d1) -> (ret(n), d3))
                //
                for (returnSite in graph.successors(current)) {
                    val factsAtReturnSite = flowSpace
                        .obtainCallToReturnFlowFunction(current, returnSite, graph)
                        .compute(currentFact)
                    for (returnSiteFact in factsAtReturnSite) {
                        val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                        val newEdge = IfdsEdge(startVertex, returnSiteVertex)
                        val predecessor = PathEdgePredecessor(currentEdge, PredecessorKind.Sequent)
                        propagate(newEdge, predecessor)
                    }
                }

                // Propagating through the call.
                //
                //   START main :: (s, d1)
                //    |
                //   CALL p :: (n, d2)
                //    : \
                //    :  \
                //    :  START p :: (s_p, d3)
                //    :   |
                //    :  EXIT p :: (e_p, d4)
                //    :  /
                //    : /
                //   RETURN FROM p :: (ret(n), d5)
                //
                // New path edge:
                //   (s -> n) + (n -> s_p) + (s_p ~> e_p) + (e_p -> ret(n)) ==> (s -> ret(n))
                //
                // Below:
                //   startVertex == (s, d1)
                //   currentVertex = (current, currentFact) == (n, d2)
                //   calleeStartVertex = (calleeStart, calleeStartFact) == (s_p, d3)
                //   exitVertex = (exit, exitFact) == (e_p, d4)
                //   returnSiteVertex = (returnSite, returnSiteFact) == (ret(n), d5)
                //   newEdge == ((s, d1) -> (ret(n), d5))
                //
                for (callee in currentCallees) {
                    val factsAtCalleeStart = flowSpace
                        .obtainCallToStartFlowFunction(current, callee)
                        .compute(currentFact)
                    for (calleeStartFact in factsAtCalleeStart) {
                        for (calleeStart in graph.entryPoints(callee)) {
                            val calleeStartVertex = IfdsVertex(calleeStart, calleeStartFact)

                            // Handle callee exit vertex:
                            val handleExitVertex: suspend (IfdsVertex) -> Unit =
                                { (exit, exitFact) ->
                                    val exitVertex = IfdsVertex(exit, exitFact)
                                    for (returnSite in graph.successors(current)) {
                                        val finalFacts = flowSpace
                                            .obtainExitToReturnSiteFlowFunction(current, returnSite, exit)
                                            .compute(exitFact)
                                        for (returnSiteFact in finalFacts) {
                                            val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                                            val newEdge = IfdsEdge(startVertex, returnSiteVertex)
                                            val summaryEdge = IfdsEdge(calleeStartVertex, exitVertex)
                                            val predecessor = PathEdgePredecessor(
                                                currentEdge,
                                                PredecessorKind.ThroughSummary(summaryEdge)
                                            )
                                            propagate(newEdge, predecessor)
                                        }
                                    }
                                }

                            if (callee.isExtern) {
                                // Notify about the cross-unit call:
                                analyzer
                                    .handleNewCrossUnitCall(CrossUnitCallFact(currentVertex, calleeStartVertex))
                                    .forEach { event ->
                                        manager.handleEvent(event, this@BaseIfdsUnitRunner)
                                    }

                                // Wait (asynchronously, via Flow) for summary edges and handle them:
                                val summaries = flow {
                                    val event = SubscriptionForSummaryEdges(callee, this@flow)
                                    manager.handleEvent(event, this@BaseIfdsUnitRunner)
                                }
                                summaries
                                    .filter { it.from == calleeStartVertex }
                                    .map { it.to }
                                    .onEach(handleExitVertex)
                                    .launchIn(this)
                            } else {
                                // Save info about the call for summary-facts that will be found later:
                                callSitesOf.getOrPut(calleeStartVertex) { mutableSetOf() }.add(currentEdge)

                                // Initiate analysis for callee:
                                val newEdge = IfdsEdge(calleeStartVertex, calleeStartVertex)
                                val predecessor = PathEdgePredecessor(currentEdge, PredecessorKind.CallToStart)
                                propagate(newEdge, predecessor)

                                // Handle already-found summary edges:
                                // Note: `.toList()` is needed below to avoid ConcurrentModificationException
                                val exits = summaryEdges[calleeStartVertex].orEmpty().toList()
                                for (vertex in exits) {
                                    handleExitVertex(vertex)
                                }
                            }
                        }
                    }
                }
            } else {
                if (currentIsExit) {
                    // Propagating through the newly found summary edge (lines 22-31 of RHS'95).
                    //
                    //   START outer :: (s_c, d3)
                    //    |
                    //   CALL p :: (c, d4)
                    //    : \
                    //    :  \
                    //    :  START p :: (s_p, d1)
                    //    :   |
                    //    :  EXIT p :: (e_p, d2)
                    //    :  /
                    //    : /
                    //   RETURN FROM p :: (ret(c), d5)
                    //
                    // New path edge:
                    //   (s -> e) + (c -> s_p) + (c -> ret(c)) + (s_c -> c) ==> (s_c -> ret(c))
                    //
                    // Below:
                    //   startVertex == (s_p, d1)
                    //   currentVertex = (current, currentFact) == (e_p, d2)
                    //   callerPathEdge == ((s_c, d3) -> (c, d4))
                    //   callerVertex = (caller, callerFact) == (c, d4)
                    //   returnSiteVertex = (returnSite, returnSiteFact) == (ret(c), d5)
                    //   newEdge == ((s_p, d3) -> (ret(n), d5))
                    //
                    // TODO: rewrite this in a more reactive way (?)
                    for (callerPathEdge in callSitesOf[startVertex].orEmpty()) {
                        val caller = callerPathEdge.to.statement
                        for (returnSite in graph.successors(caller)) {
                            val factsAtReturnSite = flowSpace
                                .obtainExitToReturnSiteFlowFunction(caller, returnSite, current)
                                .compute(currentFact)
                            for (returnSiteFact in factsAtReturnSite) {
                                val returnSiteVertex = IfdsVertex(returnSite, returnSiteFact)
                                val newEdge = IfdsEdge(callerPathEdge.from, returnSiteVertex)
                                val predecessor = PathEdgePredecessor(
                                    callerPathEdge,
                                    PredecessorKind.ThroughSummary(currentEdge)
                                )
                                propagate(newEdge, predecessor)
                            }
                        }
                    }
                    summaryEdges.getOrPut(startVertex) { mutableSetOf() }.add(currentVertex)
                }

                // Simple propagation through the intra-procedural edge (lines 34-36 of RHS'95).
                // Note that generally speaking, exit vertices may have successors (in case of exceptional flow, etc.),
                // so this part should be done for exit vertices as well.
                //
                //   START main :: (s, d1)
                //    |
                //    | (path edge)
                //    |
                //   INSTRUCTION :: (n, d2)
                //    |
                //    | (path edge)
                //    |
                //   INSTRUCTION :: (m, d3)
                //
                // New path edge:
                //   (s -> n) + (n -> m) ==> (s -> m)
                //
                // Below:
                //   startVertex == (s, d1)
                //   currentVertex == (current, currentFact) == (n, d2)
                //   nextVertex == (next, nextFact) == (m, d3)
                //   newEdge = (startVertex, nextVertex) == ((s,d1) -> (m, d3))
                //
                for (next in graph.successors(current)) {
                    val factsAtNext = flowSpace
                        .obtainSequentFlowFunction(current, next)
                        .compute(currentFact)
                    for (nextFact in factsAtNext) {
                        val nextVertex = IfdsVertex(next, nextFact)
                        val newEdge = IfdsEdge(startVertex, nextVertex)
                        val predecessor = PathEdgePredecessor(currentEdge, PredecessorKind.Sequent)
                        propagate(newEdge, predecessor)
                    }
                }
            }
        }
    }

    private val ifdsResult: IfdsResult by lazy {
        val allEdges = pathEdges.toList()
        val resultFacts = allEdges
            .groupBy({ it.to.statement }, { it.to.domainFact })
            .mapValues { (_, facts) -> facts.toSet() }
        IfdsResult(allEdges, resultFacts, pathEdgesPreds)
    }

    /**
     * Performs some initialization and runs the tabulation algorithm, sending all relevant events to the [manager].
     */
    override suspend fun run() = coroutineScope {
        try {
            // Add initial facts to workList:
            for (method in startMethods) {
                require(unitResolver.resolve(method) == unit)
                for (startStatement in graph.entryPoints(method)) {
                    val startFacts = flowSpace.obtainPossibleStartFacts(startStatement)
                    for (startFact in startFacts) {
                        val vertex = IfdsVertex(startStatement, startFact)
                        val edge = IfdsEdge(vertex, vertex) // loop
                        val predecessor = PathEdgePredecessor(edge, PredecessorKind.NoPredecessor)
                        propagate(edge, predecessor)
                    }
                }
            }

            // Run the tabulation algorithm:
            runTabulationAlgorithm()
        } finally {
            logger.info { "Finishing ${this@BaseIfdsUnitRunner} for $unit" }
            logger.info { "Total ${pathEdges.size} path edges for $unit using $analyzer" }
            // for ((i, edge) in pathEdges.sortedBy { it.toString() }.withIndex()) {
            //     logger.debug { " - [${i + 1}/${pathEdges.size}] $edge" }
            // }

            // Post-process left-over events:
            withContext(NonCancellable) {
                analyzer.handleIfdsResult(ifdsResult).forEach {
                    manager.handleEvent(it, this@BaseIfdsUnitRunner)
                }
            }
        }
    }

    override suspend fun submitNewEdge(edge: IfdsEdge) {
        val predecessor = PathEdgePredecessor(edge, PredecessorKind.Unknown)
        propagate(edge, predecessor)
    }
}
