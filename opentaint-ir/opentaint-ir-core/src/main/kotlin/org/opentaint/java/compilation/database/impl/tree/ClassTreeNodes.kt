package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.api.ByteCodeLocation

abstract class AbstractNode(open val name: String?, val parent: PackageNode?) {

    val fullName: String by lazy(LazyThreadSafetyMode.NONE) {
        val reversedNames = arrayListOf(name)
        var node: PackageNode? = parent
        while (node != null) {
            node.name?.let {
                reversedNames.add(it)
            }
            node = node.parent
        }
        reversedNames.reversed().joinToString(".")
    }
}

interface NodeVisitor {

    fun visitPackageNode(packageNode: PackageNode) {}
}

class RemoveVersionsVisitor(private val locations: Set<ByteCodeLocation>) : NodeVisitor {
    override fun visitPackageNode(packageNode: PackageNode) {
        locations.forEach {
            packageNode.dropVersion(it.id)
        }
    }

}