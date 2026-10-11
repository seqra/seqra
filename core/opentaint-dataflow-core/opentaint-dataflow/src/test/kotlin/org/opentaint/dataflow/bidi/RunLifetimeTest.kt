package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunLifetimeTest {
    @Test fun `complete barrier freezes actors only after every handler and work invocation has returned`(): Unit {
        val f = EngineShellFixtures()
        val activeHandlers = AtomicInteger()
        val returnedHandlers = AtomicInteger()
        val activeWork = AtomicInteger()
        f.shared(threads = 2).use { shared ->
            val config = f.config(index = 2, roots = listOf(f.a, f.b))
            val factory = ShellHandlers({ port, event ->
                activeHandlers.incrementAndGet()
                try {
                    assertSame(shared, port.shared)
                    assertSame(config, port.config)
                    assertSame(f.ops, port.ops)
                    assertSame(ProtocolSteps.Inline, port.steps)
                    val actor = port.analyzer(assertIs<RunEvent.Start>(event).root)
                    actor.add(3)
                    assertTrue(port.enqueue(actor))
                } finally { activeHandlers.decrementAndGet(); returnedHandlers.incrementAndGet() }
            }, make = { key, _ -> ShellActor(key,
                onWork = { actor, _ ->
                    activeWork.incrementAndGet()
                    try { assertEquals(0, actor.freezeCount.get()) } finally { activeWork.decrementAndGet() }
                },
                onFreeze = {
                    assertEquals(0, activeHandlers.get())
                    assertEquals(0, activeWork.get())
                    assertEquals(2, returnedHandlers.get())
                }) })
            val run = RunManager(config, shared, factory, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(2, result.runIndex)
            assertEquals(Direction.BACKWARD, result.direction)
            assertSame(f.vulnerabilities, result.vulnerabilities)
            assertEquals(setOf(f.a, f.b), result.analyzers.map { it.key }.toSet())
            assertTrue(result.analyzers.all { it.freezeCount.get() == 1 && it.steps == 3L })
            assertEquals(12L, result.demandLayerEdges)
            assertEquals(18L, result.recordCrossings)
            assertFails { (result.analyzers as MutableList<ShellActor>).clear() }
            assertEquals(2, result.analyzers.size)
            assertEquals(0L, run.inFlight.pending)
        }
    }

    @Test fun `handler exceptions including allocation errors select failed and expose no frozen analyzers`(): Unit {
        for (failure in listOf(IllegalStateException("handler"), AssertionError("handler"), OutOfMemoryError("handler"))) {
            val f = EngineShellFixtures()
            f.shared().use { shared ->
                val factory = ShellHandlers({ port, event ->
                    port.analyzer(assertIs<RunEvent.Start>(event).root)
                    throw failure
                })
                val run = RunManager(f.config(), shared, factory)
                val result = run.run(5.seconds)
                assertEquals(RunStatus.FAILED, result.status)
                assertTrue(result.analyzers.isEmpty())
                assertTrue(result.witnessesByKey().isEmpty())
                assertEquals(0, factory.created.single().freezeCount.get())
                assertFalse(f.cancellation.isActive())
            }
        }
    }

    @Test fun `work failure joins the worker and never freezes its partially processed actor`(): Unit {
        val f = EngineShellFixtures()
        val exited = CountDownLatch(1)
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                val actor = port.analyzer(assertIs<RunEvent.Start>(event).root)
                actor.add(2)
                port.enqueue(actor)
            }, make = { key, _ -> ShellActor(key, onWork = { _, _ ->
                try { error("work") } finally { exited.countDown() }
            }) })
            val result = RunManager(f.config(), shared, factory).run(5.seconds)
            assertEquals(RunStatus.FAILED, result.status)
            assertEquals(0L, exited.count)
            assertTrue(result.analyzers.isEmpty())
            assertEquals(0L, factory.created.single().steps)
            assertEquals(0, factory.created.single().freezeCount.get())
        }
    }

    @Test fun `factory and actor creation failures stop admission and return failed`(): Unit {
        val f = EngineShellFixtures()
        f.shared().use { shared ->
            val factoryFailure = RunnerHandlerFactory<ShellActor> { error("factory") }
            val failedFactory = RunManager(f.config(), shared, factoryFailure).run(5.seconds)
            assertEquals(RunStatus.FAILED, failedFactory.status)
            assertTrue(failedFactory.analyzers.isEmpty())

            val actorFailure = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
            }, make = { _, _ -> error("actor creation") })
            val failedActor = RunManager(f.config(), shared, actorFailure).run(5.seconds)
            assertEquals(RunStatus.FAILED, failedActor.status)
            assertTrue(failedActor.analyzers.isEmpty())
            assertTrue(actorFailure.created.isEmpty())

            val wrongKey = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
            }, make = { _, _ -> ShellActor(f.b) })
            val failedKey = RunManager(f.config(), shared, wrongKey).run(5.seconds)
            assertEquals(RunStatus.FAILED, failedKey.status)
            assertTrue(failedKey.analyzers.isEmpty())
            assertEquals(f.b, wrongKey.created.single().key)
            assertEquals(0, wrongKey.created.single().freezeCount.get())
        }
    }

    @Test fun `caller routing failure cancels and joins an already running unit`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val resolver = UnitResolver<CommonMethod> { method ->
            if (method == f.methodB) {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                error("caller routing")
            }
            f.unitResolver.resolve(method)
        }
        f.shared(threads = 2, unitResolver = resolver).use { shared ->
            val factory = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
                entered.countDown()
                try {
                    while (true) { f.cancellation.checkpoint(); LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1)) }
                } finally { exited.countDown() }
            })
            val result = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory).run(5.seconds)
            assertEquals(RunStatus.FAILED, result.status)
            assertEquals(0L, exited.count)
            assertTrue(result.analyzers.isEmpty())
            assertEquals(1, factory.ports.size)
            assertEquals(0, factory.created.single().freezeCount.get())
        }
    }

    @Test fun `timeout cancels active handlers and joins them before returning an incomplete result`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
                entered.countDown()
                try {
                    while (true) { f.cancellation.checkpoint(); LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1)) }
                } finally { exited.countDown() }
            })
            val run = RunManager(f.config(), shared, factory)
            val result = run.run(1.seconds)
            assertEquals(0L, entered.count)
            assertEquals(0L, exited.count)
            assertEquals(RunStatus.TIMEOUT, result.status)
            assertEquals(RunStatus.TIMEOUT, run.terminalStatus)
            assertTrue(result.analyzers.isEmpty())
            assertTrue(result.witnessesByKey().isEmpty())
            assertEquals(0, factory.created.single().freezeCount.get())
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test fun `first abnormal status wins before cancellation and later failures do not replace it`(): Unit {
        for (first in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) {
            val f = EngineShellFixtures()
            lateinit var run: RunManager<ShellActor>
            f.shared().use { shared ->
                val factory = ShellHandlers({ port, event ->
                    port.analyzer(assertIs<RunEvent.Start>(event).root)
                    run.fail(first)
                    assertEquals(first, run.terminalStatus)
                    assertTrue(run.ended)
                    assertFalse(f.cancellation.isActive())
                    for (later in RunStatus.entries.filter { it != RunStatus.COMPLETE }) run.fail(later)
                    assertEquals(first, run.terminalStatus)
                    assertFalse(port.send(RunEvent.Start(f.b)))
                    assertFalse(port.post(RunEvent.Delivery(f.b, emptyList())))
                })
                run = RunManager(f.config(), shared, factory)
                val result = run.run(5.seconds)
                assertEquals(first, result.status)
                assertTrue(result.analyzers.isEmpty())
                assertEquals(0, factory.created.single().freezeCount.get())
            }
        }
    }

    @Test fun `a slow root resolver cannot admit a new runner after another unit selects oom`(): Unit {
        val f = EngineShellFixtures()
        val failed = CountDownLatch(1)
        val holdSecond = AtomicBoolean(true)
        lateinit var run: RunManager<ShellActor>
        val resolver = UnitResolver<CommonMethod> { method ->
            if (method == f.methodB && holdSecond.compareAndSet(true, false)) {
                assertTrue(failed.await(5, TimeUnit.SECONDS))
                assertEquals(RunStatus.OOM, run.terminalStatus)
                assertFalse(f.cancellation.isActive())
            }
            f.unitResolver.resolve(method)
        }
        f.shared(threads = 2, unitResolver = resolver).use { shared ->
            val factory = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
                run.fail(RunStatus.OOM)
                failed.countDown()
            })
            run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.OOM, result.status)
            assertTrue(result.analyzers.isEmpty())
            assertEquals(1, factory.ports.size)
            assertEquals(listOf(f.a), factory.created.map { it.key })
            assertFalse(run.route(RunEvent.Start(f.b)))
        }
    }

    @Test fun `failed join overrides timeout and exposes no actors while the blocked worker is released for cleanup`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val shared = f.shared(cancellationTimeout = 20.milliseconds)
        try {
            val factory = ShellHandlers({ port, event ->
                port.analyzer(assertIs<RunEvent.Start>(event).root)
                entered.countDown()
                try { assertTrue(release.await(5, TimeUnit.SECONDS)) } finally { exited.countDown() }
            })
            val run = RunManager(f.config(), shared, factory)
            val future = caller.submit<RunResult<ShellActor>> { run.run(5.seconds) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            run.fail(RunStatus.TIMEOUT)
            val result = future.get(5, TimeUnit.SECONDS)
            assertEquals(RunStatus.FAILED, result.status)
            assertTrue(result.analyzers.isEmpty())
            assertTrue(result.witnessesByKey().isEmpty())
            assertEquals(1L, exited.count, "A failed join must not masquerade as a completed barrier")
            assertEquals(0, factory.created.single().freezeCount.get())
        } finally {
            release.countDown()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            caller.shutdownNow()
            shared.close()
        }
    }

    @Test fun `complete run closes every admission path and late fail does not cancel shared analysis state`(): Unit {
        val f = EngineShellFixtures()
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event -> port.analyzer(assertIs<RunEvent.Start>(event).root) })
            val run = RunManager(f.config(), shared, factory)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            val port = factory.ports.single()
            val actor = result.analyzers.single()
            val handled = run.inFlight.handled
            assertFalse(run.route(RunEvent.Start(f.b)))
            assertFalse(port.send(RunEvent.Start(f.a)))
            assertFalse(port.post(RunEvent.Delivery(f.b, emptyList())))
            assertFalse(port.enqueue(actor))
            run.fail(RunStatus.OOM)
            assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            assertTrue(f.cancellation.isActive())
            assertEquals(0L, run.inFlight.pending)
            assertEquals(handled, run.inFlight.handled)
            assertEquals(1, factory.ports.size)
            assertEquals(1, actor.freezeCount.get())
        }
    }

    @Test fun `second manager shares analysis services while actors queues counters and admission are fresh`(): Unit {
        val f = EngineShellFixtures()
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                val actor = port.analyzer(assertIs<RunEvent.Start>(event).root)
                actor.add(2)
                assertTrue(port.enqueue(actor))
            })
            val firstRun = RunManager(f.config(), shared, factory, quantum = 1)
            val first = firstRun.run(5.seconds)
            val firstPort = factory.ports.single()
            val firstActor = first.analyzers.single()
            assertEquals(RunStatus.COMPLETE, first.status)
            val secondRun = RunManager(f.config(index = 2), shared, factory, quantum = 1)
            assertNull(secondRun.terminalStatus)
            assertEquals(0L, secondRun.inFlight.pending)
            assertEquals(0L, secondRun.inFlight.handled)
            assertFalse(firstPort.post(RunEvent.Delivery(f.a, emptyList())))
            assertFalse(firstRun.route(RunEvent.Start(f.a)))
            firstRun.fail(RunStatus.FAILED)
            assertTrue(f.cancellation.isActive())
            val second = secondRun.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, second.status)
            assertEquals(1, first.runIndex)
            assertEquals(2, second.runIndex)
            assertEquals(Direction.FORWARD, first.direction)
            assertEquals(Direction.BACKWARD, second.direction)
            val secondActor = second.analyzers.single()
            assertEquals(firstActor.key, secondActor.key)
            assertNotSame(firstActor, secondActor)
            assertNotSame(firstPort, factory.ports.last())
            assertEquals(listOf(1, 1), firstActor.quanta.toList())
            assertEquals(listOf(1, 1), secondActor.quanta.toList())
            assertEquals(2L, firstActor.steps)
            assertEquals(2L, secondActor.steps)
            assertEquals(1, firstActor.freezeCount.get())
            assertEquals(1, secondActor.freezeCount.get())
            assertEquals(3L, firstRun.inFlight.handled)
            assertEquals(3L, secondRun.inFlight.handled)
            assertEquals(0L, secondRun.inFlight.pending)
            assertSame(first.vulnerabilities, second.vulnerabilities)
        }
    }
}
