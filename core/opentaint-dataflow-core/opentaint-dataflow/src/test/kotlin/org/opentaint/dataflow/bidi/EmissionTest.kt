package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.ElementAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The added leaves are explicit test data. Expected emission and starts use the raw reference operations. */
class EmissionTest {
    private val manager = ApManager(Cancellation())
    private val ops = ApOps(manager)
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val static = AccessPathBase.ClassStatic
    private val f = manager.accessors.index(FieldAccessor("Box", "f", "Box"))
    private val g = manager.accessors.index(FieldAccessor("Box", "g", "Box"))
    private val h = manager.accessors.index(FieldAccessor("Box", "h", "Box"))
    private val c = manager.accessors.index(ClassStaticAccessor("Box"))
    private val element = manager.accessors.index(ElementAccessor)
    private val t = manager.marks.mark("T")
    private val u = manager.marks.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val star = MarkSlot.STAR
    private data class Input(val facts: Facts, val leaves: List<Conclusion>)

    private fun p(vararg accessors: Int): List<Int> = accessors.toList()
    private fun ex(vararg accessors: Int): ExclusionSet = ExclusionSet.of(accessors)
    private fun marks(vararg ids: Int): MarkSet = MarkSet(ids)
    private fun pattern(path: List<Int>, tail: Tail, mark: MarkSlot = mt,
                        exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Pattern =
        Pattern(PathFact(base, path, tail, mark), exclusion)
    private fun value(path: List<Int>, tail: Tail, mark: MarkSlot = mt, demand: Boolean = false,
                      exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Conclusion =
        Conclusion(PathFact(base, path, tail, mark), exclusion, demand)

    private fun pack(leaves: List<Conclusion>): Input {
        require(leaves.isNotEmpty())
        val typed = leaves.map { manager.factsOf(it.fact, it.exclusion, if (it.demand) Layer.DEMAND else Layer.NORMAL) }
        val first = typed.first()
        require(typed.all { it.base == first.base && it.layer == first.layer })
        val facts = when (first) {
            is Reach -> first
            is FlowTree -> {
                require(typed.all { it is FlowTree && it.exclusion == first.exclusion && it.markExclusion == first.markExclusion })
                val root = typed.drop(1).fold(first.root) { root, next -> FlowAlgebra.mergeAdd(root, (next as FlowTree).root) }
                manager.flowTree(first.base, first.layer, first.exclusion, first.markExclusion, root)
            }
            is TaintTree -> {
                require(typed.all { it is TaintTree })
                val root = typed.drop(1).fold(first.root) { root, next -> manager.taintAlg.mergeAdd(root, (next as TaintTree).root) }
                val exclusion = leaves.firstOrNull { it.fact.tail == Tail.ANY_TAINT }?.exclusion ?: ExclusionSet.Empty
                require(leaves.filter { it.fact.tail == Tail.ANY_TAINT }.all { it.exclusion == exclusion })
                manager.taintTree(first.base, first.layer, root, exclusion)
            }
        }
        return Input(facts, leaves)
    }

    private fun flow(paths: List<List<Int>>, demand: Boolean = false, mx: MarkSet = MarkSet.EMPTY,
                     exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = x): Input = pack(paths.map {
        value(it, if (demand) Tail.ANY else Tail.STAR, MarkSlot.Star(mx), demand, exclusion, base)
    })

    private fun decoded(facts: Facts): Set<Conclusion> = ops.leaves(facts).map {
        Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND)
    }.toSet()

    private fun check(input: Input, demand: DemandPattern, name: String = ""): List<InitialAp> {
        val expected = input.leaves.mapNotNull { emit(demand.entry, Pattern(it.fact, it.exclusion)) }.toSet()
        val before = decoded(input.facts)
        val actual = ops.emit(demand, input.facts)
        assertEquals(expected, actual.map { it.toPattern() }.toSet(), "$name emitted patterns")
        assertEquals(actual.size, actual.toSet().size, "$name duplicate initial facts")
        assertEquals(before, decoded(input.facts), "$name changed added input")
        return actual
    }

    @Test
    fun `canonical emission matches full reference patterns for every added kind layer geometry and entry mark`() {
        val paths = listOf(p(), p(f), p(f, g), p(f, g, h), p(f, h), p(h))
        val entries = listOf(pattern(p(f, g), Tail.ANY, star),
            pattern(p(f, g), Tail.EXACT), pattern(p(f, g), Tail.ANY),
            pattern(p(f, g), Tail.EXACT, mu), pattern(p(f, g), Tail.ANY, mu))
        val exits = listOf(null, pattern(p(), Tail.ANY, mt, base = y), pattern(p(h), Tail.ANY, star, base = y))
        for (path in paths) {
            val inputs = listOf(pack(listOf(value(path, Tail.EXACT))),
                pack(listOf(value(path, Tail.EXACT, demand = true))), pack(listOf(value(path, Tail.EXACT, mu))),
                pack(listOf(value(path, Tail.ANY_TAINT))),
                pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
                pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(h)))),
                pack(listOf(value(path, Tail.ANY, demand = true))),
                flow(listOf(path), mx = marks(u.id), exclusion = ex(g)),
                flow(listOf(path), demand = true, mx = marks(u.id)))
            for ((index, input) in inputs.withIndex()) for (entry in entries) for (exit in exits)
                check(input, DemandPattern(entry, exit), "$path input $index entry=${entry.fact.tail}/${entry.fact.mark}")
        }
    }

    @Test
    fun `policy gives one canonical root FLOW premise per nonzero base and zero stays zero`() {
        for (base in listOf(x, y, static)) {
            val inputs = listOf(pack(listOf(value(p(c, f), Tail.EXACT, base = base))),
                pack(listOf(value(p(f), Tail.ANY_TAINT, exclusion = ex(g), base = base))),
                pack(listOf(value(p(element, f), Tail.ANY, mu, demand = true, base = base))),
                flow(listOf(p(c, f), p(g)), mx = marks(u.id), exclusion = ex(h), base = base))
            var first: InitialAp? = null
            for (input in inputs) {
                val actual = ops.policy(input.facts)
                assertEquals(policy(Pattern(input.leaves.first().fact, input.leaves.first().exclusion)), actual.toPattern())
                assertEquals(pattern(p(), Tail.STAR, star, base = base), actual.toPattern())
                if (first == null) first = actual else assertSame(first, actual)
                assertEquals(setOf(startFact(actual.toPattern())), decoded(ops.startFact(actual)))
                assertEquals(input.leaves.toSet(), decoded(input.facts))
            }
        }
        for (layer in Layer.entries) {
            val zero = ops.zero(layer)
            assertSame(Reach.of(layer), zero)
            assertSame(manager.zero, ops.policy(zero))
            assertSame(Reach.NORMAL, ops.startFact(ops.policy(zero)))
        }
    }

    @Test
    fun `startFact matches every legal initial tail mark and exclusion without a mode parameter`() {
        for (base in listOf(x, static)) for (path in listOf(p(), p(c), p(f, element))) {
            val entries = listOf(pattern(path, Tail.STAR, star, ex(g), base), pattern(path, Tail.ANY, star, base = base),
                pattern(path, Tail.STAR, mt, ex(g), base), pattern(path, Tail.ANY, mt, base = base),
                pattern(path, Tail.EXACT, mt, base = base), pattern(path, Tail.ANY_TAINT, mt, ex(g), base),
                pattern(path, Tail.ANY_TAINT, mu, base = base))
            for (entry in entries) {
                val initial = manager.initial(entry)
                val actual = ops.startFact(initial)
                assertEquals(setOf(startFact(entry)), decoded(actual))
                assertEquals(entry, initial.toPattern())
                if (entry.fact.mark is MarkSlot.Star) assertIs<FlowTree>(actual) else assertIs<TaintTree>(actual)
            }
        }
        assertSame(Reach.NORMAL, ops.startFact(manager.zero))
    }

    @Test
    fun `abstract ANY entry shares one normal FLOW premise across marks added layers and exits`() {
        val input = pack(buildList {
            for (mark in listOf(mt, mu)) {
                add(value(p(f), Tail.EXACT, mark))
                add(value(p(f), Tail.ANY_TAINT, mark, exclusion = ex(g)))
                add(value(p(f, h), Tail.EXACT, mark))
                add(value(p(f, h), Tail.ANY_TAINT, mark, exclusion = ex(g)))
            }
        })
        val demandInput = pack(input.leaves.map { leaf ->
            value(leaf.fact.path, if (leaf.fact.tail == Tail.ANY_TAINT) Tail.ANY else Tail.EXACT, leaf.fact.mark, demand = true)
        })
        val abstractAdded = flow(listOf(p(), p(f), p(f, h)), mx = marks(t.id, u.id), exclusion = ex(g))
        val entry = pattern(p(f), Tail.ANY, star)
        val expected = pattern(p(f), Tail.STAR, star)
        val exits = listOf(null, pattern(p(), Tail.ANY, mt, base = y),
            pattern(p(h), Tail.ANY, mu, base = y), pattern(p(f, h), Tail.ANY, star, base = y))
        var shared: InitialAp? = null
        for (added in listOf(input, demandInput, abstractAdded)) for (exit in exits) {
            val initial = check(added, DemandPattern(entry, exit)).single()
            assertEquals(expected, initial.toPattern())
            if (shared == null) shared = initial else assertSame(shared, initial)
            val start = assertIs<FlowTree>(ops.startFact(initial))
            assertEquals(Layer.NORMAL, start.layer)
            assertEquals(ExclusionSet.Empty, start.exclusion)
            assertEquals(MarkSet.EMPTY, start.markExclusion)
            assertEquals(setOf(value(p(f), Tail.STAR, star)), decoded(start))
        }
    }

    @Test
    fun `concrete entry emits multiple exact and must premises with distinct own exclusions`() {
        val input = pack(listOf(value(p(), Tail.ANY_TAINT, exclusion = ex(g)),
            value(p(f), Tail.ANY_TAINT, exclusion = ex(g)), value(p(f), Tail.EXACT),
            value(p(f, h), Tail.ANY_TAINT, exclusion = ex(g)), value(p(f, h), Tail.EXACT),
            value(p(f, g), Tail.EXACT), value(p(h), Tail.EXACT), value(p(f), Tail.EXACT, mu)))
        val demand = DemandPattern(pattern(p(f), Tail.ANY), pattern(p(), Tail.EXACT, mu, base = y))
        val initial = check(input, demand)
        val expected = setOf(pattern(p(f), Tail.ANY_TAINT), pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g)),
            pattern(p(f), Tail.EXACT), pattern(p(f, h), Tail.ANY_TAINT, exclusion = ex(g)),
            pattern(p(f, h), Tail.EXACT), pattern(p(f, g), Tail.EXACT))
        assertEquals(expected, initial.map { it.toPattern() }.toSet())
        val byPattern = initial.associateBy { it.toPattern() }
        val empty = byPattern.getValue(pattern(p(f), Tail.ANY_TAINT))
        val excluded = byPattern.getValue(pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g)))
        assertNotSame(empty, excluded)
        assertEquals(setOf(startFact(empty.toPattern())), decoded(ops.startFact(empty)))
        assertEquals(setOf(startFact(excluded.toPattern())), decoded(ops.startFact(excluded)))
        for (again in ops.emit(demand, input.facts)) assertSame(byPattern.getValue(again.toPattern()), again)
        val demandAdded = pack(input.leaves.map {
            value(it.fact.path, if (it.fact.tail == Tail.ANY_TAINT) Tail.ANY else Tail.EXACT, it.fact.mark, demand = true)
        })
        val may = check(demandAdded, demand).single { it.pathArray.toList() == p(f) && it.tail == Tail.ANY }
        assertNotSame(empty, may)
        assertEquals(Layer.NORMAL, ops.startFact(empty).layer)
        assertEquals(Layer.DEMAND, ops.startFact(may).layer)
    }

    @Test
    fun `concrete entry rejects abstract added marks and other concrete marks before producing an initial`() {
        for (input in listOf(flow(listOf(p(), p(f), p(f, g)), mx = MarkSet.EMPTY),
            flow(listOf(p(), p(f), p(f, g)), mx = marks(t.id)),
            flow(listOf(p(), p(f), p(f, g)), demand = true),
            pack(listOf(value(p(f), Tail.EXACT, mu))),
            pack(listOf(value(p(), Tail.ANY_TAINT, mu))))) {
            for (tail in listOf(Tail.EXACT, Tail.ANY))
                assertTrue(check(input, DemandPattern(pattern(p(f), tail), null)).isEmpty())
        }
    }

    @Test
    fun `must added meet keeps own E only at its own path and checks the first step above it`() {
        val original = value(p(f), Tail.ANY_TAINT, exclusion = ex(g))
        val input = pack(listOf(original))
        val cases = listOf(
            pattern(p(f), Tail.ANY) to setOf(pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g))),
            pattern(p(f), Tail.EXACT) to setOf(pattern(p(f), Tail.EXACT)),
            pattern(p(f, h), Tail.ANY) to setOf(pattern(p(f, h), Tail.ANY_TAINT)),
            pattern(p(f, h), Tail.EXACT) to setOf(pattern(p(f, h), Tail.EXACT)),
            pattern(p(f, g, h), Tail.ANY) to emptySet(),
            pattern(p(f, h, g), Tail.ANY) to setOf(pattern(p(f, h, g), Tail.ANY_TAINT)),
            pattern(p(), Tail.ANY) to setOf(pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g))),
        )
        for ((entry, expected) in cases) assertEquals(expected,
            check(input, DemandPattern(entry, null)).map { it.toPattern() }.toSet())
    }

    @Test
    fun `generic representable STAR entry cells preserve reference exclusion and start semantics`() {
        // These total-function cells are outside the stronger canonical typed demand-entry guard.
        for (entryMark in listOf(star, mt)) for (entryE in listOf(ExclusionSet.Empty, ex(g))) {
            val entry = pattern(p(f), Tail.STAR, entryMark, entryE)
            val inputs = listOf(pack(listOf(value(p(f), Tail.EXACT))),
                pack(listOf(value(p(f), Tail.ANY, demand = true))),
                pack(listOf(value(p(f, h), Tail.ANY_TAINT, exclusion = ex(g)))),
                flow(listOf(p(), p(f), p(f, h), p(f, g)), mx = marks(u.id), exclusion = ex(h)))
            for (input in inputs) for (initial in check(input, DemandPattern(entry, null)))
                assertEquals(setOf(startFact(initial.toPattern())), decoded(ops.startFact(initial)))
        }
    }

    @Test
    fun `invalid must entry and unrepresentable generic abstract exact result fail without widening`() {
        val input = pack(listOf(value(p(f), Tail.EXACT)))
        for (base in listOf(x, y)) assertFails {
            ops.emit(DemandPattern(pattern(p(f), Tail.ANY_TAINT, exclusion = ex(g), base = base), null), input.facts)
        }
        val abstractExact = pattern(p(f), Tail.EXACT, star)
        assertEquals(abstractExact, emit(abstractExact, Pattern(input.leaves.single().fact, ExclusionSet.Empty)))
        assertFails { ops.emit(DemandPattern(abstractExact, null), input.facts) }
        val must = pack(listOf(value(p(f), Tail.ANY_TAINT, exclusion = ex(g))))
        val concreteStar = DemandPattern(pattern(p(f), Tail.STAR), null)
        assertFails { ops.emit(concreteStar, must.facts) }
        assertTrue(ops.emit(DemandPattern(pattern(p(f, g), Tail.STAR), null), must.facts).isEmpty())
    }

    @Test
    fun `zero static and different bases keep their canonical emission identity`() {
        val zeroPattern = Pattern(PathFact(AccessPathBase.Zero, p(), Tail.EXACT, MarkSlot.Concrete(TaintMark.ZERO)), ExclusionSet.Empty)
        val zeroDemand = DemandPattern(zeroPattern, zeroPattern)
        for (layer in Layer.entries) {
            val input = pack(listOf(Conclusion(zeroPattern.fact, zeroPattern.exclusion, layer == Layer.DEMAND)))
            assertSame(manager.zero, check(input, zeroDemand).single())
            assertTrue(check(input, DemandPattern(pattern(p(), Tail.ANY, star), null)).isEmpty())
            assertTrue(check(input, DemandPattern(zeroPattern.copy(fact = zeroPattern.fact.copy(path = p(f))), null)).isEmpty())
        }
        for (path in listOf(p(), p(c), p(c, f))) {
            val input = pack(listOf(value(path, Tail.EXACT, base = static)))
            val demand = DemandPattern(pattern(p(c), Tail.ANY, star, base = static), null)
            val expected = if (path.isEmpty()) emptySet() else setOf(pattern(p(c), Tail.STAR, star, base = static))
            assertEquals(expected, check(input, demand).map { it.toPattern() }.toSet())
            assertTrue(check(input, DemandPattern(pattern(path, Tail.ANY, star), null)).isEmpty())
        }
        assertFails { ops.emit(DemandPattern(zeroPattern.copy(fact = zeroPattern.fact.copy(tail = Tail.ANY, mark = star)), null), ops.zero(Layer.NORMAL)) }
    }

    @Test
    fun `32768 depth sparse emission and initial starts remain iterative`() {
        val path = List(32768) { f }
        val inputs = listOf(pack(listOf(value(path, Tail.EXACT))),
            pack(listOf(value(path, Tail.ANY_TAINT, exclusion = ex(g)))),
            pack(listOf(value(path, Tail.ANY, demand = true))),
            flow(listOf(path), mx = marks(u.id), exclusion = ex(g)))
        for (input in inputs) for (entry in listOf(pattern(path, Tail.ANY, star), pattern(path, Tail.ANY),
            pattern(path, Tail.EXACT), pattern(path.dropLast(1), Tail.ANY))) {
            for (initial in check(input, DemandPattern(entry, null))) {
                assertEquals(setOf(startFact(initial.toPattern())), decoded(ops.startFact(initial)))
                assertSame(initial, manager.initial(initial.toPattern()))
            }
        }
        val above = pack(listOf(value(p(), Tail.ANY_TAINT, exclusion = ex(g))))
        assertEquals(setOf(pattern(path, Tail.ANY_TAINT)),
            check(above, DemandPattern(pattern(path, Tail.ANY), null)).map { it.toPattern() }.toSet())
    }

    @Test
    fun `cancelled emission and long start operations stop without publishing an initial list`() {
        val path = List(32768) { f }
        val input = pack(listOf(value(path, Tail.EXACT)))
        val demand = DemandPattern(pattern(path, Tail.ANY), null)
        val initial = manager.initial(pattern(path, Tail.EXACT))
        manager.cancellation.cancel()
        try {
            assertFailsWith<Cancellation.Cancelled> { ops.emit(demand, input.facts) }
            assertFailsWith<Cancellation.Cancelled> { ops.startFact(initial) }
        } finally {
            manager.cancellation.activate()
        }
        assertEquals(setOf(pattern(path, Tail.EXACT)), check(input, demand).map { it.toPattern() }.toSet())
        assertEquals(setOf(startFact(initial.toPattern())), decoded(ops.startFact(initial)))
    }
}
