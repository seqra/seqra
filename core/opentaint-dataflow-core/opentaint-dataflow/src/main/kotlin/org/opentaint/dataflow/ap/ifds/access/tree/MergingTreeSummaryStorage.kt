package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.ap.ifds.access.util.AccessorGraph
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner
import org.opentaint.dataflow.util.SoftReferenceManager
import java.util.IdentityHashMap

class MergingTreeSummaryStorage(manager: SoftReferenceManager) {
    private var edges: AccessNode? = null
    private var edgesDelta: AccessNode? = null

    private val interner = AccessTreeSoftInterner(manager)

    fun add(exitAccess: AccessNode): Boolean {
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
                val compressed = currentInterned.compressNode(AccessorInterner())

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

    private fun AccessNode.compressNode(interner: AccessorInterner): AccessNode {
        val components = connectedAccessorComponents(interner)

        var result = this
        for (component in components) {
            if (component.size < 2) continue
            result = result.removeAllAccessorChains(component, chainLengthToRemove = 2, IdentityHashMap())
        }
        if (this === result) return this

        return result.compressNode(interner)
    }

    private fun AccessNode.connectedAccessorComponents(interner: AccessorInterner): List<Set<Accessor>> {
        val graph = AccessorGraph(interner)
        buildAccessorGraph(graph, IdentityHashMap())
        return graph.nonTrivialConnectedComponents()
    }

    private fun AccessNode.buildAccessorGraph(graph: AccessorGraph, visited: IdentityHashMap<AccessNode, Unit>) {
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
