package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.fixtures.TestMark
import org.opentaint.dataflow.bidi.fixtures.testDeclarations
import org.opentaint.dataflow.bidi.store.DemandStore
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** Compare successful AP contributions, not the old all-groups candidate inventory. */
class ImplicitStaticGroupDemandTest {
    private val fixture = StoreFixtures(markDeclarations = testDeclarations(
        TestMark("T", MarkGroupKey.Rule("outer")),
        TestMark("U", MarkGroupKey.Rule("outer")),
        TestMark("V", MarkGroupKey.Rule("other")),
        TestMark("late", MarkGroupKey.Model("late")),
    ))
    private val m = fixture.manager
    private val ops = fixture.ops
    private val method = fixture.key("visited")
    private val unvisited = fixture.key("not visited", 7)
    private val cls = m.accessors.index(ClassStaticAccessor("C"))
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val v = fixture.mark("V")
    private val fulls = listOf(t, u, v)

    private fun pattern(mark: MarkSlot, path: List<Int> = listOf(cls, f), tail: Tail = Tail.ANY,
                        exclusion: ExclusionSet = ExclusionSet.Empty, base: AccessPathBase = STATIC) =
        Pattern(PathFact(base, path, tail, mark), exclusion)

    private fun pair(full: TaintMark): DemandPattern {
        val mark = MarkSlot.Concrete(m.marks.group(m.marks.groupOf(full)))
        return DemandPattern(pattern(mark, emptyList(), Tail.ANY), pattern(mark, emptyList(), Tail.STAR))
    }

    /** This finite full universe belongs to the reference test, not the runtime catalog. */
    private fun referencePairs(): List<DemandPattern> = fulls.map(::pair).distinct()
    private fun enabled() = DemandStore.Builder(m).apply { includeStaticGroups() }.build()
    private fun decoded(values: Iterable<Facts>): Set<Conclusion> = values.flatMap { facts ->
        ops.leaves(facts).map { Conclusion(it.fact, it.exclusion, facts.layer == Layer.DEMAND) }.toList()
    }.toSet()

    @Test fun `one actual full input selects only its group for every method and late declared use`() {
        val store = enabled()
        val actual = pattern(MarkSlot.Concrete(t))
        for (key in listOf(method, unvisited)) {
            assertEquals(listOf(pair(t)), store.near(key, actual))
            assertEquals(listOf(pair(t)), store.covering(key, m.initial(actual)))
        }
        assertNotEquals(pair(t), pair(v))
        assertEquals(pair(t), pair(u))
        // The declaration was fixed before this store existed; only its atom is new.
        val late = fixture.mark("late")
        val lateInput = pattern(MarkSlot.Concrete(late))
        assertEquals(listOf(pair(late)), store.near(unvisited, lateInput))
        assertEquals(listOf(pair(t)), store.near(method, actual))
        assertTrue(DemandStore.Builder(m).build().near(method, actual).isEmpty())
    }

    @Test fun `selected and enumerated guides emit the same full initials for tails exclusions and layers`() {
        val store = enabled()
        for (mark in fulls) for (path in listOf(listOf(cls), listOf(cls, f), listOf(cls, f, g))) {
            val actuals = listOf(
                pattern(MarkSlot.Concrete(mark), path, Tail.EXACT),
                pattern(MarkSlot.Concrete(mark), path, Tail.ANY),
                pattern(MarkSlot.Concrete(mark), path, Tail.ANY_TAINT),
                pattern(MarkSlot.Concrete(mark), path, Tail.ANY_TAINT, ExclusionSet.of(g)),
            )
            for (actual in actuals) for (layer in Layer.entries) {
                val facts = m.factsOf(actual.fact, actual.exclusion, layer)
                val expected = referencePairs().flatMap { ops.emit(it, facts) }.toSet()
                val selected = store.near(unvisited, actual).flatMap { ops.emit(it, facts) }.toSet()
                assertEquals(expected, selected, "$actual / $layer")
                assertTrue(selected.isNotEmpty())
                assertTrue(selected.all { it.mark == MarkSlot.Concrete(mark) }, "Keep the actual full identity")
            }
        }
    }

    @Test fun `packed inputs collect their own pairs and preserve all full members`() {
        val store = enabled()
        val actuals = fulls.map { pattern(MarkSlot.Concrete(it)) }
        val facts = actuals.map { m.factsOf(it.fact, it.exclusion, Layer.DEMAND) }
            .reduce { left, right -> m.mergeAddDelta(left, right).first }
        val selected = ops.leaves(facts).flatMap { store.near(unvisited, it) }.toSet()
        assertEquals(referencePairs().toSet(), selected)
        val expected = referencePairs().flatMap { ops.emit(it, facts) }.toSet()
        val emitted = selected.flatMap { ops.emit(it, facts) }.toSet()
        assertEquals(expected, emitted)
        assertEquals(fulls.toSet(), emitted.map { (it.mark as MarkSlot.Concrete).mark }.toSet())
    }

    @Test fun `restriction selects the entry group and keeps complete AP results and explicit exits`() {
        val explicit = DemandPattern(pattern(MarkSlot.Concrete(t)),
            pattern(MarkSlot.Concrete(v), base = AccessPathBase.Return))
        val store = DemandStore.Builder(m).apply { includeStaticGroups(); add(method, explicit) }.build()
        val initial = m.initial(pattern(MarkSlot.Concrete(t)))
        val selected = store.covering(method, initial)
        assertEquals(setOf(explicit, pair(t)), selected.toSet())
        for (mark in fulls) for (tail in listOf(Tail.EXACT, Tail.ANY, Tail.ANY_TAINT)) {
            for (layer in Layer.entries) for (base in listOf(STATIC, AccessPathBase.Return)) {
                val exclusions = if (tail == Tail.ANY_TAINT) listOf(ExclusionSet.Empty, ExclusionSet.of(g))
                    else listOf(ExclusionSet.Empty)
                for (exclusion in exclusions) {
                    val output = pattern(MarkSlot.Concrete(mark), tail = tail, exclusion = exclusion, base = base)
                    val facts = m.factsOf(output.fact, output.exclusion, layer)
                    val expected = decoded((referencePairs() + explicit).flatMap { ops.restrict(initial, facts, it) })
                    assertEquals(expected, decoded(selected.flatMap { ops.restrict(initial, facts, it) }),
                        "$output / $layer")
                }
            }
        }
        val cross = pattern(MarkSlot.Concrete(v))
        val crossFacts = m.factsOf(cross.fact, cross.exclusion, Layer.DEMAND)
        assertTrue(ops.restrict(initial, crossFacts, pair(t)).isEmpty(), "Output group cannot select its own permission")
        val answer = pattern(MarkSlot.Concrete(v), base = AccessPathBase.Return)
        val answerFacts = m.factsOf(answer.fact, answer.exclusion, Layer.DEMAND)
        assertTrue(ops.restrict(initial, answerFacts, explicit).isNotEmpty(), "Retain explicit cross-group demand")
    }

    @Test fun `flow zero group and nonstatic inputs cannot create concrete permission or facts`() {
        val store = enabled()
        val group = m.marks.group(m.marks.groupOf(t))
        val inputs = listOf(
            pattern(MarkSlot.STAR, tail = Tail.ANY),
            pattern(MarkSlot.Star(m.intern(MarkSet(intArrayOf(group.id)))), tail = Tail.STAR),
            pattern(MarkSlot.Concrete(group)),
            pattern(MarkSlot.Concrete(TaintMark.ZERO)),
            pattern(MarkSlot.Concrete(t), base = AccessPathBase.Argument(0)),
        )
        for (input in inputs) {
            assertTrue(store.near(method, input).isEmpty(), "$input")
            if (input.fact.mark !is MarkSlot.Star || input.fact.tail == Tail.ANY)
                assertTrue(store.covering(method, m.initial(input)).isEmpty(), "$input")
        }
        val zero = DemandPattern(m.zero.toPattern(), null)
        assertEquals(listOf(zero), store.near(unvisited, m.zero.toPattern()))
        assertTrue(store.covering(unvisited, m.zero).isEmpty())
        assertTrue(referencePairs().all { ops.emit(it, Reach.NORMAL).isEmpty() })
    }

    @Test fun `empty method lookups and implicit selection check cancellation`() {
        val store = enabled()
        val actual = pattern(MarkSlot.Concrete(t))
        val initial = m.initial(actual)
        fixture.cancellation.cancel()
        assertFailsWith<Cancellation.Cancelled> { store.near(unvisited, actual) }
        assertFailsWith<Cancellation.Cancelled> { store.covering(unvisited, initial) }
    }
}
