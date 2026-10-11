package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.Accessor
import org.opentaint.dataflow.ap.ifds.FactTypeChecker
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.StoreFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CallPlanReversalTest {
    private val fixture = StoreFixtures()
    private val m = fixture.manager
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Return
    private val alias = AccessPathBase.Argument(7)
    private val other = AccessPathBase.Argument(8)
    private val f = fixture.field("f")
    private val g = fixture.field("g")
    private val t = fixture.mark("T")
    private val u = fixture.mark("U")
    private val mt = MarkSlot.Concrete(t)
    private val mu = MarkSlot.Concrete(u)
    private val forward = ApMode(false, Direction.FORWARD, 8)
    private val backward = ApMode(false, Direction.BACKWARD, 8)
    private val place get() = Place(fixture.inst(10), statementEdge = false, sources = false)
    private fun p(vararg a: Int) = a.toList()
    private fun exact(base: AccessPathBase, path: List<Int> = p(), mark: MarkSlot = mt) = PathFact(base, path, Tail.EXACT, mark)
    private fun summary(vararg edges: PathEdge, filtered: Boolean = false): StatementSummary {
        val touched = edges.flatMap { listOf(it.from.base, it.to.base) }.toSet()
        val filter = TypeFilter(object : FactTypeChecker.FactApFilter {
            override fun check(accessor: Accessor): FactTypeChecker.FilterResult = FactTypeChecker.FilterResult.Reject
        })
        return StatementSummary(touched, edges.map(MicroEdge::of), emptyList(),
            if (filtered) touched.associateWith { filter } else emptyMap(),
            if (filtered) touched.associateWith { filter } else emptyMap())
    }
    private fun sink(alternative: Int = 0, end: PathEdge? = null): SinkRule {
        val rule = object : CommonTaintConfigurationSink {
            override val id = "sink"
            override val meta = object : CommonTaintConfigurationSinkMeta {
                override val message = "test sink"
                override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
            }
        }
        return SinkRule(rule, alternative, listOf(Pattern(exact(x), ExclusionSet.Empty)),
            end?.let { listOf(MicroEdge.of(it)) }.orEmpty())
    }
    private fun item(base: AccessPathBase = x, path: List<Int> = p(), demand: Boolean = false,
                     candidate: Boolean? = null, mark: MarkSlot = mt) =
        PlanItem(setOf(Pattern(exact(x), ExclusionSet.Empty)), Conclusion(exact(base, path, mark), ExclusionSet.Empty, demand), candidate)
    private fun reference(mode: ApMode) = FormsReference(fixture.ops, ReferenceAlgebra(mode, manager = m))
    private fun straightPlan(passReads: Set<AccessPathBase> = emptySet(), rewriter: List<CleanStep> = emptyList(),
                             aliases: Boolean = false): CallPlan {
        val stages = mutableListOf<CallStage>(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(BOUND, ADDED, emptyList()),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Actions(RETURNED, REWRITTEN, rewriter),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
        )
        if (aliases) stages += CallStage.Edges(REWRITTEN, AFTER, ALIASES,
            summary(starEdge(x, p(), alias, p())), Guard.MemoryEffect)
        return CallPlan(setOf(x), stages, emptyList(), BEFORE, AFTER, passReads)
    }

    @Test
    fun `all forward graph endpoints reverse while stage kinds sinks and callee identities stay stable`(): Unit {
        val end = PathEdge(ZERO_FACT, exact(y, p(f)), ExclusionSet.Empty)
        val sink = sink(end = end)
        val cleaner = CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t))
        val stages = listOf<CallStage>(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(starEdge(x, p(), AccessPathBase.This, p()), ZERO_KEEP, filtered = true)),
            CallStage.Edges(BOUND, REWRITTEN, END_FACTS, summary(end), Guard.SinkTriggered(sink)),
            CallStage.Edges(BOUND, REWRITTEN, SOURCES, summary(PathEdge(ZERO_FACT,
                PathFact(y, p(g), Tail.ANY_TAINT, mt), ExclusionSet.Empty))),
            CallStage.Actions(BOUND, ADDED, listOf(cleaner)),
            CallStage.Callees(ADDED, RETURNED, listOf(fixture.method, fixture.key("second"))),
            CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Edges(ADDED, REWRITTEN, CONSTRUCTOR, summary(keepEdge(AccessPathBase.This), ZERO_KEEP)),
            CallStage.Actions(RETURNED, REWRITTEN, listOf(cleaner)),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(y, p(), x, p()), ZERO_KEEP, filtered = true)),
            CallStage.Edges(REWRITTEN, AFTER, ALIASES, summary(starEdge(AccessPathBase.This, p(f), alias, p(g))), Guard.MemoryEffect),
        )
        val touched = setOf(STATIC, AccessPathBase.This, x, y)
        val reads = setOf(other)
        val plan = CallPlan(touched, stages, listOf(sink), BEFORE, AFTER, reads)
        val rev = plan.reversed()
        assertEquals(AFTER, rev.entry)
        assertEquals(BEFORE, rev.exit)
        assertEquals(touched + setOf(alias, AccessPathBase.Zero), rev.touched)
        assertSame(sink, rev.sinks.single())
        assertEquals(reads, rev.passReads)
        assertEquals(stages.size + 1, rev.stages.size)
        for (index in stages.indices) {
            val native = stages[index]
            val reversed = rev.stages[index]
            assertEquals(native.to to native.from, reversed.from to reversed.to)
            when (native) {
                is CallStage.Edges -> {
                    val edge = assertIs<CallStage.Edges>(reversed)
                    assertEquals(native.kind, edge.kind)
                    assertNull(edge.guard)
                    assertTrue(edge.summary.typeFilters.isEmpty() && edge.summary.resultFilters.isEmpty())
                    assertTrue(edge.summary.conjunctions.isEmpty())
                }
                is CallStage.Actions -> assertIs<CallStage.Actions>(reversed)
                is CallStage.Callees -> assertEquals(native.callees, assertIs<CallStage.Callees>(reversed).callees)
            }
        }
        for (point in CallPoint.entries) assertEquals(rev.stages.filter { it.from == point }, rev.stagesFrom[point].orEmpty())
        assertSame(sink, assertIs<CallStage.Edges>(rev.stages[1]).trigger)
    }

    @Test
    fun `ordered action reversal swaps field statements and retains the cached temporary and cleaner`(): Unit {
        val temporary = AccessPathBase.CleanerTemporary()
        val first = CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t))
        val last = CleanStep.Clean(Cleaner(y, null, CleanReach.AT_AND_BELOW, u))
        val readEdge = starEdge(x, p(f), temporary, p())
        val writeEdge = starEdge(temporary, p(), x, p(f))
        val field = CleanStep.Field(temporary, summary(readEdge, filtered = true),
            Cleaner(temporary, null, CleanReach.AT_AND_BELOW, t), summary(writeEdge, filtered = true))
        val kill = CleanStep.Kill(summary(keepEdge(STATIC, p(), ExclusionSet.of(f))))
        val actions = CallStage.Actions(BOUND, ADDED, listOf(first, field, kill, last))
        val rev = actions.reversed()
        assertEquals(ADDED to BOUND, rev.from to rev.to)
        assertSame(last, rev.steps[0])
        val reversedKill = assertIs<CleanStep.Kill>(rev.steps[1])
        assertEquals(kill.keepEdges.edges.single().edge, reversedKill.keepEdges.edges.single().edge)
        val reversedField = assertIs<CleanStep.Field>(rev.steps[2])
        assertSame(temporary, reversedField.temporary)
        assertSame(field.cleaner, reversedField.cleaner)
        assertEquals(writeEdge.to to writeEdge.from, reversedField.read.edges.single().edge.from to reversedField.read.edges.single().edge.to)
        assertEquals(readEdge.to to readEdge.from, reversedField.write.edges.single().edge.from to reversedField.write.edges.single().edge.to)
        assertTrue(reversedField.read.typeFilters.isEmpty() && reversedField.write.resultFilters.isEmpty())
        assertSame(first, rev.steps[3])
        assertEquals(listOf(first, field, kill, last), actions.steps)
    }

    @Test
    fun `end fact triggers retain the same sink while forward only guards disappear`(): Unit {
        val sink = sink()
        val end = summary(PathEdge(ZERO_FACT, exact(y), ExclusionSet.Empty))
        val stage = CallStage.Edges(BOUND, REWRITTEN, END_FACTS, end, Guard.SinkTriggered(sink))
        val rev = stage.reversed()
        assertEquals(REWRITTEN to BOUND, rev.from to rev.to)
        assertNull(rev.guard)
        assertSame(sink, rev.trigger)
        val aliases = CallStage.Edges(REWRITTEN, AFTER, ALIASES,
            summary(starEdge(x, p(), alias, p())), Guard.MemoryEffect).reversed()
        assertNull(aliases.guard)
        assertNull(aliases.trigger)
        assertFails { CallStage.Edges(REWRITTEN, BOUND, SOURCES, end, trigger = sink) }
        assertFails { CallStage.Edges(REWRITTEN, BOUND, END_FACTS, end, Guard.MemoryEffect, sink) }
        assertFails { CallStage.Edges(REWRITTEN, BOUND, END_FACTS, end, Guard.SinkTriggered(sink), sink) }
        val explicit = CallStage.Edges(REWRITTEN, BOUND, END_FACTS, end.reversed(), trigger = sink)
        assertSame(sink, explicit.trigger)
    }

    @Test
    fun `alias pass over uses only untouched final targets and never adds a second identity for a touched target`(): Unit {
        val stages = listOf<CallStage>(
            CallStage.Edges(BOUND, REWRITTEN, SOURCES, summary(PathEdge(ZERO_FACT, exact(other), ExclusionSet.Empty))),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(y, p(), x, p()))),
            CallStage.Edges(REWRITTEN, AFTER, ALIASES, summary(starEdge(y, p(), alias, p(f)),
                starEdge(y, p(g), alias, p(g)), starEdge(y, p(), x, p(g))), Guard.MemoryEffect),
        )
        val plan = CallPlan(setOf(x, y), stages, emptyList(), BEFORE, AFTER)
        val rev = plan.reversed()
        val pass = rev.stages.filterIsInstance<CallStage.Edges>().single { it.kind == PASS_OVER }
        assertEquals(AFTER to BEFORE, pass.from to pass.to)
        assertEquals(setOf(alias), pass.summary.touched)
        assertEquals(listOf(keepEdge(alias)), pass.summary.edges.map { it.edge })
        assertEquals(setOf(x, y, alias), rev.touched)
        assertFalse(other in rev.touched)
        val alreadyTouched = CallPlan(setOf(x, y, alias), stages, emptyList(), BEFORE, AFTER).reversed()
        assertTrue(alreadyTouched.stages.filterIsInstance<CallStage.Edges>().none { it.kind == PASS_OVER })
        val reached = reference(backward).run(rev, listOf(item(alias, p(f, g))), place)
        assertTrue(BEFORE to item(alias, p(f, g)) in reached)
    }

    @Test
    fun `nullable identity candidates and statement edge kinds follow independent truth rows`(): Unit {
        val identity = MicroEdge.of(keepEdge(x))
        val effect = MicroEdge.of(starEdge(x, p(), y, p()))
        val excluded = MicroEdge.of(keepEdge(x, p(), ExclusionSet.of(f)))
        for (kind in StageKind.entries) {
            assertEquals(kind == SOURCES || kind == UNRESOLVED, kind.statementEdges)
            for (previous in listOf(null, false, true)) for (edge in listOf(null, identity, effect, excluded)) {
                val expected = when (kind) {
                    SOURCES, END_FACTS -> false
                    UNRESOLVED -> edge === identity
                    CONSTRUCTOR -> true
                    else -> previous
                }
                assertEquals(expected, kind.identityCandidateOf(edge, previous), "$kind previous=$previous edge=$edge")
            }
        }
    }

    @Test
    fun `memory effects read the current layer even if a cleaner keeps a true identity candidate`(): Unit {
        for (layer in Layer.entries) for (candidate in listOf(false, true))
            assertEquals(layer == Layer.DEMAND || !candidate, Guard.MemoryEffect.admits(candidate, layer))
        val clean = CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t))
        val plan = straightPlan(rewriter = listOf(clean), aliases = true)
        val input = item(candidate = true)
        val normal = reference(forward).run(plan, listOf(input), place, PlanHooks(guards = true), from = RETURNED)
        assertTrue(normal.none { it.first == AFTER && it.second.c.fact.base == alias })
        val demoted = reference(forward).run(plan, listOf(input), place, PlanHooks(guards = true,
            clean = { _, value -> listOf(value.copy(c = value.c.copy(demand = true))) }), from = RETURNED)
        val aliased = demoted.single { it.first == AFTER && it.second.c.fact.base == alias }.second
        assertTrue(aliased.c.demand)
        assertEquals(true, aliased.identityCandidate)
        assertEquals(input.premise, aliased.premise)
    }

    @Test
    fun `only a complete forward plan can be reversed`(): Unit {
        for (entry in CallPoint.entries) for (exit in CallPoint.entries) {
            val plan = CallPlan(emptySet(), emptyList(), emptyList(), entry, exit)
            if (entry == BEFORE && exit == AFTER) {
                assertEquals(AFTER to BEFORE, plan.reversed().entry to plan.reversed().exit)
                assertFails { plan.reversed().reversed() }
            } else assertFails { plan.reversed() }
        }
    }

    @Test
    fun `plans and collection stages own immutable cached values while retaining sink objects`(): Unit {
        val sink = sink()
        val action = CleanStep.Clean(Cleaner(x, null, CleanReach.AT_AND_BELOW, t))
        val steps = mutableListOf<CleanStep>(action)
        val callees = mutableListOf(fixture.method, fixture.key("other"))
        val actions = CallStage.Actions(BOUND, ADDED, steps)
        val calls = CallStage.Callees(ADDED, RETURNED, callees)
        val touched = mutableSetOf<AccessPathBase>(x)
        val stages = mutableListOf<CallStage>(actions, calls)
        val sinks = mutableListOf(sink)
        val reads = mutableSetOf<AccessPathBase>(y)
        val plan = CallPlan(touched, stages, sinks, BEFORE, AFTER, reads)
        val rev = plan.reversed()
        steps.clear(); callees.clear(); touched.clear(); stages.clear(); sinks.clear(); reads.clear()
        assertEquals(listOf(action), actions.steps)
        assertEquals(listOf(fixture.method, fixture.key("other")), calls.callees)
        assertEquals(setOf(x), plan.touched)
        assertEquals(listOf(actions, calls), plan.stages)
        assertSame(sink, plan.sinks.single())
        assertSame(sink, rev.sinks.single())
        assertEquals(setOf(y), plan.passReads)
        assertEquals(setOf(y), rev.passReads)
        assertEquals(actions, CallStage.Actions(BOUND, ADDED, listOf(action)))
        assertEquals(actions.hashCode(), CallStage.Actions(BOUND, ADDED, listOf(action)).hashCode())
        assertEquals(calls, CallStage.Callees(ADDED, RETURNED, listOf(fixture.method, fixture.key("other"))))
        assertFails { (actions.steps as MutableList<CleanStep>).clear() }
        assertFails { (calls.callees as MutableList<MethodKey>).clear() }
        assertFails { (plan.touched as MutableSet<AccessPathBase>).clear() }
        assertFails { (plan.stages as MutableList<CallStage>).clear() }
        assertFails { (plan.sinks as MutableList<SinkRule>).clear() }
        assertFails { (plan.passReads as MutableSet<AccessPathBase>).clear() }
        assertFails { (plan.stagesFrom as MutableMap<CallPoint, List<CallStage>>).clear() }
        assertFails { (plan.stagesFrom.getValue(BOUND) as MutableList<CallStage>).clear() }
        assertEquals(listOf(actions), plan.stagesFrom.getValue(BOUND))
    }

    @Test
    fun `pass reads remain observer metadata and do not change relevance or any reached item`(): Unit {
        val inputs = listOf(item(x, p(f)), item(other, p(g)))
        for (reverse in listOf(false, true)) {
            val empty = straightPlan()
            val observed = straightPlan(passReads = setOf(other))
            val a = if (reverse) empty.reversed() else empty
            val b = if (reverse) observed.reversed() else observed
            val oracle = reference(if (reverse) backward else forward)
            assertEquals(oracle.run(a, inputs, place).toSet(), oracle.run(b, inputs, place).toSet())
            assertFalse(other in b.touched)
            assertEquals(setOf(other), b.passReads)
        }
    }

    @Test
    fun `zero passes over and enters separately in both graph directions`(): Unit {
        val zero = PlanItem(setOf(ZERO_PATTERN), Conclusion(ZERO_FACT, ExclusionSet.Empty, false), null)
        for (reverse in listOf(false, true)) {
            val plan = if (reverse) straightPlan().reversed() else straightPlan()
            val reached = reference(if (reverse) backward else forward).run(plan, listOf(zero), place)
            assertEquals(if (reverse) setOf(x, AccessPathBase.Zero) else setOf(x), plan.touched)
            assertTrue(plan.entry to zero in reached)
            assertTrue(plan.exit to zero in reached)
            assertTrue(BOUND to zero in reached || REWRITTEN to zero in reached)
            assertTrue(reached.any { it.first == plan.exit && it.second.c == zero.c && it.second.identityCandidate == true })
        }
    }

    @Test
    fun `forward returned and backward added resumes keep premise layer and candidate metadata`(): Unit {
        val plan = straightPlan()
        for (candidate in listOf(null, false, true)) for (demand in listOf(false, true)) {
            val input = item(path = p(f, g), demand = demand, candidate = candidate)
            val front = reference(forward).run(plan, listOf(input), place, from = RETURNED)
            assertEquals(setOf(RETURNED to input, REWRITTEN to input, AFTER to input), front.toSet())
            val back = reference(backward).run(plan.reversed(), listOf(input), place, from = ADDED)
            assertEquals(setOf(ADDED to input, BOUND to input, BEFORE to input), back.toSet())
        }
    }

    @Test
    fun `reversed end facts apply to concrete requirements and fire their retained sink seeds at BOUND`(): Unit {
        val end = PathEdge(ZERO_FACT, PathFact(y, p(f), Tail.ANY_TAINT, mt), ExclusionSet.Empty)
        val sink = sink(end = end)
        val plan = CallPlan(setOf(x, alias), listOf(
            CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
            CallStage.Edges(BOUND, REWRITTEN, END_FACTS, summary(end), Guard.SinkTriggered(sink)),
            CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(starEdge(y, p(), alias, p()), ZERO_KEEP)),
        ), listOf(sink), BEFORE, AFTER)
        val rev = plan.reversed()
        val seed = item(x, candidate = false).copy(premise = setOf(ZERO_PATTERN))
        for (demand in listOf(false, true)) for (path in listOf(p(f), p(f, g))) {
            val fired = ArrayList<SinkRule>()
            val reached = reference(backward).run(rev, listOf(item(alias, path, demand)), place,
                PlanHooks(guards = true, trigger = { actual -> fired += actual; listOf(seed) }))
            assertEquals(listOf(sink), fired)
            assertSame(sink, fired.single())
            assertTrue(BOUND to seed in reached)
            assertTrue(BEFORE to seed in reached)
            assertTrue(reached.any { it.first == BEFORE && it.second.c == Conclusion(ZERO_FACT, ExclusionSet.Empty, demand) })
        }
        for (input in listOf(item(alias, p(), mark = mt), item(alias, p(f), mark = mu), item(alias, p(f), mark = MarkSlot.STAR))) {
            val fired = ArrayList<SinkRule>()
            reference(backward).run(rev, listOf(input), place, PlanHooks(guards = true, trigger = { fired += it; listOf(seed) }))
            assertTrue(fired.isEmpty())
        }
    }
}
