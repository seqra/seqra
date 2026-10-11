package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Expiration is established inside the slow callback, without upper-bound timing assertions. */
class RunFailureTest {
    @Test
    fun `an unknown root returned after the deadline cannot complete through the start phantom`(): Unit {
        val f = EngineShellFixtures()
        val timeout = 80.milliseconds
        val resolved = CopyOnWriteArrayList<CommonMethod>()
        val resolver = UnitResolver<CommonMethod> { method ->
            resolved += method
            expire(timeout)
            f.unitResolver.resolve(method)
        }
        f.shared(unitResolver = resolver).use { shared ->
            val factory = ShellHandlers({ _, _ -> error("An expired unknown root cannot reach a handler") })
            val run = RunManager(f.config(roots = listOf(f.unknown, f.a)), shared, factory)
            val result = run.run(timeout)
            assertEquals(RunStatus.TIMEOUT, result.status)
            assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
            assertEquals(listOf(f.methodUnknown), resolved.toList())
            assertTrue(factory.ports.isEmpty() && factory.created.isEmpty())
            assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
            assertEquals(0L, run.inFlight.pending)
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test
    fun `a known root returned after the deadline admits neither start work nor the next root`(): Unit {
        val f = EngineShellFixtures()
        val timeout = 80.milliseconds
        val resolved = CopyOnWriteArrayList<CommonMethod>()
        val resolver = UnitResolver<CommonMethod> { method ->
            resolved += method
            expire(timeout)
            f.unitResolver.resolve(method)
        }
        f.shared(unitResolver = resolver).use { shared ->
            val events = CopyOnWriteArrayList<RunEvent>()
            val factory = ShellHandlers({ port, event ->
                events += event
                port.analyzer(assertIs<RunEvent.Start>(event).root)
            })
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)
            val result = run.run(timeout)
            assertEquals(RunStatus.TIMEOUT, result.status)
            assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
            assertEquals(listOf(f.methodA), resolved.toList())
            assertTrue(events.isEmpty())
            assertTrue(factory.ports.isEmpty() && factory.created.isEmpty())
            assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
            assertEquals(0L, run.inFlight.pending)
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test
    fun `a finished first root cannot complete the run while a second root consumes the remaining budget`(): Unit {
        val f = EngineShellFixtures()
        val timeout = 120.milliseconds
        val firstFinished = CountDownLatch(1)
        val expireSecond = AtomicBoolean(true)
        val secondResolved = AtomicInteger()
        lateinit var run: RunManager<ShellActor>
        val resolver = UnitResolver<CommonMethod> { method ->
            if (method == f.methodB) {
                secondResolved.incrementAndGet()
                if (expireSecond.compareAndSet(true, false)) {
                    assertTrue(firstFinished.await(5, TimeUnit.SECONDS))
                    awaitShell { run.inFlight.handled == 1L }
                    assertEquals(1L, run.inFlight.pending, "Only the full root-batch reservation remains")
                    assertNull(run.terminalStatus)
                    expire(timeout)
                }
            }
            f.unitResolver.resolve(method)
        }
        f.shared(threads = 2, unitResolver = resolver).use { shared ->
            val events = CopyOnWriteArrayList<RunEvent>()
            val factory = ShellHandlers({ port, event ->
                events += event
                val root = assertIs<RunEvent.Start>(event).root
                port.analyzer(root)
                if (root == f.a) firstFinished.countDown()
            })
            run = RunManager(f.config(roots = listOf(f.a, f.b, f.unknown)), shared, factory)
            val result = run.run(timeout)
            assertEquals(RunStatus.TIMEOUT, result.status)
            assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
            assertEquals(1, secondResolved.get())
            assertEquals(listOf(f.a), events.map { assertIs<RunEvent.Start>(it).root })
            assertEquals(listOf(f.a), factory.created.map { it.key })
            assertEquals(1, factory.ports.size)
            assertEquals(0, factory.created.single().freezeCount.get())
            assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
            assertEquals(0L, run.inFlight.pending)
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test
    fun `runner setup consumes the run budget before any start can be accepted`(): Unit {
        val f = EngineShellFixtures()
        val timeout = 80.milliseconds
        val resolved = CopyOnWriteArrayList<CommonMethod>()
        val resolver = UnitResolver<CommonMethod> { method ->
            resolved += method
            f.unitResolver.resolve(method)
        }
        val factories = AtomicInteger()
        val handled = AtomicInteger()
        val created = AtomicInteger()
        f.shared(unitResolver = resolver).use { shared ->
            val factory = RunnerHandlerFactory<ShellActor> {
                factories.incrementAndGet()
                expire(timeout)
                object : RunnerHandler<ShellActor> {
                    override fun createActor(key: MethodKey): ShellActor {
                        created.incrementAndGet()
                        return ShellActor(key)
                    }
                    override fun handle(event: RunEvent) { handled.incrementAndGet() }
                }
            }
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)
            val result = run.run(timeout)
            assertEquals(RunStatus.TIMEOUT, result.status)
            assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
            assertEquals(1, factories.get())
            assertEquals(0, handled.get())
            assertEquals(0, created.get())
            assertEquals(listOf(f.methodA), resolved.toList())
            assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
            assertEquals(0L, run.inFlight.pending)
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test
    fun `an earlier oom or failure remains terminal after the resolver exceeds the deadline`(): Unit {
        for (first in listOf(RunStatus.OOM, RunStatus.FAILED)) {
            val f = EngineShellFixtures()
            val timeout = 80.milliseconds
            val resolved = CopyOnWriteArrayList<CommonMethod>()
            lateinit var run: RunManager<ShellActor>
            val resolver = UnitResolver<CommonMethod> { method ->
                resolved += method
                run.fail(first)
                assertEquals(first, run.terminalStatus)
                assertFalse(f.cancellation.isActive())
                expire(timeout)
                f.unitResolver.resolve(method)
            }
            f.shared(unitResolver = resolver).use { shared ->
                val factory = ShellHandlers({ _, _ -> error("The first status closes admission") })
                run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)
                val result = run.run(timeout)
                assertEquals(first, result.status)
                assertEquals(first, run.terminalStatus)
                assertEquals(listOf(f.methodA), resolved.toList())
                assertTrue(factory.ports.isEmpty() && factory.created.isEmpty())
                assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
                assertEquals(0L, run.inFlight.pending)
                assertFalse(f.cancellation.isActive())
            }
        }
    }

    @Test
    fun `a resolver throwable after timeout selection cannot replace the first status`(): Unit {
        for (failure in listOf(IllegalStateException("late resolver"), AssertionError("late resolver"), OutOfMemoryError("late resolver"))) {
            val f = EngineShellFixtures()
            val timeout = 80.milliseconds
            lateinit var run: RunManager<ShellActor>
            val resolver = UnitResolver<CommonMethod> {
                run.fail(RunStatus.TIMEOUT)
                assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
                assertFalse(f.cancellation.isActive())
                expire(timeout)
                throw failure
            }
            f.shared(unitResolver = resolver).use { shared ->
                val factory = ShellHandlers({ _, _ -> error("A failed resolver cannot create a runner") })
                run = RunManager(f.config(), shared, factory)
                val result = run.run(timeout)
                assertEquals(RunStatus.TIMEOUT, result.status)
                assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
                assertTrue(factory.ports.isEmpty() && factory.created.isEmpty())
                assertTrue(result.analyzers.isEmpty() && result.witnessesByKey().isEmpty())
                assertEquals(0L, run.inFlight.pending)
                assertFalse(f.cancellation.isActive())
            }
        }
    }

    private fun expire(timeout: Duration) {
        val started = TimeSource.Monotonic.markNow()
        while (started.elapsedNow() <= timeout) LockSupport.parkNanos(1_000_000)
        assertTrue(started.elapsedNow() > timeout)
    }
}
