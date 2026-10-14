package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.RequestKind
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.min
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** Finite actors check the run protocol without a method-transfer implementation. */
class RunShellTest {
    @Test fun `empty and all unknown root batches complete without creating any runner actor`(): Unit {
        val f = EngineShellFixtures()
        for (roots in listOf(emptyList(), listOf(f.unknown), listOf(f.unknown, f.unknown))) {
            f.shared().use { shared ->
                val factory = ShellHandlers({ _, _ -> error("Unknown roots must not enter a handler") })
                val run = RunManager(f.config(roots = roots), shared, factory)
                val result = run.run(5.seconds)
                assertEquals(RunStatus.COMPLETE, result.status)
                assertTrue(result.analyzers.isEmpty())
                assertTrue(factory.ports.isEmpty() && factory.created.isEmpty())
                assertEquals(0L, run.inFlight.pending)
                assertEquals(1L, run.inFlight.handled, "The root batch consumes its start reservation")
                assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            }
        }
    }

    @Test fun `root batch reservation survives the first completed handler while later roots are still routed`(): Unit {
        val f = EngineShellFixtures()
        val firstFinished = CountDownLatch(1)
        val holdSecondRoot = AtomicBoolean(true)
        lateinit var run: RunManager<ShellActor>
        val resolver = object : UnitResolver<CommonMethod> {
            override fun resolve(method: CommonMethod) = if (method == f.methodB && holdSecondRoot.compareAndSet(true, false)) {
                assertTrue(firstFinished.await(5, TimeUnit.SECONDS))
                awaitShell { run.inFlight.handled == 1L }
                assertEquals(1L, run.inFlight.pending)
                assertNull(run.terminalStatus)
                assertFalse(run.ended)
                f.unitB
            } else f.unitResolver.resolve(method)
        }
        f.shared(threads = 2, unitResolver = resolver).use { shared ->
            val factory = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                port.analyzer(start.root)
                if (start.root == f.a) firstFinished.countDown()
            })
            run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(setOf(f.a, f.b), result.analyzers.map { it.key }.toSet())
            assertEquals(2, factory.ports.size)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(3L, run.inFlight.handled, "Two Starts and the full-batch reservation")
            assertTrue(result.analyzers.all { it.freezeCount.get() == 1 })
        }
    }

    @Test fun `one runner owns canonical actors and rejects a foreign actor with the same key`(): Unit {
        val f = EngineShellFixtures()
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                val actor = port.analyzer(start.root)
                assertSame(actor, port.analyzer(start.root))
                assertFails { port.analyzer(f.b) }
                assertFails { port.enqueue(ShellActor(start.root)) }
                actor.add(1)
                assertTrue(port.enqueue(actor))
            })
            val run = RunManager(f.config(), shared, factory)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(1, factory.created.size)
            assertEquals(1L, result.analyzers.single().steps)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(3L, run.inFlight.handled)
        }
    }

    @Test fun `local work deduplicates enqueue and retains one reservation across partial quanta`(): Unit {
        val f = EngineShellFixtures()
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                val start = assertIs<RunEvent.Start>(event)
                val actor = port.analyzer(start.root)
                actor.add(7)
                assertTrue(port.enqueue(actor))
                assertFalse(port.enqueue(actor))
                assertFalse(port.enqueue(actor))
            }, make = { key, _ -> ShellActor(key, onWork = { _, _ ->
                assertNull(run.terminalStatus)
                assertTrue(run.inFlight.pending >= 1L)
            }) })
            run = RunManager(f.config(), shared, factory, quantum = 2)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            val actor = result.analyzers.single()
            assertEquals(7L, actor.steps)
            assertEquals(listOf(2, 2, 2, 2), actor.quanta.toList())
            assertEquals(0, actor.remaining)
            assertEquals(3L, run.inFlight.handled, "Partial requeues keep the original Work reservation")
            assertEquals(0L, run.inFlight.pending)
            assertEquals(actor.demandLayerEdges, result.demandLayerEdges)
            assertEquals(actor.recordCrossings, result.recordCrossings)
        }
    }

    @Test fun `handlers send child link zero and request events while delivery returns to the known unit`(): Unit {
        val f = EngineShellFixtures()
        val ref = CallerRef(f.a, f.manager.zero, Layer.NORMAL, f.inst(f.methodA, 1))
        val publication = Publication(f.manager.zero, Reach.NORMAL)
        val deliveries = CopyOnWriteArrayList<RunnerPort<ShellActor>>()
        val events = CopyOnWriteArrayList<RunEvent>()
        f.shared(threads = 2).use { shared ->
            val factory = ShellHandlers({ port, event ->
                events += event
                when (event) {
                    is RunEvent.Start -> {
                        val actor = port.analyzer(event.root)
                        actor.add(if (event.root == f.a) 2 else 1)
                        assertTrue(port.enqueue(actor))
                        if (event.root == f.a) {
                            assertTrue(port.send(RunEvent.Start(f.b)))
                            assertTrue(port.send(RunEvent.LinkIn(f.b, ref, Layer.NORMAL, Reach.NORMAL)))
                            assertTrue(port.send(RunEvent.ZeroIn(f.b)))
                            assertTrue(port.send(RunEvent.RequestIn(f.b, f.manager.zero, RequestKind.Mark(f.manager.marks.mark("T")))))
                            assertFalse(port.send(RunEvent.ZeroIn(f.unknown)))
                            assertTrue(port.post(RunEvent.Delivery(f.b, listOf(publication))))
                        }
                    }
                    is RunEvent.LinkIn -> {
                        assertSame(ref, event.ref)
                        assertSame(Reach.NORMAL, event.added)
                        port.analyzer(event.callee).add(2)
                        port.enqueue(port.analyzer(event.callee))
                    }
                    is RunEvent.ZeroIn -> { port.analyzer(event.callee).add(1); port.enqueue(port.analyzer(event.callee)) }
                    is RunEvent.RequestIn -> { port.analyzer(event.method).add(1); port.enqueue(port.analyzer(event.method)) }
                    is RunEvent.MarkRequestsIn -> { port.analyzer(event.method).add(event.marks.size); port.enqueue(port.analyzer(event.method)) }
                    is RunEvent.Delivery -> {
                        assertEquals(f.b, event.callee)
                        assertSame(publication.conclusion, event.publications.single().conclusion)
                        deliveries += port
                        port.analyzer(f.a).add(1)
                        port.enqueue(port.analyzer(f.a))
                    }
                    is RunEvent.Work -> error("The runner must invoke Work directly")
                }
            })
            val run = RunManager(f.config(), shared, factory, quantum = 1)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(2, factory.ports.size)
            assertEquals(mapOf(f.a to 3L, f.b to 5L), result.analyzers.associate { it.key to it.steps })
            assertSame(factory.portsByKey[f.a], deliveries.single(), "Delivery's callee is metadata, not its routing target")
            assertEquals(6, events.size)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(result.analyzers.sumOf { it.demandLayerEdges }, result.demandLayerEdges)
            assertEquals(result.analyzers.sumOf { it.recordCrossings }, result.recordCrossings)
            assertTrue(result.analyzers.all { it.freezeCount.get() == 1 && it.remaining == 0 })
        }
    }

    @Test fun `a delayed counted handler cannot complete before it returns`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            f.shared().use { shared ->
                val factory = ShellHandlers({ port, event ->
                    port.analyzer(assertIs<RunEvent.Start>(event).root)
                    entered.countDown()
                    try { assertTrue(release.await(5, TimeUnit.SECONDS)) } finally { exited.countDown() }
                })
                val run = RunManager(f.config(), shared, factory)
                val future = executor.submit<RunResult<ShellActor>> { run.run(5.seconds) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertFalse(run.ended)
                assertNull(run.terminalStatus)
                assertTrue(run.inFlight.pending >= 1L)
                assertFalse(future.isDone)
                release.countDown()
                val result = future.get(5, TimeUnit.SECONDS)
                assertEquals(RunStatus.COMPLETE, result.status)
                assertEquals(0L, exited.count)
                assertEquals(1, result.analyzers.single().freezeCount.get())
                assertEquals(0L, run.inFlight.pending)
            }
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun `known closed channel rolls back rejected delivery send and enqueue without counting handled`(): Unit {
        val f = EngineShellFixtures()
        lateinit var run: RunManager<ShellActor>
        f.shared().use { shared ->
            val factory = ShellHandlers({ port, event ->
                val actor = port.analyzer(assertIs<RunEvent.Start>(event).root)
                awaitShell { run.inFlight.handled == 1L }
                assertEquals(1L, run.inFlight.pending)
                (port as UnitRunner<ShellActor>).close()
                assertFalse(port.post(RunEvent.Delivery(f.b, listOf(Publication(f.manager.zero, Reach.NORMAL)))))
                assertFalse(port.send(RunEvent.Start(f.a)))
                assertFalse(port.enqueue(actor))
                assertEquals(1L, run.inFlight.pending)
                assertEquals(1L, run.inFlight.handled)
                assertNull(run.terminalStatus)
            })
            run = RunManager(f.config(), shared, factory)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(0L, run.inFlight.pending)
            assertEquals(2L, run.inFlight.handled)
            assertTrue(result.analyzers.single().quanta.isEmpty())
        }
    }

    @Test fun `concurrent accepted delivery batches remain counted until every batch is handled`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val delivered = AtomicInteger()
        val caller = Executors.newSingleThreadExecutor()
        val senders = Executors.newFixedThreadPool(4)
        try {
            f.shared().use { shared ->
                val factory = ShellHandlers({ port, event -> when (event) {
                    is RunEvent.Start -> {
                        port.analyzer(event.root)
                        entered.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                    }
                    is RunEvent.Delivery -> delivered.incrementAndGet()
                    else -> error("Only root and known-destination delivery are used")
                } })
                val run = RunManager(f.config(), shared, factory)
                val resultFuture = caller.submit<RunResult<ShellActor>> { run.run(5.seconds) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val port = factory.ports.single()
                val accepted = (0 until 4).map {
                    senders.submit<Int> {
                        (0 until 50).count {
                            port.post(RunEvent.Delivery(f.b, listOf(Publication(f.manager.zero, Reach.NORMAL))))
                        }
                    }
                }.sumOf { it.get(5, TimeUnit.SECONDS) }
                assertEquals(200, accepted)
                assertNull(run.terminalStatus)
                assertEquals(0, delivered.get(), "The owning handler still holds the unit")
                release.countDown()
                val result = resultFuture.get(5, TimeUnit.SECONDS)
                assertEquals(RunStatus.COMPLETE, result.status)
                assertEquals(accepted, delivered.get())
                assertEquals(0L, run.inFlight.pending)
                assertEquals(2L + accepted, run.inFlight.handled)
            }
        } finally { release.countDown(); caller.shutdownNow(); senders.shutdownNow() }
    }

    @Test fun `nonpositive quantum is rejected before a runner can accept work`(): Unit {
        val f = EngineShellFixtures()
        f.shared().use { shared ->
            for (quantum in listOf(0, -1)) assertFailsWith<IllegalArgumentException> {
                RunManager(f.config(), shared, ShellHandlers({ _, _ -> error("No runner") }), quantum = quantum)
            }
        }
    }
}

internal class ShellActor(
    override val key: MethodKey,
    private val onWork: (ShellActor, Int) -> Unit = { _, _ -> },
    private val onFreeze: (ShellActor) -> Unit = {},
) : RunActor {
    override var steps: Long = 0
        private set
    var remaining: Int = 0
        private set
    val quanta = CopyOnWriteArrayList<Int>()
    val freezeCount = AtomicInteger()
    override val hasZeroWork: Boolean get() = remaining > 0 && steps == 0L
    override val demandLayerEdges: Long get() = steps * 2
    override val recordCrossings: Long get() = steps * 3
    fun add(count: Int) { check(freezeCount.get() == 0); remaining += count }
    override fun work(quantum: Int): Boolean {
        check(freezeCount.get() == 0)
        quanta += quantum
        onWork(this, quantum)
        val consumed = min(remaining, quantum)
        remaining -= consumed
        steps += consumed
        return remaining == 0
    }
    override fun freeze() {
        check(remaining == 0)
        onFreeze(this)
        check(freezeCount.incrementAndGet() == 1)
    }
}

internal class ShellHandlers(
    private val onEvent: (RunnerPort<ShellActor>, RunEvent) -> Unit,
    private val make: (MethodKey, RunnerPort<ShellActor>) -> ShellActor = { key, _ -> ShellActor(key) },
) : RunnerHandlerFactory<ShellActor> {
    val ports = CopyOnWriteArrayList<RunnerPort<ShellActor>>()
    val created = CopyOnWriteArrayList<ShellActor>()
    val portsByKey = ConcurrentHashMap<MethodKey, RunnerPort<ShellActor>>()
    override fun create(port: RunnerPort<ShellActor>): RunnerHandler<ShellActor> {
        ports += port
        return object : RunnerHandler<ShellActor> {
            override fun createActor(key: MethodKey): ShellActor = make(key, port).also {
                created += it
                portsByKey[key] = port
            }
            override fun handle(event: RunEvent) {
                check(event !is RunEvent.Work)
                onEvent(port, event)
            }
        }
    }
}

internal fun awaitShell(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Run protocol condition did not arrive" }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
}
