package org.opentaint.dataflow.ap.ifds.access.tree

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode

class AccessTreeInterner {
    private object InternStrategy: Hash.Strategy<AccessNode>{
        override fun hashCode(o: AccessNode?): Int {
            val obj = o ?: return 0
            return obj.hashCode()
        }

        override fun equals(a: AccessNode?, b: AccessNode?): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false

            if (a.hash != b.hash) return false

            if (a.isAbstract != b.isAbstract || a.isFinal != b.isFinal) return false

            if (!a.accessors.contentEquals(b.accessors)) return false
            return a.accessorNodes.contentIdentityEquals(b.accessorNodes)
        }

        private inline fun <reified T> Array<out T>?.contentIdentityEquals(other: Array<out T>?): Boolean {
            if (this === other) return true
            if (this == null || other == null) return false
            if (this.size != other.size) return false
            for (i in 0 until size) {
                if (this[i] !== other[i]) return false
            }
            return true
        }
    }

    private val cache = Object2ObjectOpenCustomHashMap<AccessNode, AccessNode>(InternStrategy)

    fun intern(node: AccessNode): AccessNode =
        cache.putIfAbsent(node, node) ?: node
}
