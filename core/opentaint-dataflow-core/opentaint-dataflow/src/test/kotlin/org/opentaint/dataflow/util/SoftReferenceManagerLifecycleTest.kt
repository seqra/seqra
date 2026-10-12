package org.opentaint.dataflow.util

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.SoftReference
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SoftReferenceManagerLifecycleTest {
    @Test
    fun `cleanup counts live referents and disables registration until enable`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val live = assertNotNull(manager.createRef(value))
        val cleared = assertNotNull(manager.createRef(Any())).also { it.clear() }
        val empty = assertNotNull(manager.createRef<Any?>(null))

        assertEquals(3, manager.trackedReferenceCount)
        assertEquals(1, manager.cleanup())
        assertNull(live.get())
        assertNull(cleared.get())
        assertNull(empty.get())
        assertEquals(0, manager.trackedReferenceCount)
        assertNull(manager.createRef(value))
        assertEquals(-1, manager.cleanup())

        manager.enable()
        val next = assertNotNull(manager.createRef(value))
        assertSame(value, next.get())
        assertNull(live.get())
        assertEquals(1, manager.cleanup())
        assertNull(next.get())
    }

    @Test
    fun `distinct registrations of one referent are tracked and cleared separately`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val first = assertNotNull(manager.createRef(value))
        val second = assertNotNull(manager.createRef(value))

        assertTrue(first !== second)
        assertEquals(2, manager.trackedReferenceCount)
        assertEquals(2, manager.cleanup())
        assertNull(first.get())
        assertNull(second.get())
    }

    @Test
    fun `explicit enqueue prunes only the queued registrations`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val queued = assertNotNull(manager.createRef(value))
        val standing = assertNotNull(manager.createRef(value))
        queued.clear()
        assertTrue(queued.enqueue())

        assertEquals(1, manager.drainClearedReferences())
        assertEquals(1, manager.trackedReferenceCount)
        assertEquals(0, manager.drainClearedReferences())
        assertSame(value, standing.get())
        assertEquals(1, manager.cleanup())
        assertNull(standing.get())
    }

    @Test
    fun `registration and enable perform bounded queue maintenance`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val queued = List(100) { assertNotNull(manager.createRef(value)) }
        queued.forEach { it.clear(); assertTrue(it.enqueue()) }
        assertEquals(100, manager.trackedReferenceCount)

        val standing = assertNotNull(manager.createRef(value))
        val afterRegistration = manager.trackedReferenceCount
        assertTrue(afterRegistration in 2..100,
            "Registration must prune some wrappers without draining the entire queue")
        manager.enable()
        val afterEnable = manager.trackedReferenceCount
        assertTrue(afterEnable in 2 until afterRegistration,
            "Enable must also prune a bounded batch")
        assertEquals(afterEnable - 1, manager.drainClearedReferences())
        assertEquals(1, manager.trackedReferenceCount)
        assertSame(value, standing.get())
        assertEquals(1, manager.cleanup())
    }

    @Test
    fun `cleanup removes registrations and drains queued wrappers`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val value = Any()
        val queued = assertNotNull(manager.createRef(value))
        val live = assertNotNull(manager.createRef(value))
        queued.clear()
        assertTrue(queued.enqueue())

        assertEquals(1, manager.cleanup())
        assertEquals(0, manager.trackedReferenceCount)
        assertNull(live.get())
        assertNull(queue(manager).poll())
        assertEquals(0, manager.drainClearedReferences())
    }

    @Test
    fun `cleanup cannot miss a registration paused inside the lifecycle lock`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        replaceRegistry(manager, object : IdentityHashMap<Reference<*>, Unit>() {
            override fun put(key: Reference<*>, value: Unit): Unit? {
                entered.countDown()
                await(resume)
                return super.put(key, value)
            }
        })
        val value = Any()
        val created = AtomicReference<Reference<Any>?>()
        val cleaned = AtomicReference<Int>()
        val createWorker = Worker { created.set(manager.createRef(value)) }
        val cleanupWorker = Worker { cleaned.set(manager.cleanup()) }
        try {
            createWorker.start()
            await(entered)
            cleanupWorker.start()
            await(cleanupWorker.started)
            awaitBlockedOrFinished(cleanupWorker.thread)
            resume.countDown()
            createWorker.finish()
            cleanupWorker.finish()

            assertEquals(1, cleaned.get())
            assertNull(assertNotNull(created.get()).get())
            assertEquals(0, manager.trackedReferenceCount)
            assertNull(manager.createRef(value))
            assertEquals(-1, manager.cleanup())
        } finally {
            resume.countDown()
            createWorker.finish()
            cleanupWorker.finishIfStarted()
        }
    }

    @Test
    fun `re-enabled registrations survive an older detached cleanup batch`(): Unit {
        val manager = SoftReferenceManager(RefManager())
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val oldValue = Any()
        val old = object : SoftReference<Any>(oldValue) {
            override fun clear() {
                entered.countDown()
                await(resume)
                super.clear()
            }
        }
        replaceRegistry(manager, IdentityHashMap<Reference<*>, Unit>().apply { put(old, Unit) })
        val cleaned = AtomicReference<Int>()
        val cleanupWorker = Worker { cleaned.set(manager.cleanup()) }
        val newValue = Any()
        val newReference = AtomicReference<Reference<Any>?>()
        val enableWorker = Worker {
            manager.enable()
            newReference.set(manager.createRef(newValue))
        }
        try {
            cleanupWorker.start()
            await(entered)
            enableWorker.start()
            enableWorker.finish()
            assertSame(newValue, assertNotNull(newReference.get()).get())
            assertEquals(1, manager.trackedReferenceCount)
            resume.countDown()
            cleanupWorker.finish()

            assertEquals(1, cleaned.get())
            assertNull(old.get())
            assertSame(newValue, assertNotNull(newReference.get()).get())
            assertEquals(1, manager.cleanup())
            assertNull(assertNotNull(newReference.get()).get())
        } finally {
            resume.countDown()
            cleanupWorker.finish()
            enableWorker.finishIfStarted()
        }
    }

    private fun replaceRegistry(manager: SoftReferenceManager, registry: IdentityHashMap<Reference<*>, Unit>) {
        manager.javaClass.getDeclaredField("references").apply { isAccessible = true }.set(manager, registry)
    }

    private fun queue(manager: SoftReferenceManager): ReferenceQueue<*> =
        manager.javaClass.getDeclaredField("referenceQueue").apply { isAccessible = true }
            .get(manager) as ReferenceQueue<*>

    private class Worker(body: () -> Unit) {
        val started = CountDownLatch(1)
        private val finished = CountDownLatch(1)
        private val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            started.countDown()
            try { body() } catch (error: Throwable) { failure.set(error) } finally { finished.countDown() }
        }.apply { isDaemon = true }

        fun start() { thread.start() }
        fun finish() {
            await(finished)
            failure.get()?.let { throw AssertionError("Worker failed", it) }
        }
        fun finishIfStarted() { if (thread.state != Thread.State.NEW) finish() }
    }

    companion object {
        private fun await(latch: CountDownLatch) {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Worker did not reach the controlled boundary")
        }

        private fun awaitBlockedOrFinished(thread: Thread) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (thread.state != Thread.State.BLOCKED && thread.state != Thread.State.TERMINATED) {
                assertTrue(System.nanoTime() < deadline, "Cleanup did not reach the lifecycle boundary")
                Thread.yield()
            }
        }
    }
}
