package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Specified one-run facts and witness layers, before driver support or report classification. */
class OneRunAcceptanceTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.Argument(1)
    private val z = AccessPathBase.Argument(2)
    private val returned = AccessPathBase.Return
    private val zero = setOf(ZERO_PATTERN)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun sink(id: String, literal: Pattern): SinkRule {
        val rule = object : CommonTaintConfigurationSink {
            override val id = id
            override val meta = object : CommonTaintConfigurationSinkMeta {
                override val message = id
                override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
            }
        }
        return SinkRule(rule, 0, listOf(literal), emptyList())
    }

    private fun exit(vararg sinks: SinkRule): ExitRules =
        ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, sinks.toList()), false, emptySet())

    private fun source(f: FiniteProgramFixtures, key: MethodKey, target: PathFact): Seed.Source {
        val edge = PathEdge(ZERO_FACT, target, ExclusionSet.Empty)
        f.entry(key, RuleStatement(summary(ZERO_KEEP, edge), StatementSummary.EMPTY, emptyList()))
        return Seed.Source(key, key.statement, edge)
    }

    private fun run(f: FiniteProgramFixtures, config: RunConfig, fields: List<Int>, marks: List<TaintMark>,
                    extraBases: List<AccessPathBase> = emptyList()): RunObservation {
        val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val schedule = ProductionSchedule(f, config, policy = SchedulePolicy.REPLAY_FIRST, quantum = 1)
        val actual = schedule.run()
        val universe = BoundedDenotation((listOf(AccessPathBase.Zero, x, y, z, returned) + extraBases).distinct(),
            (fields + f.field("fresh accessor")).distinct(),
            (marks + f.mark("fresh mark") + (marks + f.mark("fresh mark")).map { f.ap.marks.group(f.ap.marks.groupOf(it)) } + TaintMark.ZERO).distinct(), 2)
        try { actual.assertEquivalent(expected, universe) }
        catch (failure: AssertionError) {
            throw AssertionError("index=${config.index} program=${f.describe()} trace=${schedule.trace}", failure)
        }
        return actual
    }

    private fun selected(f: FiniteProgramFixtures, index: Int, value: Conclusion): Conclusion =
        if (index == 1) GroupedScalarMarks(f.ap.marks).conclusion(value) else value

    private fun raw(observation: RunObservation, method: MethodKey, base: AccessPathBase): Set<Conclusion> =
        observation.rawSummaries.filter { it.method == method && it.conclusion.fact.base == base }
            .mapTo(LinkedHashSet()) { it.conclusion }

    private fun witnesses(observation: RunObservation): Map<String, Set<Layer>> =
        observation.witnesses.groupBy { it.rule.id }.mapValues { (_, rows) ->
            rows.flatMap { it.edges.map { edge -> edge.layer } }.toSet()
        }

    @Test
    fun `a dead local passes an unrelated statement and reaches the actual exit sink in either forward mode`(): Unit {
        for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val method = f.method("deadLocal", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val dead = AccessPathBase.LocalVar(900)
            val other = AccessPathBase.LocalVar(901)
            val t = MarkSlot.Concrete(f.mark("T"))
            val target = PathFact(dead, emptyList(), Tail.EXACT, t)
            val seed = source(f, key, target)
            f.statement(key, 1, StatementSummary(setOf(other), emptyList(), emptyList(), emptyMap()))
            val reached = sink("dead local reached", f.pattern(dead, mark = t))
            f.exit(key, 2, exit(reached))
            val actual = run(f, f.config(index = index, L = maxOf(1, index - 1), roots = listOf(key), seeds = listOf(seed)),
                emptyList(), listOf(t.mark), listOf(dead, other))
            val conclusion = selected(f, index, Conclusion(target, ExclusionSet.Empty, false))
            assertContains(actual.processed, ObservedEdge(key, zero, conclusion, method.inst(1)))
            assertContains(actual.processed, ObservedEdge(key, zero, conclusion, method.inst(2)))
            assertEquals(mapOf(reached.rule.id to setOf(if (index == 1) Layer.DEMAND else Layer.NORMAL)), witnesses(actual))
            val witness = actual.witnesses.single()
            assertSame(reached.rule, witness.rule)
            assertEquals(method.inst(2), witness.statement)
            assertEquals(listOf(ObservedSinkEdge(zero, if (index == 1) Layer.DEMAND else Layer.NORMAL, setOf(conclusion),
                GroupKey(FactsKind.TAINT, if (index == 1) Layer.DEMAND else Layer.NORMAL, dead, tail = Tail.ANY_TAINT))), witness.edges)
            assertTrue(raw(actual, key, dead).isEmpty())
            assertTrue(actual.requests.isEmpty() && (index == 1 || actual.selectedDemand.none { it.conclusion.fact.base !in listOf(STATIC, AccessPathBase.Zero) }))
        }
    }

    @Test
    fun `the synthetic two field strong write keeps both normal must prefixes and reports only the admitted sinks`(): Unit {
        for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val method = f.method("twoFieldWrite", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val a = f.field("f")
            val b = f.field("g")
            val h = f.field("h")
            val k = f.field("k")
            val t = MarkSlot.Concrete(f.mark("T"))
            val seed = source(f, key, PathFact(x, emptyList(), Tail.ANY_TAINT, t))
            f.statement(key, 1, summary(keepEdge(x, e = ExclusionSet.of(a)),
                keepEdge(x, listOf(a), ExclusionSet.of(b))))
            val overwritten = sink("f.g excluded", f.pattern(x, listOf(a, b), mark = t))
            val sibling = sink("f.h retained", f.pattern(x, listOf(a, h), mark = t))
            val rootSibling = sink("k retained", f.pattern(x, listOf(k), mark = t))
            f.exit(key, 2, exit(overwritten, sibling, rootSibling))
            val demand = listOf(key to DemandPattern(ZERO_PATTERN, f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR)))
            val actual = run(f, f.config(index = index, L = maxOf(1, index - 1), roots = listOf(key),
                demand = demand, seeds = listOf(seed)), listOf(a, b, h, k), listOf(t.mark))
            if (index == 1) {
                val cover = selected(f, index, Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, true))
                assertContains(raw(actual, key, x), cover)
                assertTrue(raw(actual, key, x).all { it.exclusion == ExclusionSet.Empty && it.demand })
                assertEquals(listOf(overwritten, sibling, rootSibling).associate { it.rule.id to setOf(Layer.DEMAND) },
                    witnesses(actual), "The broad E-free cover requires refinement, unlike an exact destination kill")
                assertTrue(actual.requests.isEmpty())
                return@use
            }
            val atRoot = Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(a), false)
            val atField = Conclusion(PathFact(x, listOf(a), Tail.ANY_TAINT, t), ExclusionSet.of(b), false)
            assertEquals(setOf(atRoot, atField), raw(actual, key, x))
            assertEquals(mapOf(sibling.rule.id to setOf(Layer.NORMAL), rootSibling.rule.id to setOf(Layer.NORMAL)),
                witnesses(actual))
            assertEquals(setOf(atField), actual.witnesses.single { it.rule === sibling.rule }.edges.single().facts)
            assertEquals(setOf(atRoot), actual.witnesses.single { it.rule === rootSibling.rule }.edges.single().facts)
            assertTrue(actual.witnesses.all { it.edges.single().premise == zero && !it.confirmed })
            assertTrue(actual.selectedDemand.isEmpty() && actual.requests.isEmpty())
        }
    }

    @Test
    fun `a setter exclusion blocks its read and sink while an admitted sibling read stays normal must`(): Unit {
        for (index in listOf(1, 3)) FiniteProgramFixtures().use { f ->
            val method = f.method("setterReads", listOf(listOf(1), listOf(2), listOf(3), emptyList()), 3)
            val key = method.key()
            val name = f.field("name")
            val email = f.field("email")
            val t = MarkSlot.Concrete(f.mark("T"))
            val seed = source(f, key, PathFact(x, emptyList(), Tail.ANY_TAINT, t))
            f.statement(key, 1, summary(keepEdge(x, e = ExclusionSet.of(name))))
            f.statement(key, 2, StatementSummary(setOf(x, y, z), listOf(
                MicroEdge.of(keepEdge(x)),
                MicroEdge.of(starEdge(x, listOf(name), y, emptyList())),
                MicroEdge.of(starEdge(x, listOf(email), z, emptyList())),
            ), emptyList(), emptyMap()))
            val excluded = sink("name read excluded", f.pattern(y, tail = Tail.ANY, mark = t))
            val admitted = sink("email read retained", f.pattern(z, tail = Tail.ANY, mark = t))
            f.exit(key, 3, exit(excluded, admitted))
            val demand = listOf(x, y, z).map { base -> key to DemandPattern(ZERO_PATTERN,
                f.pattern(base, tail = Tail.ANY, mark = MarkSlot.STAR)) }
            val actual = run(f, f.config(index = index, L = maxOf(1, index - 1), roots = listOf(key),
                demand = demand, seeds = listOf(seed)), listOf(name, email), listOf(t.mark))
            if (index == 1) {
                val cover = selected(f, index, Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, true))
                assertEquals(setOf(cover), raw(actual, key, x))
                for (base in listOf(y, z)) {
                    assertEquals(setOf(selected(f, index, Conclusion(PathFact(base, emptyList(), Tail.ANY_TAINT, t),
                        ExclusionSet.Empty, true))), raw(actual, key, base))
                }
                assertEquals(mapOf(excluded.rule.id to setOf(Layer.DEMAND), admitted.rule.id to setOf(Layer.DEMAND)),
                    witnesses(actual))
                assertTrue(actual.requests.isEmpty())
                return@use
            }
            val kept = Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(name), false)
            val read = Conclusion(PathFact(z, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, false)
            assertEquals(setOf(kept), raw(actual, key, x))
            assertTrue(raw(actual, key, y).isEmpty())
            assertEquals(setOf(read), raw(actual, key, z))
            assertEquals(mapOf(admitted.rule.id to setOf(Layer.NORMAL)), witnesses(actual))
            assertEquals(setOf(read), actual.witnesses.single().edges.single().facts)
            assertTrue(actual.processed.none { it.method == key && it.node == method.inst(3) && it.conclusion.fact.base == y })
            assertTrue(actual.selectedDemand.isEmpty() && actual.requests.isEmpty())
        }
    }

    @Test
    fun `all three primitive cleaner reaches at a real call preserve their specified three sink outcomes`(): Unit {
        for (index in listOf(1, 3)) for (reach in CleanReach.entries) FiniteProgramFixtures().use { f ->
            val method = f.method("callCleaner$reach", listOf(listOf(1), listOf(2), emptyList()), 2)
            val key = method.key()
            val a = f.field("f")
            val b = f.field("g")
            val k = f.field("k")
            val t = MarkSlot.Concrete(f.mark("T"))
            val seed = source(f, key, PathFact(x, emptyList(), Tail.ANY_TAINT, t))
            f.call(key, 1, CallPlan(setOf(x), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED,
                    listOf(CleanStep.Clean(Cleaner(x, f.ap.path(listOf(a)), reach, t.mark)))),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(x), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val atField = sink("at f", f.pattern(x, listOf(a), mark = t))
            val belowField = sink("below f", f.pattern(x, listOf(a, b), mark = t))
            val elsewhere = sink("at k", f.pattern(x, listOf(k), mark = t))
            f.exit(key, 2, exit(atField, belowField, elsewhere))
            val demand = listOf(key to DemandPattern(ZERO_PATTERN, f.pattern(x, tail = Tail.ANY, mark = MarkSlot.STAR)))
            val actual = run(f, f.config(index = index, L = maxOf(1, index - 1), roots = listOf(key),
                demand = demand, seeds = listOf(seed)), listOf(a, b, k), listOf(t.mark))
            if (index == 1) {
                val cover = selected(f, index, Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, true))
                assertEquals(setOf(cover), raw(actual, key, x))
                assertEquals(listOf(atField, belowField, elsewhere).associate { it.rule.id to setOf(Layer.DEMAND) }, witnesses(actual))
                assertTrue(actual.witnesses.all { it.edges.single().facts == setOf(cover) && it.edges.single().premise == zero })
                assertTrue(actual.requests.isEmpty() && actual.links.isEmpty())
                return@use
            }
            val kept = Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.of(a), false)
            val exact = Conclusion(PathFact(x, listOf(a), Tail.EXACT, t), ExclusionSet.Empty, false)
            val demoted = Conclusion(PathFact(x, emptyList(), Tail.ANY_TAINT, t), ExclusionSet.Empty, true)
            val expectedFacts = when (reach) {
                CleanReach.AT_AND_BELOW -> setOf(kept)
                CleanReach.BELOW -> setOf(kept, exact)
                CleanReach.EXACT -> setOf(demoted)
            }
            val expectedSinks = when (reach) {
                CleanReach.AT_AND_BELOW -> mapOf(elsewhere.rule.id to setOf(Layer.NORMAL))
                CleanReach.BELOW -> mapOf(atField.rule.id to setOf(Layer.NORMAL), elsewhere.rule.id to setOf(Layer.NORMAL))
                CleanReach.EXACT -> mapOf(atField.rule.id to setOf(Layer.DEMAND),
                    belowField.rule.id to setOf(Layer.DEMAND), elsewhere.rule.id to setOf(Layer.DEMAND))
            }
            assertEquals(expectedFacts, raw(actual, key, x), "index=$index reach=$reach")
            assertEquals(expectedSinks, witnesses(actual), "index=$index reach=$reach")
            for (witness in actual.witnesses) {
                val support = when {
                    reach == CleanReach.EXACT -> demoted
                    witness.rule === atField.rule -> exact
                    else -> kept
                }
                assertEquals(setOf(support), witness.edges.single().facts)
                assertEquals(zero, witness.edges.single().premise)
                assertFalse(witness.confirmed)
            }
            val selectedData = actual.selectedDemand.filter { it.conclusion.fact.base == x }.toSet()
            assertEquals(if (reach == CleanReach.EXACT) setOf(ObservedEdge(key, zero, demoted)) else emptySet(),
                selectedData)
            assertTrue(actual.requests.isEmpty() && actual.links.isEmpty())
        }
    }

    @Test
    fun `actual current input authorizes a normal exact answer even when old exit guidance names another mark`(): Unit {
        for (index in listOf(1, 3)) for (matchingExit in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val callerMethod = f.method("publicationCaller", listOf(listOf(1), listOf(2), emptyList()), 2)
            val calleeMethod = f.method("publicationCallee", listOf(listOf(1), emptyList()), 1)
            val caller = callerMethod.key()
            val callee = calleeMethod.key()
            val field = f.field("f")
            val t = MarkSlot.Concrete(f.mark("T"))
            val u = MarkSlot.Concrete(f.mark("U"))
            val seed = source(f, caller, PathFact(x, emptyList(), Tail.EXACT, t))
            f.call(caller, 1, CallPlan(setOf(x, y, returned), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(x), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK,
                    summary(starEdge(returned, emptyList(), y, emptyList()), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            f.statement(callee, 0, summary(ZERO_KEEP, PathEdge(PathFact(x, emptyList(), Tail.EXACT, t),
                PathFact(returned, listOf(field), Tail.EXACT, t), ExclusionSet.Empty)))
            val initial = f.pattern(x, mark = t)
            val target = Conclusion(PathFact(returned, listOf(field), Tail.EXACT, t), ExclusionSet.Empty, false)
            val expectedRaw = ObservedEdge(callee, setOf(if (index == 1) GroupedScalarMarks(f.ap.marks).pattern(initial) else initial), selected(f, index, target))
            val demand = listOf(
                callee to DemandPattern(initial, f.pattern(returned, listOf(field), mark = if (matchingExit) t else u)),
                caller to DemandPattern(ZERO_PATTERN, f.pattern(y, tail = Tail.ANY, mark = MarkSlot.STAR)),
            )
            val actual = run(f, f.config(index = index, L = maxOf(1, index - 1), roots = listOf(caller),
                demand = demand, seeds = listOf(seed)), listOf(field), listOf(t.mark, u.mark))
            assertContains(actual.rawSummaries, expectedRaw)
            if (index == 1) assertContains(actual.selectedDemand, expectedRaw)
            else assertTrue(actual.selectedDemand.none { it.method == callee })
            val publishes = true // actual input authorizes its response independently of the old exit guide
            assertEquals(if (publishes) setOf(expectedRaw) else emptySet(),
                actual.publications.filter { it.method == callee && it.conclusion.fact.base == returned }.toSet())
            assertEquals(if (publishes) setOf(selected(f, index, Conclusion(PathFact(y, listOf(field), Tail.EXACT, t),
                ExclusionSet.Empty, false))) else emptySet(), raw(actual, caller, y))
            if (index == 3) assertTrue(actual.requests.isEmpty())
        }
    }
}
