package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

/** Pure FULL-mark transport laws. Group projection is checked by the engine suites. */
class SummaryStorageProtocolTest {
    /** A finite protocol counterexample: one applicable subscription/publication pair. */
    private class Schedule {
        val stored = linkedSetOf("publication")
        val subscribers = linkedSetOf<String>()
        val pending = arrayListOf<Pair<String, String>>()
        val applied = linkedSetOf<Pair<String, String>>()
        fun register() { subscribers += "subscription" }
        fun read(snapshot: Set<String> = stored, accepts: Boolean = true) {
            if (accepts) for (pub in snapshot) applied += "subscription" to pub
        }
        fun notify(recipients: Set<String> = subscribers.toSet()) {
            for (sub in recipients) for (pub in stored) pending += sub to pub
        }
        fun deliver(accepts: Boolean = true) {
            if (accepts) applied += pending
            pending.clear()
        }
    }

    private val pair = setOf("subscription" to "publication")

    @Test fun `broken P1 read before registration loses the publication between those two actions`(): Unit {
        val broken = Schedule().apply {
            stored.clear()
            read()
            stored += "publication"
            notify()
            register()
            deliver()
        }
        assertTrue(broken.applied.isEmpty())
        val correct = Schedule().apply {
            stored.clear()
            register()
            read()
            stored += "publication"
            notify()
            deliver()
        }
        assertEquals(pair, correct.applied)
    }

    @Test fun `broken P2 notification recipient read before insert loses a concurrent new subscriber`(): Unit {
        val broken = Schedule().apply {
            stored.clear()
            val earlyRecipients = subscribers.toSet()
            register()
            read()
            stored += "publication"
            notify(earlyRecipients)
            deliver()
        }
        assertTrue(broken.applied.isEmpty())
        val correct = Schedule().apply {
            stored.clear()
            stored += "publication"
            register()
            read()
            notify()
            deliver()
        }
        assertEquals(pair, correct.applied)
    }

    @Test fun `broken P3 stale replay read loses a publication inserted before registration`(): Unit {
        val broken = Schedule().apply {
            register()
            read(snapshot = emptySet())
            deliver()
        }
        assertTrue(broken.applied.isEmpty())
        val correct = Schedule().apply { register(); read(); deliver() }
        assertEquals(pair, correct.applied)
    }

    @Test fun `broken P4 mismatched replay or delivery predicate loses the applicable pair in its only arrival path`(): Unit {
        val badReplay = Schedule().apply { register(); read(accepts = false); deliver() }
        assertTrue(badReplay.applied.isEmpty())
        val goodReplay = Schedule().apply { register(); read(); deliver() }
        assertEquals(pair, goodReplay.applied)
        val badDelivery = Schedule().apply {
            stored.clear()
            register()
            read()
            stored += "publication"
            notify()
            deliver(accepts = false)
        }
        assertTrue(badDelivery.applied.isEmpty())
        val goodDelivery = Schedule().apply {
            stored.clear()
            register()
            read()
            stored += "publication"
            notify()
            deliver()
        }
        assertEquals(pair, goodDelivery.applied)
    }

    @Test fun `actual replay and delivery retain the full applicable pair in every arrival and queued step order`(): Unit {
        for (publicationFirst in listOf(false, true)) for (replayFirst in listOf(false, true)) {
            val queued = QueuedProtocolSteps()
            PipelineFixtures(index = 3, protocol = queued).use { f ->
                val t = MarkSlot.Concrete(f.store.mark("T"))
                val u = MarkSlot.Concrete(f.store.mark("U"))
                val initial = f.ap.initial(f.store.pattern(mark = t))
                val result = f.store.fact(AccessPathBase.Return, listOf(f.store.field("result")), mark = u)
                val pub = Publication(initial, result)
                val sub = f.subscription(f.store.fact(mark = t))
                if (publicationFirst) f.storage().publish(listOf(pub))
                f.subscriptions.subscribe(sub)
                if (!publicationFirst) f.storage().publish(listOf(pub))
                if (replayFirst) { queued.replayAll(); queued.runNotifications(); f.deliverAll() }
                else { queued.runNotifications(); f.deliverAll(); queued.replayAll() }
                assertTrue(f.summaries.isNotEmpty())
                assertEquals(setOf(Triple(sub, pub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) }.toSet())
                assertTrue(f.summaries.all { it.caller == sub.caller && it.part.ref === sub.ref })
                val count = f.summaries.size
                f.subscriptions.subscribe(sub)
                f.storage().publish(listOf(pub))
                queued.replayAll()
                queued.runNotifications()
                f.deliverAll()
                assertEquals(count, f.summaries.size, "Exact subscription and publication duplicates add no new work")
            }
        }
    }

    @Test fun `P1 registers the actual manager before its deferred replay read`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val initial = f.ap.initial(f.store.pattern())
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return))
            val sub = f.subscription()
            f.subscriptions.subscribe(sub)
            assertEquals(1, queued.replays.size)
            f.storage().publish(listOf(pub))
            queued.runNotifications()
            assertEquals(1, f.deliveries.size, "Registration precedes the still-pending replay")
            f.deliverAll()
            assertEquals(listOf(Triple(sub, pub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) })
            queued.replayAll()
            assertEquals(setOf(Triple(sub, pub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) }.toSet())
        }
    }

    @Test fun `P2 inserts the publication before entering the notification step`(): Unit {
        lateinit var fixture: PipelineFixtures
        lateinit var subscription: Subscription
        var observed = false
        val steps = object : ProtocolSteps {
            override fun replay(step: () -> Unit): Unit = step()
            override fun notify(step: () -> Unit) {
                val snapshot = fixture.storage().candidates(subscription, fixture.config.mode)
                assertEquals(1, snapshot.size)
                assertEquals(AccessPathBase.Return, snapshot.single().first.conclusion.base)
                observed = true
                step()
            }
        }
        PipelineFixtures(index = 3, protocol = steps).use { f ->
            fixture = f
            subscription = f.subscription()
            val initial = f.ap.initial(f.store.pattern())
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return))
            f.subscriptions.subscribe(subscription)
            f.storage().publish(listOf(pub))
            assertTrue(observed)
            f.deliverAll()
            assertEquals(listOf(Triple(subscription, pub, 0)), f.summaries.map { Triple(it.part, it.publication, it.member) })
        }
    }

    @Test fun `P3 concurrent replay reads observe a whole inserted batch and the completed publication`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val t = MarkSlot.Concrete(f.store.mark("T"))
            val initial = f.ap.initial(f.store.pattern(mark = t))
            val sub = f.subscription(f.store.fact(mark = t))
            val pubs = (0 until 128).map { index ->
                Publication(initial, f.store.fact(AccessPathBase.Return, listOf(f.store.field("field$index")), mark = t))
            }
            val expected = pubs.flatMap { methodLeaves(f.ops, listOf(it.conclusion)) }.toSet()
            val storage = f.storage()
            val firstRead = CountDownLatch(1)
            val inserted = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            val done = AtomicBoolean()
            val snapshots = ArrayList<Set<Conclusion>>()
            val reader = thread(name = "bidi-publication-reader") {
                try {
                    assertTrue(storage.candidates(sub, f.config.mode).isEmpty())
                    firstRead.countDown()
                    repeat(64) {
                        val snapshot = storage.candidates(sub, f.config.mode)
                        snapshots += methodLeaves(f.ops, snapshot.map { it.first.conclusion })
                        if (done.get()) return@repeat
                    }
                    assertTrue(inserted.await(10, TimeUnit.SECONDS))
                    snapshots += methodLeaves(f.ops, storage.candidates(sub, f.config.mode).map { it.first.conclusion })
                } catch (error: Throwable) { failure.compareAndSet(null, error); firstRead.countDown() }
            }
            val writer = thread(name = "bidi-publication-writer") {
                try {
                    assertTrue(firstRead.await(10, TimeUnit.SECONDS))
                    storage.publish(pubs)
                    done.set(true)
                } catch (error: Throwable) { failure.compareAndSet(null, error) }
                finally { inserted.countDown() }
            }
            reader.join(10000)
            writer.join(10000)
            assertFalse(reader.isAlive)
            assertFalse(writer.isAlive)
            failure.get()?.let { throw it }
            assertTrue(snapshots.isNotEmpty())
            assertTrue(snapshots.all { it.isEmpty() || it == expected }, "A read must not expose a partial batch")
            assertEquals(expected, snapshots.last())
        }
    }

    @Test fun `publication input lists notification deltas and replay snapshots own immutable contents`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val initial = f.ap.initial(f.store.pattern())
            val sub = f.subscription()
            val first = Publication(initial, f.store.fact(AccessPathBase.Return, listOf(f.store.field("first"))))
            val second = Publication(initial, f.store.fact(AccessPathBase.Return, listOf(f.store.field("second"))))
            f.subscriptions.subscribe(sub)
            queued.replayAll()
            val input = arrayListOf(first)
            f.storage().publish(input)
            val before = f.storage().candidates(sub, f.config.mode)
            input.clear()
            input += second
            f.storage().publish(listOf(second))
            queued.runNotifications()
            assertEquals(2, f.deliveries.size)
            assertEquals(listOf(first), f.deliveries[0].publications)
            assertEquals(listOf(second), f.deliveries[1].publications)
            assertEquals(listOf(first to 0), before)
            assertFails { (before as MutableList<Pair<Publication, Int>>).clear() }
            assertFails { (f.deliveries[0].publications as MutableList<Publication>).clear() }
            val current = f.storage().candidates(sub, f.config.mode)
            assertEquals(methodLeaves(f.ops, listOf(first.conclusion, second.conclusion)),
                methodLeaves(f.ops, current.map { it.first.conclusion }))
            assertEquals(listOf(first to 0), before, "Growing storage does not change a previous snapshot")
            f.deliverAll()
            assertEquals(methodLeaves(f.ops, listOf(first.conclusion, second.conclusion)),
                methodLeaves(f.ops, f.summaries.map { it.publication.conclusion }))
        }
    }

    @Test fun `P5 keeps earlier subscriptions and complete publications available to later links and deltas`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val initial = f.ap.initial(f.store.pattern())
            val first = Publication(initial, f.store.fact(AccessPathBase.Return, listOf(f.store.field("first"))))
            val second = Publication(initial, f.store.fact(AccessPathBase.Return, listOf(f.store.field("second"))))
            val a = f.subscription(call = 1)
            val b = f.subscription(call = 2)
            f.subscriptions.subscribe(a)
            f.storage().publish(listOf(first))
            f.deliverAll()
            f.subscriptions.subscribe(b)
            f.storage().publish(listOf(second))
            f.deliverAll()
            val expected = listOf(a.ref, b.ref).flatMap { ref ->
                methodLeaves(f.ops, listOf(first.conclusion, second.conclusion)).map { ref to it }
            }.toSet()
            assertEquals(expected, f.summaries.flatMap { call -> methodLeaves(f.ops, listOf(call.publication.conclusion))
                .map { call.part.ref to it } }.toSet())
            assertEquals(methodLeaves(f.ops, listOf(first.conclusion, second.conclusion)),
                methodLeaves(f.ops, f.storage().candidates(a, f.config.mode).map { it.first.conclusion }))
        }
    }

    @Test fun `P6 delayed delivery reads subscriptions added after notification was sent`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val a = f.subscription(call = 1)
            val b = f.subscription(call = 2)
            val initial = f.ap.initial(f.store.pattern())
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return))
            f.subscriptions.subscribe(a)
            queued.replayAll()
            f.storage().publish(listOf(pub))
            queued.runNotifications()
            assertEquals(1, f.deliveries.size)
            f.subscriptions.subscribe(b)
            assertEquals(1, queued.replays.size, "The later link's replay has not run")
            f.deliverAll()
            assertEquals(setOf(a.ref, b.ref), f.summaries.map { it.part.ref }.toSet())
            assertEquals(2, f.summaries.size)
            queued.replayAll()
            assertEquals(setOf(a.ref, b.ref), f.summaries.map { it.part.ref }.toSet())
        }
    }

    @Test fun `deferred replay keeps each fresh delta while delivery uses one cumulative group`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val a = f.store.field("a")
            val b = f.store.field("b")
            val first = f.subscription(f.store.fact(path = listOf(a)))
            val second = first.copy(added = f.store.fact(path = listOf(b)))
            val initial = f.ap.initial(f.store.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR))
            f.subscriptions.subscribe(first)
            f.subscriptions.subscribe(second)
            f.storage().publish(listOf(pub))
            queued.replayAll()
            assertEquals(listOf(first, second), f.summaries.map { it.part }, "Queued replay captures the accepted delta")
            f.summaries.clear()
            queued.runNotifications()
            f.deliverAll()
            assertEquals(1, f.summaries.size, "Delivery visits one full-key cumulative group")
            assertEquals(setOf(first.added, second.added).flatMap { f.ops.leaves(it).toList() }.toSet(),
                f.ops.leaves(f.summaries.single().part.added).toSet())
            assertEquals(first.ref, f.summaries.single().part.ref)
        }
    }

    @Test fun `delivery captures all candidate values before its first callback changes subscriptions`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val a = f.subscription(f.store.fact(path = listOf(f.store.field("a"))), call = 1)
            val b = f.subscription(f.store.fact(path = listOf(f.store.field("b"))), call = 2)
            val c = b.copy(added = f.store.fact(path = listOf(f.store.field("c"))))
            val late = f.subscription(f.store.fact(path = listOf(f.store.field("late"))), call = 3)
            val initial = f.ap.initial(f.store.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return, tail = Tail.STAR, mark = MarkSlot.STAR))
            f.subscriptions.subscribe(a)
            f.subscriptions.subscribe(b)
            queued.replayAll()
            f.storage().publish(listOf(pub))
            queued.runNotifications()
            var changed = false
            f.onSummary = {
                if (!changed) {
                    changed = true
                    f.subscriptions.subscribe(c)
                    f.subscriptions.subscribe(late)
                }
            }
            f.deliverAll()
            assertTrue(changed)
            assertEquals(setOf(a, b), f.summaries.map { it.part }.toSet(), "The current delivery uses the pre-callback snapshot")
            queued.replayAll()
            assertEquals(setOf(a, b, c, late), f.summaries.map { it.part }.toSet(), "New deltas still replay")
            f.summaries.clear()
            f.subscriptions.onDelivery(f.callee, listOf(pub))
            assertEquals(3, f.summaries.size)
            val grown = f.summaries.single { it.part.ref == b.ref }.part
            assertEquals(f.ops.leaves(b.added).toSet() + f.ops.leaves(c.added).toSet(), f.ops.leaves(grown.added).toSet())
        }
    }

    @Test fun `zero-only directory keeps the first Reach layer for the full caller reference`(): Unit {
        PipelineFixtures(index = 3).use { f ->
            val directory = CalleeSubscriptions(f.ops)
            val normal = f.subscription(Reach.NORMAL, zeroOnly = true)
            val demand = normal.copy(linkLayer = Layer.DEMAND, added = Reach.DEMAND)
            assertSame(normal, directory.add(normal))
            assertNull(directory.add(demand))
            assertFails { directory.add(demand.copy(linkLayer = Layer.NORMAL)) }
            assertFails { directory.add(normal.copy(added = f.store.fact())) }
            assertFails { directory.add(normal.copy(ref = normal.ref.copy(premise = f.ap.initial(f.store.pattern())))) }
            val pub = Publication(f.ap.zero, Reach.NORMAL)
            assertEquals(listOf(normal), directory.candidates(pub, 0, f.config.mode).toList())
            val otherCallerLayer = demand.copy(ref = normal.ref.copy(callerLayer = Layer.DEMAND))
            assertSame(otherCallerLayer, directory.add(otherCallerLayer))
            assertEquals(listOf(normal, otherCallerLayer), directory.candidates(pub, 0, f.config.mode).toList())
        }
    }

    @Test fun `ordinary cumulative groups stay indexed at all old and new paths in both matching modes`(): Unit {
        for (run in listOf(1, 2, 3)) PipelineFixtures(index = run).use { f ->
            val statistics = N2Statistics()
            val directory = CalleeSubscriptions(f.ops, statistics)
            val first = f.subscription(f.store.fact(path = listOf(f.store.field("a"))))
            val second = first.copy(added = f.store.fact(path = listOf(f.store.field("b"))))
            assertEquals(first, directory.add(first))
            assertEquals(second, directory.add(second))
            assertNull(directory.add(second))
            for (input in listOf(first, second)) {
                val leaf = f.ops.leaves(input.added).single()
                val initial = f.ap.initial(Pattern(leaf.fact, leaf.exclusion))
                val pub = Publication(initial, f.store.fact(AccessPathBase.Return))
                val candidates = directory.candidates(pub, 0, f.config.mode).toList()
                assertEquals(1, candidates.size)
                assertEquals(f.ops.leaves(first.added).toSet() + f.ops.leaves(second.added).toSet(),
                    f.ops.leaves(candidates.single().added).toSet())
            }
            val snapshot = statistics.snapshot()
            assertEquals(2L, snapshot.acceptedDeltas)
            assertEquals(1L, snapshot.cumulativeGroups)
            assertEquals(2L, snapshot.indexPostings)
            directory.clear()
            assertEquals(0L, statistics.snapshot().cumulativeGroups)
            assertEquals(0L, statistics.snapshot().indexPostings)
            assertEquals(2L, statistics.snapshot().acceptedDeltas, "The accepted count is cumulative")
        }
    }

    @Test fun `one manager registers once per callee and duplicate deliveries are harmless repeated applications`(): Unit {
        val queued = QueuedProtocolSteps()
        PipelineFixtures(index = 3, protocol = queued).use { f ->
            val a = f.subscription(call = 1)
            val b = f.subscription(call = 2)
            val initial = f.ap.initial(f.store.pattern())
            val pub = Publication(initial, f.store.fact(AccessPathBase.Return))
            f.subscriptions.subscribe(a)
            f.subscriptions.subscribe(a)
            f.subscriptions.subscribe(b)
            assertEquals(2, queued.replays.size)
            queued.replayAll()
            f.storage().publish(listOf(pub))
            f.storage().publish(listOf(pub))
            assertEquals(1, queued.notifications.size)
            queued.runNotifications()
            assertEquals(1, f.deliveries.size, "One manager registration receives one notification")
            val event = f.deliveries.single()
            f.deliverAll()
            val applied = f.summaries.map { Triple(it.part, it.publication, it.member) }.toSet()
            f.subscriptions.onDelivery(event.callee, event.publications)
            assertEquals(applied, f.summaries.map { Triple(it.part, it.publication, it.member) }.toSet())
            assertEquals(4, f.summaries.size)
            assertEquals(setOf(Triple(a, pub, 0), Triple(b, pub, 0)), applied)
            f.subscriptions.onDelivery(f.store.key(context = "unsubscribed"), event.publications)
            assertEquals(4, f.summaries.size)
        }
    }
}
