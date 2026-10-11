package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Stored-union controls: raw exclusions remain visible even when T2 changes the runtime partition. */
class ClosureUnionObservationTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val returned = AccessPathBase.Return
    private val zero = setOf(ZERO_PATTERN)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun sinkRule(): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = "root literal after T2"
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = id
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun checkCases(withSink: Boolean) {
        val failures = ArrayList<String>()
        for (sameUnit in listOf(false, true)) for (policy in listOf(SchedulePolicy.FIFO,
            SchedulePolicy.NOTIFY_FIRST, SchedulePolicy.REPLAY_FIRST)) FiniteProgramFixtures().use { f ->
            val callerMethod = f.method("T2 caller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val calleeMethod = f.method("T2 getter", listOf(listOf(1), emptyList()), 1)
            val caller = callerMethod.key("full caller context")
            val callee = calleeMethod.key("full callee context")
            val a = f.field("a")
            val b = f.field("b")
            val mark = MarkSlot.Concrete(f.mark("T"))
            val ea = ExclusionSet.of(a)
            val eb = ExclusionSet.of(b)
            val input = PathFact(x, emptyList(), Tail.ANY_TAINT, mark)
            val result = input.copy(base = y)
            val universe = BoundedDenotation(listOf(AccessPathBase.Zero, x, y, returned,
                AccessPathBase.Argument(7)), listOf(a, b, f.field("fresh accessor")),
                listOf(mark.mark, f.mark("fresh mark"), TaintMark.ZERO), 2)
            fun locations(fact: PathFact, exclusion: ExclusionSet): Set<TestLocation> =
                universe.marked(Pattern(fact, exclusion))
            assertEquals(locations(input, ExclusionSet.Empty), locations(input, ea) + locations(input, eb),
                "The independent normal must union is unchanged by intersecting Ea and Eb")

            // This is raw legal AP source geometry. Target own E is explicit, independent of any builder.
            f.statement(caller, 0, summary(ZERO_KEEP,
                PathEdge(ZERO_FACT, input, ExclusionSet.Empty, toExclusion = ea),
                PathEdge(ZERO_FACT, input, ExclusionSet.Empty, toExclusion = eb)))
            f.call(caller, 1, CallPlan(setOf(x, y, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            f.statement(callee, 0, summary(starEdge(x, emptyList(), returned, emptyList())))
            val rule = sinkRule()
            if (withSink) f.exit(caller, 2, ExitRules(RuleStatement(StatementSummary.EMPTY,
                StatementSummary.EMPTY, listOf(SinkRule(rule, 3,
                    listOf(f.pattern(y, tail = Tail.EXACT, mark = mark)), emptyList()))), false, emptySet()))
            val config = f.config(roots = listOf(caller))
            val expected = NaiveClosure(f, config).run()
            val schedule = ProductionSchedule(f, config, sameUnit = sameUnit, policy = policy, quantum = 1)
            val actual = schedule.run()

            fun checkHeaders(observation: RunObservation) {
                val links = observation.links.filter { it.callee == callee && it.added.fact.base == x }
                assertTrue(links.isNotEmpty(), "The concrete source reaches the actual added-link store")
                for (link in links) {
                    assertEquals(caller, link.caller)
                    assertEquals(zero, link.premise)
                    assertEquals(Layer.NORMAL, link.callerLayer)
                    assertSame(callerMethod.inst(1), link.call)
                    assertEquals(Layer.NORMAL, link.linkLayer)
                    assertEquals(input, link.added.fact)
                }
                assertEquals(locations(input, ExclusionSet.Empty), links.flatMap {
                    locations(it.added.fact, it.added.exclusion)
                }.toSet(), "Added facts preserve the complete independent must union")
                assertTrue(observation.requests.isEmpty() && observation.sourceHits.isEmpty())
                assertTrue(observation.selectedDemand.isEmpty())
                assertEquals(setOf(ObservedInitial(callee, f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))),
                    observation.initials.filter { it.method == callee && it.initial.fact.base == x }.toSet(),
                    "Run1 uses one canonical policy initial, independently of the concrete exclusions")
                if (!withSink) assertTrue(observation.witnesses.isEmpty()) else {
                    assertTrue(observation.witnesses.isNotEmpty(), "The root EXACT literal holds on normal must support")
                    for (witness in observation.witnesses) {
                        assertSame(rule, witness.rule)
                        assertEquals(caller, witness.methodKey)
                        assertSame(callerMethod.inst(2), witness.statement)
                        assertEquals(3, witness.alternative)
                        assertEquals(1, witness.run)
                        assertFalse(witness.confirmed)
                        assertTrue(witness.endFacts.isEmpty())
                        val edge = witness.edges.single()
                        assertEquals(zero, edge.premise)
                        assertEquals(Layer.NORMAL, edge.layer)
                        assertTrue(edge.facts.isNotEmpty())
                        for (fact in edge.facts) {
                            assertEquals(result, fact.fact)
                            assertFalse(fact.demand)
                        }
                    }
                    assertEquals(locations(result, ExclusionSet.Empty), observation.witnesses.flatMap { witness ->
                        witness.edges.single().facts.flatMap { locations(it.fact, it.exclusion) }
                    }.toSet(), "Root-literal support preserves the independent must union")
                }
            }
            checkHeaders(expected)
            checkHeaders(actual)
            assertEquals(setOf(ea, eb), expected.links.filter {
                it.callee == callee && it.added.fact.base == x
            }.map { it.added.exclusion }.toSet(), "Raw reference operands remain separately visible")
            assertTrue(actual.links.any { it.callee == callee && it.added.fact.base == x &&
                it.added.exclusion == ExclusionSet.Empty }, "Actual upstream T2 is exercised before the call")

            try { expected.assertEquivalent(actual, universe) } catch (failure: AssertionError) {
                val expectedSemantic = expected.semantic(universe)
                val actualSemantic = actual.semantic(universe)
                failures += "sameUnit=$sameUnit policy=$policy withSink=$withSink\n" +
                    "link shapes expected=${expected.links.filter { it.added.fact.base == x }} " +
                    "actual=${actual.links.filter { it.added.fact.base == x }}\n" +
                    "witness support exclusions expected=${expected.witnesses.flatMap { it.edges.single().facts }.map { it.exclusion }.toSet()} " +
                    "actual=${actual.witnesses.flatMap { it.edges.single().facts }.map { it.exclusion }.toSet()}\n" +
                    "other stored unions equal=${expectedSemantic.copy(links = emptyMap(), witnesses = emptyMap()) == actualSemantic.copy(links = emptyMap(), witnesses = emptyMap())}\n" +
                    "program=${f.describe()} trace=${schedule.trace}"
            }
        }
        if (failures.isNotEmpty()) throw AssertionError(failures.joinToString("\n\n"))
    }

    @Test
    fun `upstream normal must T2 preserves the whole added-link union under both unit layouts and fixed schedules`(): Unit =
        checkCases(withSink = false)

    @Test
    fun `upstream normal must T2 preserves the whole root EXACT sink support union and headers`(): Unit =
        checkCases(withSink = true)

    @Test
    fun `added-link comparison uses the E union while retaining every caller and initial header`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("link comparison", listOf(listOf(1), listOf(2), emptyList()), 2)
            val destination = f.method("link destination", listOf(listOf(1), emptyList()), 1)
            val caller = method.key("caller")
            val callee = destination.key("callee")
            val a = f.field("a")
            val b = f.field("b")
            val mark = MarkSlot.Concrete(f.mark("T"))
            val universe = BoundedDenotation(listOf(x, y), listOf(a, b, f.field("fresh")),
                listOf(mark.mark, f.mark("other mark"), f.mark("fresh mark")), 2)
            val fact = PathFact(x, emptyList(), Tail.ANY_TAINT, mark)
            val ea = ExclusionSet.of(a)
            val eb = ExclusionSet.of(b)
            fun link(e: ExclusionSet) = ObservedLink(callee, caller, zero, Layer.NORMAL, method.inst(1),
                Layer.NORMAL, Pattern(fact, e))
            fun observation(vararg links: ObservedLink) = RunObservation(links = links.toSet())
            val left = link(ea)
            val right = link(eb)
            val merged = link(ExclusionSet.Empty)
            assertEquals(universe.marked(merged.added), universe.marked(left.added) + universe.marked(right.added))
            val split = observation(left, right)
            val combined = observation(merged)
            assertNotEquals(split, combined, "The complete raw E partition remains observable")
            assertEquals(setOf(ea, eb), split.links.map { it.added.exclusion }.toSet())
            split.assertEquivalent(combined, universe)
            assertNotEquals(observation(left).semantic(universe), observation(right).semantic(universe),
                "One E leaf is not interchangeable with another")

            val baseline = combined.semantic(universe)
            val changes = listOf(
                merged.copy(caller = method.key("different caller context")),
                merged.copy(callee = destination.key("different callee context")),
                merged.copy(premise = setOf(f.pattern(x, mark = mark))),
                merged.copy(callerLayer = Layer.DEMAND),
                merged.copy(call = method.inst(2)),
                merged.copy(linkLayer = Layer.DEMAND,
                    added = merged.added.copy(fact = fact.copy(tail = Tail.ANY))),
                merged.copy(added = merged.added.copy(fact = fact.copy(base = y))),
                merged.copy(added = merged.added.copy(fact = fact.copy(tail = Tail.EXACT))),
                merged.copy(added = merged.added.copy(fact = fact.copy(mark = MarkSlot.Concrete(f.mark("other mark"))))),
            )
            for (changed in changes) assertNotEquals(baseline, observation(changed).semantic(universe), changed.toString())

            val initialA = ObservedInitial(callee, f.pattern(x, tail = Tail.ANY_TAINT, mark = mark, exclusion = ea))
            val initialB = initialA.copy(initial = initialA.initial.copy(exclusion = eb))
            assertNotEquals(RunObservation(initials = setOf(initialA)).semantic(universe),
                RunObservation(initials = setOf(initialB)).semantic(universe), "Initial identities retain their full own E")
        }
    }

    @Test
    fun `sink-support comparison uses the E union and retains ordered literal and vulnerability headers`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("support comparison", listOf(listOf(1), listOf(2), emptyList()), 2)
            val caller = method.key("caller")
            val a = f.field("a")
            val b = f.field("b")
            val mark = MarkSlot.Concrete(f.mark("T"))
            val otherMark = MarkSlot.Concrete(f.mark("other mark"))
            val universe = BoundedDenotation(listOf(x, y), listOf(a, b, f.field("fresh")),
                listOf(mark.mark, otherMark.mark, f.mark("fresh mark")), 2)
            val fact = PathFact(y, emptyList(), Tail.ANY_TAINT, mark)
            val ea = ExclusionSet.of(a)
            val eb = ExclusionSet.of(b)
            val rule = sinkRule()
            fun edge(e: ExclusionSet) = ObservedSinkEdge(zero, Layer.NORMAL,
                setOf(Conclusion(fact, e, false)), GroupKey(FactsKind.TAINT, Layer.NORMAL, y, e))
            fun witness(e: ExclusionSet) = ObservedWitness(rule, caller, method.inst(2), 3, 1,
                listOf(edge(e)), emptySet())
            fun observation(vararg witnesses: ObservedWitness) = RunObservation(witnesses = witnesses.toSet())
            val left = witness(ea)
            val right = witness(eb)
            val merged = witness(ExclusionSet.Empty)
            assertEquals(universe.marked(Pattern(fact, ExclusionSet.Empty)),
                universe.marked(Pattern(fact, ea)) + universe.marked(Pattern(fact, eb)))
            val split = observation(left, right)
            val combined = observation(merged)
            assertNotEquals(split, combined, "Raw witness groups and own E remain visible")
            assertEquals(setOf(ea, eb), split.witnesses.map { it.edges.single().group!!.exclusion }.toSet())
            split.assertEquivalent(combined, universe)
            assertNotEquals(observation(left).semantic(universe), observation(right).semantic(universe))

            val baseline = combined.semantic(universe)
            val rootEdge = merged.edges.single()
            val may = Conclusion(fact.copy(tail = Tail.ANY), ExclusionSet.Empty, true)
            val changes = listOf(
                merged.copy(rule = sinkRule()),
                merged.copy(methodKey = method.key("different method context")),
                merged.copy(statement = method.inst(1)),
                merged.copy(alternative = 4),
                merged.copy(run = 3),
                merged.copy(confirmed = true),
                merged.copy(endFacts = setOf(fact.copy(path = listOf(a), tail = Tail.EXACT))),
                merged.copy(edges = listOf(rootEdge.copy(premise = setOf(f.pattern(x, mark = mark))))),
                merged.copy(edges = listOf(rootEdge.copy(layer = Layer.DEMAND))),
                merged.copy(edges = listOf(ObservedSinkEdge(zero, Layer.DEMAND, setOf(may)))),
                merged.copy(edges = listOf(ObservedSinkEdge(zero, Layer.NORMAL,
                    setOf(Conclusion(fact.copy(tail = Tail.EXACT), ExclusionSet.Empty, false))))),
                merged.copy(edges = listOf(ObservedSinkEdge(zero, Layer.NORMAL,
                    setOf(Conclusion(fact.copy(mark = otherMark), ExclusionSet.Empty, false))))),
            )
            for (changed in changes) assertNotEquals(baseline, observation(changed).semantic(universe), changed.toString())

            val second = ObservedSinkEdge(setOf(f.pattern(x, mark = mark)), Layer.NORMAL,
                setOf(Conclusion(fact.copy(base = x), ExclusionSet.Empty, false)))
            val ordered = merged.copy(edges = listOf(rootEdge, second))
            assertNotEquals(observation(ordered).semantic(universe),
                observation(ordered.copy(edges = ordered.edges.asReversed())).semantic(universe),
                "Literal slots keep their order, premise and base")
        }
    }

    @Test
    fun `added FLOW union rejects intersecting both E and mark exclusions when it creates new locations`(): Unit {
        FiniteProgramFixtures().use { f ->
            val method = f.method("FLOW link comparison", listOf(listOf(1), emptyList()), 1)
            val caller = method.key("caller")
            val callee = method.key("callee")
            val a = f.field("a")
            val b = f.field("b")
            val t = f.mark("T")
            val u = f.mark("U")
            val universe = BoundedDenotation(listOf(x), listOf(a, b, f.field("fresh")),
                listOf(t, u, f.mark("fresh mark")), 2)
            val premise = setOf(f.pattern(x, tail = Tail.STAR, mark = MarkSlot.STAR))
            fun link(e: ExclusionSet, mx: MarkSet) = ObservedLink(callee, caller, premise, Layer.NORMAL,
                method.inst(0), Layer.NORMAL, Pattern(PathFact(x, emptyList(), Tail.STAR, MarkSlot.Star(mx)), e))
            val left = link(ExclusionSet.of(a), f.ap.markSetOf(t))
            val right = link(ExclusionSet.of(b), f.ap.markSetOf(u))
            val invalid = link(ExclusionSet.Empty, MarkSet.EMPTY)
            val union = universe.marked(left.added) + universe.marked(right.added)
            val added = TestLocation(x, listOf(b), t)
            assertFalse(added in union)
            assertTrue(added in universe.marked(invalid.added))
            assertNotEquals(union, universe.marked(invalid.added))
            assertNotEquals(RunObservation(links = setOf(left, right)).semantic(universe),
                RunObservation(links = setOf(invalid)).semantic(universe),
                "Both annotations remain active in the independent denotation")
        }
    }
}
