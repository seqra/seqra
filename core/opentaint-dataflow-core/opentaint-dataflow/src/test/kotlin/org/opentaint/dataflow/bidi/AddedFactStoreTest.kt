package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.store.AddedFactStore
import org.opentaint.dataflow.bidi.store.AddedFactAccumulator
import org.opentaint.dataflow.bidi.store.CallerRef
import org.opentaint.dataflow.bidi.store.Link
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AddedFactStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("fresh")
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private fun p(vararg a: Int) = a.toList()
    private fun ex(vararg a: Int) = ExclusionSet.of(a)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun pattern(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                        exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x) =
        fixture.pattern(base, path, tail, mark, exclusion)
    private fun typed(a: Pattern, layer: Layer = Layer.NORMAL) = m.factsOf(a.fact, a.exclusion, layer)
    private fun caller(context: String = "A", premise: InitialAp = m.initial(pattern(p(), Tail.EXACT)),
                       layer: Layer = Layer.NORMAL, call: Int = 10, entry: Int = 0) =
        CallerRef(fixture.key(context, entry), premise, layer, fixture.inst(call))

    @Test
    fun `indexed links equal complete scans across bases tails layers exclusions and failed branches`() {
        val store = AddedFactStore(m)
        val expected = LinkedHashSet<Link>()
        val paths = listOf(p(), p(f), p(g), p(f, g), p(f, g, h), p(f, h), p(g, f), p(h), p(cls, f))
        val refs = listOf(caller(), caller("B", layer = Layer.DEMAND, call = 11))
        for (base in listOf(x, y)) for (path in paths) for (layer in Layer.entries) {
            val shapes = buildList {
                for (mark in listOf(mt, mu)) {
                    add(pattern(path, Tail.EXACT, mark, base = base))
                    for (e in if (layer == Layer.NORMAL) listOf(ExclusionSet.Empty, ex(f), ex(g)) else listOf(ExclusionSet.Empty)) {
                        add(pattern(path, if (layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY, mark, e, base))
                    }
                }
                for (mx in listOf(MarkSet.EMPTY, marks(t.id), marks(u.id))) {
                    for (e in if (layer == Layer.NORMAL) listOf(ExclusionSet.Empty, ex(f), ex(g)) else listOf(ExclusionSet.Empty)) {
                        add(pattern(path, if (layer == Layer.NORMAL) Tail.STAR else Tail.ANY, MarkSlot.Star(mx), e, base))
                    }
                }
            }
            for (shape in shapes) for (ref in refs) {
                assertNotNull(store.add(ref, layer, typed(shape, layer)))
                expected += Link(shape, layer, ref)
            }
        }
        for (layer in Layer.entries) {
            val zero = fixture.pattern(AccessPathBase.Zero, mark = MarkSlot.Concrete(TaintMark.ZERO))
            val ref = caller(premise = m.zero)
            assertNotNull(store.add(ref, layer, Reach.of(layer)))
            expected += Link(zero, layer, ref)
        }
        assertEquals(expected, store.links().toSet())
        val queries = paths + listOf(p(f, g, h, g), p(f, g, g), p(h, h), p(cls, h), p(cls, f, g))
        for (base in listOf(x, y, STATIC, AccessPathBase.Zero)) for (path in queries) {
            val query = pattern(path, Tail.STAR, MarkSlot.STAR, base = base)
            val actual = store.overlapping(base, m.path(path)).toList()
            assertEquals(expected.filter { overlap(it.addedFact, query) }.toSet(), actual.toSet(), "$base $path")
            assertEquals(actual.toSet().size, actual.size, "one link per matching leaf/caller: $base $path")
        }
    }

    @Test
    fun `exact key merging returns only new payload leaves and duplicate inputs return null`() {
        val store = AddedFactStore(m)
        val ref = caller()
        val first = pattern(p(f), Tail.EXACT)
        val original = typed(first)
        assertSame(original, store.add(ref, Layer.NORMAL, original))
        val next = taint(listOf(first, pattern(p(f), Tail.EXACT, mu), pattern(p(f, g), Tail.EXACT)))
        val delta = assertNotNull(store.add(ref, Layer.NORMAL, next))
        assertEquals(setOf(pattern(p(f), Tail.EXACT, mu), pattern(p(f, g), Tail.EXACT)), decode(delta))
        val expected = setOf(first, pattern(p(f), Tail.EXACT, mu), pattern(p(f, g), Tail.EXACT))
        assertEquals(expected, store.links().map { it.addedFact }.toSet())
        assertNull(store.add(ref.copy(), Layer.NORMAL, next))
        assertNull(store.add(ref, Layer.NORMAL, original))
        assertEquals(expected, store.links().map { it.addedFact }.toSet())
        assertEquals(3, store.overlapping(x, m.path(p(f))).count())
    }

    @Test
    fun `accumulator keeps the admitted must key when an exact delta clears its exclusion`(): Unit {
        val accumulator = AddedFactAccumulator(m)
        val ref = caller()
        val must = pattern(p(), Tail.ANY_TAINT, exclusion = ex(g))
        val exact = pattern(p(f), Tail.EXACT, mu)
        val first = assertNotNull(accumulator.add(ref, Layer.NORMAL, typed(must)))
        val mixed = taint(listOf(must, exact), ex(g))
        val second = assertNotNull(accumulator.add(ref, Layer.NORMAL, mixed))
        assertEquals(first.handle, second.handle)
        assertEquals(ex(g), second.snapshot.group.exclusion)
        assertEquals(ExclusionSet.Empty, second.delta.groupKey.exclusion)
        assertEquals(setOf(exact), decode(second.delta))
        assertEquals(setOf(must, exact), decode(second.snapshot.facts))
        assertEquals(setOf(must), decode(first.snapshot.facts), "An old update keeps its immutable value")
        assertNull(accumulator.add(ref, Layer.NORMAL, mixed))

        val exactGroup = assertNotNull(accumulator.add(ref, Layer.NORMAL, typed(exact)))
        assertTrue(first.handle != exactGroup.handle, "The Empty-E group is a separate admitted input")
        assertEquals(2, accumulator.size)
        assertEquals(setOf(must, exact), decode(accumulator.snapshot(first.handle).facts))
        assertEquals(setOf(exact), decode(accumulator.snapshot(exactGroup.handle).facts))
    }

    @Test
    fun `indexed wrapper preserves cumulative must annotations after exact-only growth`(): Unit {
        val store = AddedFactStore(m)
        val ref = caller()
        val must = pattern(p(), Tail.ANY_TAINT, exclusion = ex(g))
        val exact = pattern(p(f), Tail.EXACT, mu)
        assertNotNull(store.add(ref, Layer.NORMAL, typed(must)))
        assertEquals(setOf(exact), decode(assertNotNull(store.add(ref, Layer.NORMAL, taint(listOf(must, exact), ex(g))))))
        assertEquals(setOf(Link(must, Layer.NORMAL, ref), Link(exact, Layer.NORMAL, ref)), store.links().toSet())
        assertEquals(setOf(Link(must, Layer.NORMAL, ref), Link(exact, Layer.NORMAL, ref)),
            store.overlapping(x, m.path(p(f))).toSet())
        assertEquals(setOf(Link(must, Layer.NORMAL, ref)), store.overlapping(x, m.path(p(h))).toSet())
    }

    @Test
    fun `added links retain covered descendants and never perform subsumption or annotation folding`() {
        val cases = listOf(
            listOf(pattern(p(), Tail.ANY), pattern(p(g), Tail.EXACT), pattern(p(g), Tail.ANY)) to Layer.DEMAND,
            listOf(pattern(p(), Tail.ANY_TAINT), pattern(p(g), Tail.ANY_TAINT), pattern(p(g), Tail.EXACT)) to Layer.NORMAL,
            listOf(pattern(p(), Tail.ANY, MarkSlot.STAR), pattern(p(g), Tail.ANY, MarkSlot.STAR)) to Layer.DEMAND,
            listOf(pattern(p(f), Tail.STAR, MarkSlot.STAR, ex(f)), pattern(p(f), Tail.STAR, MarkSlot.STAR, ex(g))) to Layer.NORMAL,
            listOf(pattern(p(f), Tail.ANY_TAINT, mt, ex(f)), pattern(p(f), Tail.ANY_TAINT, mt, ex(g))) to Layer.NORMAL,
            listOf(pattern(p(f), Tail.STAR, MarkSlot.Star(marks(t.id))),
                pattern(p(f), Tail.STAR, MarkSlot.Star(marks(u.id))), pattern(p(f), Tail.STAR, MarkSlot.STAR)) to Layer.NORMAL,
        )
        for ((leaves, layer) in cases) for (order in listOf(leaves, leaves.reversed())) {
            val store = AddedFactStore(m)
            val ref = caller()
            for (leaf in order) assertEquals(setOf(leaf), decode(assertNotNull(store.add(ref, layer, typed(leaf, layer)))))
            assertEquals(leaves.toSet(), store.links().map { it.addedFact }.toSet())
            for (leaf in order) assertNull(store.add(ref, layer, typed(leaf, layer)))
        }
    }

    @Test
    fun `every distinct caller context entry premise layer and call receives its own delta`() {
        val store = AddedFactStore(m)
        val ref = caller()
        val refs = listOf(ref, caller("B"), caller(entry = 1),
            caller(premise = m.initial(pattern(p(), Tail.EXACT, mu))), caller(layer = Layer.DEMAND), caller(call = 11))
        assertEquals(refs.size, refs.toSet().size)
        val leaf = pattern(p(f), Tail.EXACT)
        val added = typed(leaf)
        for (origin in refs) assertSame(added, store.add(origin, Layer.NORMAL, added))
        val expected = refs.map { Link(leaf, Layer.NORMAL, it) }.toSet()
        assertEquals(expected, store.links().toSet())
        assertEquals(expected, store.overlapping(x, m.path(p(f))).toSet())
        for (origin in refs) assertNull(store.add(origin.copy(), Layer.NORMAL, added))
        assertEquals(expected, store.links().toSet())
    }

    @Test
    fun `link layers are independent of caller layers and must exclusions remain on normal links`() {
        val store = AddedFactStore(m)
        val ref = caller(layer = Layer.NORMAL)
        val normal = pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g))
        val demand = pattern(p(f), Tail.ANY)
        assertNotNull(store.add(ref, Layer.NORMAL, typed(normal)))
        assertNotNull(store.add(ref, Layer.DEMAND, typed(demand, Layer.DEMAND)))
        assertEquals(setOf(Link(normal, Layer.NORMAL, ref), Link(demand, Layer.DEMAND, ref)), store.links().toSet())
        assertFails { store.add(ref, Layer.NORMAL, typed(demand, Layer.DEMAND)) }
        assertFails { store.add(ref, Layer.DEMAND, typed(normal)) }
        assertEquals(2, store.links().count())
    }

    @Test
    fun `overlap tests the first continuation only and preserves exact at or below leaves`() {
        val store = AddedFactStore(m)
        val ref = caller()
        val leaves = listOf(pattern(p(), Tail.ANY_TAINT, mt, ex(f)),
            pattern(p(g), Tail.ANY_TAINT, mu, ex(f)), pattern(p(g, g, f), Tail.EXACT),
            pattern(p(g, f), Tail.EXACT), pattern(p(f), Tail.EXACT))
        for (leaf in leaves) store.add(ref, Layer.NORMAL, typed(leaf))
        assertEquals(setOf(leaves[0], leaves[1], leaves[2]),
            store.overlapping(x, m.path(p(g, g, f))).map { it.addedFact }.toSet())
        assertEquals(setOf(leaves[0], leaves[3]),
            store.overlapping(x, m.path(p(g, f))).map { it.addedFact }.toSet())
        assertEquals(setOf(leaves[4]), store.overlapping(x, m.path(p(f))).map { it.addedFact }.toSet())
        assertTrue(store.overlapping(x, m.path(p(f, g))).none())
    }

    @Test
    fun `deep indexed links remain queryable and cancellation stops store walks`() {
        val depth = 32768
        val path = List(depth) { f }
        val leaf = pattern(path, Tail.EXACT)
        val store = AddedFactStore(m)
        val ref = caller()
        assertNotNull(store.add(ref, Layer.NORMAL, typed(leaf)))
        assertEquals(listOf(Link(leaf, Layer.NORMAL, ref)), store.overlapping(x, m.path(path)).toList())
        assertEquals(listOf(Link(leaf, Layer.NORMAL, ref)), store.overlapping(x, m.path(path.dropLast(1))).toList())
        fixture.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { store.add(ref, Layer.NORMAL, Reach.NORMAL) }
        assertFailsWith<Cancellation.Cancelled> { store.overlapping(x, null).toList() }
        assertFailsWith<Cancellation.Cancelled> { store.links().toList() }
    }

    @Test
    fun `cancellation between leaves of the same stored key stops both link iterators`() {
        for (overlapping in listOf(false, true)) {
            val fresh = StoreFixtures()
            val store = AddedFactStore(fresh.manager)
            val ref = CallerRef(fresh.method, fresh.manager.initial(fresh.pattern()), Layer.NORMAL, fresh.inst(10))
            val paths = listOf(listOf(fresh.field("first")), listOf(fresh.field("second")))
            val expected = paths.map { Link(fresh.pattern(path = it), Layer.NORMAL, ref) }.toSet()
            for (path in paths) assertNotNull(store.add(ref, Layer.NORMAL, fresh.fact(path = path)))
            val iterator = (if (overlapping) store.overlapping(x, null) else store.links()).iterator()
            assertTrue(iterator.next() in expected)
            fresh.cancellation.cancel()
            assertFailsWith<Cancellation.Cancelled>("overlapping=$overlapping") { iterator.next() }

            fresh.cancellation.activate()
            assertEquals(expected, store.links().toSet())
            assertEquals(expected, store.overlapping(x, null).toSet())
        }
    }

    private class Prefix {
        val exact = sortedSetOf<Int>()
        val any = sortedSetOf<Int>()
        val children = sortedMapOf<Int, Prefix>()
    }
    private fun taint(leaves: List<Pattern>, exclusion: ExclusionSet = ExclusionSet.Empty): TaintTree {
        val root = Prefix()
        for (leaf in leaves) {
            var at = root
            for (a in leaf.fact.path) at = at.children.getOrPut(a) { Prefix() }
            val id = (leaf.fact.mark as MarkSlot.Concrete).mark.id
            if (leaf.fact.tail == Tail.EXACT) at.exact += id else at.any += id
        }
        fun build(at: Prefix): TaintNode = m.taintAlg.node(m.taintLeaves(MarkSet(at.exact.toIntArray()), MarkSet(at.any.toIntArray())),
            at.children.keys.toIntArray(), at.children.values.map(::build).toTypedArray())
        return m.taintTree(x, Layer.NORMAL, build(root), exclusion)
    }
    private fun decode(facts: Facts): Set<Pattern> {
        val result = LinkedHashSet<Pattern>()
        fun <P : TrieLeaf> walk(root: TrieNode<P>, consume: (List<Int>, P) -> Unit) {
            val pending = ArrayDeque<Pair<List<Int>, TrieNode<P>>>()
            pending.addLast(p() to root)
            while (pending.isNotEmpty()) {
                val (path, at) = pending.removeLast()
                if (!at.leaf.isEmpty) consume(path, at.leaf)
                val accessors = at.accessors ?: continue
                for (index in accessors.indices) pending.addLast((path + accessors[index]) to at.children!![index])
            }
        }
        when (facts) {
            is Reach -> result += fixture.pattern(AccessPathBase.Zero, mark = MarkSlot.Concrete(TaintMark.ZERO))
            is FlowTree -> walk(facts.root) { path, _ -> result += pattern(path,
                if (facts.layer == Layer.NORMAL) Tail.STAR else Tail.ANY, MarkSlot.Star(facts.markExclusion), facts.exclusion, facts.base) }
            is TaintTree -> walk(facts.root) { path, payload ->
                for (id in payload.exact.ids) result += pattern(path, Tail.EXACT, MarkSlot.Concrete(TaintMark(id)), base = facts.base)
                for (id in payload.any.ids) result += pattern(path,
                    if (facts.layer == Layer.NORMAL) Tail.ANY_TAINT else Tail.ANY,
                    MarkSlot.Concrete(TaintMark(id)), facts.exclusion, facts.base)
            }
        }
        return result
    }
}
