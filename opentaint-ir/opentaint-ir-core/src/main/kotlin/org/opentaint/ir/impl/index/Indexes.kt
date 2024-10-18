package org.opentaint.ir.impl.index

import org.opentaint.ir.api.ByteCodeIndexBuilder
import org.opentaint.ir.api.GlobalIdsStore
import org.opentaint.ir.impl.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

suspend fun index(node: ClassNode, builder: ByteCodeIndexBuilder<*, *>) {
    val asmNode = node.fullByteCode()
    builder.index(asmNode)
    asmNode.methods.forEach {
        builder.index(asmNode, it)
    }
}


class InMemoryGlobalIdsStore : GlobalIdsStore {

    private val counter = AtomicInteger()

    private val all = ConcurrentHashMap<String, Int>()
    private val reversed = ConcurrentHashMap<Int, String>()

    @Volatile
    private var locked = false

    override suspend fun getId(name: String): Int {
        val id = all.getOrPut(name) {
            if (locked) {
                throw IllegalStateException("writing is locked")
            }
            counter.incrementAndGet()
        }
        reversed[id] = name
        return id
    }

    override suspend fun getName(id: Int): String? {
        return reversed[id]
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
        all[value] = key
        reversed[key] = value
        val current = counter.get()
        if (key > current) {
            counter.set(key)
        }
    }

    val count: Int get() = counter.get()
}


fun Map.Entry<Int, Set<Int>>.asString(): String {
    return key.toString() + "=" + value.joinToString(",") + "\n"
}

fun String.asEntry(): Pair<Int, Set<Int>> {
    val splitted = split("=")
    if (splitted.size == 2) {
        val key = splitted[0].toInt()
        val value = splitted[1].split(",").map { it.toInt() }.toSet()
        return key to value
    } else {
        throw IllegalStateException("can't parse string $this")
    }
}