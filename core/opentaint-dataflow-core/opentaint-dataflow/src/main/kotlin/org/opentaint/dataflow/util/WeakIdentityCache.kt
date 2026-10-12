package org.opentaint.dataflow.util

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** Identity memoization while a key survives. Values must not retain the key or its owned payloads. */
class WeakIdentityCache<K : Any, V : Any> {
    private class Key<K : Any>(key: K, queue: ReferenceQueue<K>?) : WeakReference<K>(key, queue) {
        private val identityHash = System.identityHashCode(key)
        override fun hashCode(): Int = identityHash
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key<*>) return false
            val key = get() ?: return false
            return key === other.get()
        }
    }

    private val queue = ReferenceQueue<K>()
    private val values = ConcurrentHashMap<Key<K>, V>()

    fun getOrPut(key: K, create: (K) -> V): V {
        prune(MAINTENANCE_BATCH)
        try {
            values[Key(key, null)]?.let { return it }
            return values.computeIfAbsent(Key(key, queue)) { create(key) }
        } finally {
            Reference.reachabilityFence(key)
        }
    }

    /** Drop queued wrappers without a collection. A lookup performs only bounded maintenance. */
    fun prune(limit: Int = MAINTENANCE_BATCH): Int {
        require(limit >= 0)
        var removed = 0
        repeat(limit) {
            val reference = queue.poll() ?: return removed
            @Suppress("UNCHECKED_CAST")
            val key = reference as Key<K>
            if (values.remove(key) != null) removed++
        }
        return removed
    }

    fun pruneAll(): Int = prune(Int.MAX_VALUE)
    val size: Int get() = values.size

    private companion object { const val MAINTENANCE_BATCH = 32 }
}
