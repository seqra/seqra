package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import java.util.concurrent.ConcurrentHashMap

/**
 * This is a basic [IfdsUnitRunner], which launches one [IfdsInstance] for each [run] call.
 *
 * @property analyzerFactory used to build [Analyzer] instance, which then will be used by launched [IfdsInstance].
 */
class IfdsBaseUnitRunner(private val analyzerFactory: AnalyzerFactory) : IfdsUnitRunner {
    override suspend fun <UnitType> run(
        graph: JIRApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JIRMethod>
    ) {
        val analyzer = analyzerFactory.newAnalyzer(graph)
        val instance = IfdsInstance(graph, analyzer, summary, unitResolver, unit, startMethods)
        instance.analyze()
    }
}

/**
 * Encapsulates launch of tabulation algorithm, described in RHS95, for one unit
 */
private class IfdsInstance<UnitType>(
    private val graph: ApplicationGraph<JIRMethod, JIRInst>,
    private val analyzer: Analyzer,
    private val summary: Summary,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType,
    startMethods: List<JIRMethod>
) {

    private val pathEdges: MutableSet<IfdsEdge> = ConcurrentHashMap.newKeySet()
    private val summaryEdges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = ConcurrentHashMap()
    private val verticesWithTraceGraphNeeded: MutableSet<IfdsVertex> = ConcurrentHashMap.newKeySet()
    private val summaryHandlers: MutableMap<JIRMethod, SummarySender> = ConcurrentHashMap()
    private val visitedMethods: MutableSet<JIRMethod> = mutableSetOf()

    private val flowSpace get() = analyzer.flowFunctions

    /**
     * Queue containing all unprocessed path edges.
     */
    private val workList = Channel<IfdsEdge>(Channel.UNLIMITED)

    init {
        // Adding initial facts to workList
        for (method in startMethods) {
            require(unitResolver.resolve(method) == unit)
            for (sPoint in graph.entryPoint(method)) {
                for (sFact in flowSpace.obtainPossibleStartFacts(sPoint)) {
                    val vertex = IfdsVertex(sPoint, sFact)
                    val edge = IfdsEdge(vertex, vertex)
                    propagate(edge, PathEdgePredecessor(edge, PredecessorKind.NoPredecessor))
                }
            }
        }
    }

    /**
     * Sends given [fact] to [summary], also remembering to restore [TraceGraph], if needed.
     */
    private fun stashSummaryFact(fact: SummaryFact) {
        val handler = summaryHandlers.computeIfAbsent(fact.method) { summary.createSender(fact.method) }
        handler.send(fact)
        if (fact is VulnerabilityLocation) {
            verticesWithTraceGraphNeeded.add(fact.sink)
        }
        if (fact is CrossUnitCallFact) {
            verticesWithTraceGraphNeeded.add(fact.callerVertex)
        }
    }

    /**
     * This method should be called each time new path edge is observed.
     * It will check if the edge is new and, if success, add it to [workList]
     * and summarize all [SummaryFact]s produces by the edge.
     *
     * @param e the new path edge
     * @param pred the description of predecessor of the edge
     */
    private fun propagate(e: IfdsEdge, pred: PathEdgePredecessor): Boolean {
        pathEdgesPreds.computeIfAbsent(e) {
            ConcurrentHashMap.newKeySet()
        }.add(pred)

        if (pathEdges.add(e)) {
            require(workList.trySend(e).isSuccess)

            analyzer.getSummaryFacts(e).forEach {
                stashSummaryFact(it)
            }

            if (analyzer.saveSummaryEdgesAndCrossUnitCalls && e.v.statement in graph.exitPoints(e.method)) {
                stashSummaryFact(PathEdgeFact(e))
            }

            return true
        }
        return false
    }

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    /**
     * Implementation of tabulation algorithm, based on RHS95. It slightly differs from the original in the following:
     *
     * - We do not analyze the whole supergraph (represented by [graph]), but only the methods that belong to our [unit];
     * - Path edges are added to [workList] not only by the main cycle, but they can also be obtained from [summary];
     * - By summary edge we understand the path edge from the start node of the method to its exit node;
     * - The supergraph is explored dynamically, and we do not inverse flow functions when new summary edge is found, i.e.
     * the extension from Chapter 4 of NLR10 is implemented.
     */
    private suspend fun runTabulationAlgorithm(): Unit = coroutineScope {
        while (isActive) {
            val curEdge = workList.tryReceive().getOrNull() ?: throw EmptyQueueCancellationException

            if (visitedMethods.add(curEdge.method)) {
                // Listen for incoming updates
                summary
                    .getFactsFiltered<PathEdgeFact>(curEdge.method, null)
                    .filter { it.edge.u.statement in graph.entryPoint(curEdge.method) } // Filter out backward edges
                    .onEach { propagate(it.edge, PathEdgePredecessor(it.edge, PredecessorKind.Unknown)) }
                    .launchIn(this)
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

                                // Requesting to analyze callee from sVertex, similar to lines 14-16 of RHS95
                                // Also, receiving summary edges for callee that start from sVertex
                                val exitVertices: Flow<IfdsVertex> = if (callee.isExtern) {
//                                    stashSummaryFact(PathEdgeFact(IfdsEdge(sVertex, sVertex)))
                                    summary.getFactsFiltered<PathEdgeFact>(callee, sVertex)
                                        .filter { it.edge.u == sVertex && it.edge.v.statement in graph.exitPoints(callee) }
                                        .map { it.edge.v }
                                } else {
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PredecessorKind.CallToStart))

                                    // .toList() is needed below to avoid ConcurrentModificationException
                                    summaryEdges[sVertex].orEmpty().toList().asFlow()
                                }

                                // Propagation through summary edges
                                exitVertices.onEach { (eStatement, eFact) ->
                                    val finalFacts =
                                        flowSpace.obtainExitToReturnSiteFlowFunction(curVertex, returnSite, eStatement)
                                            .compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IfdsEdge(IfdsVertex(sPoint, sFact), IfdsVertex(eStatement, eFact))
                                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, finalFact))
                                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.ThroughSummary(summaryEdge)))
                                    }
                                }.launchIn(this)

                                // Saving additional info
                                if (callee.isExtern) {
                                    if (analyzer.saveSummaryEdgesAndCrossUnitCalls) {
//                                        launch {
//                                            summary.getFactsFiltered<TraceGraphFact>(callee, null)
//                                                .filter { sVertex in it.graph.edges.keys }
//                                                .first()
//                                            stashSummaryFact(CrossUnitCallFact(v, sVertex))
//                                        }
                                        stashSummaryFact(CrossUnitCallFact(v, sVertex))
                                    }
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
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
            yield()
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
     * Finds summary facts that need [IfdsResult] and restores traces for all vertices that need it
     */
    private fun postActions() {
        analyzer.getSummaryFactsPostIfds(ifdsResult).forEach { vulnerability ->
            stashSummaryFact(vulnerability)
        }

        verticesWithTraceGraphNeeded.forEach { vertex ->
            val graph = ifdsResult.resolveTraceGraph(vertex)
            stashSummaryFact(TraceGraphFact(graph))
        }
    }

    /**
     * Runs tabulation algorithm and updates [summary] with everything that is relevant.
     */
    suspend fun analyze() = coroutineScope {
        try {
            runTabulationAlgorithm()
        } catch (_: EmptyQueueCancellationException) {}
        finally {
            postActions()
        }
    }
}

private object EmptyQueueCancellationException: CancellationException()