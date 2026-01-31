package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.AnyAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.ExclusionSet
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.ap.ifds.FinalAccessor
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode.Companion.SUBSEQUENT_ARRAY_ELEMENTS_LIMIT

class AccessPath(
    private val apManager: TreeApManager,
    override val base: AccessPathBase,
    val access: AccessNode?,
    override val exclusions: ExclusionSet
): InitialFactAp {
    override fun rebase(newBase: AccessPathBase): InitialFactAp =
        AccessPath(apManager, newBase, access, exclusions)

    override fun exclude(accessor: Accessor): InitialFactAp =
        AccessPath(apManager, base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessPath(apManager, base, access, exclusions)

    override fun getAllAccessors(): Set<Accessor> {
        val result = hashSetOf<Accessor>()
        var curNode = access
        while (curNode != null) {
            result.add(curNode.accessor)
            curNode = curNode.next
        }
        return result
    }

    override fun startsWithAccessor(accessor: Accessor): Boolean {
        if (access == null) return false
        return access.accessor == accessor
    }

    override fun getStartAccessors(): Set<Accessor> =
        access?.let { setOf(it.accessor) } ?: emptySet()

    override fun readAccessor(accessor: Accessor): AccessPath? {
        if (access == null) return null
        if (access.accessor != accessor) return null
        return AccessPath(apManager, base, access.next, exclusions)
    }

    override fun prependAccessor(accessor: Accessor): InitialFactAp {
        if (access == null) {
            return AccessPath(apManager, base, AccessNode(accessor, next = null), exclusions)
        }

        val node = access.addParent(accessor)
        return AccessPath(apManager, base, node, exclusions)
    }

    override fun clearAccessor(accessor: Accessor): InitialFactAp? {
        if (access == null) return this
        if (access.accessor != accessor) return this
        return null
    }

    sealed interface AccessPathDelta : InitialFactAp.Delta {
        data object Empty : AccessPathDelta {
            override val isEmpty: Boolean get() = true
            override fun startsWithAccessor(accessor: Accessor): Boolean = false
            override fun getStartAccessors(): Set<Accessor> = emptySet()
            override fun getAllAccessors(): Set<Accessor> = emptySet()
            override fun readAccessor(accessor: Accessor): InitialFactAp.Delta? = null
        }

        data class Delta(val node: AccessNode) : AccessPathDelta {
            override val isEmpty: Boolean get() = false
            override fun startsWithAccessor(accessor: Accessor): Boolean = node.accessor == accessor
            override fun getStartAccessors(): Set<Accessor> = setOf(node.accessor)
            override fun getAllAccessors(): Set<Accessor> = node.mapTo(hashSetOf()) { it }
            override fun readAccessor(accessor: Accessor): InitialFactAp.Delta? {
                if (node.accessor == accessor) return node.next?.let { Delta(it) }
                return null
            }
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
        val accessorsOnPath = mutableListOf<Accessor>()

        while (true) {
            if (node == null) {
                if (otherNode.isAbstract) {
                    return listOf(this to AccessPathDelta.Empty)
                }
                return emptyList()
            }

            val nextOtherNode = if (node.accessor is FinalAccessor) {
                if (otherNode.isFinal) {
                    return listOf(this to AccessPathDelta.Empty)
                }

                null
            } else {
                otherNode.getChild(apManager, node.accessor)
            }

            if (nextOtherNode == null) {
                if (otherNode.isAbstract) {
                    val filteredNode = node.filter(other.exclusions) ?: return emptyList()

                    val matchedAccessNode = accessorsOnPath.foldRight(null as AccessNode?) { accessor, prevNode ->
                        AccessNode(accessor, prevNode)
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
        is ExclusionSet.Concrete -> this.takeIf { it.accessor !in exclusion }
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
        val accessor: Accessor,
        val next: AccessNode?
    ): Iterable<Accessor> {
        private val hash: Int
        val size: Int

        init {
            var hash = accessor.hashCode()
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

        override fun iterator(): Iterator<Accessor> = object : Iterator<Accessor> {
            private var node: AccessNode? = this@AccessNode

            override fun hasNext(): Boolean = node != null

            override fun next(): Accessor {
                val node = this.node ?: error("Iterator invariant")
                val accessor = node.accessor
                this.node = node.next
                return accessor
            }
        }

        fun concat(other: AccessNode): AccessNode {
            var node = other
            for (accessor in this.toList().asReversed()) {
                node = node.addParent(accessor)
            }
            return node
        }

        override fun toString(): String = joinToString("") { it.toSuffix() }


        fun addParent(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> error("Final parent")
            ElementAccessor -> AccessNode(ElementAccessor, limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT))
            is FieldAccessor -> AccessNode(accessor, limitFieldAccess(accessor))
            is TaintMarkAccessor -> AccessNode(accessor, this)
            AnyAccessor -> this // todo: All accessors are not supported in tree base ap
        }

        private fun limitElementAccess(limit: Int): AccessNode? {
            if (accessor !is ElementAccessor) return this

            if (limit > 0) {
                val limitedChild = next?.limitElementAccess(limit - 1)
                if (limitedChild === next) return this
                return AccessNode(accessor, limitedChild)
            }

            return collapseElementAccess()
        }


        private fun collapseElementAccess(): AccessNode? {
            var node = this
            while (true) {
                if (node.accessor !is ElementAccessor) return node
                node = node.next ?: return null
            }
        }

        private fun limitFieldAccess(newRootField: FieldAccessor): AccessNode? {
            var node = this
            while (true) {
                val accessor = node.accessor
                if (accessor is FieldAccessor && accessor == newRootField) return node.next
                node = node.next ?: return this
            }
        }

        companion object {
            @JvmStatic
            fun createNodeFromAccessors(accessors: List<Accessor>): AccessNode? =
                accessors.foldRight(null as AccessNode?, ::AccessNode)

            @JvmStatic
            fun createNodeFromReversedAp(reversedAp: ReversedApNode?): AccessNode? =
                reversedAp.foldRight(null as AccessNode?, ::AccessNode)

            class ReversedApNode(val accessor: Accessor, val prev: ReversedApNode?)

            inline fun <R> ReversedApNode?.foldRight(
                initial: R, operation: (accessor: Accessor, acc: R) -> R
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
