package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.EngineShellFixtures
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.*

class CallerOwnedStaticRequestsTest {
    @Test fun `source view and subscriber registration orders preserve the shared union`() {
        for (sourceFirst in listOf(false, true)) {
            val f = EngineShellFixtures()
            val field = field(f)
            val graph = graph(f, mapOf(f.methodA to listOf(f.methodB)))
            val router = CallerOwnedStaticRequests(f.manager, graph.reachability)
            val source = StaticNeedKey(f.b, field)
            val view = StaticDemandViewKey(f.a, field)
            val authoritative = AtomicReference(marks(4))
            val received = ArrayList<RequestMarkSet>()

            if (sourceFirst) router.registerSource(source, authoritative::get)
            assertTrue(router.subscribeView(view, "first", received::add))
            if (!sourceFirst) router.registerSource(source, authoritative::get)
            assertEquals(listOf(marks(4)), received)

            authoritative.set(marks(4, 9))
            router.publishSourceDelta(source, marks(9))
            router.publishSourceDelta(source, marks(4, 9))
            assertEquals(listOf(marks(4), marks(9)), received)
            assertEquals(marks(4, 9), router.viewMarks(view))

            val late = ArrayList<RequestMarkSet>()
            assertTrue(router.subscribeView(view, "late", late::add))
            assertEquals(listOf(marks(4, 9)), late)
            assertFalse(router.subscribeView(view, "late") { error("A duplicate subscriber must not replace its callback") })
            assertFalse(router.registerSource(source) { error("The existing authoritative source reference must remain") })
            assertEquals(CallerOwnedStaticRequests.Snapshot(1, 1, 2, 2), router.snapshot())
            router.clear()
            assertEquals(CallerOwnedStaticRequests.Snapshot(0, 0, 0, 0), router.snapshot())
        }
    }

    @Test fun `field selection reachability and full callee keys remain independent`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val otherField = field(f, "other")
        val profiles = graph(f, mapOf(f.methodA to listOf(f.methodB), f.methodUnknown to emptyList()))
        val router = CallerOwnedStaticRequests(f.manager, profiles.reachability)
        val source = StaticNeedKey(f.b, field)
        router.registerSource(source) { marks(4) }
        val first = StaticDemandViewKey(f.a, field)
        val differentContext = StaticDemandViewKey(f.key(f.methodA, "other context"), field)
        val differentEntry = StaticDemandViewKey(f.key(f.methodA, entry = 1), field)
        val disconnected = StaticDemandViewKey(f.unknown, field)
        val wrongField = StaticDemandViewKey(f.a, otherField)
        val received = LinkedHashMap<StaticDemandViewKey, MutableList<RequestMarkSet>>()
        for (view in listOf(first, differentContext, differentEntry, disconnected, wrongField)) {
            val values = ArrayList<RequestMarkSet>()
            received[view] = values
            router.subscribeView(view, "site") { values += it }
        }
        for (view in listOf(first, differentContext, differentEntry))
            assertEquals(listOf(marks(4)), received.getValue(view).toList())
        assertTrue(received.getValue(disconnected).isEmpty())
        assertTrue(received.getValue(wrongField).isEmpty())
        assertEquals(5, router.snapshot().views)
        assertEquals(3L, router.snapshot().viewMarkMembers)
        assertTrue(router.containsPrescanEdge(f.methodA, f.methodB))
        assertFalse(router.containsPrescanEdge(f.methodB, f.methodA))
    }

    @Test fun `reader sources merge before callsite and original fanout`() {
        val f = EngineShellFixtures()
        val field = field(f)
        // Distinct full source keys can share a method/SCC and the same T.
        val profiles = graph(f, mapOf(f.methodA to listOf(f.methodB)))
        val router = CallerOwnedStaticRequests(f.manager, profiles.reachability)
        val view = StaticDemandViewKey(f.a, field)
        var notifications = 0
        var originalOffers = 0
        router.subscribeView(view, "one structural callsite") { delta ->
            notifications++
            originalOffers += 4 * delta.size
        }
        repeat(4) { reader ->
            val source = StaticNeedKey(f.key(f.methodB, "reader$reader"), field)
            router.registerSource(source) { marks(4) }
            router.publishSourceDelta(source, marks(4))
        }
        assertEquals(1, notifications)
        assertEquals(4, originalOffers, "Four sources must merge before the four original inputs")
        assertEquals(CallerOwnedStaticRequests.Snapshot(4, 1, 1, 1), router.snapshot())
    }

    @Test fun `snapshot and delta race cannot lose an old or a new mark`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val router = CallerOwnedStaticRequests(f.manager,
            graph(f, mapOf(f.methodA to listOf(f.methodB))).reachability)
        val source = StaticNeedKey(f.b, field)
        val view = StaticDemandViewKey(f.a, field)
        val current = AtomicReference(marks(4))
        val captured = CountDownLatch(1)
        val continueSnapshot = CountDownLatch(1)
        router.registerSource(source) {
            val old = current.get()
            captured.countDown()
            assertTrue(continueSnapshot.await(5, TimeUnit.SECONDS))
            old
        }
        val received = CopyOnWriteArrayList<RequestMarkSet>()
        val failure = AtomicReference<Throwable?>()
        val worker = thread {
            try { router.subscribeView(view, "site") { received += it } }
            catch (error: Throwable) { failure.set(error) }
        }
        try {
            assertTrue(captured.await(5, TimeUnit.SECONDS))
            current.set(marks(4, 9))
            router.publishSourceDelta(source, marks(9))
        } finally {
            continueSnapshot.countDown()
            worker.join(5000)
        }
        assertFalse(worker.isAlive)
        failure.get()?.let { throw AssertionError("Snapshot thread failed", it) }
        assertEquals(marks(4, 9), router.viewMarks(view))
        assertEquals(setOf(4, 9), received.flatMap { it.ids() }.toSet())
    }

    @Test fun `source getters and receiver callbacks run outside the shared lock`() {
        val f = EngineShellFixtures()
        val field = field(f)
        val router = CallerOwnedStaticRequests(f.manager, FixedPrescanReachability.empty())
        fun anotherThreadCanRead() {
            val read = CountDownLatch(1)
            val worker = thread { router.snapshot(); read.countDown() }
            assertTrue(read.await(5, TimeUnit.SECONDS), "External callback held the directory lock")
            worker.join(5000)
        }
        router.subscribeView(StaticDemandViewKey(f.a, field), "site") { anotherThreadCanRead() }
        router.registerSource(StaticNeedKey(f.a, field)) {
            anotherThreadCanRead()
            marks(4)
        }
    }

    @Test fun `fixed reachability is reflexive directed recursive and independent of contexts`() {
        val f = EngineShellFixtures()
        val adjacency = linkedMapOf(f.methodA to mutableListOf(f.methodB),
            f.methodB to mutableListOf(f.methodA, f.methodUnknown), f.methodUnknown to mutableListOf())
        val reachability = graph(f, adjacency).reachability
        assertEquals(2, reachability.componentCount)
        assertEquals(reachability.component(f.methodA), reachability.component(f.methodB))
        assertTrue(reachability.isReachable(f.methodA, f.methodUnknown))
        assertTrue(reachability.isReachable(f.methodB, f.methodUnknown))
        assertFalse(reachability.isReachable(f.methodUnknown, f.methodA))
        val cached = reachability.cachedPairs
        repeat(3) { assertFalse(reachability.isReachable(f.methodUnknown, f.methodA)) }
        assertEquals(cached, reachability.cachedPairs)
        adjacency.clear()
        assertTrue(reachability.containsEdge(f.methodA, f.methodB), "Prescan graph must be fixed")
        assertTrue(reachability.isReachable(f.methodA, f.methodA))

        val noGraph = FixedPrescanReachability.empty()
        assertTrue(noGraph.isReachable(f.methodA, f.methodA))
        assertFalse(noGraph.isReachable(f.methodA, f.methodB))
        assertFalse(noGraph.containsEdge(f.methodA, f.methodA), "Unknown graph cannot certify relay omission")
    }

    private fun graph(f: EngineShellFixtures,
                      edges: Map<org.opentaint.ir.api.common.CommonMethod, Collection<org.opentaint.ir.api.common.CommonMethod>>): Run1StaticProfiles =
        Run1StaticProfiles.build(f.manager, edges,
            (edges.keys + edges.values.flatten()).associateWith { Run1StaticLocalProfile() })

    private fun field(f: EngineShellFixtures, name: String = "f") =
        Run1Slot.StaticField(f.manager.accessors.index(ClassStaticAccessor("C")), f.field(name))

    private fun marks(vararg ids: Int): RequestMarkSet = RequestMarkSet.fromIds(ids)
    private fun RequestMarkSet.ids(): List<Int> = (0 until size).map(::idAt)
}
