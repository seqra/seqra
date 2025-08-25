package org.opentaint.dataflow.jvm.ap.ifds.access.tree

import org.opentaint.dataflow.jvm.ap.ifds.AccessPathBase
import org.opentaint.dataflow.jvm.ap.ifds.Accessor
import org.opentaint.dataflow.jvm.ap.ifds.ElementAccessor
import org.opentaint.dataflow.jvm.ap.ifds.ExclusionSet
import org.opentaint.dataflow.jvm.ap.ifds.FieldAccessor
import org.opentaint.dataflow.jvm.ap.ifds.FinalAccessor
import org.opentaint.dataflow.jvm.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.jvm.ap.ifds.access.FactApDelta
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.AccessTree.AccessNode.Companion.SUBSEQUENT_ARRAY_ELEMENTS_LIMIT

class AccessPath(
    override val base: AccessPathBase,
    val access: AccessNode?,
    override val exclusions: ExclusionSet
): InitialFactAp {
    override fun rebase(newBase: AccessPathBase): InitialFactAp =
        AccessPath(newBase, access, exclusions)

    override fun exclude(accessor: Accessor): InitialFactAp =
        AccessPath(base, access, exclusions.add(accessor))

    override fun replaceExclusions(exclusions: ExclusionSet): InitialFactAp =
        AccessPath(base, access, exclusions)

    override fun startsWithAccessor(accessor: Accessor): Boolean {
        if (access == null) return false
        return access.accessor == accessor
    }

    override fun readAccessor(accessor: Accessor): InitialFactAp? {
        if (access == null) return null
        if (access.accessor != accessor) return null
        return AccessPath(base, access.next, exclusions)
    }

    override fun prependAccessor(accessor: Accessor): InitialFactAp {
        if (access == null) {
            return AccessPath(base, AccessNode(accessor, next = null), exclusions)
        }

        val node = access.addParent(accessor)
        return AccessPath(base, node, exclusions)
    }

    override fun clearAccessor(accessor: Accessor): InitialFactAp? {
        if (access == null) return this
        if (access.accessor != accessor) return this
        return null
    }

    sealed interface AccessPathDelta : FactApDelta {
        data object Empty : AccessPathDelta {
            override val isEmpty: Boolean get() = true
        }

        data class Delta(val node: AccessNode) : AccessPathDelta {
            override val isEmpty: Boolean get() = false
        }
    }

    override fun splitDelta(other: FinalFactAp): List<Pair<InitialFactAp, FactApDelta>> {
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
                otherNode.getChild(node.accessor)
            }

            if (nextOtherNode == null) {
                if (otherNode.isAbstract) {
                    val filteredNode = node.filter(other.exclusions) ?: return emptyList()

                    val matchedAccessNode = accessorsOnPath.foldRight(null as AccessNode?) { accessor, prevNode ->
                        AccessNode(accessor, prevNode)
                    }
                    val matchedFact = AccessPath(base, matchedAccessNode, exclusions)

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

    override fun concat(delta: FactApDelta): InitialFactAp {
        delta as AccessPathDelta

        when (delta) {
            AccessPathDelta.Empty -> return this
            is AccessPathDelta.Delta -> {
                var node: AccessNode = delta.node
                if (access != null) {
                    for (accessor in access.toList().asReversed()) {
                        node = node.addParent(accessor)
                    }
                }
                return AccessPath(base, node, exclusions)
            }
        }
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

        override fun toString(): String = joinToString("") { it.toSuffix() }


        fun addParent(accessor: Accessor): AccessNode = when (accessor) {
            FinalAccessor -> error("Final parent")
            ElementAccessor -> AccessNode(ElementAccessor, limitElementAccess(limit = SUBSEQUENT_ARRAY_ELEMENTS_LIMIT))
            is FieldAccessor -> AccessNode(accessor, limitFieldAccess(accessor))
            is TaintMarkAccessor -> AccessNode(accessor, this)
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
                if (accessor is FieldAccessor && accessor.className == newRootField.className) return node.next
                node = node.next ?: return this
            }
        }

        companion object {
            @JvmStatic
            fun createNodeFromAp(accessors: Iterator<Accessor>): AccessNode? {
                if (!accessors.hasNext()) {
                    return null
                }

                val accessor = accessors.next()
                return AccessNode(accessor = accessor, next = createNodeFromAp(accessors))
            }

            fun AccessNode?.iterator(): Iterator<Accessor> = this?.iterator() ?: emptyList<Accessor>().iterator()

            fun AccessNode?.asIterable(): Iterable<Accessor> = this ?: emptyList()
        }
    }
}
