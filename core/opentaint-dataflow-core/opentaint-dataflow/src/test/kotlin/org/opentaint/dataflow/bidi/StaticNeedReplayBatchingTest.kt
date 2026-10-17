package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.PipelineFixtures
import org.opentaint.dataflow.bidi.fixtures.QueuedProtocolSteps
import org.opentaint.dataflow.bidi.store.RequestMarkSet
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.*

class StaticNeedReplayBatchingTest {
    @Test fun `duplicate actual binding admission does not dirty a completed owner turn`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val owner = h.owner("one")
            h.terminal(owner, a, "p"); h.source(owner, a, 4)
            h.flush(owner); h.drain()
            val before = h.dispatched.size
            h.terminal(owner, a, "p")
            assertTrue(owner.pending.isEmpty())
            h.flush(owner); h.drain()
            assertEquals(before, h.dispatched.size)
            h.assertOracle()
        }
    }

    @Test fun `equal content starts share the diamond but keep every source and endpoint mark`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b"); val x = h.node("x"); val y = h.node("y")
            val owner = h.owner("one")
            h.link(owner, a, x); h.link(owner, b, x); h.link(owner, x, y)
            h.terminal(owner, y, "p")
            h.source(owner, a, 3, 7); h.source(owner, b, 7, 3)
            assertTrue(h.dispatched.isEmpty(), "No dispatch occurs before the owner flush")
            h.flush(owner); h.drain()
            h.assertOracle()
            assertEquals(1, h.waveCount())
            assertEquals(1, h.dispatched.count { it.key == x })
            assertEquals(1, h.dispatched.count { it.key == y })
            assertEquals(2, h.router.sourcesSnapshot().size)
            assertEquals(2, h.sourceChanges.size, "Grouping must retain both source observations")
        }
    }

    @Test fun `unequal colliding mark sets never cross disconnected endpoints`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b"); val owner = h.owner("one")
            val left = RequestMarkSet.fromIds(intArrayOf(0, 33))
            val right = RequestMarkSet.fromIds(intArrayOf(1, 2))
            assertEquals(left.hashCode(), right.hashCode())
            assertNotEquals(left, right)
            h.terminal(owner, a, "left"); h.terminal(owner, b, "right")
            h.source(owner, a, 0, 33); h.source(owner, b, 1, 2)
            h.flush(owner); h.drain()
            h.assertOracle()
            assertEquals(2, h.waveCount())
            assertEquals(setOf(0, 33), h.delivered.filter { it.method == h.key("left") }.map { it.mark }.toSet())
            assertEquals(setOf(1, 2), h.delivered.filter { it.method == h.key("right") }.map { it.mark }.toSet())
        }
    }

    @Test fun `source only deltas merge and pure duplicates cause no replay`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val owner = h.owner("one")
            h.terminal(owner, a, "p"); h.flush(owner)
            h.source(owner, a, 4); h.source(owner, a, 9); h.source(owner, a, 4)
            h.flush(owner); h.drain()
            h.assertOracle()
            assertEquals(1, h.waveCount())
            assertEquals(setOf(4, 9), h.dispatched.single().marks.ids())
            assertEquals(2, h.sourceChanges.size)
            val before = h.dispatched.size
            h.source(owner, a, 4, 9); h.flush(owner); h.drain()
            assertEquals(before, h.dispatched.size)
            assertTrue(owner.pending.isEmpty())
        }
    }

    @Test fun `unequal overlapping sets do not leak marks through a shared route`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b"); val shared = h.node("shared")
            val owner = h.owner("one")
            h.link(owner, a, shared); h.link(owner, b, shared)
            h.terminal(owner, a, "left"); h.terminal(owner, b, "right")
            h.terminal(owner, shared, "shared end")
            h.source(owner, a, 4, 9); h.source(owner, b, 9, 12)
            h.flush(owner); h.drain(); h.assertOracle()
            assertEquals(2, h.waveCount())
            assertEquals(setOf(4, 9), h.delivered.filter { it.method == h.key("left") }.map { it.mark }.toSet())
            assertEquals(setOf(9, 12), h.delivered.filter { it.method == h.key("right") }.map { it.mark }.toSet())
            assertEquals(setOf(4, 9, 12), h.delivered.filter { it.method == h.key("shared end") }.map { it.mark }.toSet())
        }
    }

    @Test fun `graph growth selects cumulative sources once including same turn fresh deltas`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val owner = h.owner("one")
            h.source(owner, a, 4); h.flush(owner); h.drain()
            h.dispatched.clear()
            h.terminal(owner, a, "left")
            h.terminal(owner, a, "right")
            h.source(owner, a, 9)
            h.flush(owner); h.drain()
            h.assertOracle()
            assertEquals(1, h.dispatched.size, "One full selection subsumes this turn's delta selection")
            assertEquals(setOf(4, 9), h.dispatched.single().marks.ids())
        }
    }

    @Test fun `late edge behind visited cycle and late marks always get fresh traversal state`() {
        for (sourceFirst in listOf(false, true)) ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b"); val owner = h.owner("one")
            if (sourceFirst) { h.source(owner, a, 4); h.flush(owner); h.drain() }
            h.link(owner, a, b); h.link(owner, b, a)
            h.flush(owner); h.drain()
            if (!sourceFirst) { h.source(owner, a, 4); h.flush(owner); h.drain() }
            val before = h.waveCount()
            h.terminal(owner, b, "late")
            h.flush(owner); h.drain()
            h.source(owner, a, 9); h.flush(owner); h.drain()
            h.assertOracle()
            assertTrue(h.waveCount() > before)
            assertEquals(setOf(4, 9), h.delivered.map { it.mark }.toSet())
        }
    }

    @Test fun `one entered node fans out to every actual subscriber owner including late subscribers`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val first = h.owner("first"); val second = h.owner("second")
            h.terminal(first, a, "left"); h.terminal(second, a, "right")
            h.source(first, a, 4); h.flush(first); h.flush(second); h.drain()
            h.assertOracle()
            assertTrue(first.visits.contains(a) && second.visits.contains(a))
            val late = h.owner("late")
            h.terminal(late, a, "last"); h.flush(late); h.drain()
            h.assertOracle()
            assertTrue(late.visits.contains(a))
            assertEquals(setOf(h.key("left"), h.key("right"), h.key("last")), h.delivered.map { it.method }.toSet())
        }
    }

    @Test fun `late subscriber and edge replay while an older wave is still queued`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val x = h.node("x")
            val first = h.owner("first"); val late = h.owner("late")
            h.link(first, a, x); h.terminal(first, x, "old")
            h.source(first, a, 4); h.flush(first)
            h.step() // a entered; x is now queued in this same wave.
            h.terminal(late, a, "new")
            h.flush(late)
            assertEquals(2, h.waveCount())
            h.drain(); h.assertOracle()
            assertEquals(setOf(h.key("old"), h.key("new")), h.delivered.map { it.method }.toSet())
        }
    }

    @Test fun `deferred notifications retain wave work until callbacks and receivers finish`() {
        for (discardOnCancellation in listOf(false, true)) {
            val steps = QueuedProtocolSteps()
            val statistics = StaticNeedReplayStatistics()
            ReplayNetwork(steps, statistics).use { h ->
                val a = h.node("a"); val owner = h.owner("one")
                h.terminal(owner, a, "p"); h.source(owner, a, 4); h.flush(owner)
                assertEquals(1L, statistics.snapshot().activeWorkWaves)
                assertFalse(steps.notifications.isEmpty())
                if (discardOnCancellation) {
                    h.f.ap.cancellation.cancel()
                    h.router.clear() // Joined cancellation abandons the deferred callback.
                    steps.notifications.clear(); steps.replays.clear()
                    assertEquals(1L, statistics.snapshot().abandonedActiveWorkWaves)
                } else {
                    steps.replayAll(); steps.runNotifications()
                    assertEquals(1L, statistics.snapshot().activeWorkWaves)
                    h.drain(); h.assertOracle()
                    assertEquals(0L, statistics.snapshot().abandonedActiveWorkWaves)
                }
                assertEquals(0L, statistics.snapshot().activeWorkWaves)
                assertEquals(0L, statistics.snapshot().unbalancedWaveWorkReleases)
            }
        }
    }

    @Test fun `owner reset cannot clear another owner and a late source after snapshot still routes`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b")
            val first = h.owner("first"); val second = h.owner("second")
            h.terminal(first, a, "left"); h.terminal(second, b, "right")
            h.flush(first); h.flush(second)
            h.source(first, a, 4); h.source(second, b, 9)
            h.flush(first)
            assertFalse(second.pending.isEmpty(), "Another owner cannot take this pending delta")
            h.drain()
            assertEquals(setOf(h.key("left")), h.delivered.map { it.method }.toSet())
            h.flush(second); h.drain(); h.assertOracle()
            h.source(second, b, 12); h.flush(second); h.drain(); h.assertOracle()
        }
    }

    @Test fun `source committed after full snapshot keeps its other owner delta and exact marks`() {
        val statistics = StaticNeedReplayStatistics()
        ReplayNetwork(statistics = statistics).use { h ->
            val a = h.node("a"); val b = h.node("b")
            val first = h.owner("first"); val second = h.owner("second")
            h.terminal(first, a, "left"); h.terminal(second, b, "right")
            // Only the second owner's graph flag is cleared. The first flush
            // must select the full committed source snapshot, not delta rows.
            h.flush(second)
            h.source(first, a, 4)
            val before = statistics.snapshot()
            var committedAfterSnapshot = false
            h.onDispatch = { visit ->
                assertEquals(a, visit.key)
                assertEquals(setOf(4), visit.marks.ids())
                assertTrue(first.pending.isEmpty(), "The first owner's batch was already taken")
                h.source(second, b, 9)
                committedAfterSnapshot = true
                assertFalse(second.pending.isEmpty(), "A source committed after the snapshot owns its delta")
            }
            h.flush(first)
            h.onDispatch = null
            assertTrue(committedAfterSnapshot)
            assertEquals(before.fullSourceSelections + 1, statistics.snapshot().fullSourceSelections)
            assertEquals(before.deltaSelections, statistics.snapshot().deltaSelections)
            assertEquals(listOf(a), h.dispatched.map { it.key }, "The completed full snapshot excludes the new source")
            assertEquals(setOf(9), h.router.sourcesSnapshot().getValue(b).ids(), "The new source is authoritative now")
            assertFalse(second.pending.isEmpty(), "Finishing another owner's flush cannot reset this delta")
            h.drain()
            assertEquals(setOf(h.key("left") to 4), h.delivered.map { it.method to it.mark }.toSet())
            assertFalse(second.pending.isEmpty())
            h.flush(second); h.drain()
            assertEquals(before.deltaSelections + 1, statistics.snapshot().deltaSelections)
            assertEquals(setOf(9), h.dispatched.single { it.key == b }.marks.ids())
            assertEquals(setOf(h.key("left") to 4, h.key("right") to 9),
                h.delivered.map { it.method to it.mark }.toSet())
            assertTrue(first.pending.isEmpty() && second.pending.isEmpty())
            h.assertOracle()
        }
    }

    @Test fun `committed Base R filters delivery without changing logical endpoint coverage`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val owner = h.owner("one")
            h.terminal(owner, a, "p")
            h.admitBase("p", 4)
            h.source(owner, a, 4, 9); h.flush(owner); h.drain()
            assertEquals(setOf(9), h.delivered.map { it.mark }.toSet())
            h.assertOracle()
        }
    }

    @Test fun `packed diagonal and ordinary changed origin routes share the same exact closure`() {
        ReplayNetwork().use { h ->
            val a = h.node("a"); val b = h.node("b"); val owner = h.owner("one")
            h.packed(owner, a, b.method)
            h.terminal(owner, StaticNeedKey(b.method, a.slot), "p")
            val otherField = h.node("g", field = "g")
            h.link(owner, a, otherField, excluded = true)
            h.terminal(owner, otherField, "q")
            h.source(owner, a, 4); h.flush(owner); h.drain()
            h.assertOracle()
            assertEquals(setOf(h.key("p"), h.key("q")), h.delivered.map { it.method }.toSet())
        }
    }
}

private fun RequestMarkSet.ids(): Set<Int> = buildSet { forEachId { add(it) } }

/** A scalar adjacency list is the oracle. Actual routing uses real subscription
 * directories, full owner keys and queued receiver callbacks, not that list. */
private class ReplayNetwork(private val protocol: ProtocolSteps = ProtocolSteps.Inline,
                            private val statistics: StaticNeedReplayStatistics? = null) : AutoCloseable {
    val f = PipelineFixtures(limit = 1, protocol = protocol)
    private val cls = f.ap.accessors.index(ClassStaticAccessor("C"))
    private val fields = HashMap<String, Run1Slot.StaticField>()
    private val keys = HashMap<String, MethodKey>()
    private val stores = HashMap<MethodKey, SummaryStorage>()
    private val events = ArrayDeque<() -> Unit>()
    private val owners = ArrayList<Owner>()
    private val graph = LinkedHashMap<StaticNeedKey, MutableSet<Target>>()
    private val sourceMarks = LinkedHashMap<StaticNeedKey, MutableSet<Int>>()
    private val admittedBase = LinkedHashSet<Endpoint>()
    val delivered = LinkedHashSet<Endpoint>()
    val dispatched = ArrayList<StaticNeedVisit>()
    val sourceChanges = ArrayList<Pair<StaticNeedKey, RequestMarkSet>>()
    var onDispatch: ((StaticNeedVisit) -> Unit)? = null
    val router = StaticNeedRouter(f.ap, { visit ->
        dispatched += visit
        onDispatch?.invoke(visit)
        storage(visit.key.method).notifyStaticNeed(visit)
    }, { key, marks -> sourceChanges += key to marks }, statistics)

    data class Endpoint(val method: MethodKey, val base: Run1Slot.Base, val mark: Int)
    private sealed interface Target {
        data class Static(val node: StaticNeedKey) : Target
        data class Base(val method: MethodKey, val slot: Run1Slot.Base) : Target
    }

    fun key(name: String): MethodKey = keys.getOrPut(name) { f.store.key(context = name) }
    fun node(name: String, field: String = "f") = StaticNeedKey(key(name), fields.getOrPut(field) {
        Run1Slot.StaticField(cls, f.store.field(field))
    })
    fun owner(name: String): Owner = Owner(name).also(owners::add)
    private fun storage(method: MethodKey) = stores.getOrPut(method) { SummaryStorage(method, f.ap, f.ops, protocol) }
    inner class Owner(val name: String) {
        val pending = PendingNeedReplay(f.ap, statistics)
        val visits = ArrayList<StaticNeedKey>()
        lateinit var manager: SubscriptionManager
            private set
        init { manager = SubscriptionManager(object : SubscriptionPort by f.port {
            override val staticNeedRouter get() = router
            override val pendingNeedReplay get() = pending
            override fun summaryStorage(key: MethodKey): SummaryStorage = storage(key)
            override fun post(event: RunEvent.StaticNeed): Boolean {
                events += { visits += event.visit.key; manager.onStaticNeed(event.visit) }
                return true
            }
            override fun post(event: RunEvent.ChangedRequests): Boolean {
                events += { manager.onRequests(event.callee, event.rows) }
                return true
            }
            override fun send(event: RunEvent): Boolean {
                when (event) {
                    is RunEvent.MarkRequestsIn -> for ((slot, marks) in event.rows) {
                        val base = assertIs<Run1Slot.Base>(slot)
                        marks.forEachId { delivered += Endpoint(event.method, base, it) }
                    }
                    is RunEvent.StaticEntriesIn -> Unit
                    else -> error("Routing cannot produce $event")
                }
                return true
            }
        }) }
    }

    private fun bind(owner: Owner, from: StaticNeedKey, original: Run1Slot, caller: MethodKey, excluded: Boolean = false) {
        val facts = f.store.fact(STATIC, from.slot.pathData.toList(), Tail.STAR,
            if (excluded) MarkSlot.Star(f.ap.markSetOf(TaintMark(4))) else MarkSlot.STAR,
            if (excluded) ExclusionSet.of(from.slot.field) else ExclusionSet.Empty)
        owner.manager.subscribe(f.subscription(facts, original.initial(f.ap), caller = caller, callee = from.method))
    }
    fun link(owner: Owner, from: StaticNeedKey, to: StaticNeedKey, excluded: Boolean = false) {
        graph.getOrPut(from, ::LinkedHashSet).add(Target.Static(to))
        bind(owner, from, to.slot, to.method, excluded)
    }
    fun terminal(owner: Owner, from: StaticNeedKey, name: String) {
        val base = Run1Slot.Base(AccessPathBase.Argument(0))
        graph.getOrPut(from, ::LinkedHashSet).add(Target.Base(key(name), base))
        bind(owner, from, base, key(name))
    }
    fun packed(owner: Owner, from: StaticNeedKey, caller: MethodKey) {
        graph.getOrPut(from, ::LinkedHashSet).add(Target.Static(StaticNeedKey(caller, from.slot)))
        owner.manager.subscribeStatic(StaticSubscription(from.method, caller, f.store.inst(1),
            StaticIdentityEntries(f.ops).single(from.slot.initial(f.ap))))
    }
    fun source(owner: Owner, node: StaticNeedKey, vararg marks: Int) {
        sourceMarks.getOrPut(node, ::LinkedHashSet).addAll(marks.toList())
        owner.pending.sourcesCommitted(router.commitSourceUnions(node.method, mapOf(node.slot to RequestMarkSet.fromIds(marks))))
    }
    fun admitBase(name: String, mark: Int) {
        val base = Run1Slot.Base(AccessPathBase.Argument(0))
        val pending = RequestStorage()
        pending.addAll(base, RequestMarkSet.of(TaintMark(mark)))
        storage(key(name)).commit(pending)
        admittedBase += Endpoint(key(name), base, mark)
    }
    fun flush(owner: Owner) = owner.pending.flush(router)
    fun step() { events.removeFirst().invoke() }
    fun drain() {
        var count = 0
        while (events.isNotEmpty()) {
            check(++count < 100_000) { "Finite replay did not close" }
            step()
        }
    }
    fun waveCount(): Int = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply {
        dispatched.forEach { add(it.wave) }
    }.size
    fun assertOracle() {
        val expected = LinkedHashSet<Endpoint>()
        for ((source, marks) in sourceMarks) {
            val seen = HashSet<StaticNeedKey>()
            val todo = ArrayDeque<StaticNeedKey>().apply { add(source) }
            while (todo.isNotEmpty()) {
                val node = todo.removeFirst()
                if (!seen.add(node)) continue
                for (target in graph[node].orEmpty()) when (target) {
                    is Target.Static -> todo.addLast(target.node)
                    is Target.Base -> marks.forEach { expected += Endpoint(target.method, target.slot, it) }
                }
            }
        }
        assertEquals(expected, (delivered + admittedBase).intersect(expected))
        assertTrue(delivered.all { it in expected }, "Routing invented an endpoint or mark")
    }
    override fun close() {
        owners.forEach { it.manager.freeze(); it.pending.clear() }
        router.clear()
        f.close()
    }
}
