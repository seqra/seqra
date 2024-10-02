package org.opentaint.ir.impl.tree

import org.opentaint.ir.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode) {

    override val name: String = simpleName

    val location get() = source.location

    suspend fun info() = source.info()
    suspend fun fullByteCode() = source.fullByteCode()
    fun onAfterIndexing() = source.onAfterIndexing()

}