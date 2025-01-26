package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.AnalysisEngine
import org.opentaint.ir.analysis.DumpableAnalysisResult
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.CALL_TO_START
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.UNKNOWN
import org.opentaint.ir.analysis.points2.Devirtualizer
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import java.util.*

class IFDSInstance(
    private val graph: ApplicationGraph<JIRMethod, JIRInst>,
    private val analyzer: Analyzer,
    private val devirtualizer: Devirtualizer
): AnalysisEngine {

    private class EdgesStorage {
        private val byStart: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
        private val byEnd:   MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
        operator fun contains(e: IFDSEdge<DomainFact>): Boolean {
            return e in getByStart(e.u)
        }

        fun add(e: IFDSEdge<DomainFact>) {
            byStart
                .getOrPut(e.u) { mutableSetOf() }
                .add(e)

            byEnd
                .getOrPut(e.v) { mutableSetOf() }
                .add(e)
        }

        fun getByStart(start: IFDSVertex<DomainFact>): Set<IFDSEdge<DomainFact>> = byStart.getOrDefault(start, emptySet())
        fun getByEnd(end: IFDSVertex<DomainFact>): Set<IFDSEdge<DomainFact>> = byEnd.getOrDefault(end, emptySet())

        fun getAll(): Set<IFDSEdge<DomainFact>> {
            return byStart.flatMap { it.value.toList() }.toSet()
        }
    }

    private val pathEdges = EdgesStorage()
    private val startToEndEdges = EdgesStorage()
    private val workList: Queue<IFDSEdge<DomainFact>> = LinkedList()
    private val summaryEdges = EdgesStorage()
    private val summaryEdgeToStartToEndEdges: MutableMap<IFDSEdge<DomainFact>, MutableSet<IFDSEdge<DomainFact>>> = mutableMapOf()
    private val callSitesOf: MutableMap<IFDSVertex<DomainFact>, MutableSet<IFDSVertex<DomainFact>>> = mutableMapOf()
    private val pathEdgesPreds: MutableMap<IFDSEdge<DomainFact>, MutableSet<PathEdgePredecessor<DomainFact>>> = mutableMapOf()

    private val flowSpace get() = analyzer.flowFunctions

    private val listeners: MutableList<IFDSInstanceListener> = mutableListOf()

    // Returns true if summary edge is new
    private fun addNewSummaryEdge(summaryEdge: IFDSEdge<DomainFact>, startToEndEdge: IFDSEdge<DomainFact>): Boolean {
        val result = summaryEdge !in summaryEdges
        summaryEdges.add(summaryEdge)
        summaryEdgeToStartToEndEdges.getOrPut(summaryEdge) { mutableSetOf() }.add(startToEndEdge)
        return result
    }

    fun addListener(listener: IFDSInstanceListener) = listeners.add(listener)

    override fun addStart(method: JIRMethod) {
        val entryPoints = graph.entryPoint(method)

        for (entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = IFDSVertex(entryPoint, fact)
                val startU = IFDSVertex(entryPoint, ZEROFact)
                val startE = IFDSEdge(startU, startV)
                propagate(startE, PathEdgePredecessor(startE, NO_PREDECESSOR))
            }
        }
    }

    private fun propagate(e: IFDSEdge<DomainFact>, pred: PathEdgePredecessor<DomainFact>): Boolean {
        pathEdgesPreds.getOrPut(e) { mutableSetOf() }.add(pred)
        if (e !in pathEdges) {
            pathEdges.add(e)
            workList.add(e)
            val isNew = pred.kind != SEQUENT || pred.predEdge.v.domainFact != e.v.domainFact
            val predInst = pred.predEdge.v.statement.takeIf { it != e.v.statement && it.location.method == e.v.statement.location.method }
            listeners.forEach { it.onPropagate(e, predInst, isNew) }
            return true
        }
        return false
    }

    fun addNewPathEdge(e: IFDSEdge<DomainFact>): Boolean {
        return propagate(e, PathEdgePredecessor(e, UNKNOWN))
    }

    // Build summary edges of form (caller, d4) -> (*, *)
    private fun findNewSummaryEdges(callSite: JIRInst, d4: DomainFact, startToEndEdge: IFDSEdge<DomainFact>) {
        val (ep, d2) = startToEndEdge.v

        val returnSitesOfCaller = graph.successors(callSite)
        for (returnSiteOfCaller in returnSitesOfCaller) {
            val exitToReturnFlowFunction = flowSpace.obtainExitToReturnSiteFlowFunction(callSite, returnSiteOfCaller, ep)
            val d5Set = exitToReturnFlowFunction.compute(d2)
            for (d5 in d5Set) {
                val newSummaryEdge = IFDSEdge(IFDSVertex(callSite, d4), IFDSVertex(returnSiteOfCaller, d5))
                if (addNewSummaryEdge(newSummaryEdge, startToEndEdge)) {
                    for (pathEdge in pathEdges.getByEnd(newSummaryEdge.u).toList()) {
                        val newPathEdge = IFDSEdge(pathEdge.u, newSummaryEdge.v)
                        propagate(newPathEdge, PathEdgePredecessor(pathEdge, THROUGH_SUMMARY))
                    }
                }
            }
        }
    }

    fun run() {
        while(!workList.isEmpty()) {
            val curEdge = workList.poll()
            val (u, v) = curEdge
            val (n, d2) = v

            val callees = devirtualizer.findPossibleCallees(n).toList()
            // 13
            if (callees.isNotEmpty()) {
                //14
                for (calledProc in callees) {
                    val flowFunction = flowSpace.obtainCallToStartFlowFunction(n, calledProc)
                    val nextFacts = flowFunction.compute(d2)
                    for (sCalledProc in graph.entryPoint(calledProc)) {
                        for (d3 in nextFacts) {
                            //15
                            val sCalledProcWithD3 = IFDSVertex(sCalledProc, d3)
                            val nextEdge = IFDSEdge(sCalledProcWithD3, sCalledProcWithD3)
                            propagate(nextEdge, PathEdgePredecessor(curEdge, CALL_TO_START))
                        }
                    }
                }
                //17-18
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    val flowFunction = flowSpace.obtainCallToReturnFlowFunction(n, returnSite)
                    val nextFacts = flowFunction.compute(d2)
                    for (d3 in nextFacts) {
                        val returnSiteVertex = IFDSVertex(returnSite, d3)
                        val nextEdge = IFDSEdge(u, returnSiteVertex)
                        propagate(nextEdge, PathEdgePredecessor(curEdge, SEQUENT))
                    }
                }
                for (callee in callees) {
                    for (d1 in flowSpace.obtainCallToStartFlowFunction(n, callee).compute(d2)) {
                        for (entryPoint in graph.entryPoint(callee)) {
                            val startVertex = IFDSVertex(entryPoint, d1)
                            if (v !in callSitesOf[startVertex].orEmpty()) {
                                callSitesOf.getOrPut(startVertex) { mutableSetOf() }.add(v)
                                for (startToEndEdge in startToEndEdges.getByStart(IFDSVertex(entryPoint, d1)).toList()) {
                                    findNewSummaryEdges(n, d2, startToEndEdge)
                                }
                            }
                        }
                    }
                }
                //17-18 for summary edges
                for (summaryEdge in summaryEdges.getByStart(v).toList()) {
                    val newPathEdge = IFDSEdge(u, summaryEdge.v)
                    propagate(newPathEdge, PathEdgePredecessor(curEdge, THROUGH_SUMMARY))
                }
            } else {
                // 21-22
                val nMethod = graph.methodOf(n)
                val nMethodExitPoints = graph.exitPoints(nMethod).toList()
                if (n in nMethodExitPoints) {
                    listeners.forEach { it.onExitPoint(IFDSEdge(u, v)) }
                    for ((c, d4) in callSitesOf[u].orEmpty()) {
                        findNewSummaryEdges(c, d4, IFDSEdge(u, v))
                    }
                    startToEndEdges.add(IFDSEdge(u, v))
                } else {
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
    }

    private fun collectResults(): IFDSResult {
        val resultFacts = mutableMapOf<JIRInst, MutableSet<DomainFact>>()

        // 6-8
        // todo: think about optimizations when we don't need all facts
        for (pathEdge in pathEdges.getAll()) {
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return IFDSResult(
            graph,
            pathEdges.getAll().toList(),
            summaryEdges.getAll().toList(),
            resultFacts,
            pathEdgesPreds,
            summaryEdgeToStartToEndEdges
        )
    }

    override fun analyze(): DumpableAnalysisResult {
        run()

        val ifdsResult = collectResults()
        val paths = analyzer.calculateSources(ifdsResult)

        return paths
    }
}