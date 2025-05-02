package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation

/**
 * A directed graph with selected [sink] and [sources], where each path from one of [sources] to [sink] is a trace.
 *
 * @property sink is usually some interesting vertex that we want to reach (e.g. vertex that produces vulnerability)
 * @property sources are the entry points, e.g. the vertices with [ZEROFact] or method starts
 */
data class TraceGraph<Method, Location, Statement>(
    val sink: IfdsVertex<Method, Location, Statement>,
    val sources: Set<IfdsVertex<Method, Location, Statement>>,
    val edges: Map<IfdsVertex<Method, Location, Statement>, Set<IfdsVertex<Method, Location, Statement>>>,
) where Method : CoreMethod<Statement>,
        Location : CoreInstLocation<Method>,
        Statement : CoreInst<Location, Method, *> {

    private fun getAllTraces(
        curTrace: MutableList<IfdsVertex<Method, Location, Statement>>
    ): Sequence<List<IfdsVertex<Method, Location, Statement>>> = sequence {
        val v = curTrace.last()

        if (v == sink) {
            yield(curTrace.toList())
            return@sequence
        }

        for (u in edges[v].orEmpty()) {
            if (u !in curTrace) {
                curTrace.add(u)
                yieldAll(getAllTraces(curTrace))
                curTrace.removeLast()
            }
        }
    }

    /**
     * Returns a sequence with all traces from [sources] to [sink]
     */
    fun getAllTraces(): Sequence<List<IfdsVertex<Method, Location, Statement>>> = sequence {
        sources.forEach {
            yieldAll(getAllTraces(mutableListOf(it)))
        }
    }

    /**
     * Merges two graphs.
     *
     * [sink] will be chosen from receiver, and edges from both graphs will be merged.
     * Also, all edges from [upGraph]'s sink to [entryPoints] will be added
     * (these are edges "connecting" [upGraph] with receiver).
     *
     * Informally, this method extends receiver's traces from one side using [upGraph].
     */
    fun mergeWithUpGraph(
        upGraph: TraceGraph<Method, Location, Statement>,
        entryPoints: Set<IfdsVertex<Method, Location, Statement>>
    ): TraceGraph<Method, Location, Statement> {
        val validEntryPoints = entryPoints.intersect(edges.keys).ifEmpty {
            return this
        }

        val newSources = sources + upGraph.sources

        val newEdges = edges.toMutableMap()
        for ((source, dests) in upGraph.edges) {
            newEdges[source] = newEdges.getOrDefault(source, emptySet()) + dests
        }
        newEdges[upGraph.sink] = newEdges.getOrDefault(upGraph.sink, emptySet()) + validEntryPoints
        return TraceGraph(sink, newSources, newEdges)
    }

    companion object {
        fun <Method : CoreMethod<Statement>, Location : CoreInstLocation<Method>, Statement : CoreInst<Location, Method, *>> bySink(
            sink: IfdsVertex<Method, Location, Statement>
        ) = TraceGraph(sink, setOf(sink), emptyMap())
    }
}