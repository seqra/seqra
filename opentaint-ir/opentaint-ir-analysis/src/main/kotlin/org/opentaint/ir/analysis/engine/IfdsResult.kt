package org.opentaint.ir.analysis.engine
import org.opentaint.ir.api.jvm.cfg.JIRInst

/**
 * Aggregates all facts and edges found by tabulation algorithm
 */
class IfdsResult(
    val pathEdges: List<IfdsEdge>,
    val resultFacts: Map<JIRInst, Set<DomainFact>>,
    val pathEdgesPreds: Map<IfdsEdge, Set<PathEdgePredecessor>>
) {
    private inner class TraceGraphBuilder(private val sink: IfdsVertex) {
        private val sources: MutableSet<IfdsVertex> = mutableSetOf()
        private val edges: MutableMap<IfdsVertex, MutableSet<IfdsVertex>> = mutableMapOf()
        private val visited: MutableSet<IfdsEdge> = mutableSetOf()

        private fun addEdge(from: IfdsVertex, to: IfdsVertex) {
            if (from != to) {
                edges.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }

        private fun dfs(e: IfdsEdge, lastVertex: IfdsVertex, stopAtMethodStart: Boolean) {
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
                    is PredecessorKind.CallToStart -> {
                        if (!stopAtMethodStart) {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, false)
                        }
                    }
                    is PredecessorKind.Sequent -> {
                        if (pred.predEdge.v.domainFact == v.domainFact) {
                            dfs(pred.predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(pred.predEdge.v, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart)
                        }
                    }
                    is PredecessorKind.ThroughSummary -> {
                        val summaryEdge = pred.kind.summaryEdge
                        addEdge(summaryEdge.v, lastVertex) // Return to next vertex
                        addEdge(pred.predEdge.v, summaryEdge.u) // Call to start
                        dfs(summaryEdge, summaryEdge.v, true) // Expand summary edge
                        dfs(pred.predEdge, pred.predEdge.v, stopAtMethodStart) // Continue normal analysis
                    }
                    is PredecessorKind.Unknown -> {
                        addEdge(pred.predEdge.v, lastVertex)
                        if (pred.predEdge.u != pred.predEdge.v) {
                            // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                            //  for now we just go to method start
                            dfs(IfdsEdge(pred.predEdge.u, pred.predEdge.u), pred.predEdge.v, stopAtMethodStart)
                        }
                    }
                    is PredecessorKind.NoPredecessor -> {
                        sources.add(v)
                        addEdge(pred.predEdge.v, lastVertex)
                    }
                }
            }
        }

        fun build(): TraceGraph {
            val initEdges = pathEdges.filter { it.v == sink }
            initEdges.forEach {
                dfs(it, it.v, false)
            }
            return TraceGraph(sink, sources, edges)
        }
    }

    /**
     * Builds a graph with traces to given [vertex].
     */
    fun resolveTraceGraph(vertex: IfdsVertex): TraceGraph {
        return TraceGraphBuilder(vertex).build()
    }
}