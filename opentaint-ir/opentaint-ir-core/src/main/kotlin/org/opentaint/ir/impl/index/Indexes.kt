package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeLocationIndexBuilder
import org.opentaint.ir.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

suspend fun index(node: ClassNode, builder: ByteCodeLocationIndexBuilder<*, *>) {
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

    @Volatile
    private var locked = false

    fun getId(name: String): Int {
        val id = all.getOrPut(name) {
            if (locked) {
                throw IllegalStateException("writing is locked")
            }
            counter.incrementAndGet()
        }
        reversed[id] = name
        return id
    }

    fun getName(id: Int): String? {
        return reversed.get(id)
    }

    fun all(since: Int, to: Int): Map<Int, String> {
        return reversed.filterKeys { it in since..to }
    }

    fun restore(action: () -> Unit) {
        locked = true
        try {
            action()
        } finally {
            locked = false
        }
    }

    fun append(key: Int, value: String) {
        all.put(value, key)
        reversed.put(key, value)
        val current = counter.get()
        if (key > current) {
            counter.set(key)
        }
    }

    val count: Int get() = counter.get()
}

