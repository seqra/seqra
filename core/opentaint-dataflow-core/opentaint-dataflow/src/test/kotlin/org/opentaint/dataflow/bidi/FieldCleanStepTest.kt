package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.ap.ifds.access.util.AccessorInterner.Companion.ELEMENT_ACCESSOR_IDX
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FieldCleanStepTest {
    private val fixture = StoreFixtures()
    private val x = AccessPathBase.Argument(0)
    private val alias = AccessPathBase.Argument(1)
    private val otherAlias = AccessPathBase.Argument(2)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val h = fixture.field("h")
    private val sibling = fixture.field("sibling")
    private val cls = fixture.manager.accessors.index(ClassStaticAccessor("Box"))
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val premise = setOf(fixture.pattern(x, mark = mt))
    private val at = Place(fixture.inst(1), statementEdge = true, sources = true)

    // Construction expectations use raw edges, without the production read/write/keep helpers.
    private fun star(base: AccessPathBase, path: List<Int> = emptyList()) =
        PathFact(base, path, Tail.STAR, MarkSlot.STAR)
    private fun edge(from: AccessPathBase, fromPath: List<Int>, to: AccessPathBase, toPath: List<Int>,
                     exclusion: ExclusionSet = ExclusionSet.Empty) = PathEdge(star(from, fromPath), star(to, toPath), exclusion)
    private fun fact(base: AccessPathBase = x, path: List<Int> = emptyList(), tail: Tail = Tail.EXACT,
                     mark: MarkSlot = mt, demand: Boolean = false, exclusion: ExclusionSet = ExclusionSet.Empty) =
        Conclusion(PathFact(base, path, tail, mark), exclusion, demand)
    private fun item(c: Conclusion) = PlanItem(premise, c, identityCandidate = true)
    private fun starts(path: List<Int>, prefix: List<Int>) =
        path.size >= prefix.size && path.take(prefix.size) == prefix

    private fun walk(step: CleanStep.Field, inputs: List<PlanItem>, cleaned: MutableList<PlanItem> = ArrayList(),
                     candidateRequests: MutableList<TaintMark> = ArrayList(), cuts: MutableList<PlanItem> = ArrayList(),
                     events: MutableList<String>? = null): List<PlanItem> {
        val plan = CallPlan(setOf(x), listOf(CallStage.Actions(RETURNED, AFTER, listOf(step))), emptyList(), BEFORE, AFTER)
        val oracle = FormsReference(fixture.ops,
            ReferenceAlgebra(ApMode(false, Direction.FORWARD, Int.MAX_VALUE), manager = fixture.manager,
                allowsSource = { _, _ -> error("A field action is not a source place") },
                sourceHit = { _, _ -> error("A field action cannot record a source hit") }))
        val reached = oracle.run(plan, inputs, at, PlanHooks(clean = { cleaner, input ->
            assertSame(step.cleaner, cleaner)
            cleaned += input
            events?.add(if (input.c.fact.base === step.temporary) "clean:temporary" else "clean:base")
            val out = cleanRes(cleaner, input.c)
            // cleanRes gives a candidate request; the caller decides whether its run records it.
            out.request?.let { candidateRequests += it }
            out.facts.map { input.copy(c = it) }
        }, statementCut = { cuts += it; it }), from = RETURNED)
        val results = reached.filter { it.first == AFTER }.map { it.second }
        assertTrue(results.none { it.c.fact.base is AccessPathBase.CleanerTemporary }, "Temporary projection at write-back")
        assertTrue(results.all { it.premise == premise && it.identityCandidate == true })
        return results
    }

    @Test
    fun `field action has a fresh temporary full read strong write aliases and shared reversal geometry`(): Unit {
        val path = listOf(ELEMENT_ACCESSOR_IDX, f, g)
        val aliasPrefix = listOf(h, ELEMENT_ACCESSOR_IDX, sibling)
        val secondPrefix = listOf(g, h)
        val aliases = listOf(alias to aliasPrefix, otherAlias to secondPrefix, x to listOf(h))
        val step = fieldCleanStep(x, path, CleanReach.BELOW, t, aliasPaths = aliases)
        val next = fieldCleanStep(x, path, CleanReach.BELOW, t, aliasPaths = aliases)
        assertIs<AccessPathBase.CleanerTemporary>(step.temporary)
        assertNotSame(step.temporary, next.temporary)
        assertNotSame(step.cleaner, next.cleaner)
        assertSame(step.temporary, step.cleaner.base)
        assertNull(step.cleaner.path)
        assertEquals(CleanReach.BELOW, step.cleaner.reach)
        assertEquals(t, step.cleaner.mark)
        assertEquals(setOf(x, step.temporary), step.read.touched)
        assertEquals(setOf(
            edge(x, emptyList(), x, emptyList()),
            edge(x, path, step.temporary, emptyList())), step.read.edges.map { it.edge }.toSet())
        val write = path.indices.map { k -> edge(x, path.take(k), x, path.take(k), ExclusionSet.of(path[k])) } +
            listOf(edge(step.temporary, emptyList(), step.temporary, emptyList()),
                edge(step.temporary, emptyList(), x, path),
                edge(step.temporary, emptyList(), alias, aliasPrefix + path),
                edge(step.temporary, emptyList(), otherAlias, secondPrefix + path))
        assertEquals(write.toSet(), step.write.edges.map { it.edge }.toSet())
        assertEquals(write.size, step.write.edges.size)
        assertEquals(setOf(x, step.temporary), step.write.touched)
        assertFalse(alias in step.write.touched)
        assertFalse(otherAlias in step.write.touched)
        assertTrue(step.read.edges.all { it.edge.exclusion == ExclusionSet.Empty }, "The field read adds no exclusion")
        assertTrue(step.read.typeFilters.isEmpty() && step.read.resultFilters.isEmpty())
        assertTrue(step.write.typeFilters.isEmpty() && step.write.resultFilters.isEmpty())
        val reversed = step.reversed()
        assertSame(step.temporary, reversed.temporary)
        assertSame(step.cleaner, reversed.cleaner)
        assertEquals(step.write.edges.map { it.edge.to to it.edge.from }.toSet(),
            reversed.read.edges.filter { it.forward in step.write.edges.map { e -> e.forward } }
                .map { it.edge.from to it.edge.to }.toSet())
        for (forward in step.write.edges) assertSame(forward.forward,
            reversed.read.edges.single { it.forward === forward.forward }.forward)
        for (forward in step.read.edges) assertSame(forward.forward,
            reversed.write.edges.single { it.forward === forward.forward }.forward)
        assertTrue(reversed.read.typeFilters.isEmpty() && reversed.read.resultFilters.isEmpty())
        assertTrue(reversed.write.typeFilters.isEmpty() && reversed.write.resultFilters.isEmpty())
    }

    @Test
    fun `field action rejects static empty and nonfield target shapes but admits a full path ending in a field`(): Unit {
        for (reach in CleanReach.entries) for (mark in listOf<TaintMark?>(null, t)) {
            assertFails { fieldCleanStep(AccessPathBase.ClassStatic, listOf(cls, f), reach, mark) }
            for (path in listOf(emptyList(), listOf(ELEMENT_ACCESSOR_IDX), listOf(cls), listOf(f, ELEMENT_ACCESSOR_IDX)))
                assertFails { fieldCleanStep(x, path, reach, mark) }
            for (path in listOf(listOf(f), listOf(f, g), listOf(ELEMENT_ACCESSOR_IDX, f, g))) {
                val step = fieldCleanStep(x, path, reach, mark)
                assertEquals(path, step.read.edges.single { it.edge.to.base === step.temporary }.edge.from.path)
                assertEquals(path, step.write.edges.single { it.edge.to.base == x && it.edge.from.base === step.temporary }.edge.to.path)
                assertEquals(reach, step.cleaner.reach)
                assertEquals(mark, step.cleaner.mark)
            }
        }
    }

    @Test
    fun `nested concrete field geometry cleans selected locations while preserving marks layers siblings and aliases`(): Unit {
        val aliasPrefix = listOf(h, ELEMENT_ACCESSOR_IDX, sibling)
        var cases = 0
        for (path in listOf(listOf(f, g), listOf(ELEMENT_ACCESSOR_IDX, f, g))) {
            val locations = listOf(emptyList(), path.take(1), path.dropLast(1), path,
                path + h, path + listOf(ELEMENT_ACCESSOR_IDX, h), path.dropLast(1) + sibling, listOf(sibling, g)).distinct()
            for (reach in CleanReach.entries) for (selected in listOf<TaintMark?>(null, t, u))
                for (mark in listOf(mt, mu)) for (demand in listOf(false, true)) for (location in locations) {
                    val step = fieldCleanStep(x, path, reach, selected,
                        aliasPaths = listOf(alias to aliasPrefix, x to listOf(h)))
                    val input = item(fact(path = location, mark = mark, demand = demand))
                    val under = starts(location, path)
                    val suffix = if (under) location.drop(path.size) else emptyList()
                    val cleans = under && (selected == null || selected == mark.mark) && when (reach) {
                        CleanReach.EXACT -> suffix.isEmpty()
                        CleanReach.BELOW -> suffix.isNotEmpty()
                        CleanReach.AT_AND_BELOW -> true
                    }
                    val expected = buildSet {
                        if (!cleans) {
                            add(input.c)
                            if (under) add(input.c.copy(fact = input.c.fact.copy(base = alias,
                                path = aliasPrefix + path + suffix)))
                        }
                    }
                    val candidates = ArrayList<TaintMark>()
                    val got = walk(step, listOf(input), candidateRequests = candidates).map { it.c }.toSet()
                    assertEquals(expected, got, "path=$path location=$location reach=$reach selected=$selected mark=$mark demand=$demand")
                    assertTrue(candidates.isEmpty(), "Concrete marks need no selected-mark request")
                    cases++
                }
        }
        assertTrue(cases >= 500)
    }

    @Test
    fun `the actual read clean write chain removes one mark and retains other marks sibling leaves and untouched aliases`(): Unit {
        val path = listOf(f, g)
        val aliasPrefix = listOf(h, ELEMENT_ACCESSOR_IDX)
        val step = fieldCleanStep(x, path, CleanReach.AT_AND_BELOW, t, aliasPaths = listOf(alias to aliasPrefix))
        val inputs = listOf(item(fact(path = path, mark = mt)), item(fact(path = path, mark = mu)),
            item(fact(path = path + h, mark = mt)), item(fact(path = path + h, mark = mu)),
            item(fact(path = listOf(f, sibling), mark = mt)), item(fact(path = listOf(sibling), mark = mt)),
            item(fact(base = alias, path = listOf(g, sibling), mark = mt, demand = true)))
        val expected = setOf(fact(path = path, mark = mu), fact(path = path + h, mark = mu),
            fact(path = listOf(f, sibling), mark = mt), fact(path = listOf(sibling), mark = mt),
            fact(base = alias, path = listOf(g, sibling), mark = mt, demand = true),
            fact(base = alias, path = aliasPrefix + path, mark = mu),
            fact(base = alias, path = aliasPrefix + path + h, mark = mu))
        val cleaned = ArrayList<PlanItem>()
        val cuts = ArrayList<PlanItem>()
        val got = walk(step, inputs, cleaned = cleaned, cuts = cuts)
        assertEquals(expected, got.map { it.c }.toSet())
        assertEquals(setOf(fact(step.temporary, mark = mt), fact(step.temporary, mark = mu),
            fact(step.temporary, listOf(h), mark = mt), fact(step.temporary, listOf(h), mark = mu)),
            cleaned.filter { it.c.fact.base === step.temporary }.map { it.c }.toSet())
        assertTrue(cleaned.any { it.c.fact.base == alias }, "An untouched alias passes both statements unchanged")
        assertTrue(cuts.any { it.c.fact.base === step.temporary }, "The read result reaches its statement cut")
        assertTrue(cuts.any { it.c.fact.base == alias && starts(it.c.fact.path, aliasPrefix + path) },
            "The full write alias result reaches its statement cut")
        assertTrue(cuts.none { it.c.fact.base == alias && it.c.fact.path == listOf(g, sibling) },
            "An untouched input does not receive a statement cut")
    }

    @Test
    fun `must and may field facts apply the root cleaner reach table before write back`(): Unit {
        val path = listOf(f, g, h)
        for (tail in listOf(Tail.ANY_TAINT, Tail.ANY)) for (reach in CleanReach.entries)
            for (selected in listOf<TaintMark?>(null, t, u)) {
                val demand = tail == Tail.ANY
                val exclusion = if (demand) ExclusionSet.Empty else ExclusionSet.of(sibling)
                val input = fact(path = path, tail = tail, exclusion = exclusion, demand = demand)
                val step = fieldCleanStep(x, path, reach, selected)
                val expected = if (selected == u) setOf(input) else when (reach) {
                    CleanReach.AT_AND_BELOW -> emptySet()
                    CleanReach.BELOW -> setOf(fact(path = path, demand = demand))
                    CleanReach.EXACT -> setOf(fact(path = path, tail = tail, exclusion = exclusion, demand = true))
                }
                val cleaned = ArrayList<PlanItem>()
                val got = walk(step, listOf(item(input)), cleaned = cleaned).map { it.c }.toSet()
                assertEquals(expected, got, "$tail/$reach/$selected")
                assertEquals(listOf(input.copy(fact = input.fact.copy(base = step.temporary, path = emptyList()))),
                    cleaned.filter { it.c.fact.base === step.temporary }.map { it.c }, "Read retains the must exclusion")
            }
    }

    @Test
    fun `selected mark field action keeps a normal FLOW ancestor outside the field with all its original marks`(): Unit {
        val originalMarks = MarkSlot.Star(MarkSet(intArrayOf(u.id)))
        val cleanedMarks = MarkSlot.Star(MarkSet(intArrayOf(t.id, u.id)))
        val input = fact(tail = Tail.STAR, mark = originalMarks)
        for (path in listOf(listOf(f), listOf(f, g))) for (reach in CleanReach.entries) {
            val step = fieldCleanStep(x, path, reach, t)
            val expected = buildSet {
                add(fact(tail = Tail.STAR, mark = originalMarks, exclusion = ExclusionSet.of(path[0])))
                for (k in 1 until path.size) add(fact(path = path.take(k), tail = Tail.ANY,
                    mark = originalMarks, demand = true))
                add(fact(path = path, tail = Tail.ANY, mark = cleanedMarks, demand = true))
            }
            val candidateRequests = ArrayList<TaintMark>()
            val got = walk(step, listOf(item(input)), candidateRequests = candidateRequests).map { it.c }.toSet()
            assertEquals(expected, got, "$path/$reach")
            assertEquals(listOf(t), candidateRequests, "F75 acts on the temporary, once, for every reach")
            val normal = got.single { !it.demand }
            assertEquals(originalMarks, normal.fact.mark, "The outside normal FLOW part still admits T")
            assertEquals(ExclusionSet.of(path[0]), normal.exclusion)
        }
    }

    @Test
    fun `injected base and value filters retain identity and run in read clean write order`(): Unit {
        val path = listOf(f, g)
        for (admitBase in listOf(false, true)) for (admitValue in listOf(false, true)) {
            val events = ArrayList<String>()
            val baseFilter = TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: Accessor): FactTypeChecker.FilterResult {
                    events += "base"
                    return if (admitBase) FactTypeChecker.FilterResult.Accept else FactTypeChecker.FilterResult.Reject
                }
            })
            val valueFilter = TypeFilter(object : FactTypeChecker.FactApFilter {
                override fun check(accessor: Accessor): FactTypeChecker.FilterResult =
                    error("This test value has the empty temporary path")
            }, MarkPolicy { mark, level ->
                assertEquals(u, mark)
                assertEquals(0, level)
                events += "value"
                admitValue
            })
            val step = fieldCleanStep(x, path, CleanReach.AT_AND_BELOW, t, baseFilter, valueFilter)
            assertSame(baseFilter, step.read.typeFilters[x])
            assertSame(valueFilter, step.read.resultFilters[step.temporary])
            assertEquals(setOf(x), step.read.typeFilters.keys)
            assertEquals(setOf(step.temporary), step.read.resultFilters.keys)
            assertSame(baseFilter, step.write.typeFilters[x])
            assertSame(valueFilter, step.write.typeFilters[step.temporary])
            assertEquals(setOf(x, step.temporary), step.write.typeFilters.keys)
            assertTrue(step.write.resultFilters.isEmpty())
            val input = fact(path = path, mark = mu)
            val got = walk(step, listOf(item(input)), events = events).map { it.c }.toSet()
            assertEquals(if (admitBase && admitValue) setOf(input) else emptySet(), got)
            val expectedEvents = when {
                !admitBase -> listOf("base")
                !admitValue -> listOf("base", "value", "clean:base", "base")
                else -> listOf("base", "value", "clean:base", "clean:temporary", "base", "value")
            }
            assertEquals(expectedEvents, events)
            events.clear()
            val untouched = fact(base = alias, path = path, mark = mt)
            assertEquals(listOf(untouched), walk(step, listOf(item(untouched))).map { it.c })
            assertTrue(events.isEmpty(), "Both statements leave an unrelated base outside their filters")
        }
    }
}
