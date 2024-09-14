package org.opentaint.java.compilation.database.impl.tree

import org.opentaint.java.compilation.database.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    suspend fun info() = source.info()
    suspend fun asmNode() = source.asmNode()

}