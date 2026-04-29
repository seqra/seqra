package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.graph.IntGraph
import org.opentaint.dataflow.util.toIntSet
import java.util.BitSet
import java.util.IdentityHashMap

class MergingTreeSummaryStorage(val manager: TreeApManager) {
    private var edges: AccessNode? = null
    private var edgesDelta: AccessNode? = null

    private val interner = AccessTreeSoftInterner(manager)

    fun add(exitAccess: AccessNode): Boolean {
        manager.cancellation.checkpoint()

        val currentEdges = edges

        val (modifiedEdges, modificationDelta) = if (currentEdges == null) {
            exitAccess to exitAccess
        } else {
            currentEdges.mergeAddDelta(exitAccess)
        }

        if (modificationDelta == null) return false

        if (modifiedEdges.size > COMPRESSION_THRESHOLD) {
            interner.withInterner { interner, cache ->
                val currentInterned = modifiedEdges.internNodes(interner, cache)
                val compressed = currentInterned.compressNode()

                if (compressed !== currentInterned) {
                    val interned = compressed.internNodes(interner, cache)
                    edges = interned
                    edgesDelta = interned
                    return true
                }
            }
        }

        edges = modifiedEdges
        edgesDelta = edgesDelta?.mergeAdd(modificationDelta) ?: modificationDelta
        return true
    }

    fun edges(): AccessNode? = edges

    fun getAndResetDelta(): AccessNode? {
        val delta = edgesDelta ?: return null
        edgesDelta = null

        return interner.withInterner { interner, cache ->
            edges = edges?.internNodes(interner, cache)
            delta.internNodes(interner, cache)
        }
    }

    private fun AccessNode.compressNode(): AccessNode {
        val components = connectedAccessorComponents()

        var result = this
        for (component in components) {
            if (component.cardinality() < 2) continue

            result = result.removeAllAccessorChains(
                component.toIntSet(), chainLengthToRemove = 2, IdentityHashMap(), manager.cancellation
            )
        }
        if (this === result) return this

        return result.compressNode()
    }

    private fun AccessNode.connectedAccessorComponents(): List<BitSet> {
        val graph = IntGraph()
        buildAccessorGraph(graph, IdentityHashMap())
        return graph.nonTrivialSccs()
    }

    private fun AccessNode.buildAccessorGraph(graph: IntGraph, visited: IdentityHashMap<AccessNode, Unit>) {
        if (visited.put(this, Unit) != null) return

        forEachAccessor { outer, node ->
            node.forEachAccessor { inner, _ ->
                graph.addEdge(outer, inner)
            }
            node.buildAccessorGraph(graph, visited)
        }
    }

    companion object {
        private const val COMPRESSION_THRESHOLD = 10_000
    }
}
