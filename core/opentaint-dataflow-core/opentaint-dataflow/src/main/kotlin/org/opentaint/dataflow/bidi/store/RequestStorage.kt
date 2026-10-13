package org.opentaint.dataflow.bidi.store

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.InitialAp
import org.opentaint.dataflow.util.Cancellation

/** Append-only primitive lane. Handles are insertion ordinals, so a horizon needs no copied member list. */
internal class RequestIntLane(private val census: N1Statistics.Census?) {
    private var data = REQUEST_EMPTY_INTS
    var size = 0
        private set
    operator fun get(index: Int): Int = data[index]
    fun add(value: Int) {
        if (size == data.size) {
            val capacity = requestCapacity(data.size)
            val next = data.copyOf(capacity)
            census?.primitiveArray(data.size, capacity)
            data = next
        }
        data[size] = value
        size = Math.addExact(size, 1)
    }
    fun countBefore(horizon: Int): Int {
        var lower = 0
        var upper = size
        while (lower < upper) {
            val middle = lower + (upper - lower) / 2
            if (data[middle] < horizon) lower = middle + 1 else upper = middle
        }
        return lower
    }
    fun sorted(): IntArray = data.copyOf(size).also { it.sort() }
    fun clear() { data = REQUEST_EMPTY_INTS; size = 0 }
}

internal class RequestDirectory<T : Any>(private val census: N1Statistics.Census?) {
    private var data = REQUEST_EMPTY_REFERENCES
    var size = 0
        private set
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T = data[index] as T
    fun add(value: T): Int {
        if (size == data.size) {
            val capacity = requestCapacity(data.size)
            val next = data.copyOf(capacity)
            census?.referenceArray(data.size, capacity)
            data = next
        }
        val handle = size
        data[handle] = value
        size = Math.addExact(size, 1)
        return handle
    }
    fun clear() { data = REQUEST_EMPTY_REFERENCES; size = 0 }
}

/** One InitialAp reference per local header. Hash slots contain local handles, never manager IDs. */
internal class RequestOrigins(private val census: N1Statistics.Census?, private val cancellation: Cancellation?) {
    private val values = RequestDirectory<InitialAp>(census)
    private var slots = REQUEST_EMPTY_INTS
    private var markRows = REQUEST_EMPTY_INTS
    operator fun get(handle: Int): InitialAp = values[handle]
    fun markRow(handle: Int): Int = markRows[handle] - 1
    fun setMarkRow(handle: Int, row: Int) { markRows[handle] = Math.addExact(row, 1) }
    fun find(initial: InitialAp): Int {
        if (slots.isEmpty()) return -1
        var slot = requestMix(System.identityHashCode(initial)) and (slots.size - 1)
        while (slots[slot] != 0) {
            val handle = slots[slot] - 1
            if (values[handle] === initial) return handle
            slot = (slot + 1) and (slots.size - 1)
        }
        return -1
    }
    fun getOrAdd(initial: InitialAp): Int {
        find(initial).let { if (it >= 0) return it }
        if (slots.isEmpty() || values.size >= slots.size - slots.size / 4) rehash()
        if (values.size == markRows.size) {
            val capacity = requestCapacity(markRows.size)
            val next = markRows.copyOf(capacity)
            census?.primitiveArray(markRows.size, capacity)
            markRows = next
        }
        val handle = values.add(initial)
        var slot = requestMix(System.identityHashCode(initial)) and (slots.size - 1)
        while (slots[slot] != 0) slot = (slot + 1) and (slots.size - 1)
        slots[slot] = Math.addExact(handle, 1)
        census?.origin()
        return handle
    }
    private fun rehash() {
        val next = IntArray(if (slots.isEmpty()) 4 else Math.multiplyExact(slots.size, 2))
        for (handle in 0 until values.size) {
            if (handle and 255 == 0) cancellation?.checkpoint()
            var slot = requestMix(System.identityHashCode(values[handle])) and (next.size - 1)
            while (next[slot] != 0) slot = (slot + 1) and (next.size - 1)
            next[slot] = handle + 1
        }
        census?.primitiveArray(slots.size, next.size)
        slots = next
    }
    fun clear() { values.clear(); slots = REQUEST_EMPTY_INTS; markRows = REQUEST_EMPTY_INTS }
}

/** Empty/singleton fields, a small primitive array, then an open-addressed sparse int-to-handle table. */
internal class RequestMembers(private val census: N1Statistics.Census?, private val cancellation: Cancellation?) {
    var size = 0
        private set
    private var firstKey = 0
    private var firstHandle = -1
    private var keys = REQUEST_EMPTY_INTS
    private var handles = REQUEST_EMPTY_INTS
    private var hashed = false
    operator fun get(key: Int): Int {
        if (size == 0) return -1
        if (size == 1) return if (firstKey == key) firstHandle else -1
        if (!hashed) {
            for (index in 0 until size) if (keys[index] == key) return handles[index]
            return -1
        }
        var slot = requestMix(key) and (keys.size - 1)
        while (handles[slot] >= 0) {
            if (keys[slot] == key) return handles[slot]
            slot = (slot + 1) and (keys.size - 1)
        }
        return -1
    }
    fun put(key: Int, handle: Int) {
        check(handle >= 0 && get(key) < 0)
        if (size == 0) { firstKey = key; firstHandle = handle; size = 1; return }
        if (size == 1) {
            allocate(2, hashed = false)
            keys[0] = firstKey; handles[0] = firstHandle
        }
        if (!hashed && size == keys.size) {
            if (size < 8) allocate(Math.multiplyExact(keys.size, 2), hashed = false)
            else allocate(16, hashed = true)
        } else if (hashed && size >= keys.size - keys.size / 4) {
            allocate(Math.multiplyExact(keys.size, 2), hashed = true)
        }
        if (hashed) {
            var slot = requestMix(key) and (keys.size - 1)
            while (handles[slot] >= 0) slot = (slot + 1) and (keys.size - 1)
            keys[slot] = key; handles[slot] = handle
        } else { keys[size] = key; handles[size] = handle }
        size = Math.addExact(size, 1)
    }
    private fun allocate(capacity: Int, hashed: Boolean) {
        val nextKeys = IntArray(capacity)
        val nextHandles = IntArray(capacity) { -1 }
        if (hashed) {
            for (index in handles.indices) if (handles[index] >= 0) {
                if (index and 255 == 0) cancellation?.checkpoint()
                var slot = requestMix(keys[index]) and (capacity - 1)
                while (nextHandles[slot] >= 0) slot = (slot + 1) and (capacity - 1)
                nextKeys[slot] = keys[index]; nextHandles[slot] = handles[index]
            }
        } else {
            keys.copyInto(nextKeys); handles.copyInto(nextHandles)
        }
        census?.primitiveArray(Math.multiplyExact(keys.size, 2), Math.multiplyExact(capacity, 2))
        keys = nextKeys; handles = nextHandles; this.hashed = hashed
    }
}

internal class RequestBucket(census: N1Statistics.Census?) {
    val rows = RequestIntLane(census)
    val lane = RequestIntLane(census)
    val positions = RequestIntLane(census)
}

/** The same prefix/extension traversal as PathTrie, with buckets only at populated positions. */
internal class RequestPathIndex(private val cancellation: Cancellation?, private val census: N1Statistics.Census?) {
    private class Node {
        var children: Int2ObjectOpenHashMap<Node>? = null
        var bucket: RequestBucket? = null
        fun child(accessor: Int): Node? = children?.get(accessor)
    }
    private val roots = HashMap<AccessPathBase, Node>(4)
    fun bucket(base: AccessPathBase, path: IntArray): RequestBucket {
        cancellation?.checkpoint()
        var node = roots.getOrPut(base) { Node().also { census?.node() } }
        for (accessor in path) {
            cancellation?.checkpoint()
            val children = node.children ?: Int2ObjectOpenHashMap<Node>().also { node.children = it }
            node = children.getOrPut(accessor) { Node().also { census?.node() } }
        }
        cancellation?.checkpoint()
        return node.bucket ?: RequestBucket(census).also { node.bucket = it; census?.bucket() }
    }
    fun around(base: AccessPathBase, path: List<Int>): List<RequestBucket> {
        cancellation?.checkpoint()
        var node = roots[base] ?: return emptyList()
        val out = ArrayList<RequestBucket>()
        for (accessor in path) {
            cancellation?.checkpoint()
            node.bucket?.let(out::add)
            node = node.child(accessor) ?: return out
        }
        node.bucket?.let(out::add)
        val stack = ArrayDeque<Node>()
        node.children?.values?.let(stack::addAll)
        while (stack.isNotEmpty()) {
            cancellation?.checkpoint()
            val child = stack.removeLast()
            child.bucket?.let(out::add)
            child.children?.values?.let(stack::addAll)
        }
        return out
    }
    fun clear() { roots.clear() }
}

private fun requestCapacity(capacity: Int): Int =
    if (capacity == 0) 2 else Math.addExact(capacity, maxOf(1, capacity / 2))

private fun requestMix(value: Int): Int {
    val product = value * -1640531527
    return product xor (product ushr 16)
}

private val REQUEST_EMPTY_INTS = IntArray(0)
private val REQUEST_EMPTY_REFERENCES = arrayOfNulls<Any>(0)
