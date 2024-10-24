package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeIndexer
import org.opentaint.ir.impl.vfs.ClassVfsItem

suspend fun index(node: ClassVfsItem, builder: ByteCodeIndexer) {
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}