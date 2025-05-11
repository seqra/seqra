package org.opentaint.ir.analysis.ifds2

import org.opentaint.ir.analysis.ifds2.taint.Zero

/**
 * Aggregates all facts and edges found by the tabulation algorithm.
 */
class Aggregate<Fact>(
    pathEdges: Collection<Edge<Fact>>,
    val facts: Map<Statement, Set<Fact>>,
    val reasons: Map<Edge<Fact>, Set<Reason>>,
) {
    private val pathEdgesBySink: Map<Vertex<Fact>, Collection<Edge<Fact>>> =
        pathEdges.groupByTo(HashMap()) { it.to }

    fun buildTraceGraph(sink: Vertex<Fact>): TraceGraph<Fact> {
        val sources: MutableSet<Vertex<Fact>> = hashSetOf()
        val edges: MutableMap<Vertex<Fact>, MutableSet<Vertex<Fact>>> =
            hashMapOf()
        val visited: MutableSet<Edge<Fact>> = hashSetOf()

        fun addEdge(
            from: Vertex<Fact>,
            to: Vertex<Fact>,
        ) {
            if (from != to) {
                edges.getOrPut(from) { hashSetOf() }.add(to)
            }
        }

        fun dfs(
            edge: Edge<Fact>,
            lastVertex: Vertex<Fact>,
            stopAtMethodStart: Boolean,
        ) {
            if (!visited.add(edge)) {
                return
            }

            if (stopAtMethodStart && edge.from == edge.to) {
                addEdge(edge.from, lastVertex)
                return
            }

            val vertex = edge.to
            // FIXME: not all domains have "Zero" fact!
            if (vertex.fact == Zero) {
                addEdge(vertex, lastVertex)
                sources.add(vertex)
                return
            }

            for (reason in reasons[edge].orEmpty()) {
                when (reason) {
                    is Reason.Sequent<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        reason as Reason.Sequent<Fact>
                        val predEdge = reason.edge
                        if (predEdge.to.fact == vertex.fact) {
                            dfs(predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(predEdge.to, lastVertex)
                            dfs(predEdge, predEdge.to, stopAtMethodStart)
                        }
                    }

                    is Reason.CallToStart<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        reason as Reason.CallToStart<Fact>
                        val predEdge = reason.edge
                        if (!stopAtMethodStart) {
                            addEdge(predEdge.to, lastVertex)
                            dfs(predEdge, predEdge.to, false)
                        }
                    }

                    is Reason.ThroughSummary<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        reason as Reason.ThroughSummary<Fact>
                        val predEdge = reason.edge
                        val summaryEdge = reason.summaryEdge
                        addEdge(summaryEdge.to, lastVertex) // Return to next vertex
                        addEdge(predEdge.to, summaryEdge.from) // Call to start
                        dfs(summaryEdge, summaryEdge.to, true) // Expand summary edge
                        dfs(predEdge, predEdge.to, stopAtMethodStart) // Continue normal analysis
                    }

                    is Reason.External -> {
                        // TODO: check
                        addEdge(edge.to, lastVertex)
                        if (edge.from != edge.to) {
                            // TODO: ideally, we should analyze the place from which the edge was given to ifds,
                            //  for now we just go to method start
                            dfs(Edge(edge.from, edge.from), edge.to, stopAtMethodStart)
                        }
                    }

                    is Reason.Initial -> {
                        // TODO: check
                        sources.add(vertex)
                        addEdge(edge.to, lastVertex)
                    }
                }
            }
        }

        for (edge in pathEdgesBySink[sink].orEmpty()) {
            dfs(edge, edge.to, false)
        }
        return TraceGraph(sink, sources, edges)
    }
}
