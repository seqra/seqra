package org.opentaint.ir.impl.vfs

import org.objectweb.asm.tree.ClassNode
import org.opentaint.ir.api.ByteCodeContainer
import org.opentaint.ir.impl.fs.ClassSource

class ClassVfsItem(
    override val name: String,
    packageNode: PackageVfsItem,
    internal val source: ClassSource
) : AbstractVfsItem<PackageVfsItem>(name, packageNode), ByteCodeContainer {

    val location get() = source.location

    fun fullAsmNode() = source.fullAsmNode

    override val asmNode: ClassNode
        get() = source.asmNode

    override val binary: ByteArray
        get() = source.byteCode

}