package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.create
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor

class AccessTreeAnySuffixMatcher(suffixNode: AccessTree.AccessNode) {
    private val manager = suffixNode.manager
    private val root = TrieNode(false, null, 0)

    private data class TrieNode(
        val isAbstract: Boolean,
        val prefixLink: TrieNode?,
        val depth: Int,
        val children: Int2ObjectOpenHashMap<TrieNode> = Int2ObjectOpenHashMap<TrieNode>()
    ) {
        fun findChild(accessor: Int): TrieNode? {
            val child = children.get(accessor)
            if (child != null)
                return child
            return prefixLink?.findChild(accessor)
        }

        override fun toString(): String {
            return "(isAbstract=$isAbstract, children=$children)"
        }
    }

    private fun AccessorIdx.coveredByAny(): Boolean =
        this == ELEMENT_ACCESSOR_IDX || this.isFieldAccessor()

    private data class RawNodeWithParent(
        val node: AccessTree.AccessNode,
        val accessor: AccessorIdx,
        val parent: TrieNode,
        val depth: Int,
        val notCoveredByAny: Int?,
    )

    init {
        if (suffixNode.accessors != null && suffixNode.accessorNodes != null) {
            val unprocessed = ArrayDeque<RawNodeWithParent>()
            suffixNode.forEachAccessor { accessor, accessorNode ->
                val notCoveredByAny = if (accessor.coveredByAny()) null else 1
                unprocessed.addLast(RawNodeWithParent(accessorNode, accessor, root, 1, notCoveredByAny))
            }

            while (unprocessed.isNotEmpty()) {
                val (node, accessor, triePar, depth, notCoveredByAny) = unprocessed.removeFirst()
                // disallowing [any]->...->[any]
                check(accessor != ANY_ACCESSOR_IDX)

                val curNotCoveredByAny = when {
                    notCoveredByAny != null -> notCoveredByAny
                    !accessor.coveredByAny() -> depth
                    else -> null
                }

                var prefix = triePar.prefixLink
                while (prefix != null) {
                    val next = prefix.children.get(accessor)
                    val notCoveredStillInSuffix = curNotCoveredByAny == null || depth - next.depth > curNotCoveredByAny
                    if (next != null && notCoveredStillInSuffix) {
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
                val newTrieNode = TrieNode(node.isAbstract || prefix.isAbstract, prefix, depth)
                triePar.children.put(accessor, newTrieNode)

                node.forEachAccessor{ accessor, accessorNode ->
                    unprocessed.addLast(RawNodeWithParent(accessorNode, accessor, newTrieNode, depth + 1, curNotCoveredByAny))
                }
            }
        }
    }

    fun getNonMatchingNode(node: AccessTree.AccessNode): Pair<IntArray, Array<AccessTree.AccessNode>> {
        val accessorIdx = mutableListOf<AccessorIdx>()
        val accessorNodes = mutableListOf<AccessTree.AccessNode>()

        node.forEachAccessor { accessor, accessorNode ->
            if (accessor.coveredByAny()) {
                val child = getNonMatchingNode(root, accessorNode, true)
                if (child != null) {
                    accessorIdx.add(accessor)
                    accessorNodes.add(child)
                }
            }
            else {
                // two [any]-branches can be merged naturally, as those not accepted by [any]
                accessorIdx.add(accessor)
                accessorNodes.add(accessorNode)
            }
        }

        return accessorIdx.toIntArray() to accessorNodes.toTypedArray()
    }

    private fun getNonMatchingNode(trie: TrieNode, node: AccessTree.AccessNode, prefixCoveredByAny: Boolean): AccessTree.AccessNode? {
        val accessorIdx = mutableListOf<AccessorIdx>()
        val accessorNodes = mutableListOf<AccessTree.AccessNode>()

        node.forEachAccessor { accessor, accessorNode ->
            val prefixStillCovered = prefixCoveredByAny && accessor.coveredByAny()
            // prefix has an accessor not covered by [any], so the whole suffix is not matched
            val fallback = if (prefixStillCovered) root else null
            val next =
                if (accessor == ANY_ACCESSOR_IDX) fallback
                else trie.findChild(accessor) ?: fallback
            val child = next?.let { getNonMatchingNode(it, accessorNode, prefixStillCovered) }
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
