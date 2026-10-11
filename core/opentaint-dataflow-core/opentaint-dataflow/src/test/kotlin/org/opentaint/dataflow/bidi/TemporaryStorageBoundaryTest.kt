package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** F74 permits action-local facts, but every durable hand-off rejects their base before mutation. */
class TemporaryStorageBoundaryTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val temp = AccessPathBase.CleanerTemporary()
    private val flowPremise = m.initial(fixture.pattern(tail = Tail.STAR, mark = MarkSlot.STAR))

    private fun premise(flow: Boolean): InitialAp = if (flow) flowPremise else m.zero
    private fun facts(base: AccessPathBase, flow: Boolean, layer: Layer): Facts = fixture.fact(
        base = base, tail = if (flow) Tail.STAR else Tail.EXACT,
        mark = if (flow) MarkSlot.STAR else MarkSlot.Concrete(fixture.mark("T")), layer = layer)

    @Test
    fun `initial factories reject every temporary premise while transient facts remain available`(): Unit {
        val path = m.path(listOf(fixture.field("f")))
        for (tail in Tail.entries) for (flow in listOf(false, true)) {
            if (flow && (tail == Tail.EXACT || tail == Tail.ANY_TAINT)) continue
            val mark = if (flow) MarkSlot.STAR else MarkSlot.Concrete(fixture.mark("T"))
            val pattern = fixture.pattern(base = temp, tail = tail, mark = mark)
            assertFailsWith<IllegalStateException> { m.initial(pattern) }
            assertFailsWith<IllegalStateException> { m.initial(temp, path, tail, ExclusionSet.Empty, mark) }
            assertFailsWith<IllegalStateException> { InitialAp(temp, path, tail, ExclusionSet.Empty, mark, 1000, m) }
        }
        for (flow in listOf(false, true)) for (layer in Layer.entries) {
            assertSame(temp, facts(temp, flow, layer).base)
        }
        assertSame(m.zero, m.initial(m.zero.toPattern()))
        assertSame(flowPremise, m.initial(flowPremise.toPattern()))
    }

    @Test
    fun `method edges reject temporary conclusions in both kinds and layers without storing them`(): Unit {
        val store = MethodEdgeStore(m, fixture.method, fixture.lm, 1)
        for (flow in listOf(false, true)) for (layer in Layer.entries) {
            assertFailsWith<IllegalStateException> {
                store.add(fixture.inst(1), premise(flow), facts(temp, flow, layer))
            }
            assertTrue(store.edgesAt(fixture.inst(1)).none())
        }
        val good = facts(AccessPathBase.Return, false, Layer.NORMAL)
        assertNotNull(store.add(fixture.inst(1), m.zero, good))
        assertEquals(listOf(m.zero to good), store.edgesAt(fixture.inst(1)).toList())
    }

    @Test
    fun `raw and publication summaries reject temporary conclusions before either collection changes`(): Unit {
        val store = RunSummaryStore(m)
        for (flow in listOf(false, true)) for (layer in Layer.entries) {
            val bad = facts(temp, flow, layer)
            assertFailsWith<IllegalStateException> { store.add(premise(flow), bad) }
            assertFailsWith<IllegalStateException> { store.addDemand(premise(flow), bad) }
            assertTrue(store.all().none())
            assertTrue(store.demandEdges().none())
        }
        val good = facts(AccessPathBase.Return, false, Layer.NORMAL)
        assertNotNull(store.add(m.zero, good))
        assertNotNull(store.addDemand(m.zero, good))
        assertEquals(listOf(m.zero to good), store.all().toList())
        assertEquals(listOf(m.zero to good), store.demandEdges().toList())
    }

    @Test
    fun `added links reject temporary values without adding an index or caller value`(): Unit {
        val store = AddedFactStore(m)
        for (flow in listOf(false, true)) for (layer in Layer.entries) {
            val ref = CallerRef(fixture.method, premise(flow), layer, fixture.inst(2))
            assertFailsWith<IllegalStateException> { store.add(ref, layer, facts(temp, flow, layer)) }
            assertTrue(store.links().none())
            assertTrue(store.overlapping(temp, null).none())
        }
        val ref = CallerRef(fixture.method, m.zero, Layer.NORMAL, fixture.inst(2))
        val good = facts(AccessPathBase.Return, false, Layer.NORMAL)
        assertNotNull(store.add(ref, Layer.NORMAL, good))
        assertEquals(1, store.links().count())
        assertEquals(1, store.overlapping(AccessPathBase.Return, null).count())
    }

    @Test
    fun `source hits reject either temporary endpoint and retain valid forward identities`(): Unit {
        val store = SourceHitStore()
        val source = PathEdge(m.zero.toPattern().fact, fixture.pattern(base = AccessPathBase.Return).fact,
            ExclusionSet.Empty)
        for (edge in listOf(source.copy(from = source.from.copy(base = temp)),
            source.copy(to = source.to.copy(base = temp)))) {
            assertFailsWith<IllegalStateException> { store.add(fixture.method, fixture.inst(3), edge) }
            assertTrue(store.entries().none())
        }
        assertTrue(store.add(fixture.method, fixture.inst(3), source))
        assertEquals(listOf(Triple(fixture.method, fixture.inst(3), source)), store.entries().toList())
    }

    @Test
    fun `native records reject transient conclusions before entry and exit indexes change`(): Unit {
        val store = PersistentRecordStore(m)
        for (flow in listOf(false, true)) {
            val initial = premise(flow)
            assertFailsWith<IllegalStateException> {
                store.add(Record(fixture.method, Direction.FORWARD, initial, facts(temp, flow, Layer.NORMAL)))
            }
            assertTrue(store.byEntry(fixture.method, initial.toPattern()).none())
            assertTrue(store.byExit(fixture.method, fixture.pattern(base = temp)).none())
        }
        val record = Record(fixture.method, Direction.FORWARD, m.zero,
            facts(AccessPathBase.Return, false, Layer.NORMAL))
        store.add(record)
        assertEquals(listOf(record), store.byEntry(fixture.method, m.zero.toPattern()).toList())
        assertEquals(listOf(record), store.byExit(fixture.method, fixture.pattern(base = AccessPathBase.Return)).toList())
    }

    @Test
    fun `demand hand off rejects temporary entry and exit patterns before retaining them`(): Unit {
        val builder = DemandStore.Builder(m)
        val entry = fixture.pattern(tail = Tail.ANY)
        val exit = fixture.pattern(base = AccessPathBase.Return, tail = Tail.ANY)
        for (bad in listOf(DemandPattern(entry.copy(fact = entry.fact.copy(base = temp)), exit),
            DemandPattern(entry, exit.copy(fact = exit.fact.copy(base = temp))))) {
            assertFailsWith<IllegalStateException> { builder.add(fixture.method, bad) }
        }
        val before = builder.build()
        assertTrue(before.near(fixture.method, temp, null).isEmpty())
        assertTrue(before.near(fixture.method, entry.fact.base, null).isEmpty())
        assertTrue(builder.add(fixture.method, DemandPattern(entry, exit)))
        assertEquals(listOf(DemandPattern(entry, exit)), builder.build().near(fixture.method, entry.fact.base, null))
    }
}
