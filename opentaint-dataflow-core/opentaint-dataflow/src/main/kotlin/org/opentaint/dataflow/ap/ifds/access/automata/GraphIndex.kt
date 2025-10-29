package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.util.contains
import org.opentaint.dataflow.util.forEach
import org.opentaint.dataflow.util.forEachEntry
import org.opentaint.dataflow.util.int2ObjectMap
import java.util.BitSet

class GraphIndex {
    private var size = 0
    private val emptyGraphs = BitSet()
    private val initialIsFinalGraphs = BitSet()
    private val accessorIndex = int2ObjectMap<AccessorIndex>()

    fun add(graph: AccessGraph, idx: Int) {
        size = maxOf(size, idx + 1)

        if (graph.isEmpty()) {
            emptyGraphs.set(idx)
            return
        }

        if (graph.initialNodeIsFinal()) {
            initialIsFinalGraphs.set(idx)
        }

        for (edgeWithAccessor in graph.iterateEdges()) {
            val accessor = edgeWithAccessor.intKey
            val edge = edgeWithAccessor.longValue

            val index = accessorIndex.getOrPut(accessor) { AccessorIndex() }
            index.containsAccessor.set(idx)

            if (graph.getEdgeFrom(edge) == graph.initial) {
                index.initialSuccessor.set(idx)
            }

            if (graph.getEdgeTo(edge) == graph.final) {
                index.finalPredecessor.set(idx)
            }

            graph.nodeSuccessors(graph.getEdgeTo(edge)).forEach { successor ->
                val successorIdx = index.successors.getOrPut(successor) { BitSet() }
                successorIdx.set(idx)
            }

            graph.nodePredecessors(graph.getEdgeFrom(edge)).forEach { predecessor ->
                val predecessorIdx = index.predecessors.getOrPut(predecessor) { BitSet() }
                predecessorIdx.set(idx)
            }
        }
    }

    private fun allGraphs() = BitSet().also { it.set(0, size) }

    private fun empty() = BitSet()

    fun localizeGraphHasDeltaWithIndexedGraph(graph: AccessGraph): BitSet {
        val relevantGraphs = allGraphs()

        accessorIndex.forEachEntry { accessor, index ->
            val edge = graph.getEdge(accessor)
            if (edge == null) {
                relevantGraphs.andNot(index.containsAccessor)
                return@forEachEntry
            }

            if (graph.getEdgeFrom(edge) != graph.initial) {
                relevantGraphs.andNot(index.initialSuccessor)
            }

            val successors = graph.nodeSuccessors(graph.getEdgeTo(edge))
            index.successors.forEachEntry { indexSuccessor, graphsWithSuccessor ->
                if (indexSuccessor !in successors) {
                    relevantGraphs.andNot(graphsWithSuccessor)
                }
            }
        }

        return relevantGraphs
    }

    fun localizeGraphContainsAllIndexedGraph(graph: AccessGraph): BitSet {
        if (graph.isEmpty()) {
            return emptyGraphs
        }

        val relevantGraphs = allGraphs()

        if (!graph.initialNodeIsFinal()) {
            relevantGraphs.andNot(emptyGraphs)
        }

        accessorIndex.forEachEntry { accessor, index ->
            val edge = graph.getEdge(accessor)
            if (edge == null) {
                relevantGraphs.andNot(index.containsAccessor)
                return@forEachEntry
            }

            if (graph.getEdgeFrom(edge) != graph.initial) {
                relevantGraphs.andNot(index.initialSuccessor)
            }

            if (graph.getEdgeTo(edge) != graph.final) {
                relevantGraphs.andNot(index.finalPredecessor)
            }

            val successors = graph.nodeSuccessors(graph.getEdgeTo(edge))
            index.successors.forEachEntry { indexSuccessor, graphsWithSuccessor ->
                if (indexSuccessor !in successors) {
                    relevantGraphs.andNot(graphsWithSuccessor)
                }
            }

            val predecessors = graph.nodePredecessors(graph.getEdgeFrom(edge))
            index.predecessors.forEachEntry { indexPredecessor, graphsWithPredecessor ->
                if (indexPredecessor !in predecessors) {
                    relevantGraphs.andNot(graphsWithPredecessor)
                }
            }
        }

        return relevantGraphs
    }

    fun localizeIndexedGraphHasDeltaWithGraph(graph: AccessGraph): BitSet {
        val result = allGraphs()
        for (edgeWithAccessor in graph.iterateEdges()) {
            val accessor = edgeWithAccessor.intKey
            val edge = edgeWithAccessor.longValue

            val index = accessorIndex.get(accessor) ?: return empty()
            result.and(index.containsAccessor)

            if (graph.getEdgeFrom(edge) == graph.initial) {
                result.and(index.initialSuccessor)
            }

            graph.nodeSuccessors(graph.getEdgeTo(edge)).forEach { successor ->
                val successorIdx = index.successors.get(successor) ?: return empty()
                result.and(successorIdx)
            }
        }
        return result
    }

    fun localizeIndexedGraphContainsAllGraph(graph: AccessGraph): BitSet {
        if (graph.isEmpty()) {
            val result = empty()
            result.or(emptyGraphs)
            result.or(initialIsFinalGraphs)
            return result
        }

        val result = allGraphs()

        if (graph.initialNodeIsFinal()) {
            result.and(initialIsFinalGraphs)
        }

        for (edgeWithAccessor in graph.iterateEdges()) {
            val accessor = edgeWithAccessor.intKey
            val edge = edgeWithAccessor.longValue

            val index = accessorIndex.get(accessor) ?: return empty()
            result.and(index.containsAccessor)

            if (graph.getEdgeFrom(edge) == graph.initial) {
                result.and(index.initialSuccessor)
            }

            if (graph.getEdgeTo(edge) == graph.final) {
                result.and(index.finalPredecessor)
            }

            graph.nodeSuccessors(graph.getEdgeTo(edge)).forEach { successor ->
                val successorIdx = index.successors.get(successor) ?: return empty()
                result.and(successorIdx)
            }

            graph.nodePredecessors(graph.getEdgeFrom(edge)).forEach { predecessor ->
                val predecessorIdx = index.predecessors.get(predecessor) ?: return empty()
                result.and(predecessorIdx)
            }
        }
        return result
    }

    fun localizeGraphsWithAccessors(accessors: BitSet): BitSet {
        val result = BitSet()
        accessors.forEach { accessor ->
            val index = accessorIndex.get(accessor) ?: return@forEach
            result.or(index.containsAccessor)
        }
        return result
    }

    private class AccessorIndex {
        val containsAccessor = BitSet()
        val initialSuccessor = BitSet()
        val finalPredecessor = BitSet()
        val successors = int2ObjectMap<BitSet>()
        val predecessors = int2ObjectMap<BitSet>()
    }
}
