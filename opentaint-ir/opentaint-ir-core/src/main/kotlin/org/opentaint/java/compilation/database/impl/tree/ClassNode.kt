package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource
import java.util.concurrent.ConcurrentLinkedQueue

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    private val _subTypes = ConcurrentLinkedQueue<ClassNode>()

    val subTypes: List<ClassNode> get() = _subTypes.toList()

    fun addSubType(subTypeNode: ClassNode) {
        _subTypes.add(subTypeNode)
    }

    fun removeSubTypesOfVersion(version: String) {
        val toBeRemoved = subTypes.filter { it.location.id == version }.toSet()
        _subTypes.removeAll(toBeRemoved)
    }

    suspend fun info() = source.meta()

}