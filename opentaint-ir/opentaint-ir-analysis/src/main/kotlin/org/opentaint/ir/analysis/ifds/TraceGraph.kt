package org.opentaint.ir.analysis.ifds

data class TraceGraph<Fact>(
    val sink: Vertex<Fact>,
    val sources: MutableSet<Vertex<Fact>>,
    val edges: MutableMap<Vertex<Fact>, MutableSet<Vertex<Fact>>>,
    val unresolvedCrossUnitCalls: Map<Vertex<Fact>, Set<Vertex<Fact>>>,
) {
    /**
     * Returns all traces from [sources] to [sink].
     */
    fun getAllTraces(): Sequence<List<Vertex<Fact>>> = sequence {
        for (v in sources) {
            yieldAll(getAllTraces(mutableListOf(v)))
        }
    }

    private fun getAllTraces(
        trace: MutableList<Vertex<Fact>>,
    ): Sequence<List<Vertex<Fact>>> = sequence {
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
        upGraph: TraceGraph<Fact>,
        entryPoints: Set<Vertex<Fact>>,
    ) {
        sources.addAll(upGraph.sources)

        for (edge in upGraph.edges) {
            edges.getOrPut(edge.key) { hashSetOf() }.addAll(edge.value)
        }

        edges.getOrPut(upGraph.sink) { hashSetOf() }.addAll(entryPoints)
    }
}
