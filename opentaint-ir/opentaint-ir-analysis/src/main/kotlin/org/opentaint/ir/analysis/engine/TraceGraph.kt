package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.DumpableVulnerabilityInstance

class TraceGraph(
    val sink: IfdsVertex,
    val sources: Set<IfdsVertex>,
    val edges: Map<IfdsVertex, Set<IfdsVertex>>,
) {

    private fun getAllTraces(curTrace: MutableList<IfdsVertex>): Sequence<List<IfdsVertex>> = sequence {
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

    fun getAllTraces(): Sequence<List<IfdsVertex>> = sequence {
        sources.forEach {
            yieldAll(getAllTraces(mutableListOf(it)))
        }
    }

    fun toVulnerability(vulnerabilityType: String, maxTracesCount: Int = 100): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            sources.map { it.statement.toString() },
            sink.statement.toString(),
            getAllTraces().take(maxTracesCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.toString() }
            }.toList()
        )
    }

    fun mergeWithUpGraph(upGraph: TraceGraph, entryPoints: Set<IfdsVertex>): TraceGraph {
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
        fun bySink(sink: IfdsVertex) = TraceGraph(sink, setOf(sink), emptyMap())
    }
}