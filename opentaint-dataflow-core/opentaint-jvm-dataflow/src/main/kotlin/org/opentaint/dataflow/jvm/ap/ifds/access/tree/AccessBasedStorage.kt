package org.opentaint.dataflow.jvm.ap.ifds.access.tree

import kotlinx.collections.immutable.persistentHashMapOf
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor

abstract class AccessBasedStorage<S : AccessBasedStorage<S>> {
    private var children = persistentHashMapOf<Accessor, S>()

    abstract fun createStorage(): S

    fun getOrCreateNode(access: AccessPath.AccessNode?): S {
        if (access == null) {
            @Suppress("UNCHECKED_CAST")
            return this as S
        }

        var storage = this
        for (accessor in access) {
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
            children[FinalAccessor]?.let { nodes.add(it) }
        }

        pattern.forEachAccessor { accessor, accessorPattern ->
            children[accessor]?.collectNodesContains(accessorPattern, nodes)
        }
    }

    fun allNodes(): Sequence<S> {
        val storages = mutableListOf<S>()

        val unprocessedStorages = mutableListOf(this)
        while (unprocessedStorages.isNotEmpty()) {
            val storage = unprocessedStorages.removeLast()
            @Suppress("UNCHECKED_CAST")
            storages.add(storage as S)
            unprocessedStorages.addAll(storage.children.values)
        }

        return storages.asSequence()
    }

    private fun getOrCreateChild(accessor: Accessor): S =
        children.getOrElse(accessor) {
            createStorage().also { children = children.put(accessor, it) }
        }
}