package org.opentaint.java.compilation.database.impl.tree

import jetbrains.exodus.core.dataStructures.persistent.PersistentHashSet
import jetbrains.exodus.core.dataStructures.persistent.writeFinally
import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    private val _subTypes = PersistentHashSet<ClassNode>()

    val subTypes: List<ClassNode> get() = _subTypes.toList()

    fun addSubType(subTypeNode: ClassNode) {
        _subTypes.writeFinally {
            add(subTypeNode)
        }
    }

    fun removeSubTypesOfVersion(version: String) {
        val toBeRemoved = subTypes.filter { it.location.id == version }.toSet()
        _subTypes.writeFinally {
            toBeRemoved.forEach {
                remove(it)
            }
        }

    }

    suspend fun info() = source.meta()

}