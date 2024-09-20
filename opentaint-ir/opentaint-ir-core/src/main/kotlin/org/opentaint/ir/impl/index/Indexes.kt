package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeLocation
import org.opentaint.ir.api.ByteCodeLocationIndex
import org.opentaint.ir.api.ByteCodeLocationIndexBuilder
import org.opentaint.ir.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

suspend fun <T> ByteCodeLocation.index(
    nodes: Collection<ClassNode>,
    builderGetter: () -> ByteCodeLocationIndexBuilder<T>
): ByteCodeLocationIndex<T> {
    val builder = builderGetter()
    nodes.forEach { clazz ->
        val asmNode = clazz.asmNode()
        builder.index(asmNode)
        asmNode.methods.forEach {
            builder.index(asmNode, it)
        }
    }
    return builder.build(this)
}


object GlobalIds {

    private val counter = AtomicInteger()

    private val all = ConcurrentHashMap<String, Int>()
    private val reversed = ConcurrentHashMap<Int, String>()

    fun getId(name: String): Int {
        val id = all.getOrPut(name) {
            counter.incrementAndGet()
        }
        reversed[id] = name
        return id
    }

    fun getName(id: Int): String? {
        return reversed.get(id)
    }


}

