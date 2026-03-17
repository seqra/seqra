package org.opentaint.dataflow.util

import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import kotlin.math.roundToLong
import kotlin.system.exitProcess


class MemoryManager(
    private val memoryThreshold: Double,
    private val onOutOfMemory: () -> Unit
) {
    private fun MemoryPoolMXBean.updateThresholds(): Boolean {
        val currentThreshold = collectionUsageThreshold
        val threshold = (memoryThreshold * usage.max).roundToLong()

        if (currentThreshold >= threshold) return false

        collectionUsageThreshold = threshold
        usageThreshold = threshold

        return true
    }

    fun createMemoryListener(): NotificationListener {
        val memoryPool = ManagementFactory.getMemoryPoolMXBeans()
            .singleOrNull {
                it.type == MemoryType.HEAP
                        && it.isUsageThresholdSupported
                        && it.isCollectionUsageThresholdSupported
            }
            ?: error("Expected exactly one memory pool that support threshold")

        memoryPool.updateThresholds()

        return NotificationListener { notification: Notification, _ ->
            if (notification.type == MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) {
                // The pool could have been resized => updating absolute thresholds
                memoryPool.updateThresholds()
            } else {
                check(notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)
                if (!memoryPool.updateThresholds()) {
                    handleOOM()
                    onOutOfMemory()
                }
            }
        }
    }

    fun registerListener(listener: NotificationListener) {
        val notificationFilter: (Notification) -> Boolean = { notification ->
            notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED
                    || notification.type == MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED
        }

        val emitter = ManagementFactory.getMemoryMXBean() as NotificationEmitter
        emitter.addNotificationListener(listener, notificationFilter, null)
    }

    fun removeListener(listener: NotificationListener) {
        val emitter = ManagementFactory.getMemoryMXBean() as NotificationEmitter
        emitter.removeNotificationListener(listener)
    }

    inline fun <T> runWithMemoryManager(body: () -> T): T {
        val listener = createMemoryListener()
        registerListener(listener)
        try {
            return body()
        } finally {
            removeListener(listener)
        }
    }

    private fun handleOOM() {
        if (!DEBUG_DUMP_HEAP_ON_OOM) return

        dumpHeapAndExitProcess()
    }

    private fun dumpHeapAndExitProcess() {
        val server = ManagementFactory.getPlatformMBeanServer()
        val mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean::class.java
        )

        val path = "opentaint.heapdump.hprof"
        mxBean.dumpHeap(path, true)
        System.err.println("Heap dumped to: $path")
        exitProcess(-1)
    }

    companion object {
        private const val DEBUG_DUMP_HEAP_ON_OOM = false
    }
}
