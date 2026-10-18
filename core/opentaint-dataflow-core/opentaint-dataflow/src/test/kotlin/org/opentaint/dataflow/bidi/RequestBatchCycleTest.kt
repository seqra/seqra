package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RequestBatchCycleTest {
    @Test fun `caller FLOW cycle admits both authoritative rows and completes with immutable merged batches`(): Unit {
        for (sameUnit in listOf(false, true)) for (quantum in listOf(1, 1000)) for (size in listOf(16, 32)) {
            val f = EngineShellFixtures()
            val resolver = if (sameUnit) UnitResolver<CommonMethod> { f.unitA } else f.unitResolver
            f.shared(threads = 2, unitResolver = resolver).use { shared ->
                val slot = Run1Slot.Base(AccessPathBase.Argument(0))
                val initial = slot.initial(f.manager)
                val marks = RequestMarkSet.fromIds(IntArray(size) { f.manager.marks.mark("T$it").id })
                val outgoing = CopyOnWriteArrayList<RunEvent.MarkRequestsIn>()
                val stores = ConcurrentHashMap<MethodKey, SummaryStorage>()
                val queued = AtomicInteger()
                val inputs = CopyOnWriteArrayList<Pair<MethodKey, Link>>()
                lateinit var run: RunManager<RunMethodAnalyzer>
                val actual = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                    MethodCallServicesFactory(::RunMethodCallServices))
                val factory = RunnerHandlerFactory<RunMethodAnalyzer> { owner ->
                    val observed = object : RunnerPort<RunMethodAnalyzer> by owner {
                        override fun summaryStorage(key: MethodKey): SummaryStorage = owner.summaryStorage(key).also { stores[key] = it }
                        override fun send(event: RunEvent): Boolean {
                            if (event is RunEvent.MarkRequestsIn) outgoing += event
                            return owner.send(event)
                        }
                        override fun enqueue(analyzer: RunMethodAnalyzer): Boolean {
                            assertNull(run.terminalStatus, "Counted pending request/fact work cannot survive COMPLETE")
                            assertTrue(run.inFlight.pending > 0)
                            queued.incrementAndGet()
                            return owner.enqueue(analyzer)
                        }
                    }
                    val delegate = actual.create(observed)
                    object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                        override fun createActor(key: MethodKey) = delegate.createActor(key)
                        override fun drainRequests(caller: MethodKey) = (delegate as SubscriptionHandler).drainRequests(caller)
                        override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                        override fun freeze() = (delegate as SubscriptionHandler).freeze()
                        override fun handle(event: RunEvent) {
                            if (event is RunEvent.Start) {
                                val flow = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
                                val destination = if (event.root == f.a) f.b else f.a
                                val ref = CallerRef(event.root, initial, Layer.NORMAL, f.inst(event.root.method, 2))
                                (delegate as SubscriptionHandler).subscribe(Subscription(destination, ref, Layer.NORMAL, flow))
                                f.ops.leaves(flow).forEach {
                                    inputs += destination to Link(it, Layer.NORMAL, ref)
                                }
                                if (event.root == f.a) assertTrue(observed.send(RunEvent.MarkRequestsIn(f.a, mapOf(slot to marks))))
                            } else {
                                if (event is RunEvent.LinkIn) f.ops.leaves(event.added).forEach {
                                    inputs += event.callee to Link(it, event.linkLayer, event.ref)
                                }
                                delegate.handle(event)
                            }
                        }
                    }
                }
                run = RunManager(f.config(roots = listOf(f.a, f.b)), shared, factory, quantum = quantum)
                val result = run.run(10.seconds)
                assertEquals(RunStatus.COMPLETE, result.status, "$sameUnit/$quantum/$size")
                assertEquals(0L, run.inFlight.pending)
                assertTrue(queued.get() > 0)
                assertEquals(setOf(f.a, f.b), result.analyzers.map { it.key }.toSet())
                for (method in listOf(f.a, f.b)) {
                    assertEquals(marks, stores.getValue(method).borrowRequest(slot).dynamic)
                    val arrived = inputs.filter { it.first == method }.map { it.second }
                    assertEquals(1, arrived.size)
                    assertEquals(initial.toPattern(), arrived.single().addedFact)
                }
                assertEquals(setOf(f.a, f.b), outgoing.map { it.method }.toSet())
                assertTrue(outgoing.all { it.rows == mapOf<Run1Slot, RequestMarkSet>(slot to marks) })
                // Concurrent stale target reads may enqueue a duplicate complete batch. They do not
                // add a new logical membership or justify an exactly-once transport assertion.
                assertTrue(outgoing.size >= 2)
            }
        }
    }

    @Test fun `failed collected temporary requests cannot leak into a fresh restricted run`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 1).use { shared ->
            val firstSlot = Run1Slot.Base(AccessPathBase.Argument(0))
            val secondSlot = Run1Slot.Base(AccessPathBase.Argument(1))
            val marks = RequestMarkSet.fromIds(IntArray(32) { f.manager.marks.mark("T$it").id })
            var collectedBeforeCommit = false
            val actual = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                MethodCallServicesFactory(::RunMethodCallServices))
            val firstFactory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                val delegate = actual.create(port)
                object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                    override fun createActor(key: MethodKey) = delegate.createActor(key)
                    override fun drainRequests(caller: MethodKey) = (delegate as SubscriptionHandler).drainRequests(caller)
                    override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                    override fun freeze() = (delegate as SubscriptionHandler).freeze()
                    override fun handle(event: RunEvent) {
                        if (event is RunEvent.Start) {
                            assertTrue(port.send(RunEvent.MarkRequestsIn(f.a, mapOf(firstSlot to marks, secondSlot to marks))))
                        } else {
                            delegate.handle(event)
                            if (event is RunEvent.MarkRequestsIn) {
                                assertTrue(port.summaryStorage(f.a).borrowRequest(firstSlot).isEmpty)
                                assertTrue(port.summaryStorage(f.a).borrowRequest(secondSlot).isEmpty)
                                collectedBeforeCommit = true
                                error("Controlled failure after collecting P and reserving Work, before commit")
                            }
                        }
                    }
                }
            }
            val first = RunManager(f.config(), shared, firstFactory, quantum = 1)
            assertEquals(RunStatus.FAILED, first.run(5.seconds).status)
            assertTrue(collectedBeforeCommit)
            assertFailsWith<org.opentaint.dataflow.util.Cancellation.Cancelled> { f.config(index = 3) }
            f.cancellation.activate()
            val second = RunManager(f.config(index = 3), shared, actual, quantum = 1)
            val result = second.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(0L, second.inFlight.pending)
            assertEquals(0L, result.analyzers.single().demandLayerEdges)
            assertTrue(result.analyzers.single().summaries.all().all { it.first.isZero })
        }
    }
}
