package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.engine.InFlight
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InFlightTest {
    @Test
    fun `a reservation stays live until its handler ends and zero sees updated handled count`(): Unit {
        val zeros = arrayListOf<Pair<Long, Long>>()
        lateinit var count: InFlight
        count = InFlight { zeros += count.pending to count.handled }
        assertEquals(0L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(emptyList(), zeros)

        count.beforeSend()
        assertEquals(1L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(emptyList(), zeros)
        count.afterHandler()
        assertEquals(0L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(listOf(0L to 1L), zeros)

        count.beforeSend()
        assertEquals(listOf(0L to 1L), zeros)
        count.afterHandler()
        assertEquals(listOf(0L to 1L, 0L to 2L), zeros)
    }

    @Test
    fun `rejected send releases only its reservation and does not count as handled`(): Unit {
        val zeros = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        count.beforeSend() // Running handler.
        count.beforeSend() // A child send reserves before publication, but is rejected.
        count.rejectedSend()
        assertEquals(1L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(0, zeros.get())
        count.afterHandler()
        assertEquals(0L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(1, zeros.get())
    }

    @Test
    fun `a rejected last reservation reports zero with the prior handled count`(): Unit {
        val zeros = arrayListOf<Pair<Long, Long>>()
        lateinit var count: InFlight
        count = InFlight { zeros += count.pending to count.handled }
        count.beforeSend()
        count.beforeSend()
        count.afterHandler()
        assertEquals(1L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(emptyList(), zeros)
        count.rejectedSend()
        assertEquals(listOf(0L to 1L), zeros)

        count.beforeSend()
        count.rejectedSend()
        assertEquals(listOf(0L to 1L, 0L to 1L), zeros)
    }

    @Test
    fun `a child is reserved before its parent handler completes`(): Unit {
        val zeros = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        count.beforeSend() // Parent enters the queue, then starts its handler.
        assertEquals(1L, count.pending)
        count.beforeSend() // Parent sends its child before it completes.
        assertEquals(2L, count.pending)
        count.afterHandler() // Parent completes while its child is still queued.
        assertEquals(1L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(0, zeros.get())
        count.afterHandler() // Child completes.
        assertEquals(0L, count.pending)
        assertEquals(2L, count.handled)
        assertEquals(1, zeros.get())
    }

    @Test
    fun `start reservation prevents zero between root sends including a rejected root`(): Unit {
        val zeros = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        count.beforeSend() // Start batch guard.
        count.beforeSend() // First root.
        count.afterHandler()
        assertEquals(1L, count.pending)
        assertEquals(0, zeros.get())
        count.beforeSend() // A rejected root must also leave the batch guard live.
        count.rejectedSend()
        assertEquals(1L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(0, zeros.get())
        count.beforeSend() // Last root.
        count.afterHandler()
        assertEquals(1L, count.pending)
        assertEquals(0, zeros.get())
        count.afterHandler() // Entire batch was sent.
        assertEquals(0L, count.pending)
        assertEquals(3L, count.handled)
        assertEquals(1, zeros.get())
    }

    @Test
    fun `old run releases cannot alter a fresh counter`(): Unit {
        val oldZeros = AtomicInteger()
        val newZeros = AtomicInteger()
        val old = InFlight { oldZeros.incrementAndGet() }
        val fresh = InFlight { newZeros.incrementAndGet() }
        old.beforeSend()
        old.beforeSend()
        fresh.beforeSend()
        old.afterHandler()
        old.rejectedSend()
        assertEquals(1L, fresh.pending)
        assertEquals(0L, fresh.handled)
        assertEquals(0, newZeros.get())
        assertEquals(1, oldZeros.get())
        fresh.afterHandler()
        assertEquals(1, newZeros.get())
        assertEquals(1L, old.handled)
    }

    @Test
    fun `underflow rejects before either counter or callback changes`(): Unit {
        val zeros = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        assertFailsWith<IllegalStateException> { count.afterHandler() }
        assertFailsWith<IllegalStateException> { count.rejectedSend() }
        assertEquals(0L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(0, zeros.get())
        count.beforeSend()
        count.afterHandler()
        assertFailsWith<IllegalStateException> { count.afterHandler() }
        assertFailsWith<IllegalStateException> { count.rejectedSend() }
        assertEquals(0L, count.pending)
        assertEquals(1L, count.handled)
        assertEquals(1, zeros.get())
    }

    @Test
    fun `concurrent handled and rejected releases preserve a single consistent zero observation`(): Unit {
        val workers = 4
        val releasesPerWorker = 500
        val zeros = ConcurrentLinkedQueue<Pair<Long, Long>>()
        lateinit var count: InFlight
        count = InFlight { zeros += count.pending to count.handled }
        repeat(workers * releasesPerWorker) { count.beforeSend() }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)
        try {
            val tasks = (0 until workers).map {
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    repeat(releasesPerWorker) { n ->
                        if (n % 2 == 0) count.afterHandler() else count.rejectedSend()
                    }
                }
            }
            start.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        val handled = (workers * releasesPerWorker / 2).toLong()
        assertEquals(0L, count.pending)
        assertEquals(handled, count.handled)
        assertEquals(listOf(0L to handled), zeros.toList())
    }

    @Test
    fun `competing final releases accept exactly one and leave failed release counters unchanged`(): Unit {
        val zeros = AtomicInteger()
        val handledSuccesses = AtomicInteger()
        val failures = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        count.beforeSend()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(true, false).map { handled ->
                executor.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    try {
                        if (handled) {
                            count.afterHandler()
                            handledSuccesses.incrementAndGet()
                        } else count.rejectedSend()
                    } catch (_: IllegalStateException) {
                        failures.incrementAndGet()
                    }
                }
            }
            start.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(0L, count.pending)
        assertEquals(handledSuccesses.get().toLong(), count.handled)
        assertEquals(1, failures.get())
        assertEquals(1, zeros.get())
    }

    @Test
    fun `concurrent reservations are all visible before their handlers complete`(): Unit {
        val zeros = AtomicInteger()
        val count = InFlight { zeros.incrementAndGet() }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 4).map { executor.submit { repeat(500) { count.beforeSend() } } }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(2000L, count.pending)
        assertEquals(0L, count.handled)
        assertEquals(0, zeros.get())
        repeat(1999) { count.afterHandler() }
        assertEquals(1L, count.pending)
        assertEquals(0, zeros.get())
        count.afterHandler()
        assertEquals(2000L, count.handled)
        assertEquals(1, zeros.get())
    }
}
