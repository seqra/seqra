package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.util.forEachEntry
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.getOrCreate
import org.opentaint.dataflow.util.int2ObjectMap

abstract class AccessBasedStorage<S : AccessBasedStorage<S>> {
    private val children = int2ObjectMap<S>()

    abstract fun createStorage(): S

    fun getOrCreateNode(access: AccessPath.AccessNode?): S {
        if (access == null) {
            @Suppress("UNCHECKED_CAST")
            return this as S
        }

        var storage = this
        access.toList().forEachInt { accessor ->
            storage = storage.getOrCreateChild(accessor)
        }

        @Suppress("UNCHECKED_CAST")
        return storage as S
    }

    fun filterContains(pattern: AccessTree.AccessNode): Sequence<S> {
        val nodes = mutableListOf<S>()
        collectNodesContains(pattern, nodes)
        return nodes.asSequence()
    }

    private fun collectNodesContains(pattern: AccessTree.AccessNode, nodes: MutableList<S>) {
        @Suppress("UNCHECKED_CAST")
        nodes.add(this as S)

        if (pattern.isFinal) {
            children[FINAL_ACCESSOR_IDX]?.let { nodes.add(it) }
        }

        pattern.forEachAccessor { accessor, accessorPattern ->
            collectNodesContainsAccessor(accessorPattern, accessor, nodes)
        }
    }

    open fun collectNodesContainsAccessor(
        pattern: AccessTree.AccessNode,
        accessor: AccessorIdx,
        nodes: MutableList<S>
    ) {
        children.get(accessor)?.collectNodesContains(pattern, nodes)
    }

    fun allNodes(): Sequence<S> {
        val storages = mutableListOf<S>()

        val unprocessedStorages = mutableListOf(this)
        while (unprocessedStorages.isNotEmpty()) {
            val storage = unprocessedStorages.removeLast()
            @Suppress("UNCHECKED_CAST")
            storages.add(storage as S)

            storage.children.forEachEntry { _, s ->
                unprocessedStorages.add(s)
            }
        }

        return storages.asSequence()
    }

    private fun getOrCreateChild(accessor: AccessorIdx): S =
        children.getOrCreate(accessor) { createStorage() }
}