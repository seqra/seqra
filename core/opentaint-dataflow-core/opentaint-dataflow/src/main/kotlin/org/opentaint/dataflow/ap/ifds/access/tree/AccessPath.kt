package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.SUBSEQUENT_ARRAY_ELEMENTS_LIMIT
import org.opentaint.dataflow.ap.ifds.access.util.AccessorIdx
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ANY_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.FINAL_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.VALUE_ACCESSOR_IDX
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isFieldAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isStaticAccessor
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.isTaintMarkAccessor
import org.opentaint.dataflow.util.reversedForEachInt

class AccessPath(
    private val apManager: TreeApManager,
    override val base: AccessPathBase,
    val access: AccessNode?,
    override val exclusions: ExclusionSet
): InitialFactAp {
    override fun rebase(newBase: AccessPathBase): InitialFactAp =
        AccessPath(apManager, newBase, access, exclusions)

    override fun isAbstract(): Boolean = access == null

    override fun exclude(accessor: Accessor): InitialFactAp =
        AccessPath(apManager, base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessPath(apManager, base, access, exclusions)

    override fun getAllAccessors(): Set<Accessor> =
        access?.accessorList()?.toSet().orEmpty()

    override fun startsWithAccessor(accessor: Accessor): Boolean = with(apManager) {
        if (access == null) return false
        return access.accessor.accessor == accessor
    }

    override fun getStartAccessors(): Set<Accessor> = with(apManager) {
        access?.let { setOf(it.accessor.accessor) } ?: emptySet()
    }

    override fun readAccessor(accessor: Accessor): AccessPath? = with(apManager) {
        if (access == null) return null
        if (access.accessor.accessor != accessor) return null
        return AccessPath(apManager, base, access.next, exclusions)
    }

    override fun prependAccessor(accessor: Accessor): InitialFactAp {
        val accessorIdx = with(apManager) { accessor.idx }

        if (access == null) {
            return AccessPath(apManager, base, AccessNode(apManager, accessorIdx, next = null), exclusions)
        }

        val node = access.addParent(accessorIdx)
        return AccessPath(apManager, base, node, exclusions)
    }

    override fun clearAccessor(accessor: Accessor): InitialFactAp? = with(apManager) {
        if (access == null) return this@AccessPath
        if (access.accessor.accessor != accessor) return this@AccessPath
        return null
    }

    override fun compatibilityFilter(typeChecker: FactTypeChecker): FactTypeChecker.FactCompatibilityFilter {
        val node = access ?: return FactTypeChecker.AlwaysCompatibleFilter
        return typeChecker.accessPathCompatibilityFilter(node.accessorList())
    }

    sealed interface AccessPathDelta : InitialFactAp.Delta {
        data object Empty : AccessPathDelta {
            override val isEmpty: Boolean get() = true
            override fun startsWithAccessor(accessor: Accessor): Boolean = false
            override fun getStartAccessors(): Set<Accessor> = emptySet()
            override fun getAllAccessors(): Set<Accessor> = emptySet()
            override fun readAccessor(accessor: Accessor): InitialFactAp.Delta? = null
            override fun isAbstract(): Boolean = true
        }

        data class Delta(val node: AccessNode) : AccessPathDelta {
            override val isEmpty: Boolean get() = false
            override fun startsWithAccessor(accessor: Accessor): Boolean =
                with(node.manager) { node.accessor.accessor == accessor }

            override fun getStartAccessors(): Set<Accessor> =
                with(node.manager) { setOf(node.accessor.accessor) }

            override fun getAllAccessors(): Set<Accessor> =
                node.accessorList().toSet()

            override fun readAccessor(accessor: Accessor): InitialFactAp.Delta? = with(node.manager) {
                if (node.accessor.accessor == accessor) return node.next?.let { Delta(it) }
                return null
            }

            override fun isAbstract(): Boolean = false
        }

        override fun concat(other: InitialFactAp.Delta): InitialFactAp.Delta {
            other as AccessPathDelta

            return when (this) {
                is Empty -> other
                is Delta -> when (other) {
                    is Empty -> this
                    is Delta -> Delta(node.concat(other.node))
                }
            }
        }
    }

    override fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, InitialFactAp.Delta>> {
        other as AccessTree

        if (base != other.base) return emptyList()

        var node: AccessNode? = access
        var otherNode: AccessTree.AccessNode = other.access
        val accessorsOnPath = IntArrayList()

        while (true) {
            if (node == null) {
                if (otherNode.isAbstract) {
                    return listOf(this to AccessPathDelta.Empty)
                }
                return emptyList()
            }

            val nextOtherNode = if (node.accessor == FINAL_ACCESSOR_IDX) {
                if (otherNode.isFinal) {
                    return listOf(this to AccessPathDelta.Empty)
                }

                null
            } else {
                otherNode.getChild(node.accessor)
            }

            if (nextOtherNode == null) {
                if (otherNode.isAbstract) {
                    val filteredNode = node.filter(other.exclusions) ?: return emptyList()

                    val matchedAccessNode = accessorsOnPath.foldRight(null as AccessNode?) { accessor, prevNode ->
                        AccessNode(apManager, accessor, prevNode)
                    }
                    val matchedFact = AccessPath(apManager, base, matchedAccessNode, exclusions)

                    return listOf(matchedFact to AccessPathDelta.Delta(filteredNode))
                }

                return emptyList()
            }

            accessorsOnPath.add(node.accessor)
            node = node.next
            otherNode = nextOtherNode
        }
    }

    private fun AccessNode.filter(exclusion: ExclusionSet): AccessNode? = when (exclusion) {
        ExclusionSet.Empty -> this
        is ExclusionSet.Concrete -> this.takeIf { with(manager) { it.accessor.accessor !in exclusion } }
        ExclusionSet.Universe -> null
    }

    override fun concat(delta: InitialFactAp.Delta): InitialFactAp {
        delta as AccessPathDelta

        when (delta) {
            AccessPathDelta.Empty -> return this
            is AccessPathDelta.Delta -> {
                val node = access?.concat(delta.node) ?: delta.node
                return AccessPath(apManager, base, node, exclusions)
            }
        }
    }

    override fun contains(factAp: InitialFactAp): Boolean {
        factAp as AccessPath
        return this == factAp
    }

    override val size: Int
        get() = access?.size ?: 0

    override val depth: Int get() = size

    override fun toString(): String = "$base${access ?: ""}.*/$exclusions"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccessPath

        if (base != other.base) return false
        if (access != other.access) return false
        if (exclusions != other.exclusions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + access.hashCode()
        result = 31 * result + exclusions.hashCode()
        return result
    }

    class AccessNode(
        val manager: TreeApManager,
        val accessor: AccessorIdx,
        val next: AccessNode?
    ) {
        private val hash: Int
        val size: Int

        init {
            var hash = accessor
            if (next != null) hash += 17 * next.hash
            this.hash = hash
        }

        init {
            var size = 1
            if (next != null) size += next.size
            this.size = size
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AccessNode) return false

            if (hash != other.hash) return false
            if (accessor != other.accessor) return false

            return next == other.next
        }

        fun toList(): IntArrayList {
            val result = IntArrayList()
            var node = this
            while (true) {
                result.add(node.accessor)
                node = node.next ?: break
            }
            return result
        }

        fun concat(other: AccessNode): AccessNode {
            val thisAccessors = this.toList()
            var node = other
            thisAccessors.reversedForEachInt { accessor ->
                node = node.addParent(accessor)
            }
            return node
        }

        fun accessorList(): List<Accessor> = toList().map { with(manager) { it.accessor } }

        override fun toString(): String = accessorList().joinToString("") { it.toSuffix() }

        fun addParent(accessor: AccessorIdx): AccessNode {
            checkNoClassStaticAccessor()

            return when {
                accessor == FINAL_ACCESSOR_IDX -> error("Final parent")
                accessor == ELEMENT_ACCESSOR_IDX -> AccessNode(
                    manager, ELEMENT_ACCESSOR_IDX,
                    limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT)
                )
                accessor.isFieldAccessor() -> AccessNode(manager, accessor, limitFieldAccess(accessor))
                accessor.isStaticAccessor() -> AccessNode(manager, accessor, this)
                accessor.isTaintMarkAccessor() -> AccessNode(manager, accessor, this)
                accessor == VALUE_ACCESSOR_IDX -> {
                    check(this.accessor.isTaintMarkAccessor()) {
                        "Value accessor can only be prepended before a taint mark"
                    }
                    AccessNode(manager, accessor, this)
                }

                accessor == ANY_ACCESSOR_IDX -> this // todo: All accessors are not supported in tree base ap
                else -> error("Unsupported accessor $accessor")
            }
        }

        private fun checkNoClassStaticAccessor() {
            var node: AccessNode? = this
            while (node != null) {
                check(!node.accessor.isStaticAccessor()) {
                    "At most one ClassStaticAccessor is allowed in access path"
                }
                node = node.next
            }
        }

        private fun limitElementAccess(limit: Int): AccessNode? {
            if (accessor != ELEMENT_ACCESSOR_IDX) return this

            if (limit > 0) {
                val limitedChild = next?.limitElementAccess(limit - 1)
                if (limitedChild === next) return this
                return AccessNode(manager, accessor, limitedChild)
            }

            return collapseElementAccess()
        }


        private fun collapseElementAccess(): AccessNode? {
            var node = this
            while (true) {
                if (node.accessor != ELEMENT_ACCESSOR_IDX) return node
                node = node.next ?: return null
            }
        }

        private fun limitFieldAccess(newRootField: AccessorIdx): AccessNode? {
            var node = this
            while (true) {
                val accessor = node.accessor
                if (accessor == newRootField) return node.next
                node = node.next ?: return this
            }
        }

        companion object {
            @JvmStatic
            fun TreeApManager.createNodeFromAccessors(accessors: IntList): AccessNode? =
                accessors.foldRight(null as AccessNode?) { accessor, acc -> AccessNode(this, accessor, acc) }

            @JvmStatic
            fun TreeApManager.createNodeFromReversedAp(reversedAp: ReversedApNode?): AccessNode? =
                reversedAp.foldRight(null as AccessNode?) { accessor, acc -> AccessNode(this, accessor, acc) }

            class ReversedApNode(val accessor: AccessorIdx, val prev: ReversedApNode?)

            inline fun <R> ReversedApNode?.foldRight(
                initial: R, operation: (accessor: AccessorIdx, acc: R) -> R
            ): R {
                if (this == null) return initial

                var resultNode: R = initial
                var reversedNode: ReversedApNode = this

                while (true) {
                    val accessor = reversedNode.accessor
                    resultNode = operation(accessor, resultNode)
                    reversedNode = reversedNode.prev ?: return resultNode
                }
            }
        }
    }
}
