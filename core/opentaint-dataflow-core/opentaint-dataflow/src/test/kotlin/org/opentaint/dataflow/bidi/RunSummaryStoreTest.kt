package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.RunSummaryStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RunSummaryStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val v = StoreValues(fixture)

    @Test
    fun `both raw and publication stores enforce K6 and multi-premise TAINT`(): Unit = with(v) {
        val concrete = premise()
        val abstract = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        val multiple = nd()
        val taint = pack(leaf(p(f)))
        val flow = pack(flow(p(f)))
        for ((key, value) in listOf(concrete to flow, abstract to taint, abstract to Reach.NORMAL,
            multiple to flow, multiple to Reach.NORMAL, multiple to Reach.DEMAND)) {
            val store = RunSummaryStore(m)
            assertFails { store.add(key, value) }
            assertFails { store.addDemand(key, value) }
            assertTrue(store.all().none())
            assertTrue(store.demandEdges().none())
        }
        val store = RunSummaryStore(m)
        for ((key, value) in listOf(concrete to taint, abstract to flow, multiple to taint)) {
            assertNotNull(store.add(key, value))
            assertNotNull(store.addDemand(key, value))
        }
    }

    @Test
    fun `raw full summaries and selected narrower publications remain separate in both insertion orders`(): Unit = with(v) {
        val key = nd()
        val rawLeaves = setOf(leaf(p(f), Tail.ANY_TAINT, e = ex(g)), leaf(p(h), mark = mu))
        val pieceLeaves = setOf(leaf(p(f)))
        for (rawFirst in listOf(false, true)) {
            val store = RunSummaryStore(m)
            val raw = pack(*rawLeaves.toTypedArray())
            val piece = pack(*pieceLeaves.toTypedArray())
            assertSame(raw, fixture.ops.demandPart(key, raw, Direction.FORWARD))
            if (rawFirst) {
                store.add(key, raw)
                store.addDemand(key, piece)
            } else {
                store.addDemand(key, piece)
                store.add(key, raw)
            }
            assertEquals(rawLeaves.map { key to it }.toSet(), observed(store.all()))
            assertEquals(pieceLeaves.map { key to it }.toSet(), observed(store.demandEdges()))
            assertNull(store.add(key, raw))
            assertNull(store.addDemand(key, piece))
            val nextRaw = leaf(p(g), mark = mu)
            assertEquals(setOf(nextRaw), decoded(assertNotNull(store.add(key, pack(nextRaw)))))
            assertEquals(pieceLeaves.map { key to it }.toSet(), observed(store.demandEdges()))
            val nextPiece = leaf(p(f, h), Tail.ANY_TAINT, e = ex(g))
            assertEquals(setOf(nextPiece), decoded(assertNotNull(store.addDemand(key, pack(nextPiece)))))
            assertEquals((rawLeaves + nextRaw).map { key to it }.toSet(), observed(store.all()))
            assertEquals((pieceLeaves + nextPiece).map { key to it }.toSet(), observed(store.demandEdges()))
        }
    }

    @Test
    fun `raw structural selection routes publications without changing raw layers or native records`(): Unit = with(v) {
        val keys = listOf(premise(), premise(tail = Tail.ANY_TAINT, e = ex(g)), nd(), m.zero)
        for (key in keys) for (layer in Layer.entries) for (direction in Direction.entries) {
            val store = RunSummaryStore(m)
            val rawLeaves = setOf(leaf(p(f), layer = layer), leaf(p(g), mark = mu, layer = layer))
            val raw = pack(*rawLeaves.toTypedArray())
            store.add(key, raw)
            val selected = layer == Layer.DEMAND || key is PremiseSet || (direction == Direction.BACKWARD && key.isZero)
            if (fixture.ops.demandPart(key, raw, direction) != null) store.addDemand(key, pack(leaf(p(f), layer = layer)))
            assertEquals(rawLeaves.map { key to it }.toSet(), observed(store.all()))
            assertEquals(if (selected) setOf(key to leaf(p(f), layer = layer)) else emptySet(), observed(store.demandEdges()))
        }
        val flowKey = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        val raw = pack(flow(p(f), e = ex(g)))
        val store = RunSummaryStore(m)
        store.add(flowKey, raw)
        assertNull(fixture.ops.demandPart(flowKey, raw, Direction.FORWARD))
        assertNull(fixture.ops.demandPart(flowKey, raw, Direction.BACKWARD))
        assertTrue(store.demandEdges().none())
        assertEquals(setOf(flowKey to flow(p(f), e = ex(g))), observed(store.all()))
    }

    @Test
    fun `REACH layers and complete premise tail exclusion keys stay independent in each collection`(): Unit = with(v) {
        val keys = listOf(m.zero, premise(), premise(tail = Tail.ANY), premise(tail = Tail.ANY_TAINT),
            premise(tail = Tail.ANY_TAINT, e = ex(g)), premise(tail = Tail.ANY_TAINT, e = ex(h)))
        val store = RunSummaryStore(m)
        for (key in keys) for (layer in Layer.entries) {
            assertSame(Reach.of(layer), store.add(key, Reach.of(layer)))
            assertSame(Reach.of(layer), store.addDemand(key, Reach.of(layer)))
            assertNull(store.add(key, Reach.of(layer)))
            assertNull(store.addDemand(key, Reach.of(layer)))
        }
        val expected: Set<Pair<PremiseKey, Facts>> = keys.flatMap { key -> Layer.entries.map { key to (Reach.of(it) as Facts) } }.toSet()
        assertEquals(expected, store.all().toSet())
        assertEquals(expected, store.demandEdges().toSet())
        assertTrue(RunSummaryStore(m).all().none())
    }

    @Test
    fun `T1 returns literal new-only deltas in raw and publication collections in either order`(): Unit = with(v) {
        val cases = listOf(
            Triple(premise(), listOf(leaf(p(f)), leaf(p(g), mark = mu)), listOf(leaf(p(f)), leaf(p(g)))),
            Triple(premise(tail = Tail.STAR, mark = MarkSlot.STAR), listOf(flow(p(f))), listOf(flow(p(f)), flow(p(g)))),
        )
        for ((key, left, right) in cases) for (order in listOf(listOf(left, right), listOf(right, left))) for (publication in listOf(false, true)) {
            val store = RunSummaryStore(m)
            fun add(leaves: List<Conclusion>): Facts? = if (publication) store.addDemand(key, pack(*leaves.toTypedArray()))
                else store.add(key, pack(*leaves.toTypedArray()))
            assertEquals(order[0].toSet(), decoded(assertNotNull(add(order[0]))))
            val expected = order[1].toSet() - order[0].toSet()
            val delta = add(order[1])
            if (expected.isEmpty()) assertNull(delta) else assertEquals(expected, decoded(assertNotNull(delta)))
            val values = if (publication) store.demandEdges() else store.all()
            assertEquals((left + right).map { key to it }.toSet(), observed(values))
            assertNull(add(left))
            assertNull(add(right))
            assertTrue((if (publication) store.all() else store.demandEdges()).none())
        }
    }

    @Test
    fun `T2 annotation changes return whole merged trees in both collections and insertion orders`(): Unit = with(v) {
        data class Case(val key: PremiseKey, val left: List<Conclusion>, val right: List<Conclusion>, val merged: Set<Conclusion>)
        val flowKey = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        val exact = leaf(p(h), mark = mu)
        val cases = listOf(
            Case(flowKey, listOf(flow(p(), e = ex(f, h))), listOf(flow(p(), e = ex(g, h))), setOf(flow(p(), e = ex(h)))),
            Case(flowKey, listOf(flow(p(), e = ex(h), mx = marks(t.id))), listOf(flow(p(), e = ex(h), mx = marks(u.id))),
                setOf(flow(p(), e = ex(h)))),
            Case(premise(), listOf(leaf(p(), Tail.ANY_TAINT, e = ex(f)), exact),
                listOf(leaf(p(), Tail.ANY_TAINT, e = ex(g)), exact), setOf(leaf(p(), Tail.ANY_TAINT), exact)),
        )
        for (case in cases) for (order in listOf(listOf(case.left, case.right), listOf(case.right, case.left)))
            for (publication in listOf(false, true)) {
                val store = RunSummaryStore(m)
                fun add(leaves: List<Conclusion>): Facts? = if (publication) store.addDemand(case.key, pack(*leaves.toTypedArray()))
                    else store.add(case.key, pack(*leaves.toTypedArray()))
                add(order[0])
                assertEquals(case.merged, decoded(assertNotNull(add(order[1]))))
                assertEquals(case.merged.map { case.key to it }.toSet(),
                    observed(if (publication) store.demandEdges() else store.all()))
                assertNull(add(case.left))
                assertNull(add(case.right))
            }
    }

    @Test
    fun `subsumption stays inside one collection premise kind and layer`(): Unit = with(v) {
        val key = premise()
        val general = leaf(p(f), Tail.ANY, layer = Layer.DEMAND)
        val specific = leaf(p(f, g), layer = Layer.DEMAND)
        val normal = leaf(p(f, g))
        for (publicationBroad in listOf(false, true)) {
            val store = RunSummaryStore(m)
            if (publicationBroad) {
                store.addDemand(key, pack(general))
                assertNotNull(store.add(key, pack(specific)))
                assertNull(store.addDemand(key, pack(specific)))
            } else {
                store.add(key, pack(general))
                assertNotNull(store.addDemand(key, pack(specific)))
                assertNull(store.add(key, pack(specific)))
            }
            assertNotNull(store.add(key, pack(normal)))
            assertNotNull(store.addDemand(key, pack(normal)))
            val raw = if (publicationBroad) setOf(specific, normal) else setOf(general, normal)
            val pieces = if (publicationBroad) setOf(general, normal) else setOf(specific, normal)
            assertEquals(raw.map { key to it }.toSet(), observed(store.all()))
            assertEquals(pieces.map { key to it }.toSet(), observed(store.demandEdges()))
        }
        val store = RunSummaryStore(m)
        val must = leaf(p(f), Tail.ANY_TAINT, e = ex(h))
        store.add(key, pack(must))
        assertNull(store.add(key, pack(leaf(p(f, g), Tail.ANY_TAINT, e = ex(h)))))
        assertNotNull(store.add(key, pack(normal)))
        val otherKey = premise(tail = Tail.ANY_TAINT, e = ex(g))
        assertNotNull(store.add(otherKey, pack(leaf(p(f, g), Tail.ANY_TAINT, e = ex(h)))))
    }

    @Test
    fun `deep summaries survive cache resets and preserve raw versus demand output layers`(): Unit = with(v) {
        val path = List(32768) { f }
        val raw = leaf(path, Tail.ANY_TAINT, e = ex(h))
        val piece = leaf(path, Tail.ANY, layer = Layer.DEMAND)
        val key = nd()
        val store = RunSummaryStore(m)
        assertEquals(setOf(raw), decoded(assertNotNull(store.add(key, pack(raw)))))
        assertEquals(setOf(piece), decoded(assertNotNull(store.addDemand(key, pack(piece)))))
        m.softRefs.cleanup()
        m.softRefs.enable()
        assertNull(store.add(key, pack(raw)))
        assertNull(store.addDemand(key, pack(piece)))
        val fresh = leaf(path.dropLast(1) + g, mark = mu)
        assertEquals(setOf(fresh), decoded(assertNotNull(store.add(key, pack(fresh)))))
        assertEquals(setOf(key to raw, key to fresh), observed(store.all()))
        assertEquals(setOf(key to piece), observed(store.demandEdges()))
    }

    @Test
    fun `cancelled mutation and both enumerations stop without changing either collection`(): Unit = with(v) {
        val key = premise()
        val first = leaf(p(f))
        val piece = leaf(p(g), layer = Layer.DEMAND)
        val store = RunSummaryStore(m)
        store.add(key, pack(first))
        store.addDemand(key, pack(piece))
        val next = pack(leaf(List(32768) { h }))
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { store.add(key, next) }
            assertFailsWith<Cancellation.Cancelled> { store.addDemand(key, next) }
            assertFailsWith<Cancellation.Cancelled> { store.all().toList() }
            assertFailsWith<Cancellation.Cancelled> { store.demandEdges().toList() }
        } finally {
            fixture.cancellation.activate()
        }
        assertEquals(setOf(key to first), observed(store.all()))
        assertEquals(setOf(key to piece), observed(store.demandEdges()))
    }
}
