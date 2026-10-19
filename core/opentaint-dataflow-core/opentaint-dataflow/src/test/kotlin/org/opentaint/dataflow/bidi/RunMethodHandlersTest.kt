package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.*
import org.opentaint.dataflow.ifds.UnitResolver
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class RunMethodHandlersTest {
    private class Harness(val f: EngineShellFixtures, index: Int = 1) : AutoCloseable {
        val shared = f.shared()
        val config = f.config(index = index)
        val actors = linkedMapOf<MethodKey, RunMethodAnalyzer>()
        val storages = linkedMapOf<MethodKey, SummaryStorage>()
        val requests = arrayListOf<Pair<InitialAp, RequestKind>>()
        val links = arrayListOf<Triple<CallerRef, Layer, Facts>>()
        val applications = arrayListOf<Triple<Subscription, Publication, Int>>()
        val events = arrayListOf<RunEvent>()
        val enqueued = linkedSetOf<RunMethodAnalyzer>()
        var localCreated = 0
        var callsCreated = 0
        var localFrozen = 0
        var callsFrozen = 0
        lateinit var handler: RunnerHandler<RunMethodAnalyzer>
        val actualHandler: RunnerHandler<RunMethodAnalyzer>
        val port = object : RunnerPort<RunMethodAnalyzer> {
            override val config get() = this@Harness.config
            override val shared get() = this@Harness.shared
            override val forms = shared.contexts.directed(config.direction)
            override val ops get() = f.ops
            override val steps = ProtocolSteps.Inline
            override fun summaryStorage(key: MethodKey): SummaryStorage = storages.getOrPut(key) {
                SummaryStorage(key, f.manager, f.ops, steps)
            }
            override fun subscribe(sub: Subscription) = (handler as SubscriptionHandler).subscribe(sub)
            override fun link(callee: MethodKey, ref: CallerRef, linkLayer: Layer, added: Facts) =
                handler.handle(RunEvent.LinkIn(callee, ref, linkLayer, added))
            override fun send(event: RunEvent): Boolean { events += event; return true }
            override fun post(event: RunEvent.Delivery): Boolean { events += event; return true }
            override fun enqueue(analyzer: RunMethodAnalyzer): Boolean = enqueued.add(analyzer)
            override fun analyzer(key: MethodKey): RunMethodAnalyzer = actors.getOrPut(key) {
                handler.createActor(key)
            }
        }
        init {
            actualHandler = RunMethodHandlers(MethodLocalServicesFactory { core ->
                localCreated++
                object : MethodLocalServices by RunMethodLocalServices(core) {
                    override fun addMarkRequest(premise: InitialAp, mark: TaintMark) {
                            val kind = RequestKind.Mark(mark)
                        requests += premise to kind
                    }
                    override fun addMarkRequests(premise: InitialAp, marks: RequestMarkSet) {
                        marks.forEachMark { addMarkRequest(premise, it) }
                    }
                    override fun freeze() { localFrozen++ }
                }
            }, MethodCallServicesFactory { core ->
                callsCreated++
                finiteCalls(core, applications) { callsFrozen++ }
            }).create(port)
            handler = object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                override fun createActor(key: MethodKey) = actualHandler.createActor(key)
                override fun subscribe(sub: Subscription) = (actualHandler as SubscriptionHandler).subscribe(sub)
                override fun freeze() = (actualHandler as SubscriptionHandler).freeze()
                override fun handle(event: RunEvent) {
                    if (event is RunEvent.LinkIn) links += Triple(event.ref, event.linkLayer, event.added)
                    actualHandler.handle(event)
                }
            }
        }
        fun drain() {
            var remaining = 1000
            while (enqueued.isNotEmpty()) {
                check(remaining-- > 0)
                val actor = enqueued.first()
                enqueued.remove(actor)
                if (!actor.work(100)) enqueued += actor
            }
        }
        override fun close() = shared.close()
    }

    @Test fun `actual handler creates both required services once and dispatches root zero to work`(): Unit {
        val f = EngineShellFixtures()
        Harness(f).use { h ->
            h.handler.handle(RunEvent.Start(f.a))
            h.handler.handle(RunEvent.Start(f.a))
            assertEquals(1, h.localCreated)
            assertEquals(1, h.callsCreated)
            assertEquals(1, h.enqueued.size)
            h.drain()
            val actor = h.actors.getValue(f.a)
            assertTrue(actor.steps > 0)
            assertTrue(actor.summaries.all().any { it.first.isZero && it.second == Reach.NORMAL })
            assertEquals(1, h.storages.size)
            val sub = Subscription(f.a, CallerRef(f.b, f.manager.zero, Layer.NORMAL,
                f.inst(f.methodB, 2)), Layer.NORMAL, Reach.NORMAL, zeroOnly = true)
            assertTrue(h.port.summaryStorage(f.a).candidates(sub, h.config.mode)
                .any { it.first.premise.isZero && it.first.conclusion == Reach.NORMAL })
        }
    }

    @Test fun `actual handler dispatches complete link and request keys without merging caller layers`(): Unit {
        val f = EngineShellFixtures()
        Harness(f).use { h ->
            val added = f.fact()
            val initial = f.manager.initial(f.pattern())
            val ref = CallerRef(f.b, initial, Layer.DEMAND, f.inst(f.methodB, 2))
            h.handler.handle(RunEvent.LinkIn(f.a, ref, Layer.NORMAL, added))
            h.handler.handle(RunEvent.LinkIn(f.a, ref, Layer.NORMAL, added))
            val kind = RequestKind.Mark(f.manager.marks.mark("request"))
            val slot = Run1Slot.Base(added.base)
            h.handler.handle(RunEvent.MarkRequestsIn(f.a, mapOf(slot to RequestMarkSet.of(kind.mark))))
            assertEquals(listOf(Triple(ref, Layer.NORMAL, added), Triple(ref, Layer.NORMAL, added)), h.links)
            assertEquals(1, h.actors.getValue(f.a).initials.all().count { it.mark is MarkSlot.Star && it.base == added.base },
                "Repeated input deliveries remain observable while initial emission deduplicates")
            assertTrue(h.port.summaryStorage(f.a).borrowRequest(slot).isEmpty,
                "Handler dispatch collects P; it does not commit a request early")
            h.drain()
            assertEquals(RequestMarkSet.of(kind.mark), h.port.summaryStorage(f.a).borrowRequest(slot).marks)
            assertEquals(1, h.localCreated)
            assertEquals(1, h.callsCreated)
        }
    }

    @Test fun `zero input starts a backward callee and rejects a forward zero entry or Work dispatch`(): Unit {
        val f = EngineShellFixtures()
        Harness(f, index = 2).use { h ->
            h.handler.handle(RunEvent.ZeroIn(f.a))
            assertTrue(h.actors.getValue(f.a).initials.all().any { it.isZero })
            assertTrue(h.enqueued.isNotEmpty())
            h.drain()
            assertFails { h.handler.handle(RunEvent.Work(h.actors.getValue(f.a))) }
        }
        Harness(f).use { h -> assertFails { h.handler.handle(RunEvent.ZeroIn(f.a)) } }
    }

    @Test fun `delivery applies on the subscribed caller actor even when the callee belongs to another unit`(): Unit {
        val f = EngineShellFixtures()
        Harness(f).use { h ->
            val full = f.manager.marks.mark("T")
            val group = MarkSlot.Concrete(f.manager.marks.group(f.manager.marks.groupOf(full)))
            val input = f.fact(mark = group, layer = Layer.DEMAND)
            val initial = f.manager.initial(f.pattern(mark = group))
            val ref = CallerRef(f.a, initial, Layer.DEMAND, f.inst(f.methodA, 2))
            val sub = Subscription(f.b, ref, Layer.DEMAND, input)
            h.port.subscribe(sub)
            val pub = Publication(initial, f.fact(base = org.opentaint.dataflow.ap.ifds.AccessPathBase.Return, mark = group, layer = Layer.DEMAND))
            h.port.summaryStorage(f.b).publish(listOf(pub))
            assertTrue(h.applications.isEmpty(), "Publishing only posts to the caller runner")
            val delivery = assertIs<RunEvent.Delivery>(h.events.single())
            assertEquals(f.b, delivery.callee)
            h.handler.handle(delivery)
            val applied = h.applications.single()
            assertEquals(sub, applied.first)
            assertSame(pub.conclusion, applied.second.conclusion)
            assertEquals(0, applied.third)
            assertEquals(setOf(f.a), h.actors.keys, "The subscribed caller owns the resume service")
        }
    }

    @Test fun `complete actor and handler release freeze both required services and discard subscriptions`(): Unit {
        val f = EngineShellFixtures()
        Harness(f).use { h ->
            val ref = CallerRef(f.a, f.manager.zero, Layer.NORMAL, f.inst(f.methodA, 2))
            h.port.subscribe(Subscription(f.b, ref, Layer.NORMAL, Reach.NORMAL, zeroOnly = true))
            h.handler.handle(RunEvent.Start(f.a))
            h.drain()
            h.actors.values.forEach { it.freeze() }
            (h.handler as SubscriptionHandler).freeze()
            val subscriptions = h.actualHandler.javaClass.declaredFields.single {
                it.type == SubscriptionManager::class.java
            }.apply { isAccessible = true }
            assertNull(subscriptions.get(h.actualHandler), "Joined release drops the subscription manager reference")
            assertEquals(1, h.localFrozen)
            assertEquals(1, h.callsFrozen)
            assertFails { h.port.subscribe(Subscription(f.b, ref, Layer.NORMAL, Reach.NORMAL, zeroOnly = true)) }
            assertFails { h.handler.handle(RunEvent.Delivery(f.b, listOf(Publication(f.manager.zero, Reach.NORMAL)))) }
            assertFails { h.actors.getValue(f.a).work(1) }
        }
    }

    @Test fun `run manager owns one storage per full method key and creates fresh storage for the next run`(): Unit {
        val f = EngineShellFixtures()
        f.shared(threads = 2).use { shared ->
            val factory = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                MethodCallServicesFactory { finiteCalls(it) })
            val run = RunManager(f.config(roots = emptyList()), shared, factory)
            val first = run.summaryStorage(f.a)
            val reads = CopyOnWriteArrayList<SummaryStorage>()
            val threads = List(8) { Thread { repeat(10) { reads += run.summaryStorage(f.a) } } }
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)
            assertEquals(80, reads.size)
            assertTrue(reads.all { it === first })
            assertNotSame(first, run.summaryStorage(f.key(context = "other")))
            assertEquals(RunStatus.COMPLETE, run.run(5.seconds).status)
            assertFails { run.summaryStorage(f.a) }
            val next = RunManager(f.config(roots = emptyList()), shared, factory)
            assertNotSame(first, next.summaryStorage(f.a))
            assertEquals(RunStatus.COMPLETE, next.run(5.seconds).status)
        }
    }

    @Test fun `same unit links dispatch inline and remote links are counted destination events`(): Unit {
        for (sameUnit in listOf(true, false)) {
            val f = EngineShellFixtures()
            val seen = CopyOnWriteArrayList<String>()
            val frozen = AtomicInteger()
            val resolver = if (sameUnit) UnitResolver { method ->
                if (method == f.methodA || method == f.methodB) f.unitA else f.unitResolver.resolve(method)
            } else f.unitResolver
            f.shared(threads = 2, unitResolver = resolver).use { shared ->
                val delegateFactory = RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
                    MethodCallServicesFactory { finiteCalls(it) })
                val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                    val delegate = delegateFactory.create(port)
                    object : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
                        override fun createActor(key: MethodKey) = delegate.createActor(key)
                        override fun subscribe(sub: Subscription) = (delegate as SubscriptionHandler).subscribe(sub)
                        override fun freeze() { (delegate as SubscriptionHandler).freeze(); frozen.incrementAndGet() }
                        override fun handle(event: RunEvent) {
                            if (event is RunEvent.Start) {
                                val ref = CallerRef(f.a, f.manager.zero, Layer.NORMAL, f.inst(f.methodA, 2))
                                seen += "before"
                                port.link(f.b, ref, Layer.NORMAL, Reach.NORMAL)
                                if (sameUnit) assertEquals(listOf("before", "link"), seen.toList())
                                seen += "after"
                            }
                            if (event is RunEvent.LinkIn) seen += "link"
                            delegate.handle(event)
                        }
                    }
                }
                val run = RunManager(f.config(), shared, factory)
                val result = run.run(5.seconds)
                assertEquals(RunStatus.COMPLETE, result.status)
                assertEquals(setOf(f.a, f.b), result.analyzers.map { it.key }.toSet())
                assertEquals(if (sameUnit) 1 else 2, frozen.get())
                assertEquals(0L, run.inFlight.pending)
                assertEquals(if (sameUnit) listOf("before", "link", "after") else setOf("before", "link", "after"),
                    if (sameUnit) seen.toList() else seen.toSet())
                assertEquals(if (sameUnit) 4L else 5L, run.inFlight.handled,
                    "Root batch, Start and two Work events; only the remote link adds a reservation")
            }
        }
    }

    private companion object {
        fun finiteCalls(core: RunMethodAnalyzer,
                        applications: MutableList<Triple<Subscription, Publication, Int>> = arrayListOf(),
                        frozen: () -> Unit = {}): MethodCallServices = object : MethodCallServices {
            override fun process(item: EdgeDelta) = core.emitUnchanged(item)
            override fun applySummary(part: Subscription, pub: Publication, member: Int) {
                applications += Triple(part, pub, member)
            }
            override fun applyRecord(part: Subscription, record: Record): Unit = error("No records in this fixture")
            override fun applyRecordView(part: Subscription, view: RecordLeafView): Unit = error("No record views in this fixture")
            override fun freeze() = frozen()
        }
    }
}
