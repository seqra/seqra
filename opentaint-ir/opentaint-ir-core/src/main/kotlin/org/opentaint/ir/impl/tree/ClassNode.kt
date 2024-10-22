package org.opentaint.ir.impl.tree

import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.ByteCodeContainer
import org.opentaint.ir.impl.fs.ClassByteCodeSource

class ClassNode(
    simpleName: String,
    packageNode: PackageNode,
    private val source: ClassByteCodeSource
) : AbstractNode<PackageNode>(simpleName, packageNode), ByteCodeContainer {

    override val name: String = simpleName
    val location get() = source.location

    fun fullByteCode() = source.fullByteCode
    fun info() = source.info

    override val classNode: ClassNode
        get() = source.byteCode
    override val binary: ByteArray
        get() = source.binaryByteCode

}