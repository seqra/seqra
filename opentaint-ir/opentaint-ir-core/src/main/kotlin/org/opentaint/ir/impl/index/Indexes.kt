package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.impl.fs.fullAsmNode
import org.opentaint.ir.impl.vfs.ClassVfsItem

suspend fun index(node: ClassVfsItem, builder: ByteCodeIndexer) {
    val asmNode = node.source.fullAsmNode
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}