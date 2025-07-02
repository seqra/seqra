package org.opentaint.dataflow.jvm.ap.ifds

class AccessPath(val base: AccessPathBase, val access: AccessNode?, val exclusions: ExclusionSet) {
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

    class AccessNode private constructor(
        private val accessor: Accessor,
        private val next: AccessNode?
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

        fun lastAccessor(): Accessor {
            if (next == null) return accessor
            return next.lastAccessor()
        }

        fun parentAccessPath(): AccessNode? {
            if (next == null) return null
            return AccessNode(accessor, next = next.parentAccessPath())
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
