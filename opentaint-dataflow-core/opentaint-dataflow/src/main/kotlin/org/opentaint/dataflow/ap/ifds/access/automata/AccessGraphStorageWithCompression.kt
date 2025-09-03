package org.opentaint.dataflow.ap.ifds.access.automata

import org.opentaint.dataflow.ap.ifds.Accessor
import java.util.concurrent.ConcurrentHashMap

class AccessGraphStorageWithCompression {
    private val noPredecessorGroups = ConcurrentHashMap<Set<Accessor>, AgGroup>()
    private val otherGraphs = AgGroup(mergeLimit = HUGE_GROUP_LIMIT)

    private val modifiedGroups = mutableListOf<AgGroup>()

    fun mapAndResetDelta(block: (AccessGraph) -> Unit) {
        modifiedGroups.forEach { it.mapAndResetDelta(block) }
        modifiedGroups.clear()
    }

    fun add(graph: AccessGraph): Boolean {
        val graphInitialPredecessors = graph.nodePredecessors(graph.initial)
        if (graphInitialPredecessors.isEmpty()) {
            val initialSuccessors = graph.nodeSuccessors(graph.initial)
            val graphGroup = noPredecessorGroups.computeIfAbsent(initialSuccessors) {
                AgGroup(mergeLimit = SMALL_GROUP_LIMIT)
            }

            if (!graphGroup.add(graph)) return false

            modifiedGroups += graphGroup
            return true
        }

        if (!otherGraphs.add(graph)) return false

        modifiedGroups.add(otherGraphs)
        return true
    }

    fun allGraphs(): Sequence<AccessGraph> {
        val allGroups = noPredecessorGroups.elements().asSequence() + otherGraphs
        return allGroups.flatMap { it.allGraphs() }
    }

    override fun toString(): String {
        val size = allGraphs().sumOf { it.size }
        return "(size: $size)"
    }

    companion object {
        private const val SMALL_GROUP_LIMIT = 5
        private const val HUGE_GROUP_LIMIT = SMALL_GROUP_LIMIT * 3
    }
}

private class AgGroup(private val mergeLimit: Int) {
    private var mergedGraph: AccessGraph? = null
    private var agStorage: AccessGraphSet? = null
    private val delta = mutableListOf<AccessGraph>()

    fun mapAndResetDelta(block: (AccessGraph) -> Unit) {
        delta.forEach(block)
        delta.clear()
    }

    fun add(graph: AccessGraph): Boolean {
        if (mergedGraph != null) {
            return addMergedGraph(graph)
        }

        val currentStorage = agStorage ?: AccessGraphSet.create()
        val storage = currentStorage.add(graph) ?: return false

        agStorage = storage
        delta.add(graph)

        if (storage.setSize > mergeLimit) {
            compress(storage)
        }

        return true
    }

    private fun addMergedGraph(graph: AccessGraph): Boolean {
        val currentMergedGraph = mergedGraph!!
        if (currentMergedGraph.containsAll(graph)) return false

        val merged = currentMergedGraph.merge(graph)
        updateMergedGraph(merged)

        return true
    }

    private fun compress(storage: AccessGraphSet) {
        agStorage = null
        val graph = storage.toList().reduce { acc, graph -> acc.merge(graph) }
        updateMergedGraph(graph)
    }

    private fun updateMergedGraph(graph: AccessGraph) {
        mergedGraph = graph
        delta.clear()
        delta.add(graph)
    }

    fun allGraphs(): Sequence<AccessGraph> =
        mergedGraph?.let { sequenceOf(it) }
            ?: agStorage?.toList()?.asSequence()
            ?: emptySequence()

    override fun toString(): String {
        val size = allGraphs().sumOf { it.size }
        return "(size: $size)"
    }
}
