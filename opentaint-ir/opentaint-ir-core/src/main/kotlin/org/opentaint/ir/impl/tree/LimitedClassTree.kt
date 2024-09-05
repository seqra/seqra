package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation
import kotlinx.collections.immutable.PersistentList

class LimitedClassTree(
    private val classTree: ClassTree,
    locations: PersistentList<ByteCodeLocation>
) {

    private val locationHashes = locations.map { it.version }.toHashSet()

    fun findClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationHashes.contains(it)
        }
    }
}