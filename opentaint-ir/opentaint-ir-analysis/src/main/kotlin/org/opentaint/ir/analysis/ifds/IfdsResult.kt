package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonInst

/**
 * Aggregates all facts and edges found by the tabulation algorithm.
 */
class IfdsResult<Fact, Statement : CommonInst> internal constructor(
    val pathEdgesBySink: Map<Vertex<Fact, Statement>, Collection<Edge<Fact, Statement>>>,
    val facts: Map<Statement, Set<Fact>>,
    val reasons: Map<Edge<Fact, Statement>, Set<Reason<Fact, Statement>>>,
    val zeroFact: Fact?,
) {

    constructor(
        pathEdges: Collection<Edge<Fact, Statement>>,
        facts: Map<Statement, Set<Fact>>,
        reasons: Map<Edge<Fact, Statement>, Set<Reason<Fact, Statement>>>,
        zeroFact: Fact?,
    ) : this(
        pathEdges.groupByTo(HashMap()) { it.to },
        facts,
        reasons,
        zeroFact
    )

    fun buildTraceGraph(sink: Vertex<Fact, Statement>): TraceGraph<Fact, Statement> {
        val sources: MutableSet<Vertex<Fact, Statement>> =
            hashSetOf()
        val edges: MutableMap<Vertex<Fact, Statement>, MutableSet<Vertex<Fact, Statement>>> =
            hashMapOf()
        val unresolvedCrossUnitCalls: MutableMap<Vertex<Fact, Statement>, MutableSet<Vertex<Fact, Statement>>> =
            hashMapOf()
        val visited: MutableSet<Pair<Edge<Fact, Statement>, Vertex<Fact, Statement>>> =
            hashSetOf()

        fun addEdge(
            from: Vertex<Fact, Statement>,
            to: Vertex<Fact, Statement>,
        ) {
            if (from != to) {
                edges.getOrPut(from) { hashSetOf() }.add(to)
            }
        }

        fun dfs(
            edge: Edge<Fact, Statement>,
            lastVertex: Vertex<Fact, Statement>,
            stopAtMethodStart: Boolean,
        ) {
            if (!visited.add(edge to lastVertex)) {
                return
            }

            // Note: loop-edge represents method start
            if (stopAtMethodStart && edge.from == edge.to) {
                addEdge(edge.from, lastVertex)
                return
            }

            val vertex = edge.to
            if (vertex.fact == zeroFact) {
                addEdge(vertex, lastVertex)
                sources.add(vertex)
                return
            }

            for (reason in reasons[edge].orEmpty()) {
                when (reason) {
                    is Reason.Sequent<Fact, Statement> -> {
                        val predEdge = reason.edge
                        if (predEdge.to.fact == vertex.fact) {
                            dfs(predEdge, lastVertex, stopAtMethodStart)
                        } else {
                            addEdge(predEdge.to, lastVertex)
                            dfs(predEdge, predEdge.to, stopAtMethodStart)
                        }
                    }

                    is Reason.CallToStart<Fact, Statement> -> {
                        val predEdge = reason.edge
                        if (!stopAtMethodStart) {
                            addEdge(predEdge.to, lastVertex)
                            dfs(predEdge, predEdge.to, false)
                        }
                    }

                    is Reason.ThroughSummary<Fact, Statement> -> {
                        val predEdge = reason.edge
                        val summaryEdge = reason.summaryEdge
                        addEdge(summaryEdge.to, lastVertex) // Return to next vertex
                        addEdge(predEdge.to, summaryEdge.from) // Call to start
                        dfs(summaryEdge, summaryEdge.to, true) // Expand summary edge
                        dfs(predEdge, predEdge.to, stopAtMethodStart) // Continue normal analysis
                    }

                    is Reason.CrossUnitCall<Fact, Statement> -> {
                        addEdge(edge.to, lastVertex)
                        unresolvedCrossUnitCalls.getOrPut(reason.caller) { hashSetOf() }.add(edge.to)
                    }

                    is Reason.External -> {
                        TODO("External reason is not supported yet")
                    }

                    is Reason.Initial -> {
                        sources.add(vertex)
                        addEdge(edge.to, lastVertex)
                    }
                }
            }
        }

        for (edge in pathEdgesBySink[sink].orEmpty()) {
            dfs(edge, edge.to, false)
        }
        return TraceGraph(sink, sources, edges, unresolvedCrossUnitCalls)
    }
}
