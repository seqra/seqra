package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.create
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX

class AccessTreeAnySuffixMatcher(suffixNode: AccessTree.AccessNode) {
    private val manager = suffixNode.manager
    private val root = TrieNode(false, null)

    private data class TrieNode(
        val isAbstract: Boolean,
        val prefixLink: TrieNode?,
        val children: Int2ObjectOpenHashMap<TrieNode> = Int2ObjectOpenHashMap<TrieNode>()
    ) {
        fun findChild(accessor: Int): TrieNode? {
            val child = children.get(accessor)
            if (child != null)
                return child
            return prefixLink?.findChild(accessor)
        }
    }

    private data class RawNodeWithParent(
        val node: AccessTree.AccessNode,
        val accessor: Int,
        val parent: TrieNode
    )

    init {
        if (suffixNode.accessors != null && suffixNode.accessorNodes != null) {
            val unprocessed = ArrayDeque<RawNodeWithParent>()
            suffixNode.forEachAccessor { accessor, accessorNode ->
                unprocessed.addLast(RawNodeWithParent(accessorNode, accessor, root))
            }

            while (unprocessed.isNotEmpty()) {
                val (node, accessor, triePar) = unprocessed.removeFirst()
                // disallowing [any]->...->[any]
                check(accessor != ANY_ACCESSOR_IDX)

                var prefix = triePar.prefixLink
                while (prefix != null) {
                    val next = prefix.children.get(accessor)
                    if (next != null) {
                        prefix = next
                        break
                    }
                    prefix = prefix.prefixLink
                }
                if (triePar === root) {
                    prefix = root
                }
                if (prefix == null) {
                    prefix = root.children.get(accessor) ?: root
                }
                val newTrieNode = TrieNode(node.isAbstract || prefix.isAbstract, prefix)
                triePar.children.put(accessor, newTrieNode)

                node.forEachAccessor{ accessor, accessorNode ->
                    unprocessed.addLast(RawNodeWithParent(accessorNode, accessor, newTrieNode))
                }
            }
        }
    }

    fun getNonMatchingNode(node: AccessTree.AccessNode): Pair<IntArray, Array<AccessTree.AccessNode>> {
        val accessorIdx = mutableListOf<AccessorIdx>()
        val accessorNodes = mutableListOf<AccessTree.AccessNode>()

        node.forEachAccessor { accessor, accessorNode ->
            if (accessor != ANY_ACCESSOR_IDX) {
                val child = getNonMatchingNode(root, accessorNode)
                if (child != null) {
                    accessorIdx.add(accessor)
                    accessorNodes.add(child)
                }
            }
            else {
                // two [any]-branches can be merged naturally
                accessorIdx.add(accessor)
                accessorNodes.add(accessorNode)
            }
        }

        return accessorIdx.toIntArray() to accessorNodes.toTypedArray()
    }

    private fun getNonMatchingNode(trie: TrieNode, node: AccessTree.AccessNode): AccessTree.AccessNode? {
        val accessorIdx = mutableListOf<AccessorIdx>()
        val accessorNodes = mutableListOf<AccessTree.AccessNode>()

        node.forEachAccessor { accessor, accessorNode ->
            val next =
                if (accessor == ANY_ACCESSOR_IDX) root
                else trie.findChild(accessor) ?: root
            val child = getNonMatchingNode(next, accessorNode)
            if (child != null) {
                accessorIdx.add(accessor)
                accessorNodes.add(child)
            }
        }

        val thisAbstract = node.isAbstract && !trie.isAbstract

        // all branches matched the any-suffix
        if (!thisAbstract && accessorIdx.isEmpty())
            return null

        val thisFinal = node.isFinal && accessorIdx.any { it == FINAL_ACCESSOR_IDX }

        return manager.create(thisAbstract, thisFinal, accessorIdx.toIntArray(), accessorNodes.toTypedArray())
    }
}
