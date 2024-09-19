package org.opentaint.java.compilation.database.impl.index

import org.opentaint.java.compilation.database.api.ByteCodeLocation
import org.opentaint.java.compilation.database.api.ByteCodeLocationIndex
import org.opentaint.java.compilation.database.api.ByteCodeLocationIndexBuilder
import org.opentaint.java.compilation.database.impl.tree.ClassNode

suspend fun <T> ByteCodeLocation.index(
    nodes: Collection<ClassNode>,
    builderGetter: (ByteCodeLocation) -> ByteCodeLocationIndexBuilder<T>
): ByteCodeLocationIndex<T> {
    val builder = builderGetter(this)
    nodes.forEach { clazz ->
        val asmNode = clazz.asmNode()
        builder.index(asmNode)
        asmNode.methods.forEach {
            builder.index(asmNode, it)
        }
    }
    return builder.build()
}
