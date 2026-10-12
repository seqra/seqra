package org.opentaint.dataflow.util

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.IdentityHashMap

class SoftReferenceManager(@Suppress("unused") refManager: RefManager) {
    private val lifecycleLock = Any()
    private val referenceQueue = ReferenceQueue<Any?>()
    private var references = IdentityHashMap<Reference<*>, Unit>()
    private var enabled = true

    fun cleanup(): Int {
        val detached = synchronized(lifecycleLock) {
            if (!enabled) return -1
            enabled = false
            references.also { references = IdentityHashMap() }
        }
        var cleanedRefs = 0
        for (ref in detached.keys) {
            if (ref.get() != null) cleanedRefs++
            ref.clear()
        }
        detached.clear()
        drainClearedReferences()
        return cleanedRefs
    }

    fun enable() {
        synchronized(lifecycleLock) {
            drainQueue(MAINTENANCE_BATCH)
            enabled = true
        }
    }

    /** Remove queued wrappers without waiting for a JVM collection. */
    internal fun drainClearedReferences(): Int = synchronized(lifecycleLock) {
        drainQueue(Int.MAX_VALUE)
    }

    /** Raw registered wrappers, including cleared wrappers not yet pruned; this is not a live payload count. */
    val trackedReferenceCount: Int
        get() = synchronized(lifecycleLock) { references.size }

    private fun drainQueue(limit: Int): Int {
        var pruned = 0
        repeat(limit) {
            val ref = referenceQueue.poll() ?: return pruned
            if (references.remove(ref) != null) pruned++
        }
        return pruned
    }

    fun <T> createRef(obj: T): Reference<T>? = synchronized(lifecycleLock) {
        drainQueue(MAINTENANCE_BATCH)
        if (!enabled) return null
        SoftReference(obj, referenceQueue).also { references[it] = Unit }
    }

    private companion object {
        const val MAINTENANCE_BATCH = 32
    }
}
