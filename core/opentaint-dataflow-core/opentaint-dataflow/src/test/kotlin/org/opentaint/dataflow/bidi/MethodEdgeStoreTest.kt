package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.MethodEdgeStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Explicit leaves supply store expectations and the full reference scan for query matching. */
internal class StoreValues(val fixture: StoreFixtures) {
    val m = fixture.manager
    val x = AccessPathBase.Argument(0)
    val y = AccessPathBase.Return
    val f = fixture.field("f")
    val g = fixture.field("g")
    val h = fixture.field("h")
    val t = fixture.mark("T")
    val u = fixture.mark("U")
    val mt = MarkSlot.Concrete(t)
    val mu = MarkSlot.Concrete(u)
    fun ex(vararg ids: Int): ExclusionSet = ExclusionSet.of(ids)
    fun marks(vararg ids: Int): MarkSet = MarkSet(ids)
    fun p(vararg ids: Int): List<Int> = ids.toList()
    fun leaf(path: List<Int>, tail: Tail = Tail.EXACT, mark: MarkSlot = mt, layer: Layer = Layer.NORMAL,
             e: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Conclusion =
        Conclusion(PathFact(base, path, tail, mark), e, layer == Layer.DEMAND)
    fun flow(path: List<Int>, layer: Layer = Layer.NORMAL, e: ExclusionSet = ExclusionSet.Empty,
             mx: MarkSet = MarkSet.EMPTY): Conclusion = leaf(path, if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY,
        MarkSlot.Star(mx), layer, e)
    fun premise(path: List<Int> = p(), tail: Tail = Tail.EXACT, mark: MarkSlot = mt,
                e: ExclusionSet = ExclusionSet.Empty): InitialAp = m.initial(x, m.path(path), tail, e, mark)
    fun nd(): PremiseKey = m.premiseOf(listOf(premise(), premise(mark = mu)))
    fun pack(vararg leaves: Conclusion): Facts {
        require(leaves.isNotEmpty())
        val values = leaves.map { m.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        val first = values.first()
        require(values.all { it.base == first.base && it.layer == first.layer })
        return when (first) {
            is Reach -> first
            is FlowTree -> {
                require(values.all { it is FlowTree && it.exclusion == first.exclusion && it.markExclusion == first.markExclusion })
                m.flowTree(first.base, first.layer, first.exclusion, first.markExclusion,
                    values.drop(1).fold(first.root) { root, next -> FlowAlgebra.mergeAdd(root, (next as FlowTree).root) })
            }
            is TaintTree -> {
                val e = leaves.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
                require(leaves.filter { it.fact.tail == Tail.ANY_TAINT }.all { it.exclusion == e })
                m.taintTree(first.base, first.layer,
                    values.drop(1).fold(first.root) { root, next -> m.taintAlg.mergeAdd(root, (next as TaintTree).root) }, e)
            }
        }
    }
    fun decoded(facts: Facts): Set<Conclusion> = fixture.ops.leaves(facts).map {
        Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND)
    }.toSet()
    fun observed(entries: Sequence<Pair<PremiseKey, Facts>>): Set<Pair<PremiseKey, Conclusion>> =
        entries.flatMap { (premise, facts) -> decoded(facts).asSequence().map { premise to it } }.toSet()
}

class MethodEdgeStoreTest {
    private val fixture = StoreFixtures()
    private val v = StoreValues(fixture)
    private val m = fixture.manager
    private fun store(limit: Int = Int.MAX_VALUE): MethodEdgeStore = MethodEdgeStore(m, fixture.method, fixture.lm, limit)

    @Test
    fun `K6 and multi-premise TAINT guards reject invalid edges without adding values`(): Unit = with(v) {
        val store = store()
        val node = fixture.inst(1)
        val concrete = premise()
        val abstract = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        val multiple = nd()
        val taint = pack(leaf(p(f)))
        val flow = pack(flow(p(f)))
        for ((key, fact) in listOf(concrete to flow, abstract to taint, abstract to Reach.NORMAL,
            multiple to flow, multiple to Reach.NORMAL, multiple to Reach.DEMAND)) {
            assertFails { store.add(node, key, fact) }
            assertTrue(store.edgesAt(node).none())
        }
        assertNotNull(store.add(node, concrete, taint))
        assertNotNull(store.add(node, abstract, flow))
        assertNotNull(store.add(node, multiple, taint))
    }

    @Test
    fun `REACH bits keep every statement premise and layer separate`(): Unit = with(v) {
        val store = store()
        val keys = listOf(m.zero, premise(), premise(tail = Tail.ANY), premise(tail = Tail.ANY_TAINT, e = ex(g)))
        for (index in listOf(0, 3, fixture.maxIndex)) for (key in keys) for (layer in Layer.entries) {
            val reach = Reach.of(layer)
            assertSame(reach, store.add(fixture.inst(index), key, reach))
            assertNull(store.add(fixture.inst(index), key, reach))
        }
        for (index in listOf(0, 3, fixture.maxIndex)) {
            assertEquals(keys.flatMap { key -> Layer.entries.map { key to (Reach.of(it) as Facts) } }.toSet(),
                store.edgesAt(fixture.inst(index)).toSet())
            for (key in keys) assertEquals(Layer.entries.map { key to (Reach.of(it) as Facts) }.toSet(),
                store.edgesAt(fixture.inst(index), key).toSet())
        }
        assertTrue(store.edgesAt(fixture.inst(2)).none())
        assertTrue(store.edgesAt(fixture.inst(0), premise(p(h))).none())
    }

    @Test
    fun `statement and complete premise tail exclusion keys are independent`(): Unit = with(v) {
        val store = store()
        val keys = listOf(premise(), premise(tail = Tail.ANY), premise(tail = Tail.ANY_TAINT),
            premise(tail = Tail.ANY_TAINT, e = ex(g)), premise(tail = Tail.ANY_TAINT, e = ex(h)), nd())
        val leaf = leaf(p(f))
        val facts = pack(leaf)
        for (node in listOf(fixture.inst(0), fixture.inst(1))) for (key in keys) {
            assertEquals(setOf(leaf), decoded(assertNotNull(store.add(node, key, facts))))
            assertNull(store.add(node, key, facts))
            assertEquals(setOf(key to leaf), observed(store.edgesAt(node, key)))
        }
        assertEquals(keys.map { it to leaf }.toSet(), observed(store.edgesAt(fixture.inst(1))))
        val separateRun = MethodEdgeStore(m, fixture.key("other-context", 1), fixture.lm, 1)
        assertTrue(separateRun.edgesAt(fixture.inst(1)).none())
        assertNotNull(separateRun.add(fixture.inst(1), keys.first(), facts))
        assertEquals(keys.map { it to leaf }.toSet(), observed(store.edgesAt(fixture.inst(1))))
    }

    @Test
    fun `T1 returns only new literal leaves in both insertion orders`(): Unit = with(v) {
        val shapes = listOf(
            Triple(premise(), listOf(leaf(p(f)), leaf(p(g), mark = mu)), listOf(leaf(p(f)), leaf(p(g), mark = mt))),
            Triple(premise(tail = Tail.STAR, mark = MarkSlot.STAR), listOf(flow(p(f))), listOf(flow(p(f)), flow(p(g)))),
        )
        for ((key, left, right) in shapes) for (order in listOf(listOf(left, right), listOf(right, left))) {
            val store = store()
            val node = fixture.inst(1)
            assertEquals(order[0].toSet(), decoded(assertNotNull(store.add(node, key, pack(*order[0].toTypedArray())))))
            val delta = store.add(node, key, pack(*order[1].toTypedArray()))
            val expected = order[1].toSet() - order[0].toSet()
            if (expected.isEmpty()) assertNull(delta) else assertEquals(expected, decoded(assertNotNull(delta)))
            assertEquals((left + right).map { key to it }.toSet(), observed(store.edgesAt(node)))
            assertNull(store.add(node, key, pack(*left.toTypedArray())))
            assertNull(store.add(node, key, pack(*right.toTypedArray())))
        }
    }

    @Test
    fun `T2 flow and mark exclusion intersection returns the whole merged delta`(): Unit = with(v) {
        val key = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        val cases = listOf(
            Triple(listOf(flow(p(), e = ex(f, h)), flow(p(g), e = ex(f, h))),
                listOf(flow(p(), e = ex(g, h)), flow(p(g), e = ex(g, h))),
                setOf(flow(p(), e = ex(h)), flow(p(g), e = ex(h)))),
            Triple(listOf(flow(p(), e = ex(h), mx = marks(t.id))), listOf(flow(p(), e = ex(h), mx = marks(u.id))),
                setOf(flow(p(), e = ex(h)))),
        )
        for ((left, right, merged) in cases) for (order in listOf(listOf(left, right), listOf(right, left))) {
            val store = store()
            val node = fixture.inst(1)
            store.add(node, key, pack(*order[0].toTypedArray()))
            assertEquals(merged, decoded(assertNotNull(store.add(node, key, pack(*order[1].toTypedArray())))))
            assertEquals(merged.map { key to it }.toSet(), observed(store.edgesAt(node)))
            assertNull(store.add(node, key, pack(*left.toTypedArray())))
            assertNull(store.add(node, key, pack(*right.toTypedArray())))
        }
    }

    @Test
    fun `T2 must exclusion intersection returns old exact leaves in the merged delta`(): Unit = with(v) {
        val key = premise()
        val exact = leaf(p(h), mark = mu)
        val left = listOf(leaf(p(), Tail.ANY_TAINT, e = ex(f)), exact)
        val right = listOf(leaf(p(), Tail.ANY_TAINT, e = ex(g)), exact)
        val merged = setOf(leaf(p(), Tail.ANY_TAINT), exact)
        for (order in listOf(listOf(left, right), listOf(right, left))) {
            val store = store()
            val node = fixture.inst(1)
            store.add(node, key, pack(*order[0].toTypedArray()))
            assertEquals(merged, decoded(assertNotNull(store.add(node, key, pack(*order[1].toTypedArray())))))
            assertEquals(merged.map { key to it }.toSet(), observed(store.edgesAt(node)))
        }
    }

    @Test
    fun `incoming subsumption follows normal must demand any and FLOW mark rows in either order`(): Unit = with(v) {
        data class Case(val key: PremiseKey, val general: Conclusion, val specific: Conclusion)
        val cases = listOf(
            Case(premise(), leaf(p(f), Tail.ANY_TAINT, e = ex(h)), leaf(p(f, g), Tail.ANY_TAINT, e = ex(h))),
            Case(premise(), leaf(p(f), Tail.ANY_TAINT, e = ex(h)), leaf(p(f, g))),
            Case(premise(), leaf(p(f), Tail.ANY_TAINT, e = ex(h)), leaf(p(f, h), Tail.ANY_TAINT, e = ex(h))),
            Case(premise(), leaf(p(f), Tail.ANY_TAINT, e = ex(h)), leaf(p(f, g), Tail.ANY_TAINT, mu, e = ex(h))),
            Case(premise(), leaf(p(f), Tail.ANY, layer = Layer.DEMAND), leaf(p(f, g), layer = Layer.DEMAND)),
            Case(premise(), leaf(p(f), Tail.ANY, layer = Layer.DEMAND), leaf(p(f, g), Tail.ANY, layer = Layer.DEMAND)),
            Case(premise(tail = Tail.STAR, mark = MarkSlot.STAR), flow(p(f), Layer.DEMAND, mx = marks(u.id)),
                flow(p(f, g), Layer.DEMAND, mx = marks(t.id, u.id))),
            Case(premise(tail = Tail.STAR, mark = MarkSlot.STAR), flow(p(f)), flow(p(f, g))),
        )
        for (case in cases) for (order in listOf(listOf(case.general, case.specific), listOf(case.specific, case.general))) {
            val store = store()
            val node = fixture.inst(1)
            store.add(node, case.key, pack(order[0]))
            val dropped = subsumes(order[0], order[1])
            val delta = store.add(node, case.key, pack(order[1]))
            if (dropped) assertNull(delta) else assertEquals(setOf(order[1]), decoded(assertNotNull(delta)))
            val expected = if (dropped) setOf(order[0]) else order.toSet()
            assertEquals(expected.map { case.key to it }.toSet(), observed(store.edgesAt(node)))
        }
        val store = store()
        val key = premise()
        val normal = leaf(p(f))
        val demand = leaf(p(), Tail.ANY, layer = Layer.DEMAND)
        store.add(fixture.inst(1), key, pack(demand))
        assertEquals(setOf(normal), decoded(assertNotNull(store.add(fixture.inst(1), key, pack(normal)))))
    }

    @Test
    fun `pattern query matches full independent scans and returns whole stored values`(): Unit = with(v) {
        val store = store()
        val node = fixture.inst(1)
        val rows = listOf(
            premise() to listOf(leaf(p(f)), leaf(p(f, g)), leaf(p(h))),
            premise(p(f)) to listOf(leaf(p(), Tail.ANY_TAINT, e = ex(g)), leaf(p(f, h), Tail.ANY_TAINT, e = ex(g))),
            premise(p(g)) to listOf(leaf(p(f), Tail.ANY, layer = Layer.DEMAND), leaf(p(h), layer = Layer.DEMAND)),
            premise(p(h)) to listOf(leaf(p(f), base = y)),
            premise(p(), Tail.STAR, MarkSlot.STAR) to listOf(flow(p(), e = ex(g)), flow(p(f, h), e = ex(g))),
            premise(p(f), Tail.STAR, MarkSlot.STAR) to listOf(flow(p(f), Layer.DEMAND), flow(p(h), Layer.DEMAND)),
            m.zero to listOf(leaf(p(), mark = MarkSlot.Concrete(TaintMark.ZERO), base = AccessPathBase.Zero)),
        )
        for ((key, leaves) in rows) store.add(node, key, pack(*leaves.toTypedArray()))
        for (base in listOf(x, y, AccessPathBase.Zero)) for (path in listOf(p(), p(f), p(f, g), p(f, h), p(g), p(h), p(h, f)))
            for (tail in Tail.entries) for (e in listOf(ExclusionSet.Empty, ex(g))) {
                val query = Pattern(PathFact(base, path, tail, mu), e)
                val expected = rows.filter { (_, leaves) -> leaves.any { overlap(Pattern(it.fact, it.exclusion), query) } }
                    .flatMap { (key, leaves) -> leaves.map { key to it } }.toSet()
                assertEquals(expected, observed(store.edgesAt(node, query)), "$base $path $tail $e")
            }
        assertTrue(store.edgesAt(fixture.inst(2), fixture.pattern(path = p(f))).none())
    }

    @Test
    fun `positive field limit and exact 32768 counted depth assertions enforce W3`(): Unit = with(v) {
        for (limit in listOf(0, -1, Int.MIN_VALUE)) assertFailsWith<IllegalArgumentException> { store(limit) }
        val classAccessor = m.accessors.index(ClassStaticAccessor("Box"))
        val elementAccessor = m.accessors.index(ElementAccessor)
        val oneField = store(1)
        assertNotNull(oneField.add(fixture.inst(1), premise(), pack(leaf(p(classAccessor, f)))))
        assertFailsWith<AssertionError> {
            oneField.add(fixture.inst(1), premise(), pack(leaf(p(classAccessor, f, elementAccessor))))
        }
        val path = List(32768) { f }
        val facts = pack(leaf(path))
        val key = premise()
        val accepted = store(32768)
        assertNotNull(accepted.add(fixture.inst(1), key, facts))
        assertEquals(setOf(key to leaf(path)), observed(accepted.edgesAt(fixture.inst(1), fixture.pattern(path = path))))
        val rejected = store(32767)
        assertFailsWith<AssertionError> { rejected.add(fixture.inst(1), key, facts) }
        assertTrue(rejected.edgesAt(fixture.inst(1)).none())
        val abstract = premise(tail = Tail.STAR, mark = MarkSlot.STAR)
        assertFailsWith<AssertionError> { rejected.add(fixture.inst(1), abstract, pack(flow(path))) }
    }

    @Test
    fun `soft cache resets keep stored content duplicates and later deltas unchanged`(): Unit = with(v) {
        val store = store()
        val key = premise()
        val path = List(160) { f }
        val first = leaf(path)
        val second = leaf(path.dropLast(1) + g, mark = mu)
        store.add(fixture.inst(1), key, pack(first))
        m.softRefs.cleanup()
        m.softRefs.enable()
        assertNull(store.add(fixture.inst(1), key, pack(first)))
        assertEquals(setOf(second), decoded(assertNotNull(store.add(fixture.inst(1), key, pack(first, second)))))
        assertEquals(setOf(key to first, key to second), observed(store.edgesAt(fixture.inst(1))))
    }

    @Test
    fun `cancelled insertion and both query forms stop before changing stored values`(): Unit = with(v) {
        val store = store()
        val key = premise()
        val first = leaf(p(f))
        store.add(fixture.inst(1), key, pack(first))
        val next = pack(leaf(List(32768) { g }))
        fixture.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { store.add(fixture.inst(1), key, next) }
            assertFailsWith<Cancellation.Cancelled> { store.add(fixture.inst(1), m.zero, Reach.NORMAL) }
            assertFailsWith<Cancellation.Cancelled> { store.edgesAt(fixture.inst(1)).toList() }
            assertFailsWith<Cancellation.Cancelled> { store.edgesAt(fixture.inst(1), fixture.pattern()).toList() }
        } finally {
            fixture.cancellation.activate()
        }
        assertEquals(setOf(key to first), observed(store.edgesAt(fixture.inst(1))))
    }

    @Test
    fun `cancellation between normal and demand REACH stops key specific and all key iterators`(): Unit = with(v) {
        for (keySpecific in listOf(true, false)) {
            val store = store()
            val node = fixture.inst(1)
            store.add(node, m.zero, Reach.NORMAL)
            store.add(node, m.zero, Reach.DEMAND)
            val iterator = (if (keySpecific) store.edgesAt(node, m.zero) else store.edgesAt(node)).iterator()
            val first = iterator.next()
            assertSame(m.zero, first.first)
            assertSame(Reach.NORMAL, first.second)
            fixture.cancellation.cancel()
            try {
                assertFailsWith<Cancellation.Cancelled>("keySpecific=$keySpecific") { iterator.next() }
            } finally {
                fixture.cancellation.activate()
            }
            assertEquals(setOf(m.zero to Reach.NORMAL, m.zero to Reach.DEMAND), store.edgesAt(node).toSet())
        }
    }
}
