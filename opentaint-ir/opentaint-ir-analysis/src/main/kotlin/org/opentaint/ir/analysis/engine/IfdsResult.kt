package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.cfg.JIRInst

/**
 * Aggregates all facts and edges found by tabulation algorithm
 */
class IfdsResult(
    pathEdgeList: List<IfdsEdge>,
    val resultFacts: Map<JIRInst, Set<DomainFact>>,
    val pathEdgesPreds: Map<IfdsEdge, Set<PathEdgePredecessor>>,
) {
    private val pathEdges = pathEdgeList.groupBy { it.to }

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

            if (stopAtMethodStart && e.from == e.to) {
                addEdge(e.from, lastVertex)
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
                            addEdge(pred.predEdge.to, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.to, false)
                        }
                    }

                    is PredecessorKind.Sequent -> {
                        if (pred.predEdge.to.domainFact == v.domainFact) {
                            dfs(pred.predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(pred.predEdge.to, lastVertex)
                            dfs(pred.predEdge, pred.predEdge.to, stopAtMethodStart)
                        }
                    }

                    is PredecessorKind.ThroughSummary -> {
                        val summaryEdge = pred.kind.summaryEdge
                        addEdge(summaryEdge.to, lastVertex) // Return to next vertex
                        addEdge(pred.predEdge.to, summaryEdge.from) // Call to start
                        dfs(summaryEdge, summaryEdge.to, true) // Expand summary edge
                        dfs(pred.predEdge, pred.predEdge.to, stopAtMethodStart) // Continue normal analysis
                    }

                    is PredecessorKind.Unknown -> {
                        addEdge(pred.predEdge.to, lastVertex)
                        if (pred.predEdge.from != pred.predEdge.to) {
                            // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                            //  for now we just go to method start
                            dfs(IfdsEdge(pred.predEdge.from, pred.predEdge.from), pred.predEdge.to, stopAtMethodStart)
                        }
                    }

                    is PredecessorKind.NoPredecessor -> {
                        sources.add(v)
                        addEdge(pred.predEdge.to, lastVertex)
                    }
                }
            }
        }

        fun build(): TraceGraph {
            val initEdges = pathEdges[sink].orEmpty()
            initEdges.forEach {
                dfs(it, it.to, false)
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
