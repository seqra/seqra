package org.opentaint.dataflow.ap.ifds.access.tree

import org.opentaint.dataflow.ap.ifds.access.tree.AccessTree.AccessNode
import org.opentaint.dataflow.util.SoftReferenceManager
import java.lang.ref.Reference
import java.util.IdentityHashMap

class AccessTreeSoftInterner(private val manager: SoftReferenceManager) {
    private var cache: Reference<AccessTreeInterner>? = null

    fun intern(node: AccessNode): AccessNode = node.internNodes(getOrCreateInterner(), IdentityHashMap())

    inline fun <T> withInterner(body: (AccessTreeInterner, IdentityHashMap<AccessNode, AccessNode>) -> T): T =
        body(getOrCreateInterner(), IdentityHashMap())

    fun getOrCreateInterner(): AccessTreeInterner {
        cache?.get()?.let { return it }
        return AccessTreeInterner().also {
            cache = manager.createRef(it)
        }
    }
}
