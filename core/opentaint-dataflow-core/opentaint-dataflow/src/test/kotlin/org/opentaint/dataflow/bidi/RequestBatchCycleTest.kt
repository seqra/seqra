package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RequestBatchCycleTest {
    private data class Member(val method: MethodKey, val initial: InitialAp, val mark: TaintMark)

    @Test
    fun `full raw FLOW cycle reaches both standing rows with immutable batches and completes`(): Unit {
        for (sameUnit in listOf(false, true)) for (quantum in listOf(1, 1000)) for (size in listOf(16, 32)) {
            val f = EngineShellFixtures()
            val resolver = if (sameUnit) UnitResolver<CommonMethod> { f.unitA } else f.unitResolver
            f.shared(threads = 2, unitResolver = resolver).use { shared ->
                val initial = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                    ExclusionSet.Empty, MarkSlot.STAR)
                val marks = RequestMarkSet.fromIds(IntArray(size) { f.manager.marks.mark("T$it").id })
                val offered = ConcurrentHashMap.newKeySet<Member>()
                val outgoing = CopyOnWriteArrayList<RunEvent.MarkRequestsIn>()
                val services = AtomicInteger()
                val inputs = CopyOnWriteArrayList<Pair<MethodKey, Link>>()
                lateinit var run: RunManager<RunMethodAnalyzer>
                val actual = RunMethodHandlers(MethodLocalServicesFactory { actor ->
                    val local = RunMethodLocalServices(actor)
                    object : MethodLocalServices by local {
                        override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                            val kind = RequestKind.Mark(mark)
                            if (kind is RequestKind.Mark) offered += Member(actor.key, premise, kind.mark)
                            local.addMarkRequest(premise, mark)
                        }
                        override fun addMarkRequests(premise: InitialAp, members: RequestMarkSet) {
                            for (i in 0 until members.size) offered += Member(actor.key, premise, members.markAt(i))
                            local.addMarkRequests(premise, members)
                        }
                        override fun flushRequests(budget: Int): Boolean {
                            if (local.hasPendingRequests) {
                                assertNull(run.terminalStatus, "A pending phase cannot survive COMPLETE")
                                assertTrue(run.inFlight.pending > 0)
                                services.incrementAndGet()
                            }
                            return local.flushRequests(budget)
                        }
                    }
                }, MethodCallServicesFactory(::RunMethodCallServices))
                val factory = RunnerHandlerFactory<RunMethodAnalyzer> { owner ->
                    val observing = object : RunnerPort<RunMethodAnalyzer> by owner {
                        override fun send(event: RunEvent): Boolean {
                            if (event is RunEvent.MarkRequestsIn) outgoing += event
                            return owner.send(event)
                        }
                    }
                    val delegate = actual.create(observing)
                    object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                        override fun createActor(key: MethodKey) = delegate.createActor(key)
                        override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                        override fun freeze() = (delegate as SubscriptionHandler).freeze()
                        override fun handle(event: RunEvent) {
                            if (event is RunEvent.Start) {
                                val flow = f.fact(tail = Tail.STAR, mark = MarkSlot.STAR)
                                val destination = if (event.root == f.a) f.b else f.a
                                val ref = CallerRef(event.root, initial, Layer.NORMAL, f.inst(event.root.method, 2))
                                // Retained caller N2 owns the route; the callee stores no raw input.
                                (delegate as SubscriptionHandler).subscribe(Subscription(destination, ref, Layer.NORMAL, flow))
                                assertTrue(observing.send(RunEvent.LinkIn(destination, ref, Layer.NORMAL, flow)))
                                if (event.root == f.a) assertTrue(observing.send(RunEvent.MarkRequestsIn(f.a, initial, marks)))
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
                assertTrue(services.get() > 0)
                val expected = setOf(f.a, f.b).flatMap { method ->
                    (0 until marks.size).map { Member(method, initial, marks.markAt(it)) }
                }.toSet()
                assertEquals(expected, offered.toSet())
                assertEquals(setOf(f.a, f.b), result.analyzers.map { it.key }.toSet())
                // One seed envelope and one complete owned climb batch in each direction.
                assertEquals(3, outgoing.size)
                assertEquals(listOf(2, 1), listOf(outgoing.count { it.method == f.a }, outgoing.count { it.method == f.b }))
                assertTrue(outgoing.all { it.premise === initial && it.marks == marks && it.marks.size == size })
                for (actor in result.analyzers) {
                    val arrived = inputs.filter { it.first == actor.key }.map { it.second }
                    assertEquals(1, arrived.size)
                    assertEquals(initial.toPattern(), arrived.single().addedFact)
                }
            }
        }
    }

    @Test
    fun `failed partial question publication cannot leak into a fresh run`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 1).use { shared ->
            val initial = f.manager.initial(AccessPathBase.Argument(0), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val otherInitial = f.manager.initial(AccessPathBase.Argument(1), null, Tail.STAR,
                ExclusionSet.Empty, MarkSlot.STAR)
            val marks = RequestMarkSet.fromIds(IntArray(32) { f.manager.marks.mark("T$it").id })
            var failOnce = true
            var partialSeen = false
            val actual = RunMethodHandlers(MethodLocalServicesFactory { actor ->
                val local = RunMethodLocalServices(actor)
                object : MethodLocalServices by local {
                    override fun flushRequests(quantum: Int): Boolean {
                        if (failOnce && local.hasPendingRequests) {
                            failOnce = false
                            assertFalse(local.flushRequests(1))
                            partialSeen = true
                            assertTrue(local.hasPendingRequests, "A second independent question remains unpublished")
                            error("Controlled failure after the first question publication")
                        }
                        return local.flushRequests(quantum)
                    }
                }
            }, MethodCallServicesFactory(::RunMethodCallServices))
            val firstFactory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                val delegate = actual.create(port)
                object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                    override fun createActor(key: MethodKey) = delegate.createActor(key)
                    override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                    override fun freeze() = (delegate as SubscriptionHandler).freeze()
                    override fun handle(event: RunEvent) {
                        if (event is RunEvent.Start) {
                            assertTrue(port.send(RunEvent.MarkRequestsIn(f.a, initial, marks)))
                            assertTrue(port.send(RunEvent.MarkRequestsIn(f.a, otherInitial, marks)))
                        } else delegate.handle(event)
                    }
                }
            }
            val first = RunManager(f.config(), shared, firstFactory, quantum = 1)
            assertEquals(RunStatus.FAILED, first.run(5.seconds).status)
            assertTrue(partialSeen)
            // The failed run leaves cancellation set. Restricted config construction already reads
            // AP state, before the RunManager constructor can activate a fresh run's cancellation.
            assertFailsWith<org.opentaint.dataflow.util.Cancellation.Cancelled> { f.config(index = 3) }
            f.cancellation.activate()
            // Fresh setup installs new actors/policies/Work owners; pending publications are not reused.
            val second = RunManager(f.config(index = 3), shared, actual, quantum = 1)
            val result = second.run(5.seconds)
            assertEquals(RunStatus.COMPLETE, result.status)
            assertEquals(0L, second.inFlight.pending)
            assertEquals(0L, result.analyzers.single().demandLayerEdges)
            assertTrue(result.analyzers.single().summaries.all().all { it.first.isZero })
        }
    }
}
