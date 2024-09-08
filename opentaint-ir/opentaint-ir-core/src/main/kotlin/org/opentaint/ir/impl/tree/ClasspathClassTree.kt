package org.opentaint.ir.impl.tree

import org.opentaint.ir.api.ByteCodeLocation

class ClasspathClassTree(
    private val classTree: ClassTree,
    locations: List<ByteCodeLocation>
) {

    private val locationHashes = locations.map { it.version }.toHashSet()
    fun firstClassOrNull(fullName: String): ClassNode? {
        return classTree.firstClassNodeOrNull(fullName) {
            locationHashes.contains(it)
        }
    }

    fun findSubTypesOf(fullName: String): List<ClassNode> {
        return firstClassOrNull(fullName)?.subTypes.orEmpty().filter {
            locationHashes.contains(it.location.version)
        }
    }
}