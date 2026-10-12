package org.opentaint.dataflow.bidi

import com.sun.management.ThreadMXBean
import org.opentaint.dataflow.bidi.interp.ownedList
import org.opentaint.dataflow.bidi.interp.ownedMap
import org.opentaint.dataflow.bidi.interp.ownedSet
import java.lang.management.ManagementFactory
import java.util.Collections

/** Standalone storage measurement; no timing assertions and no change to project scan settings. */
object CompactFormLookupBenchmark {
    private data class Key(val index: Int)
    @Volatile private var retained: Any? = null
    @Volatile private var checksum = 0L

    @JvmStatic fun main(args: Array<String>) {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        println("kind,size,representation,bytes_per_copy,lookup_ns,checksum")
        for (size in listOf(0, 1, 2, 4, 8, 9, 16, 64)) {
            val keys = List(size) { Key(it) }
            val inputMap = keys.associateWith { it.index }
            val inputSet = keys.toCollection(LinkedHashSet())
            val queries = List(128) { if (size == 0 || it % 4 == 0) Key(size + it) else keys[it % size] }
            measure(bean, "map", size, "legacy", { Collections.unmodifiableMap(LinkedHashMap(inputMap)) }) {
                lookupMap(it, queries)
            }
            measure(bean, "map", size, "owned", { ownedMap(inputMap) }) { lookupMap(it, queries) }
            measure(bean, "set", size, "legacy", { Collections.unmodifiableSet(LinkedHashSet(inputSet)) }) {
                lookupSet(it, queries)
            }
            measure(bean, "set", size, "owned", { ownedSet(inputSet) }) { lookupSet(it, queries) }
            measure(bean, "list", size, "legacy", { Collections.unmodifiableList(ArrayList(keys)) }) {
                lookupList(it)
            }
            measure(bean, "list", size, "owned", { ownedList(keys) }) { lookupList(it) }
        }
    }

    private fun <T : Any> measure(bean: ThreadMXBean, kind: String, size: Int, representation: String,
                                copy: () -> T, lookup: (T) -> Long) {
        repeat(20_000) { retained = copy() }
        val thread = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(thread)
        repeat(30_000) { retained = copy() }
        val allocated = (bean.getThreadAllocatedBytes(thread) - before) / 30_000.0
        val value = copy()
        repeat(50_000) { checksum = lookup(value) }
        var total = 0L
        val start = System.nanoTime()
        repeat(100_000) { total += lookup(value) }
        val operations = if (kind == "list") maxOf(1, size) else 128
        val nanos = (System.nanoTime() - start).toDouble() / (100_000L * operations)
        checksum = total
        println("$kind,$size,$representation,$allocated,$nanos,$total")
    }

    private fun lookupMap(values: Map<Key, Int>, queries: List<Key>): Long {
        var total = 0L
        for (query in queries) total += values[query] ?: -1
        return total
    }
    private fun lookupSet(values: Set<Key>, queries: List<Key>): Long {
        var total = 0L
        for (query in queries) if (query in values) total++
        return total
    }
    private fun lookupList(values: List<Key>): Long {
        var total = 0L
        for (index in values.indices) total += values[index].index
        return total
    }
}
