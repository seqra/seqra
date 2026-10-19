package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.GroupedScalarMarks
import org.opentaint.dataflow.util.Cancellation
import kotlin.test.*

/** Exhaustive bounded comparisons use the scalar AP geometry and explicit catalog
 * ownership, without the packed mark gate, direct service, or group NNF index. */
class GroupedScalarReferenceTest {
    private val m = ApManager(Cancellation())
    private val ops = ApOps(m)
    private val t = m.marks.reserveFull("outer#left;T")
    private val u = m.marks.reserveFull("outer#right;U")
    private val v = m.marks.reserveFull("other;V")
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val a = m.accessors.index(FieldAccessor("Box", "a", "Box"))
    private val b = m.accessors.index(FieldAccessor("Box", "b", "Box"))
    private val mode = ApMode(true, Direction.FORWARD, 1)
    init {
        m.marks.register(m.marks.name(t), MarkGroupKey.Rule("outer"))
        m.marks.register(m.marks.name(u), MarkGroupKey.Rule("outer"))
        m.marks.register(m.marks.name(v), MarkGroupKey.Rule("other"))
        listOf(t, u, v).forEach(m.marks::markEligible)
        m.marks.finishOwnershipCensus()
    }
    private val scalar = GroupedScalarMarks(m.marks)
    private fun output(facts: List<Facts>): Set<Conclusion> = facts.flatMap { value ->
        ops.leaves(value).map { Conclusion(it.fact, it.exclusion, value.layer == Layer.DEMAND) }.toList()
    }.toSet()

    @Test fun `bounded full forms agree with independent group interpretation before and after mark gates`() {
        val paths = listOf(emptyList(), listOf(a), listOf(b))
        val concrete = listOf(t, u, v).map { MarkSlot.Concrete(it) }
        val marks = listOf(MarkSlot.STAR, MarkSlot.Star(MarkExclusions.of(m.marks, t))) + concrete
        var checked = 0
        for (inputPath in paths) for (inputMark in marks) {
            val input = scalar.conclusion(Conclusion(PathFact(x, inputPath,
                if (inputMark is MarkSlot.Star) Tail.STAR else Tail.EXACT, inputMark), ExclusionSet.Empty, false))
            for (fromPath in paths) for (fromMark in listOf(MarkSlot.STAR) + concrete) {
                val targets = if (fromMark is MarkSlot.Star)
                    listOf(MarkSlot.STAR, MarkSlot.Star(MarkExclusions.of(m.marks, t)))
                    else listOf(MarkSlot.Concrete(t), MarkSlot.Concrete(v), MarkSlot.STAR)
                for (target in targets) {
                    val edge = PathEdge(PathFact(x, fromPath, Tail.STAR, fromMark),
                        PathFact(y, emptyList(), Tail.STAR, target), ExclusionSet.Empty)
                    val expected = scalar.apply(input, edge, false)
                    val expectedRequests = buildSet {
                        if (input.fact.mark is MarkSlot.Star)
                            (edge.to.mark as? MarkSlot.Star)?.excluded?.ids?.forEach { add(scalar.atom(TaintMark(it))) }
                        if (expected is EdgeOutcome.Request) add(expected.mark)
                    }
                    val premise = if (input.fact.mark is MarkSlot.Star)
                        m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR) else m.zero
                    val actual = CollectingOut()
                    ops.applyEdge(m.factsOf(input.fact, input.exclusion, if (input.demand) Layer.DEMAND else Layer.NORMAL), premise, edge, false, mode, actual)
                    assertEquals(expectedRequests, actual.markRequests.toSet(), "input=$input edge=$edge")
                    val expectedFacts = if (expected is EdgeOutcome.Fact) setOf(expected.conclusion) else emptySet()
                    assertEquals(expectedFacts, output(actual.results), "input=$input edge=$edge")
                    checked++
                }
            }
        }
        assertEquals(495, checked)
    }

    @Test fun `selected cleaners and RemoveAll agree for matching disjoint and partial geometry`() {
        val paths = listOf(emptyList(), listOf(a), listOf(b))
        var checked = 0
        for (path in paths) for (flow in listOf(false, true)) for (cleanPath in paths)
            for (reach in CleanReach.entries) for (selected in listOf(null, t, v)) {
                val input = scalar.conclusion(Conclusion(PathFact(x, path,
                    if (flow) Tail.STAR else Tail.EXACT,
                    if (flow) MarkSlot.Star(MarkExclusions.of(m.marks, t)) else MarkSlot.Concrete(u)),
                    ExclusionSet.Empty, false))
                val cleaner = Cleaner(x, m.path(cleanPath), reach, selected)
                val expected = scalar.clean(cleaner, input)
                val premise = if (flow) m.initial(x, null, Tail.STAR, ExclusionSet.Empty, MarkSlot.STAR) else m.zero
                val actual = CollectingOut()
                ops.clean(m.factsOf(input.fact, input.exclusion, if (input.demand) Layer.DEMAND else Layer.NORMAL), premise, cleaner, mode, actual)
                assertEquals(expected.facts.toSet(), output(actual.results), "input=$input cleaner=$cleaner")
                assertEquals(setOfNotNull(expected.request), actual.markRequests.toSet(), "input=$input cleaner=$cleaner")
                checked++
            }
        assertEquals(162, checked)
    }
}
