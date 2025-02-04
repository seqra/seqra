package org.opentaint.ir.analysis.engine
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.CALL_TO_START
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.NO_PREDECESSOR
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.SEQUENT
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.THROUGH_SUMMARY
import org.opentaint.ir.analysis.engine.PathEdgePredecessorKind.UNKNOWN
import org.opentaint.ir.api.cfg.JIRInst

class IFDSResult(
    val pathEdges: List<IFDSEdge>,
    val resultFacts: Map<JIRInst, Set<DomainFact>>,
    val pathEdgesPreds: Map<IFDSEdge, Set<PathEdgePredecessor>>,
    val summaryEdgeToStartToEndEdges: Map<IFDSEdge, Set<IFDSEdge>>,
    val crossUnitCallees: Map<IFDSVertex, Set<IFDSVertex>>
) {
    private inner class RealisationsGraphBuilder(private val sink: IFDSVertex) {
        private val sources: MutableSet<IFDSVertex> = mutableSetOf()
        private val edges: MutableMap<IFDSVertex, MutableSet<IFDSVertex>> = mutableMapOf()
        private val visited: MutableSet<IFDSEdge> = mutableSetOf()

        private fun addEdge(from: IFDSVertex, to: IFDSVertex) {
            if (from != to) {
                edges.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }

        private fun dfs(e: IFDSEdge, lastVertex: IFDSVertex, stopAtMethodStart: Boolean) {
            if (e in visited) {
                return
            }

            visited.add(e)

            if (stopAtMethodStart && e.u == e.v) {
                addEdge(e.u, lastVertex)
                return
            }

            val (_, v) = e
            if (v.domainFact == ZEROFact) {
                addEdge(v, lastVertex)
                sources.add(v)
                return
            }

            for (pred in pathEdgesPreds[e].orEmpty()) {
                when (pred.kind) {
                    CALL_TO_START -> {
                        if (!stopAtMethodStart) {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, false)
                        }
                    }
                    SEQUENT -> {
                        if (pred.predEdge.v.domainFact == v.domainFact) {
                            dfs(pred.predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart)
                        }
                    }
                    THROUGH_SUMMARY -> {
                        val summaryEdge = IFDSEdge(pred.predEdge.v, v)
                        summaryEdgeToStartToEndEdges[summaryEdge]?.forEach { startToEndEdge ->
                            addEdge(startToEndEdge.v, lastVertex) // Return to next vertex
                            addEdge(pred.predEdge.v, startToEndEdge.u) // Call to start
                            dfs(startToEndEdge, startToEndEdge.v, true) // Expand summary edge
//                            if (startToEndEdge.u.domainFact != ZEROFact) {
                                dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart) // Continue normal analysis
//                            }
                        }
                    }
                    UNKNOWN -> {
                        addEdge(pred.predEdge.v, lastVertex) // Turning point
                        // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                        //  for now we just go to method start
                        dfs(IFDSEdge(pred.predEdge.u, pred.predEdge.u), pred.predEdge.v, stopAtMethodStart)
                    }
                    NO_PREDECESSOR -> {
                        sources.add(v)
                        addEdge(pred.predEdge.v, lastVertex)
                    }
                }
            }
        }

        fun build(): TaintRealisationsGraph {
            val initEdges = pathEdges.filter { it.v == sink }
            initEdges.forEach {
                dfs(it, it.v, false)
            }
            return TaintRealisationsGraph(sink, sources, edges)
        }
    }

    fun resolveTaintRealisationsGraph(vertex: IFDSVertex): TaintRealisationsGraph {
        return RealisationsGraphBuilder(vertex).build()
    }
}