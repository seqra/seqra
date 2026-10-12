package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.IntArrayList
import org.opentaint.dataflow.ap.ifds.access.tree.AccessPath.AccessNode.Companion.createNodeFromAccessors
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.util.ConcurrentReadSafeInt2ObjectMap
import org.opentaint.dataflow.util.forEachEntry
import org.opentaint.dataflow.util.forEachInt
import org.opentaint.dataflow.util.getOrCreateNullable
import org.opentaint.dataflow.util.int2ObjectMap

abstract class AccessBasedStorage<S : AccessBasedStorage<S>>(
    val manager: TreeApManager
) {
    /**
     * Allocated on the first child, not on construction.
     *
     * A premise trie is mostly leaves: in a ThingsBoard heap dump 85.5 % of the 1,652,208
     * `IF2FFStorage` nodes had no children at all and 94.2 % had at most one, yet every node
     * carried a full [ConcurrentReadSafeInt2ObjectMap] - the map plus its two 17-slot tables is
     * ~256 B, and those empty maps alone were 345 MiB of a 9.7 GiB live heap.
     *
     * `@Volatile` is load-bearing, not decoration. The field used to be a `val`, so final-field
     * semantics published the map's internals for free; a plain `var` assigned after construction
     * would let a reader see a non-null map whose `key`/`value` tables are not yet visible, which
     * the map's own seqlock cannot repair - it guards mutations of a published map, not the
     * publication of the map itself. The volatile write here is the release that pairs with the
     * reader's acquiring load, and it happens once per node.
     */
    @Volatile
    private var children: ConcurrentReadSafeInt2ObjectMap<S?>? = null

    /** Double-checked under the monitor, so racing writers cannot each install a table. */
    private fun childrenForWrite(): ConcurrentReadSafeInt2ObjectMap<S?> {
        children?.let { return it }

        synchronized(this) {
            children?.let { return it }
            return int2ObjectMap<S?>().also { children = it }
        }
    }

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

    fun find(access: AccessPath.AccessNode?): S? {
        if (access == null) {
            @Suppress("UNCHECKED_CAST")
            return this as S
        }

        var storage = this
        access.toList().forEachInt { accessor ->
            storage = storage.findChild(accessor) ?: return null
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
            children?.get(FINAL_ACCESSOR_IDX)?.let { nodes.add(it) }
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
        children?.get(accessor)?.collectNodesContains(pattern, nodes)
    }

    fun allNodes(): Sequence<S> {
        val storages = mutableListOf<S>()

        val unprocessedStorages = mutableListOf(this)
        while (unprocessedStorages.isNotEmpty()) {
            val storage = unprocessedStorages.removeLast()
            @Suppress("UNCHECKED_CAST")
            storages.add(storage as S)

            storage.children?.forEachEntry { _, s ->
                if (s == null) return@forEachEntry
                unprocessedStorages.add(s)
            }
        }

        return storages.asSequence()
    }

    fun forEachNode(body: (AccessPath.AccessNode?, S) -> Unit) {
        forEachNodeWithAccessorChain { accessors, s ->
            val ap = manager.createNodeFromAccessors(accessors)
            body(ap, s)
        }
    }

    fun forEachNodeWithAccessorChain(body: (IntArrayList, S) -> Unit) {
        val unprocessedStorages = mutableListOf(IntArrayList() to this)
        while (unprocessedStorages.isNotEmpty()) {
            val (accessors, storage) = unprocessedStorages.removeLast()

            @Suppress("UNCHECKED_CAST")
            body(accessors, storage as S)

            storage.children?.forEachEntry { accessor, s ->
                if (s == null) return@forEachEntry

                val childrenAccessors = accessors.clone()
                childrenAccessors.add(accessor)

                unprocessedStorages.add(childrenAccessors to s)
            }
        }
    }

    fun removeChildren(predicate: (AccessorIdx, S) -> Boolean) {
        val children = this.children ?: return
        val accessorsToRemove = IntArrayList()

        children.forEachEntry { accessor, s ->
            if (s == null) return@forEachEntry

            if (predicate(accessor, s)) {
                accessorsToRemove.add(accessor)
            }
        }

        if (accessorsToRemove.isEmpty) return

        accessorsToRemove.forEachInt { accessor ->
            children.put(accessor, null)
        }
    }

    open fun getOrCreateChild(accessor: AccessorIdx): S =
        childrenForWrite().getOrCreateNullable(accessor) { createStorage() }

    open fun findChild(accessor: AccessorIdx): S? =
        children?.get(accessor)

    override fun toString(): String = buildString {
        print(this, prefix = "")
    }

    abstract fun printStorageNode(): String

    fun print(builder: StringBuilder, prefix: String) {
        builder.appendLine("$prefix${printStorageNode()}")
        children?.forEachEntry { accessorIdx, s ->
            if (s == null) return@forEachEntry
            val accessor = with(manager) { accessorIdx.accessor }
            builder.appendLine("$prefix$accessor ->")
            s.print(builder, prefix + " ".repeat(4))
        }
    }
}