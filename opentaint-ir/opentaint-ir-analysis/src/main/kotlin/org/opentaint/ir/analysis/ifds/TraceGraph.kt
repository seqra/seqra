package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonInst

data class TraceGraph<Fact, Statement : CommonInst>(
    val sink: Vertex<Fact, Statement>,
    val sources: MutableSet<Vertex<Fact, Statement>>,
    val edges: MutableMap<Vertex<Fact, Statement>, MutableSet<Vertex<Fact, Statement>>>,
    val unresolvedCrossUnitCalls: Map<Vertex<Fact, Statement>, Set<Vertex<Fact, Statement>>>,
) {

    /**
     * Returns all traces from [sources] to [sink].
     */
    fun getAllTraces(): Sequence<List<Vertex<Fact, Statement>>> =
        sources.asSequence().flatMap { getAllTraces(mutableListOf(it)) }

    private fun getAllTraces(
        trace: MutableList<Vertex<Fact, Statement>>,
    ): Sequence<List<Vertex<Fact, Statement>>> = sequence {
        val v = trace.last()
        if (v == sink) {
            yield(trace.toList()) // copy list
            return@sequence
        }
        for (u in edges[v].orEmpty()) {
            if (u !in trace) {
                trace.add(u)
                yieldAll(getAllTraces(trace))
                trace.removeLast()
            }
        }
    }

    /**
     * Merges [upGraph] into this graph.
     */
    fun mergeWithUpGraph(
        upGraph: TraceGraph<Fact, Statement>,
        entryPoints: Set<Vertex<Fact, Statement>>,
    ) {
        sources.addAll(upGraph.sources)

        for (edge in upGraph.edges) {
            edges.getOrPut(edge.key) { hashSetOf() }.addAll(edge.value)
        }

        edges.getOrPut(upGraph.sink) { hashSetOf() }.addAll(entryPoints)
    }
}
