package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeIndexBuilder
import org.opentaint.ir.impl.tree.ClassNode

suspend fun index(node: ClassNode, builder: ByteCodeIndexBuilder<*, *>) {
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}