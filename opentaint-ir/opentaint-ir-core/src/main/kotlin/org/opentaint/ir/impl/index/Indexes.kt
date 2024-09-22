package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeLocationIndexBuilder
import org.opentaint.ir.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

suspend fun index(node: ClassNode, builder: ByteCodeLocationIndexBuilder<*>) {
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
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

