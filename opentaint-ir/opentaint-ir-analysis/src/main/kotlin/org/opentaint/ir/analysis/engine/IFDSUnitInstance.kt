package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.AnalysisResult
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import java.util.*

class IFDSUnitInstance<UnitType>(
    private val graph: ApplicationGraph<JIRMethod, JIRInst>,
    private val analyzer: Analyzer,
    private val devirtualizer: Devirtualizer,
    private val context: AnalysisContext,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType
): IFDSInstance {

    private class EdgesStorage {
        private val byStart: MutableMap<IFDSVertex, MutableSet<IFDSEdge>> = mutableMapOf()

        operator fun contains(e: IFDSEdge): Boolean {
            return e in getByStart(e.u)
        }

        fun add(e: IFDSEdge) {
            byStart
                .getOrPut(e.u) { mutableSetOf() }
                .add(e)
        }

        fun getByStart(start: IFDSVertex): Set<IFDSEdge> = byStart.getOrDefault(start, emptySet())

        fun getAll(): Set<IFDSEdge> {
            return byStart.flatMap { it.value.toList() }.toSet()
        }
    }

    private val pathEdges = EdgesStorage()
    private val startToEndEdges = EdgesStorage()
    private val workList: Queue<IFDSEdge> = LinkedList()
    private val summaryEdgeToStartToEndEdges: MutableMap<IFDSEdge, MutableSet<IFDSEdge>> = mutableMapOf()
    private val callSitesOf: MutableMap<IFDSVertex, MutableSet<IFDSEdge>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IFDSEdge, MutableSet<PathEdgePredecessor>> = mutableMapOf()
    private val crossUnitCallees: MutableMap<IFDSVertex, MutableSet<IFDSVertex>> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    private val listeners: MutableList<IFDSInstanceListener> = mutableListOf()

    fun addListener(listener: IFDSInstanceListener) = listeners.add(listener)

    override fun addStart(method: JIRMethod) {
        require(unitResolver.resolve(method) == unit)
        for (sPoint in graph.entryPoint(method)) {
            for (sFact in flowSpace.obtainAllPossibleStartFacts(sPoint)) {
                val vertex = IFDSVertex(sPoint, sFact)
                val edge = IFDSEdge(vertex, vertex)
                propagate(edge, PathEdgePredecessor(edge, NO_PREDECESSOR))
            }
        }
    }

    private fun propagate(e: IFDSEdge, pred: PathEdgePredecessor): Boolean {
        pathEdgesPreds.getOrPut(e) { mutableSetOf() }.add(pred)
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            val isNew =
                pred.kind != SEQUENT && pred.kind != PathEdgePredecessorKind.UNKNOWN || pred.predEdge.v.domainFact != e.v.domainFact
            val predInst =
                pred.predEdge.v.statement.takeIf { it != e.v.statement && it.location.method == e.v.statement.location.method }
            listeners.forEach { it.onPropagate(e, predInst, isNew) }
            return true
        }
        return false
    }

    fun addNewPathEdge(e: IFDSEdge): Boolean {
        return propagate(e, PathEdgePredecessor(e, PathEdgePredecessorKind.UNKNOWN))
    }

    private val JIRMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    fun run() {
        while (!workList.isEmpty()) {
            val curEdge = workList.poll()
            val (u, v) = curEdge
            val (n, d2) = v

            val callees = devirtualizer.findPossibleCallees(n).toList()
            if (callees.isNotEmpty()) {
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    for (fact in flowSpace.obtainCallToReturnFlowFunction(n, returnSite).compute(d2)) {
                        val newEdge = IFDSEdge(u, IFDSVertex(returnSite, fact))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                    for (callee in callees) {
                        val factsAtStart = flowSpace.obtainCallToStartFlowFunction(n, callee).compute(d2)
                        for (sPoint in graph.entryPoint(callee)) {
                            for (sFact in factsAtStart) {

                                val sVertex = IFDSVertex(sPoint, sFact)
                                val exitVertexes = if (callee.isExtern) {
                                    context.summaries[callee]?.factsAtExits?.get(sVertex).orEmpty()
                                } else {
                                    startToEndEdges.getByStart(sVertex).map { it.v }
                                }

                                for ((exitStatement, eFact) in exitVertexes) {
                                    val finalFacts = flowSpace.obtainExitToReturnSiteFlowFunction(n, returnSite, exitStatement).compute(eFact)
                                    for (finalFact in finalFacts) {
                                        val summaryEdge = IFDSEdge(v, IFDSVertex(returnSite, finalFact))
                                        val startToEndEdge = IFDSEdge(IFDSVertex(sPoint, sFact), IFDSVertex(exitStatement, eFact))
                                        val newEdge = IFDSEdge(u, IFDSVertex(returnSite, finalFact))
                                        summaryEdgeToStartToEndEdges.getOrPut(summaryEdge) { mutableSetOf() }.add(startToEndEdge)
                                        propagate(newEdge, PathEdgePredecessor(curEdge, THROUGH_SUMMARY))
                                    }
                                }

                                if (callee.isExtern) {
                                    crossUnitCallees.getOrPut(v) { mutableSetOf() }.add(sVertex)
                                } else {
                                    callSitesOf.getOrPut(sVertex) { mutableSetOf() }.add(curEdge)
                                    val nextEdge = IFDSEdge(sVertex, sVertex)
                                    propagate(nextEdge, PathEdgePredecessor(curEdge, PathEdgePredecessorKind.CALL_TO_START))
                                }
                            }
                        }
                    }
                }
            } else {
                if (n in graph.exitPoints(graph.methodOf(n))) {
                    listeners.forEach { it.onExitPoint(curEdge) }
                    for (predEdge in callSitesOf[u].orEmpty()) {
                        val callerStatement = predEdge.v.statement
                        for (returnSite in graph.successors(callerStatement)) {
                            for (returnSiteFact in flowSpace.obtainExitToReturnSiteFlowFunction(callerStatement, returnSite, n).compute(d2)) {
                                val returnSiteVertex = IFDSVertex(returnSite, returnSiteFact)
                                val newEdge = IFDSEdge(predEdge.u, returnSiteVertex)
                                summaryEdgeToStartToEndEdges.getOrPut(IFDSEdge(predEdge.v, returnSiteVertex)) { mutableSetOf() }.add(curEdge)
                                propagate(newEdge, PathEdgePredecessor(predEdge, THROUGH_SUMMARY))
                            }
                        }
                    }
                    startToEndEdges.add(curEdge)
                }

                val nextInstrs = graph.successors(n)
                for (m in nextInstrs) {
                    val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                    val d3Set = flowFunction.compute(d2)
                    for (d3 in d3Set) {
                        val newEdge = IFDSEdge(u, IFDSVertex(m, d3))
                        propagate(newEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                }
            }
        }
    }

    private val fullResults: IFDSResult by lazy {
        val resultFacts = mutableMapOf<JIRInst, MutableSet<DomainFact>>()

        for (pathEdge in pathEdges.getAll()) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }

        IFDSResult(
            pathEdges.getAll().toList(),
            resultFacts,
            pathEdgesPreds,
            summaryEdgeToStartToEndEdges,
            crossUnitCallees
        )
    }

    override fun analyze(): Map<JIRMethod, IFDSMethodSummary> {
        // TODO: rewrite this method cleaner
        run()

        val methods = fullResults.pathEdges.map { graph.methodOf(it.u.statement) }.distinct()

        val factsAtExits = mutableMapOf<JIRMethod, MutableMap<IFDSVertex, MutableSet<IFDSVertex>>>()
        for (pathEdge in pathEdges.getAll()) {
            val method = graph.methodOf(pathEdge.u.statement)
            if (pathEdge.v.statement in graph.exitPoints(method)) {
                factsAtExits.getOrPut(method) { mutableMapOf() }.getOrPut(pathEdge.u) { mutableSetOf() }.add(pathEdge.v)
            }
        }

        val relevantVulnerabilities = mutableMapOf<JIRMethod, MutableList<VulnerabilityInstance>>()
        analyzer.calculateSources(fullResults).vulnerabilities.forEach {
            relevantVulnerabilities.getOrPut(graph.methodOf(it.realisationsGraph.sink.statement)) { mutableListOf() }
                .add(it)
        }

        val sortedCrossUnitCallees = mutableMapOf<JIRMethod, MutableMap<IFDSVertex, CalleeInfo>>()
        crossUnitCallees.forEach { (callVertex, sVertexes) ->
            val method = graph.methodOf(callVertex.statement)
            sortedCrossUnitCallees.getOrPut(method) { mutableMapOf() }[callVertex] = CalleeInfo(
                sVertexes,
                fullResults.resolveTaintRealisationsGraph(callVertex)
            )
        }
        return methods.associateWith {
            IFDSMethodSummary(
                factsAtExits[it].orEmpty(),
                sortedCrossUnitCallees[it].orEmpty(),
                AnalysisResult(relevantVulnerabilities[it].orEmpty())
            )
        }
    }

    companion object : IFDSInstanceProvider {
        override fun <UnitType> createInstance(
            graph: ApplicationGraph<JIRMethod, JIRInst>,
            analyzer: Analyzer,
            devirtualizer: Devirtualizer,
            context: AnalysisContext,
            unitResolver: UnitResolver<UnitType>,
            unit: UnitType
        ): IFDSInstance {
            return IFDSUnitInstance(graph, analyzer, devirtualizer, context, unitResolver, unit)
        }
    }
}