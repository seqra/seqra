package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.ZERO_FACT
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Candidate indexes are checked before and after the exact per-path operation. */
class DemandStoreTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val z = AccessPathBase.Argument(1)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("fresh")
    private val absent = fixture.field("absent")
    private val cls = m.accessors.index(ClassStaticAccessor("Box"))
    private val element = m.accessors.index(ElementAccessor)
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val fresh = fixture.mark("Fresh")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val star = MarkSlot.STAR
    private val empty = ExclusionSet.Empty
    private val zero = DemandPattern(m.zero.toPattern(), null)
    private val methods = listOf(fixture.key("one"), fixture.key("two"), fixture.key("one", 1))
    private val missing = fixture.key("missing", 2)
    private fun ex(vararg ids: Int) = ExclusionSet.of(ids)
    private fun marks(vararg ids: Int) = MarkSet(ids)
    private fun p(path: List<Int> = emptyList(), tail: Tail = Tail.ANY, mark: MarkSlot = mt,
                  exclusion: ExclusionSet = empty, base: AccessPathBase = x) =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun d(path: List<Int>, tail: Tail = Tail.ANY, mark: MarkSlot = mt,
                  exit: Pattern? = p(listOf(g), mark = mu, base = y), base: AccessPathBase = x) =
        DemandPattern(p(path, tail, mark, base = base), exit)
    private fun pathPrefixes(a: List<Int>, b: List<Int>): Boolean =
        a.size <= b.size && a.indices.all { a[it] == b[it] }
    private fun patterns(): List<DemandPattern> {
        val result = ArrayList<DemandPattern>()
        val paths = listOf(emptyList(), listOf(f), listOf(f, g), listOf(f, h), listOf(g), listOf(h),
            listOf(cls), listOf(cls, f), listOf(element), listOf(element, f))
        for (path in paths) for ((tail, mark) in listOf(Tail.EXACT to mt, Tail.ANY to mt,
            Tail.EXACT to mu, Tail.ANY to mu, Tail.ANY to star)) {
            val exits = listOf<Pattern?>(null, p(mark = star, base = y),
                p(listOf(f), Tail.STAR, star, ex(g), y),
                p(listOf(f), Tail.STAR, mt, ex(h), y),
                p(listOf(cls, f), Tail.STAR, mu, ex(h), STATIC),
                p(listOf(element), mark = star, base = y),
                p(listOf(g), mark = mu, base = x)) +
                if (mark is MarkSlot.Concrete) listOf(p(listOf(f), Tail.EXACT, mu, base = y)) else emptyList()
            for (exit in exits) result += d(path, tail, mark, exit)
        }
        result += d(listOf(f), mark = mt, base = y)
        result += d(listOf(cls), mark = star, base = STATIC)
        result += DemandPattern(m.zero.toPattern(), p(listOf(f), Tail.EXACT, mt, base = y))
        return result.distinct()
    }
    private fun dataset(): Map<MethodKey, List<DemandPattern>> {
        val all = patterns()
        return methods.mapIndexed { index, method -> method to all.filterIndexed { position, _ ->
            index == 0 || position % 3 == index - 1
        } }.toMap()
    }
    private fun built(data: Map<MethodKey, List<DemandPattern>>): DemandStore {
        val builder = DemandStore.Builder(m)
        for ((method, demands) in data) for (demand in demands) assertTrue(builder.add(method, demand))
        return builder.build()
    }
    private fun queries() = listOf(emptyList(), listOf(f), listOf(f, g), listOf(f, h), listOf(f, absent),
        listOf(f, g, f), listOf(g), listOf(h), listOf(absent), listOf(cls), listOf(cls, f),
        listOf(element), listOf(element, f))

    @Test
    fun `near and covering preserve all indexed payloads and every path relation`() {
        val data = dataset()
        val store = built(data)
        for (method in methods + missing) for (base in listOf(x, y, z, STATIC, AccessPathBase.Zero)) for (path in queries()) {
            val stored = data[method].orEmpty()
            val expectedNear = stored.filter { it.entry.fact.base == base &&
                (pathPrefixes(it.entry.fact.path, path) || pathPrefixes(path, it.entry.fact.path)) }.toSet() +
                if (base == AccessPathBase.Zero) setOf(zero) else emptySet()
            val expectedCovering = stored.filter { it.entry.fact.base == base && pathPrefixes(it.entry.fact.path, path) }.toSet()
            assertEquals(expectedNear, store.near(method, p(path, base = base)).toSet(), "near: $method, $base, $path")
            assertEquals(expectedCovering, store.covering(method, if (base == AccessPathBase.Zero) m.zero else m.initial(p(path, base = base))).toSet(), "covering: $method, $base, $path")
        }
    }

    @Test
    fun `near followed by emission equals a full demand scan for all added tail mark and E cells`() {
        val data = dataset()
        val store = built(data)
        val added = ArrayList<Pattern>()
        for (base in listOf(x, y, STATIC)) for (path in queries()) {
            for (mark in listOf(mt, mu, MarkSlot.Concrete(fresh))) {
                added += p(path, Tail.EXACT, mark, base = base)
                added += p(path, Tail.ANY, mark, base = base)
                for (e in listOf(empty, ex(f), ex(g))) added += p(path, Tail.ANY_TAINT, mark, e, base)
            }
            for (mark in listOf(star, MarkSlot.Star(marks(u.id)))) {
                added += p(path, Tail.ANY, mark, base = base)
                for (e in listOf(empty, ex(f), ex(g))) added += p(path, Tail.STAR, mark, e, base)
            }
        }
        added += m.zero.toPattern()
        for (method in methods + missing) for (a in added) {
            val full = data[method].orEmpty() + zero
            val expected = full.mapNotNull { emit(it.entry, a) }.toSet()
            val candidates = store.near(method, a)
            val indexed = candidates.mapNotNull { emit(it.entry, a) }.toSet()
            assertEquals(expected, indexed, "$method, added=$a")
        }
    }

    @Test
    fun `covering followed by whole premise restriction equals a complete annotated scan`() {
        val data = dataset()
        val store = built(data)
        val initials = ArrayList<InitialAp>()
        for (base in listOf(x, y, STATIC)) for (path in queries()) {
            for ((tail, mark) in listOf(Tail.STAR to star, Tail.ANY to star, Tail.EXACT to mt,
                Tail.ANY to mt, Tail.EXACT to mu, Tail.ANY to mu))
                initials += m.initial(p(path, tail, mark, base = base))
            initials += m.initial(p(path, Tail.ANY_TAINT, mt, ex(g), base))
        }
        initials += m.zero
        val conclusions = ArrayList<Conclusion>()
        for (base in listOf(x, y, STATIC)) for (path in listOf(emptyList(), listOf(f), listOf(f, g), listOf(g), listOf(cls, f), listOf(element))) {
            for (mark in listOf(mt, mu, MarkSlot.Concrete(fresh))) {
                conclusions += Conclusion(PathFact(base, path, Tail.EXACT, mark), empty, false)
                conclusions += Conclusion(PathFact(base, path, Tail.EXACT, mark), empty, true)
                conclusions += Conclusion(PathFact(base, path, Tail.ANY, mark), empty, true)
                conclusions += Conclusion(PathFact(base, path, Tail.ANY_TAINT, mark), ex(g), false)
            }
            conclusions += Conclusion(PathFact(base, path, Tail.STAR, MarkSlot.Star(marks(u.id))), ex(g), false)
            conclusions += Conclusion(PathFact(base, path, Tail.ANY, MarkSlot.Star(marks(u.id))), empty, true)
        }
        conclusions += Conclusion(m.zero.toPattern().fact, empty, false)
        for (method in methods + missing) for (initial in initials) {
            val full = data[method].orEmpty() + zero
            val candidates = store.covering(method, initial)
            for (conclusion in conclusions) {
                val premise = initial.toPattern()
                val expected = full.mapNotNull { restrict(premise, conclusion, it) }.toSet()
                val indexed = candidates.mapNotNull { restrict(premise, conclusion, it) }.toSet()
                assertEquals(expected, indexed, "$method, premise=$premise, conclusion=$conclusion")
            }
        }
    }

    @Test
    fun `every method has implicit zero near and no implicit exit-bearing covering pattern`() {
        val builder = DemandStore.Builder(m)
        for (method in methods + missing) assertFalse(builder.add(method, zero))
        val storedZero = DemandPattern(m.zero.toPattern(), p(listOf(f), Tail.EXACT, mt, base = y))
        assertTrue(builder.add(methods[0], storedZero))
        val store = builder.build()
        for (method in methods + missing) {
            val stored = if (method == methods[0]) setOf(storedZero) else emptySet()
            assertEquals(stored + zero, store.near(method, m.zero.toPattern()).toSet())
            assertEquals(stored + zero, store.near(method, Pattern(ZERO_FACT.copy(path = listOf(f)), empty)).toSet())
            assertEquals(stored, store.covering(method, m.zero).toSet())
            assertEquals(setOf(m.zero.toPattern()), store.near(method, m.zero.toPattern())
                .mapNotNull { emit(it.entry, m.zero.toPattern()) }.toSet())
            assertTrue(store.near(method, p(base = z)).isEmpty())
        }
    }

    @Test
    fun `deduplication is exact and separated by method context entry path tails marks and exit payload`() {
        val builder = DemandStore.Builder(m)
        val original = d(listOf(f))
        assertTrue(builder.add(methods[0], original))
        assertFalse(builder.add(fixture.key("one"), original.copy(entry = original.entry.copy(
            fact = original.entry.fact.copy(path = arrayListOf(f))))))
        val variants = listOf(
            original.copy(entry = p(listOf(f), Tail.EXACT, mt)),
            original.copy(entry = p(listOf(f), Tail.ANY, mu)),
            original.copy(entry = p(listOf(f), Tail.ANY, star)),
            original.copy(exit = null), original.copy(exit = p(listOf(g), Tail.EXACT, mu, base = y)),
            original.copy(exit = p(listOf(g), Tail.STAR, mu, ex(f), y)),
            original.copy(exit = p(listOf(g), Tail.STAR, mu, ex(h), y)),
            original.copy(exit = p(listOf(g), Tail.ANY, mt, base = y)),
            original.copy(exit = p(listOf(h), Tail.ANY, mu, base = y)),
            original.copy(exit = p(listOf(g), Tail.ANY, mu, base = x)),
            original.copy(entry = p(listOf(f, g), Tail.ANY, mt)))
        for (variant in variants) {
            assertTrue(builder.add(methods[0], variant))
            assertFalse(builder.add(methods[0], variant))
        }
        for (method in methods.drop(1)) assertTrue(builder.add(method, original))
        val store = builder.build()
        assertEquals((variants + original).toSet(), store.near(methods[0], p(base = x)).toSet())
        for (method in methods.drop(1)) assertEquals(setOf(original), store.near(method, p(base = x)).toSet())
        assertTrue(store.near(missing, p(base = x)).isEmpty())
    }

    @Test
    fun `builder rejects unnormalized marks must tails STAR entries malformed field E and unpaired exact exits`() {
        val builder = DemandStore.Builder(m)
        val legal = d(listOf(f))
        val badEntries = listOf(
            p(listOf(f), Tail.ANY_TAINT, mt),
            p(listOf(f), Tail.ANY, MarkSlot.Star(marks(t.id))),
            p(listOf(f), Tail.STAR, star), p(listOf(f), Tail.STAR, mt),
            p(listOf(f), Tail.STAR, mt, ex(g)), p(listOf(f), Tail.EXACT, star),
            p(listOf(f), Tail.ANY, mt, ex(g)), p(listOf(f), Tail.EXACT, mt, ex(g)))
        for (entry in badEntries) assertFails { builder.add(methods[0], legal.copy(entry = entry)) }
        for (exit in listOf(p(listOf(g), Tail.ANY_TAINT, mu, base = y),
            p(listOf(g), Tail.ANY, MarkSlot.Star(marks(t.id)), base = y),
            p(listOf(g), Tail.EXACT, mu, ex(f), y), p(listOf(g), Tail.ANY, mu, ex(f), y)))
            assertFails { builder.add(methods[0], legal.copy(exit = exit)) }
        assertFails { builder.add(methods[0], DemandPattern(p(listOf(f), Tail.ANY, star),
            p(listOf(g), Tail.EXACT, mu, base = y))) }
        for (entry in listOf(p(listOf(f), Tail.EXACT, mt), p(listOf(f), Tail.ANY, mu), p(listOf(f), Tail.ANY, star))) {
            val exit = if (entry.fact.mark is MarkSlot.Concrete) p(listOf(g), Tail.EXACT, mu, base = y)
                else p(listOf(g), Tail.STAR, star, ex(h), y)
            assertTrue(builder.add(methods[0], DemandPattern(entry, exit)))
        }
        assertEquals(3, builder.build().near(methods[0], p(base = x)).toSet().size)
    }

    @Test
    fun `caller path mutation before build does not corrupt the stored key or exact deduplication`() {
        val builder = DemandStore.Builder(m)
        val entryPath = arrayListOf(f)
        val exitPath = arrayListOf(g)
        val demand = DemandPattern(p(entryPath, Tail.ANY, mt), p(exitPath, Tail.ANY, mu, base = y))
        val expected = d(listOf(f), exit = p(listOf(g), Tail.ANY, mu, base = y))
        assertTrue(builder.add(methods[0], demand))
        entryPath[0] = h
        exitPath[0] = f
        assertFalse(builder.add(methods[0], expected))
        val store = builder.build()
        assertEquals(setOf(expected), store.near(methods[0], p(listOf(f), base = x)).toSet())
        assertTrue(store.near(methods[0], p(listOf(h), base = x)).isEmpty())
        assertEquals(setOf(expected), store.covering(methods[0], m.initial(p(listOf(f, g), base = x))).toSet())
    }

    @Test
    fun `built snapshots keep owned paths and indexes across later caller and builder changes`() {
        val builder = DemandStore.Builder(m)
        val entryPath = arrayListOf(f)
        val exitPath = arrayListOf(g)
        val first = DemandPattern(p(entryPath, Tail.EXACT, mt), p(exitPath, Tail.ANY, mu, base = y))
        val expected = d(listOf(f), Tail.EXACT, mt, p(listOf(g), Tail.ANY, mu, base = y))
        assertTrue(builder.add(methods[0], first))
        val old = builder.build()
        entryPath += h
        exitPath.clear()
        assertEquals(setOf(expected), old.near(methods[0], p(base = x)).toSet())
        val next = d(listOf(h), mark = mu)
        assertTrue(builder.add(methods[0], next))
        assertTrue(builder.add(methods[1], d(listOf(f), mark = star, exit = null)))
        val newer = builder.build()
        assertEquals(setOf(expected), old.near(methods[0], p(base = x)).toSet())
        assertTrue(old.near(methods[1], p(base = x)).isEmpty())
        assertEquals(setOf(expected, next), newer.near(methods[0], p(base = x)).toSet())
        assertTrue(builder.add(methods[0], d(listOf(g), exit = null)))
        assertEquals(setOf(expected, next), newer.near(methods[0], p(base = x)).toSet())
        assertEquals(setOf(expected), old.covering(methods[0], m.initial(p(listOf(f), base = x))).toSet())
    }

    @Test
    fun `query payload path casts cannot change stored semantics`() {
        val expected = d(listOf(f), exit = p(listOf(g), Tail.ANY, mu, base = y))
        val builder = DemandStore.Builder(m)
        val source = DemandPattern(p(arrayListOf(f)), p(arrayListOf(g), mark = mu, base = y))
        assertTrue(builder.add(methods[0], source))
        val store = builder.build()
        fun attempt(path: List<Int>) {
            val mutable = path as? MutableList<Int> ?: return
            try { mutable[0] = h } catch (_: UnsupportedOperationException) { }
        }
        val near = store.near(methods[0], p(listOf(f), base = x)).single()
        attempt(near.entry.fact.path)
        attempt(requireNotNull(near.exit).fact.path)
        assertEquals(setOf(expected), store.near(methods[0], p(listOf(f), base = x)).toSet())
        val covering = store.covering(methods[0], m.initial(p(listOf(f, g), base = x))).single()
        attempt(covering.entry.fact.path)
        attempt(requireNotNull(covering.exit).fact.path)
        assertEquals(setOf(expected), store.covering(methods[0], m.initial(p(listOf(f, g), base = x))).toSet())
        assertTrue(store.near(methods[0], p(listOf(h), base = x)).isEmpty())
    }

    @Test
    fun `deep entry indexes are iterative and observe cancellation on long queries`() {
        val depth = 32768
        val path = List(depth) { f }
        val root = d(emptyList(), exit = null)
        val deep = d(path, exit = p(listOf(g), Tail.STAR, mu, ex(h), y))
        val builder = DemandStore.Builder(m)
        assertTrue(builder.add(methods[0], root))
        assertTrue(builder.add(methods[0], deep))
        val store = builder.build()
        assertEquals(setOf(root, deep), store.near(methods[0], p(path, base = x)).toSet())
        assertEquals(setOf(root, deep), store.covering(methods[0], m.initial(p(path, base = x))).toSet())
        assertEquals(setOf(root), store.covering(methods[0], m.initial(p(base = x))).toSet())
        assertEquals(setOf(root, deep), store.near(methods[0], p(base = x)).toSet())
        fixture.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { store.near(methods[0], p(path, base = x)) }
        assertFailsWith<Cancellation.Cancelled> { store.covering(methods[0], m.initial(p(path, base = x))) }
    }
}
