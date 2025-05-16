package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class TraceGraph<Fact, Method, Statement>(
    val sink: Vertex<Fact, Method, Statement>,
    val sources: MutableSet<Vertex<Fact, Method, Statement>>,
    val edges: MutableMap<Vertex<Fact, Method, Statement>, MutableSet<Vertex<Fact, Method, Statement>>>,
    val unresolvedCrossUnitCalls: Map<Vertex<Fact, Method, Statement>, Set<Vertex<Fact, Method, Statement>>>,
) where Method : CommonMethod<Method, Statement>,
        Statement : CommonInst<Method, Statement> {

    /**
     * Returns all traces from [sources] to [sink].
     */
    fun getAllTraces(): Sequence<List<Vertex<Fact, Method, Statement>>> = sequence {
        for (v in sources) {
            yieldAll(getAllTraces(mutableListOf(v)))
        }
    }

    private fun getAllTraces(
        trace: MutableList<Vertex<Fact, Method, Statement>>,
    ): Sequence<List<Vertex<Fact, Method, Statement>>> = sequence {
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
        upGraph: TraceGraph<Fact, Method, Statement>,
        entryPoints: Set<Vertex<Fact, Method, Statement>>,
    ) {
        sources.addAll(upGraph.sources)

        for (edge in upGraph.edges) {
            edges.getOrPut(edge.key) { hashSetOf() }.addAll(edge.value)
        }

        edges.getOrPut(upGraph.sink) { hashSetOf() }.addAll(entryPoints)
    }
}
