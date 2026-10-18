package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.ifds.UnitType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RunCleanupTest {
    @Test fun `actor freeze failure releases all joined units and all run storage owners`(): Unit {
        val f = EngineShellFixtures()
        val failure = IllegalStateException("actor A freeze")
        f.shared(threads = 2).use { shared ->
            val factory = CleanupHandlers(actorFailure = { key -> failure.takeIf { key == f.a } })
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

            assertSame(failure, assertFailsWith<IllegalStateException> { run.run(5.seconds) })

            assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            assertEquals(listOf(f.a, f.b), factory.actorFreezes.toList())
            assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
            assertEquals(2, factory.storages.size)
            assertReleased(run, factory)
            assertTrue(f.cancellation.isActive(), "A release failure must not replace the selected status")
        }
    }

    @Test fun `handler freeze failure still clears its own runner and releases every other unit`(): Unit {
        val f = EngineShellFixtures()
        val failure = AssertionError("handler A freeze")
        f.shared(threads = 2).use { shared ->
            val factory = CleanupHandlers(handlerFailure = { unit -> failure.takeIf { unit == f.unitA } })
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

            assertSame(failure, assertFailsWith<AssertionError> { run.run(5.seconds) })

            assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            assertEquals(listOf(f.a, f.b), factory.actorFreezes.toList())
            assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
            assertTrue(factory.actors.all { it.freezes.get() == 1 })
            assertReleased(run, factory)
        }
    }

    @Test fun `all actor and handler failures are retained after every joined owner is released`(): Unit {
        val f = EngineShellFixtures()
        val first = IllegalStateException("actor A")
        val second = AssertionError("actor B")
        val third = UnsupportedOperationException("handler A")
        val fourth = OutOfMemoryError("handler B")
        f.shared(threads = 2).use { shared ->
            val factory = CleanupHandlers(
                actorFailure = { key -> if (key == f.a) first else second },
                handlerFailure = { unit -> if (unit == f.unitA) third else fourth })
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

            assertSame(first, assertFailsWith<IllegalStateException> { run.run(5.seconds) })

            assertContentEquals(arrayOf<Throwable>(second, third, fourth), first.suppressed)
            assertEquals(listOf(f.a, f.b), factory.actorFreezes.toList())
            assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
            assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            assertReleased(run, factory)
        }
    }

    @Test fun `one Throwable from several freeze callbacks is propagated without self suppression`(): Unit {
        val f = EngineShellFixtures()
        val failure = IllegalStateException("shared freeze failure")
        f.shared(threads = 2).use { shared ->
            val factory = CleanupHandlers(actorFailure = { failure }, handlerFailure = { failure })
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

            assertSame(failure, assertFailsWith<IllegalStateException> { run.run(5.seconds) })

            assertTrue(failure.suppressed.isEmpty())
            assertEquals(listOf(f.a, f.b), factory.actorFreezes.toList())
            assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
            assertEquals(RunStatus.COMPLETE, run.terminalStatus)
            assertReleased(run, factory)
        }
    }

    @Test fun `joined abnormal runs release all handlers without freezing incomplete actors or changing status`(): Unit {
        for (status in listOf(RunStatus.TIMEOUT, RunStatus.OOM, RunStatus.FAILED)) {
            val f = EngineShellFixtures()
            val arrived = CountDownLatch(2)
            val failure = IllegalStateException("release after $status")
            lateinit var run: RunManager<CleanupActor>
            f.shared(threads = 2).use { shared ->
                val factory = CleanupHandlers(
                    handlerFailure = { unit -> failure.takeIf { unit == f.unitA } },
                    onStart = { _, key ->
                        arrived.countDown()
                        assertTrue(arrived.await(5, TimeUnit.SECONDS))
                        if (key == f.a) run.fail(status)
                    })
                run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

                assertSame(failure, assertFailsWith<IllegalStateException> { run.run(5.seconds) })

                assertEquals(status, run.terminalStatus)
                assertTrue(factory.actorFreezes.isEmpty())
                assertTrue(factory.actors.all { it.freezes.get() == 0 })
                assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
                assertReleased(run, factory)
                assertFalse(f.cancellation.isActive())
            }
        }
    }

    @Test fun `joined release clears pending Work and its queued identity even when handler freeze throws`(): Unit {
        val f = EngineShellFixtures()
        val failure = IllegalStateException("release with pending Work")
        lateinit var run: RunManager<CleanupActor>
        f.shared().use { shared ->
            val factory = CleanupHandlers(handlerFailure = { failure }, onStart = { port, key ->
                assertTrue(port.enqueue(port.analyzer(key)))
                assertFalse(field<EventQueue>(port, "queue").isEmpty())
                assertEquals(1, field<Set<*>>(port, "queued").size)
                run.fail(RunStatus.FAILED)
            })
            run = RunManager(f.config(), shared, factory)

            assertSame(failure, assertFailsWith<IllegalStateException> { run.run(5.seconds) })

            assertEquals(RunStatus.FAILED, run.terminalStatus)
            assertTrue(factory.actorFreezes.isEmpty())
            assertEquals(listOf(f.unitA), factory.handlerFreezes.toList())
            assertReleased(run, factory)
        }
    }

    @Test fun `failed join leaves every live actor handler and storage owner unreleased`(): Unit {
        val f = EngineShellFixtures()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val caller = Executors.newSingleThreadExecutor()
        val shared = f.shared(cancellationTimeout = 20.milliseconds)
        val failure = IllegalStateException("A live handler must not freeze")
        val factory = CleanupHandlers(actorFailure = { failure }, handlerFailure = { failure },
            onStart = { _, _ ->
                entered.countDown()
                try { assertTrue(release.await(5, TimeUnit.SECONDS)) } finally { exited.countDown() }
            })
        try {
            val run = RunManager(f.config(), shared, factory)
            val future = caller.submit<RunResult<CleanupActor>> { run.run(5.seconds) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            run.fail(RunStatus.TIMEOUT)

            val result = future.get(5, TimeUnit.SECONDS)

            assertEquals(RunStatus.FAILED, result.status)
            assertEquals(RunStatus.FAILED, run.terminalStatus)
            assertTrue(result.analyzers.isEmpty())
            assertEquals(1L, exited.count, "The owner is still live after the failed join")
            assertTrue(factory.actorFreezes.isEmpty())
            assertTrue(factory.handlerFreezes.isEmpty())
            assertEquals(0, factory.actors.single().freezes.get())
            assertSame(factory.storages.single(), field<Map<*, *>>(run, "storages")[f.a])
        } finally {
            release.countDown()
            assertTrue(exited.await(5, TimeUnit.SECONDS))
            caller.shutdownNow()
            shared.close()
        }
        assertTrue(factory.actorFreezes.isEmpty())
        assertTrue(factory.handlerFreezes.isEmpty())
    }

    @Test fun `successful joined cleanup preserves frozen result actors and releases all run containers`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 2).use { shared ->
            val factory = CleanupHandlers()
            val run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory)

            val result = run.run(5.seconds)

            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(listOf(f.a, f.b), result.analyzers.map { it.key })
            assertTrue(result.analyzers.all { it.freezes.get() == 1 })
            assertEquals(listOf(f.a, f.b), factory.actorFreezes.toList())
            assertEquals(listOf(f.unitA, f.unitB), factory.handlerFreezes.toList())
            assertReleased(run, factory)
            assertTrue(f.cancellation.isActive())
        }
    }

    @Test fun `real method freeze attempts both required services and releases state while retaining barrier evidence`(): Unit {
        for (index in listOf(1, 2)) MethodAnalysisFixtures().use { f ->
            val freezes = ArrayList<String>()
            val first = IllegalStateException("local service freeze")
            val second = AssertionError("call service freeze")
            val core = f.core(index = index,
                localFactory = MethodLocalServicesFactory { owner ->
                    val delegate = RunMethodLocalServices(owner)
                    object : MethodLocalServices by delegate {
                        override fun freeze() { freezes += "local"; delegate.freeze(); throw first }
                    }
                },
                callFactory = MethodCallServicesFactory { owner ->
                    val delegate = RunMethodCallServices(owner)
                    object : MethodCallServices by delegate {
                        override fun freeze() { freezes += "call"; delegate.freeze(); throw second }
                    }
                })
            val initial = f.manager.initial(f.pattern())
            core.addInitial(initial)
            val ref = CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1))
            f.admit(core, ref, Layer.NORMAL, f.fact())
            val source = PathEdge(ZERO_FACT, f.pattern().fact, ExclusionSet.Empty)
            if (index == 2) core.sourceHits!!.add(f.key, f.inst(1), source)
            f.drain(core)
            core.countRecordCrossing()
            core.countTriggerSeed()
            val raw = core.summaries.all().toList()
            val demand = core.summaries.demandEdges().toList()
            val stats = listOf(core.steps, core.edgeDeltas, core.demandLayerEdges, core.recordCrossings, core.triggerSeeds)
            assertTrue(raw.isNotEmpty())
            assertTrue(core.nonZeroInitial)

            assertSame(first, assertFailsWith<IllegalStateException> { core.freeze() })

            assertEquals(listOf("local", "call"), freezes)
            assertContentEquals(arrayOf<Throwable>(second), first.suppressed)
            for (name in listOf("local", "calls", "publicationSink", "port", "runContext", "edgeStore", "initialStore")) {
                assertNull(field<Any?>(core, name), name)
            }
            assertTrue(field<DeltaWorklist>(core, "worklist").isEmpty)
            assertTrue(field<List<*>>(core, "pending").isEmpty())
            assertFailsWith<IllegalStateException> { core.work(1) }
            assertFailsWith<IllegalStateException> { core.edges }
            assertFailsWith<IllegalStateException> { core.initials }
            assertEquals(raw, core.summaries.all().toList())
            assertEquals(demand, core.summaries.demandEdges().toList())
            assertEquals(stats, listOf(core.steps, core.edgeDeltas, core.demandLayerEdges, core.recordCrossings, core.triggerSeeds))
            assertTrue(core.nonZeroInitial)
            assertTrue(core.javaClass.declaredFields.none { it.name == "linkStore" },
                "Method actors never own an incoming supplier store, including before release")
            if (index == 1) {
                assertEquals(ref, f.links(core).single().caller)
                assertNull(core.sourceHits)
            } else {
                assertNull(core.abstraction)
                val hit = core.sourceHits!!.entries().single()
                assertEquals(f.key, hit.first)
                assertSame(f.inst(1), hit.second)
                assertSame(source, hit.third)
            }
        }
    }

    @Test fun `real method unfinished freeze is nondestructive and permits later closure before throwing release`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val freezes = ArrayList<String>()
            val failure = IllegalStateException("local service release")
            val core = f.core(
                localFactory = MethodLocalServicesFactory { owner ->
                    val delegate = RunMethodLocalServices(owner)
                    object : MethodLocalServices by delegate {
                        override fun freeze() { freezes += "local"; delegate.freeze(); throw failure }
                    }
                },
                callFactory = MethodCallServicesFactory { owner ->
                    val delegate = RunMethodCallServices(owner)
                    object : MethodCallServices by delegate {
                        override fun freeze() { freezes += "call"; delegate.freeze() }
                    }
                })
            core.addInitial(f.manager.initial(f.pattern()))
            val local = field<Any?>(core, "local")
            val calls = field<Any?>(core, "calls")
            val port = field<Any?>(core, "port")

            val rejected = assertFailsWith<IllegalStateException> { core.freeze() }

            assertNotSame(failure, rejected)
            assertTrue(freezes.isEmpty())
            assertSame(local, field<Any?>(core, "local"))
            assertSame(calls, field<Any?>(core, "calls"))
            assertSame(port, field<Any?>(core, "port"))
            assertEquals(0L, core.steps)
            f.drain(core, quantum = 1)
            assertTrue(core.steps > 0)
            assertTrue(core.summaries.all().any())
            assertSame(failure, assertFailsWith<IllegalStateException> { core.freeze() })
            assertEquals(listOf("local", "call"), freezes)
            assertNull(field<Any?>(core, "port"))
        }
    }

    @Test fun `real method service callbacks sharing one failure do not self suppress or skip detachment`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val failure = IllegalStateException("both services")
            val freezes = ArrayList<String>()
            val core = f.core(
                localFactory = MethodLocalServicesFactory { owner ->
                    val delegate = RunMethodLocalServices(owner)
                    object : MethodLocalServices by delegate {
                        override fun freeze() { freezes += "local"; delegate.freeze(); throw failure }
                    }
                },
                callFactory = MethodCallServicesFactory { owner ->
                    val delegate = RunMethodCallServices(owner)
                    object : MethodCallServices by delegate {
                        override fun freeze() { freezes += "call"; delegate.freeze(); throw failure }
                    }
                })

            assertSame(failure, assertFailsWith<IllegalStateException> { core.freeze() })

            assertEquals(listOf("local", "call"), freezes)
            assertTrue(failure.suppressed.isEmpty())
            assertNull(field<Any?>(core, "local"))
            assertNull(field<Any?>(core, "calls"))
            assertNull(field<Any?>(core, "port"))
        }
    }

    private fun assertReleased(run: RunManager<CleanupActor>, factory: CleanupHandlers) {
        assertTrue(field<Map<*, *>>(run, "runners").isEmpty())
        assertTrue(field<List<*>>(run, "runnerJobs").isEmpty())
        assertTrue(field<Map<*, *>>(run, "storages").isEmpty())
        for (port in factory.ports) {
            val runner = assertIs<UnitRunner<CleanupActor>>(port)
            assertTrue(runner.analyzers().isEmpty(), "The joined runner must drop every actor")
            assertTrue(field<Set<*>>(runner, "queued").isEmpty())
            assertTrue(field<EventQueue>(runner, "queue").isEmpty())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T

    private class CleanupActor(
        override val key: MethodKey,
        private val onFreeze: (CleanupActor) -> Unit,
    ) : RunActor {
        val freezes = AtomicInteger()
        override val steps: Long = 0
        override val hasZeroWork: Boolean = false
        override val demandLayerEdges: Long = 0
        override val recordCrossings: Long = 0
        override fun work(quantum: Int): Boolean = error("These cleanup rows do not schedule Work")
        override fun freeze() {
            freezes.incrementAndGet()
            onFreeze(this)
        }
    }

    private class CleanupHandlers(
        private val actorFailure: (MethodKey) -> Throwable? = { null },
        private val handlerFailure: (UnitType) -> Throwable? = { null },
        private val onStart: (RunnerPort<CleanupActor>, MethodKey) -> Unit = { _, _ -> },
    ) : RunnerHandlerFactory<CleanupActor> {
        val ports = CopyOnWriteArrayList<RunnerPort<CleanupActor>>()
        val actors = CopyOnWriteArrayList<CleanupActor>()
        val storages = CopyOnWriteArrayList<SummaryStorage>()
        val actorFreezes = CopyOnWriteArrayList<MethodKey>()
        val handlerFreezes = CopyOnWriteArrayList<UnitType>()
        private val activeHandlers = AtomicInteger()

        override fun create(port: RunnerPort<CleanupActor>): RunnerHandler<CleanupActor> {
            ports += port
            val unit = assertIs<UnitRunner<CleanupActor>>(port).unit
            return object : RunnerHandler<CleanupActor>, SubscriptionHandler {
                override fun drainRequests(caller: MethodKey) = Unit
                override fun createActor(key: MethodKey): CleanupActor = CleanupActor(key) {
                    assertEquals(0, activeHandlers.get(), "Every handler must return before actor freeze")
                    actorFreezes += it.key
                    actorFailure(it.key)?.let { failure -> throw failure }
                }.also { actors += it }

                override fun handle(event: RunEvent) {
                    activeHandlers.incrementAndGet()
                    try {
                        val key = assertIs<RunEvent.Start>(event).root
                        port.analyzer(key)
                        storages += port.summaryStorage(key)
                        onStart(port, key)
                    } finally { activeHandlers.decrementAndGet() }
                }

                override fun subscribe(sub: Subscription): Unit = error("No subscriptions are used")
                override fun freeze() {
                    assertEquals(0, activeHandlers.get(), "Every handler must return before joined release")
                    handlerFreezes += unit
                    handlerFailure(unit)?.let { throw it }
                }
            }
        }
    }
}
