package org.opentaint.dataflow.util

import com.sun.management.GarbageCollectionNotificationInfo
import com.sun.management.HotSpotDiagnosticMXBean
import mu.KLogging
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.management.Notification
import javax.management.NotificationEmitter
import javax.management.NotificationListener
import javax.management.openmbean.CompositeData
import kotlin.system.exitProcess

class MemoryManager(
    private val memoryThreshold: Double,
    val refManager: SoftReferenceManager?,
    private val onOutOfMemory: () -> Unit
) {
    private val memoryManagerState = AtomicInteger(STATE_NORMAL)
    private val lastGcRequestTime = AtomicLong(0)
    private val thresholdBytes = AtomicLong(0)

    inner class GCNotificationListener(
        private val memMx: MemoryMXBean,
    ) : NotificationListener {
        init {
            thresholdBytes.set((memMx.heapMemoryUsage.max * memoryThreshold).toLong())
        }

        override fun handleNotification(n: Notification, handback: Any?) {
            if (n.type != GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION) return

            val info = GarbageCollectionNotificationInfo.from(n.userData as CompositeData)
            val after = info.gcInfo.memoryUsageAfterGc

            var usedAfterGc = 0L
            for ((poolName, usage) in after) {
                val pool = ManagementFactory.getMemoryPoolMXBeans().firstOrNull { it.name == poolName }
                if (pool?.type == MemoryType.HEAP) {
                    usedAfterGc += usage.used
                }
            }

            val thr = thresholdBytes.get()
            val state = memoryManagerState.get()

            if (usedAfterGc < thr) {
                refManager?.enable()
                val currentState = memoryManagerState.getAndSet(STATE_NORMAL)
                if (currentState != STATE_NORMAL) {
                    logger.info("Memory back to normal state: $usedAfterGc < $thr")
                }
                return
            }

            logger.info("Detected high memory usage: $usedAfterGc > $thr")

            when(state) {
                STATE_NORMAL -> {
                    val cleaned = refManager?.cleanup()
                    if (cleaned != null && cleaned < 0) return

                    logger.debug("Cleaned soft refs: $cleaned")

                    memoryManagerState.compareAndSet(STATE_NORMAL, STATE_SOFT_REF_RESET)

                    // Ask JVM for another GC; we confirm on the next GC end
                    memMx.gc()
                }

                STATE_SOFT_REF_RESET -> {
                    memMx.gc()
                    memoryManagerState.compareAndSet(STATE_SOFT_REF_RESET, GC_AFTER_CLEANUP)
                    lastGcRequestTime.set(info.gcInfo.endTime)
                }

                GC_AFTER_CLEANUP -> {
                    if (info.gcInfo.startTime <= lastGcRequestTime.get() + 10) return
                    if (currentMemoryUsage() < thr) return

                    handleOOM()
                    onOutOfMemory()
                }
            }
        }
    }

    fun registerListener(listener: NotificationListener) {
        ManagementFactory.getGarbageCollectorMXBeans()
            .mapNotNull { it as? NotificationEmitter }
            .forEach { it.addNotificationListener(listener, null, null) }
    }

    fun removeListener(listener: NotificationListener) {
        ManagementFactory.getGarbageCollectorMXBeans()
            .mapNotNull { it as? NotificationEmitter }
            .forEach { it.removeNotificationListener(listener) }
    }

    inline fun <T> runWithMemoryManager(body: () -> T): T {
        val memMx = ManagementFactory.getMemoryMXBean()
        val listener = GCNotificationListener(memMx)
        registerListener(listener)
        try {
            refManager?.enable()
            return body()
        } finally {
            removeListener(listener)
            refManager?.cleanup()
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
        private const val STATE_NORMAL = 0
        private const val STATE_SOFT_REF_RESET = 1
        private const val GC_AFTER_CLEANUP = 2

        private val logger = object : KLogging() {}.logger

        private const val DEBUG_DUMP_HEAP_ON_OOM = false

        fun currentMemoryUsage(): Long {
            val maxMemory = Runtime.getRuntime().maxMemory()
            val usedMemory = maxMemory - Runtime.getRuntime().freeMemory()
            return usedMemory
        }
    }
}
