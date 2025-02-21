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

private class IfdsInstance<UnitType>(
    private val graph: ApplicationGraph<JIRMethod, JIRInst>,
    private val analyzer: Analyzer,
    private val summary: Summary,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType,
    private val startMethods: List<JIRMethod>
) {

    private val pathEdges: MutableSet<IfdsEdge> = ConcurrentHashMap.newKeySet()
    private val summaryEdges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<IfdsVertex, MutableSet<IfdsEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IfdsEdge, MutableSet<PathEdgePredecessor>> = ConcurrentHashMap()
    private val verticesWithTraceGraphNeeded: MutableSet<IfdsVertex> = ConcurrentHashMap.newKeySet()
    private val summaryHandlers: MutableMap<JIRMethod, SummarySender> = ConcurrentHashMap()
    private val visitedMethods: MutableSet<JIRMethod> = mutableSetOf()

    private val flowSpace get() = analyzer.flowFunctions

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

    private fun addStartMethod(method: JIRMethod) {
        require(unitResolver.resolve(method) == unit)
        for (sPoint in graph.entryPoint(method)) {
            for (sFact in flowSpace.obtainPossibleStartFacts(sPoint)) {
                val vertex = IfdsVertex(sPoint, sFact)
                val edge = IfdsEdge(vertex, vertex)
                propagate(edge, PathEdgePredecessor(edge, PredecessorKind.NoPredecessor))
            }
        }
    }

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

    private val workList = Channel<IfdsEdge>(Channel.UNLIMITED)

    private suspend fun runTabulationAlgorithm(): Unit = coroutineScope {
        startMethods.forEach { addStartMethod(it) }

        while (isActive) {
            val curEdge = workList.tryReceive().getOrNull() ?: throw EmptyQueueCancellationException

            if (visitedMethods.add(curEdge.method)) {
                summary // Listen for incoming updates
                    .getFactsFiltered<PathEdgeFact>(curEdge.method, null)
                    .filter { it.edge.u.statement in graph.entryPoint(curEdge.method) } // Filter out backward edges
                    .onEach { propagate(it.edge, PathEdgePredecessor(it.edge, PredecessorKind.Unknown)) }
                    .launchIn(this)
            }

            val (u, v) = curEdge
            val (curVertex, curFact) = v

            val callees = graph.callees(curVertex).toList()
            if (callees.isNotEmpty()) {
                for (returnSite in graph.successors(curVertex)) {
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(curVertex, returnSite).compute(curFact)) {
                        val newEdge = IfdsEdge(u, IfdsVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, PredecessorKind.Sequent))
                    }
                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(curVertex, callee).compute(curFact)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {
                                val sVertex = IfdsVertex(sPoint, sFact)

                                val exitVertices: Flow<IfdsVertex> = if (callee.isExtern) {
//                                    stashSummaryFact(PathEdgeFact(IfdsEdge(sVertex, sVertex))) // Requesting info for this start fact
                                    summary.getFactsFiltered<PathEdgeFact>(callee, sVertex)
                                        .filter { it.edge.u == sVertex && it.edge.v.statement in graph.exitPoints(callee) }
                                        .map { it.edge.v }
                                } else {
                                    val nextEdge = IfdsEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PredecessorKind.CallToStart))
                                    // .toList() is needed below to avoid ConcurrentModificationException
                                    summaryEdges[sVertex].orEmpty().toList().asFlow()
                                }

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
                if (curVertex in graph.exitPoints(graph.methodOf(curVertex))) {
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

    private val fullResults: IfdsResult by lazy {
        val allEdges = pathEdges.toList()

        val resultFacts = allEdges.groupBy({ it.v.statement }) {
            it.v.domainFact
        }.mapValues { (_, facts) -> facts.toSet() }

        IfdsResult(allEdges, resultFacts, pathEdgesPreds)
    }

    private fun postActions() {
        analyzer.getSummaryFactsPostIfds(fullResults).forEach { vulnerability ->
            stashSummaryFact(vulnerability)
        }

        verticesWithTraceGraphNeeded.forEach { vertex ->
            val graph = fullResults.resolveTraceGraph(vertex)
            stashSummaryFact(TraceGraphFact(graph))
        }
    }

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