package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RunSchedulingTest {
    @Test fun `actual method yields on the zero work phase change with its publications pending before the nonzero phase`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val actor = f.core()
            val initial = f.manager.initial(f.pattern())
            actor.addRootZero()
            actor.addInitial(initial)
            assertTrue(actor.hasZeroWork)
            assertFalse(actor.work(1000), "A phase change yields even with quantum budget remaining")
            assertFalse(actor.hasZeroWork)
            assertEquals(3L, actor.steps)
            assertEquals(listOf(0, 1, 2), f.processed.map { it.node.location.index })
            assertTrue(f.processed.all { it.premise.isZero && it.facts == Reach.NORMAL })
            assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false)),
                methodEntries(f.ops, actor.summaries.all().withoutPrecomputedStaticDefault()))
            assertTrue(f.publications.isEmpty(), "The counted partial Work still owns its pending zero publication")
            assertFailsWith<IllegalStateException> { actor.freeze() }

            assertTrue(actor.work(1000))
            val expected = setOf(
                f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                initial to Conclusion(initial.toPattern().fact, ExclusionSet.Empty, false))
            assertEquals(expected, methodEntries(f.ops, actor.summaries.all().withoutPrecomputedStaticDefault()))
            assertEquals(6L, actor.steps)
            assertEquals(listOf(0, 1, 2), f.processed.filter { it.premise === initial }.map { it.node.location.index })
            assertEquals(1, f.publications.size)
            assertEquals(expected, methodEntries(f.ops, f.publications.flatten().withoutPrecomputedStaticDefault().asSequence().map { it.premise to it.conclusion }))
            assertTrue(actor.work(1))
        }
    }

    private class PriorityActor(
        override val key: MethodKey,
        var zero: Boolean,
        private val body: (PriorityActor) -> Boolean,
    ) : RunActor {
        override var steps: Long = 0
        override val hasZeroWork: Boolean get() = zero
        override val demandLayerEdges: Long = 0
        override val recordCrossings: Long = 0
        var workCalls = 0
        var freezeCalls = 0
        override fun work(quantum: Int): Boolean {
            assertEquals(1000, quantum)
            assertEquals(0, freezeCalls)
            workCalls++
            return body(this)
        }
        override fun freeze() { assertEquals(0, freezeCalls); freezeCalls++ }
    }

    @Test fun `actual runner resamples partial Work priority while retaining its reservation and queued actor identity`(): Unit {
        val f = EngineShellFixtures()
        val trace = CopyOnWriteArrayList<String>()
        val resolver = UnitResolver<CommonMethod> { method ->
            if (method == f.methodA || method == f.methodB) f.unitA else UnknownUnit
        }
        f.shared(unitResolver = resolver).use { shared ->
            lateinit var run: RunManager<PriorityActor>
            val factory = RunnerHandlerFactory<PriorityActor> { port ->
                object : RunnerHandler<PriorityActor> {
                    override fun createActor(key: MethodKey): PriorityActor = when (key) {
                        f.a -> PriorityActor(key, zero = true) { actor ->
                            assertNull(run.terminalStatus)
                            assertFalse(port.enqueue(actor), "One partial Work owns the existing reservation")
                            if (actor.workCalls == 1) {
                                trace += "A-zero"
                                actor.zero = false
                                actor.steps = 1
                                false
                            } else {
                                trace += "A-rest"
                                actor.steps = 2
                                true
                            }
                        }
                        f.b -> PriorityActor(key, zero = false) { actor ->
                            trace += "B"
                            actor.steps = 1
                            true
                        }
                        else -> error("Only the two known actors participate")
                    }
                    override fun handle(event: RunEvent) {
                        when (event) {
                            is RunEvent.Start -> {
                                assertEquals(f.a, event.root)
                                assertTrue(port.enqueue(port.analyzer(f.a)))
                                assertTrue(port.enqueue(port.analyzer(f.b)))
                                assertTrue(port.post(RunEvent.Delivery(f.unknown, emptyList())))
                            }
                            is RunEvent.Delivery -> {
                                assertEquals(f.unknown, event.callee, "Delivery uses its known owning runner")
                                trace += "Delivery"
                                assertEquals(listOf("A-zero", "Delivery"), trace.toList())
                                assertFalse(port.enqueue(port.analyzer(f.a)))
                                assertNull(run.terminalStatus)
                                assertTrue(run.inFlight.pending >= 3L, "Delivery and both Work events remain counted")
                            }
                            else -> error("The runner handles Work directly; no other finite events are sent")
                        }
                    }
                }
            }
            run = RunManager(f.config(), shared, factory, quantum = 1000)
            val result = run.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(listOf("A-zero", "Delivery", "B", "A-rest"), trace.toList())
            assertEquals(mapOf(f.a to 2, f.b to 1), result.analyzers.associate { it.key to it.workCalls })
            assertEquals(mapOf(f.a to 2L, f.b to 1L), result.analyzers.associate { it.key to it.steps })
            assertTrue(result.analyzers.all { it.freezeCalls == 1 })
            assertEquals(0L, run.inFlight.pending)
            assertEquals(5L, run.inFlight.handled, "Start phantom, Start, Delivery and two completed Work reservations")
        }
    }

    @Test fun `actual method Work remains counted through blocked publication and reentrant work until every publication is stored`(): Unit {
        MethodAnalysisFixtures().use { f ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val caller = Executors.newSingleThreadExecutor()
            val actorRef = AtomicReference<RunMethodAnalyzer>()
            val storageRef = AtomicReference<SummaryStorage>()
            val notifications = AtomicInteger()
            val returned = AtomicInteger()
            val localFrozen = AtomicInteger()
            val processed = CopyOnWriteArrayList<EdgeDelta>()
            val follow = f.manager.initial(f.pattern(AccessPathBase.Argument(1), mark = MarkSlot.Concrete(f.mark("follow"))))
            val extra = f.fact(AccessPathBase.Return, mark = MarkSlot.Concrete(f.mark("extra")))
            val expected = setOf(
                f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false),
                follow to Conclusion(follow.toPattern().fact, ExclusionSet.Empty, false),
                f.manager.zero to Conclusion(PathFact(AccessPathBase.Return, emptyList(), Tail.EXACT,
                    MarkSlot.Concrete(f.mark("extra"))), ExclusionSet.Empty, false))
            lateinit var run: RunManager<RunMethodAnalyzer>
            val steps = object : ProtocolSteps {
                override fun replay(step: () -> Unit): Unit = step()
                override fun notify(step: () -> Unit) {
                    val count = notifications.incrementAndGet()
                    assertNull(run.terminalStatus)
                    assertTrue(run.inFlight.pending >= 1L)
                    if (count == 1) {
                        entered.countDown()
                        assertTrue(release.await(5, TimeUnit.SECONDS))
                        val actor = checkNotNull(actorRef.get())
                        actor.addInitial(follow)
                        actor.emitAfter(f.manager.zero, f.inst(2), extra)
                    } else {
                        assertEquals(2, count)
                        assertEquals(listOf(0, 1, 2), processed.filter { it.premise === follow }.map { it.node.location.index })
                        assertEquals(expected, methodEntries(f.ops, checkNotNull(actorRef.get()).summaries.all().withoutPrecomputedStaticDefault()))
                    }
                    step()
                    returned.incrementAndGet()
                }
            }
            val unit = EngineShellFixtures.Unit("finite method")
            val resolver = UnitResolver<CommonMethod> { method -> if (method == f.key.method) unit else UnknownUnit }
            val shared = SharedObjects(f.manager, f.ops, f.interpreter, f.language, f.contexts, f.records,
                f.vulnerabilities, resolver, f.refManager, f.cancellation, threads = 1)
            try {
                shared.use {
                    val real = RunMethodHandlers(MethodLocalServicesFactory { actor ->
                        val delegate = RunMethodLocalServices(actor)
                        object : MethodLocalServices by delegate {
                            override fun freeze() {
                                assertEquals(2, returned.get(), "Freeze follows both completed publication callbacks")
                                assertEquals(expected, methodEntries(f.ops, actor.summaries.all().withoutPrecomputedStaticDefault()))
                                delegate.freeze()
                                localFrozen.incrementAndGet()
                            }
                        }
                    }, MethodCallServicesFactory(::RunMethodCallServices))
                    val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                        val observed = object : RunnerPort<RunMethodAnalyzer> by port {
                            override val onProcess: (MethodKey, EdgeDelta) -> Unit = { key, edge ->
                                assertEquals(f.key, key)
                                processed += edge
                                assertEquals(0, localFrozen.get())
                            }
                        }
                        val delegate = real.create(observed)
                        object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                            override fun createActor(key: MethodKey): RunMethodAnalyzer {
                                storageRef.set(port.summaryStorage(key))
                                return delegate.createActor(key).also { actorRef.set(it) }
                            }
                            override fun handle(event: RunEvent): Unit = delegate.handle(event)
                            override fun subscribe(sub: Subscription): Unit = (delegate as SubscriptionHandler).subscribe(sub)
                            override fun freeze(): Unit = (delegate as SubscriptionHandler).freeze()
                        }
                    }
                    val config = f.config(L = 1)
                    run = RunManager(config, shared, factory, steps = steps, quantum = 1)
                    val future = caller.submit<RunResult<RunMethodAnalyzer>> { run.run(10.seconds) }
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    assertNull(run.terminalStatus)
                    assertFalse(run.ended)
                    assertFalse(future.isDone)
                    assertTrue(run.inFlight.pending >= 1L)
                    assertEquals(3L, checkNotNull(actorRef.get()).steps)
                    assertEquals(0, localFrozen.get())
                    assertEquals(0, returned.get())
                    val zeroPart = Subscription(f.key, CallerRef(f.key, f.manager.zero, Layer.NORMAL, f.inst(1)),
                        Layer.NORMAL, Reach.NORMAL, zeroOnly = true)
                    val storage = checkNotNull(storageRef.get())
                    assertEquals(setOf(f.manager.zero to Conclusion(ZERO_FACT, ExclusionSet.Empty, false)),
                        methodEntries(f.ops, storage.candidates(zeroPart, config.mode).asSequence().map { it.first.premise to it.first.conclusion }))
                    release.countDown()
                    val result = future.get(10, TimeUnit.SECONDS)
                    assertEquals(RunStatus.COMPLETE, result.status)
                    val actor = result.analyzers.single()
                    assertSame(actorRef.get(), actor)
                    assertEquals(expected, methodEntries(f.ops, actor.summaries.all().withoutPrecomputedStaticDefault()))
                    assertEquals(6L, actor.steps)
                    assertEquals(2, notifications.get())
                    assertEquals(2, returned.get())
                    assertEquals(1, localFrozen.get())
                    val followPart = zeroPart.copy(ref = zeroPart.ref.copy(premise = follow),
                        added = f.ops.startFact(follow), zeroOnly = false)
                    val publications = storage.candidates(zeroPart, config.mode) + storage.candidates(followPart, config.mode)
                    assertEquals(expected, methodEntries(f.ops, publications.asSequence().map { it.first.premise to it.first.conclusion }))
                    assertEquals(0L, run.inFlight.pending)
                    assertEquals(3L, run.inFlight.handled, "One Work reservation spans all quanta, publication and reentrant work")
                    assertFails { actor.work(1) }
                }
            } finally {
                release.countDown()
                caller.shutdownNow()
                assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }
}
