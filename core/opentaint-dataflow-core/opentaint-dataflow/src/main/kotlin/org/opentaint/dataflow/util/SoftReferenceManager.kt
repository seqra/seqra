package org.opentaint.dataflow.util

import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class SoftReferenceManager {
    private val references = ConcurrentLinkedQueue<SoftReference<*>>()
    private val enabled = AtomicBoolean(true)

    fun cleanup(): Int {
        if (!enabled.compareAndSet(true, false)) return -1
        return cleanupRefs()
    }

    fun enable() {
        enabled.set(true)
    }

    private fun cleanupRefs(): Int {
        var cleanedRefs = 0
        while (true) {
            val ref = references.poll() ?: return cleanedRefs
            if (ref.get() != null) {
                cleanedRefs++
            }
            ref.clear()
        }
    }

    fun <T> createRef(obj: T): Reference<T>? {
        if (!enabled.get()) return null
        return SoftReference(obj).also { references.add(it) }
    }
}
