package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.store.RuleId
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** Root-shaped actual data is not the removed executable static-control initial. */
class Run1BroadStaticDataTest {
    private val arg = AccessPathBase.Argument(0)
    private val ret = AccessPathBase.Return
    private fun summary(vararg edges: PathEdge, sources: List<SourceRuleOccurrence> = emptyList(),
                        conjunctions: List<ConjunctiveEdge> = emptyList()): StatementSummary =
        StatementSummary(edges.mapTo(linkedSetOf()) { it.from.base }, edges.map(MicroEdge::of),
            conjunctions, emptyMap(), sources = sources)

    private fun rule(name: String): RuleId = object : CommonTaintConfigurationSink {
        override val id = name
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = name
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }
    private fun sink(name: String, pattern: Pattern) = SinkRule(rule(name), 0, listOf(pattern), emptyList())
    private fun root(mark: TaintMark) = PathFact(STATIC, emptyList(), Tail.ANY_TAINT, MarkSlot.Concrete(mark))
    private fun gen(target: PathFact) = PathEdge(ZERO_FACT, target, ExclusionSet.Empty)

    private fun compare(f: FiniteProgramFixtures, key: MethodKey, marks: List<TaintMark>): RunObservation {
        val config = f.config(roots = listOf(key))
        val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run()
        val actual = ProductionSchedule(f, config, sameUnit = false, policy = SchedulePolicy.NOTIFY_FIRST,
            duplicateDeliveries = true).run()
        val universe = BoundedDenotation(listOf(AccessPathBase.Zero, STATIC, arg, ret),
            listOf(f.field("fresh")), (marks + TaintMark.ZERO + f.mark("fresh")).distinct(), 2)
        expected.assertEquivalent(actual, universe)
        assertTrue(actual.initials.none { it.initial.fact.base == STATIC && it.initial.fact.path.isEmpty() },
            "A broad current value never starts a static-root analysis")
        return actual
    }

    @Test fun `zero-origin broad TAINT takes the ordinary scalar marked edge`() {
        MethodAnalysisFixtures().use { f ->
            val t = f.store.mark("T")
            val u = f.store.mark("U")
            val input = f.manager.factsOf(root(t), ExclusionSet.Empty, Layer.NORMAL)
            val edge = PathEdge(root(t).copy(tail = Tail.STAR),
                PathFact(ret, emptyList(), Tail.EXACT, MarkSlot.Concrete(u)), ExclusionSet.Empty)
            val core = f.core()
            val expected = CollectingOut()
            f.ops.applyEdge(input, f.manager.zero, edge, false, core.mode, expected)
            val actual = ArrayList<Facts>()
            core.applier.stage(summary(edge), f.manager.zero, input, Place(f.inst(0), false, false)) {
                    premise, value, _ ->
                assertSame(f.manager.zero, premise)
                actual += value
            }
            assertEquals(expected.results, actual)
            assertEquals(ret, actual.single().base)
            assertEquals(setOf(u), f.ops.leaves(actual.single()).map {
                (it.fact.mark as MarkSlot.Concrete).mark
            }.toSet())
            f.drain(core)
        }
    }

    @Test fun `entry sink and exit source predicate consume actual broad static TAINT`() {
        FiniteProgramFixtures().use { f ->
            val key = f.method("root data boundaries", listOf(listOf(1), emptyList()), 1).key()
            val t = f.mark("T"); val u = f.mark("U")
            val entrySink = sink("entry root mark", Pattern(root(t).copy(tail = Tail.ANY), ExclusionSet.Empty))
            f.entry(key, RuleStatement(summary(ZERO_KEEP, gen(root(t))), StatementSummary.EMPTY, listOf(entrySink)))
            val builder = RulePredicate.Builder(f.cancellation)
            val predicate = builder.build(builder.leaf(Pattern(root(t).copy(tail = Tail.ANY), ExclusionSet.Empty)))
            val id = RuleOccurrenceId(FormKey(FormOwner(), key.method, key, f.inst(key, 1)), RuleKind.EXIT_SOURCE, 0)
            val target = PathFact(ret, emptyList(), Tail.EXACT, MarkSlot.Concrete(u))
            val source = SourceRuleOccurrence(id, predicate, listOf(SourceAction(ActionId(id, ActionRole.SOURCE, 0), target)))
            val exitSink = sink("root-derived source", Pattern(target, ExclusionSet.Empty))
            f.exit(key, 1, ExitRules(RuleStatement(summary(keepEdge(STATIC), sources = listOf(source)),
                StatementSummary.EMPTY, listOf(exitSink)), false, emptySet()))
            val actual = compare(f, key, listOf(t, u))
            assertEquals(setOf(entrySink.rule, exitSink.rule), actual.witnesses.map { it.rule }.toSet())
            assertTrue(actual.witnesses.all { w -> w.edges.all { edge ->
                edge.premise == setOf(ZERO_PATTERN) && edge.layer == Layer.NORMAL
            } })
        }
    }

    @Test fun `call source end and unresolved stages do not strip actual broad static data`() {
        for (kind in listOf(SOURCES, END_FACTS, UNRESOLVED)) FiniteProgramFixtures().use { f ->
            val key = f.method("root data $kind", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val t = f.mark("T"); val u = f.mark("U")
            f.entry(key, RuleStatement(summary(ZERO_KEEP, gen(root(t))), StatementSummary.EMPTY, emptyList()))
            val target = PathFact(ret, emptyList(), Tail.EXACT, MarkSlot.Concrete(u))
            val boundSink = sink("bound root $kind", Pattern(root(t).copy(tail = Tail.ANY), ExclusionSet.Empty))
            f.call(key, 1, CallPlan(setOf(STATIC, ret), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Edges(ADDED, RETURNED, kind, summary(ZERO_KEEP,
                    PathEdge(root(t).copy(tail = Tail.STAR), target, ExclusionSet.Empty))),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(ret), ZERO_KEEP)),
            ), listOf(boundSink), BEFORE, AFTER))
            val afterSink = sink("after root $kind", Pattern(target, ExclusionSet.Empty))
            f.exit(key, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(afterSink)), false, emptySet()))
            val actual = compare(f, key, listOf(t, u))
            assertEquals(setOf(boundSink.rule, afterSink.rule), actual.witnesses.map { it.rule }.toSet(), "$kind")
        }
    }

    @Test fun `call cleaners remove selected broad mark or all marks while preserving unrelated mark`() {
        for (all in listOf(false, true)) FiniteProgramFixtures().use { f ->
            val key = f.method("root data cleaner $all", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val t = f.mark("T"); val u = f.mark("U")
            f.entry(key, RuleStatement(summary(ZERO_KEEP, gen(root(t)), gen(root(u))), StatementSummary.EMPTY, emptyList()))
            f.call(key, 1, CallPlan(setOf(STATIC), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, listOf(CleanStep.Clean(Cleaner(STATIC, null,
                    CleanReach.AT_AND_BELOW, if (all) null else t)))),
                CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(STATIC), ZERO_KEEP)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(STATIC), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            val tSink = sink("cleaned root T", Pattern(root(t).copy(tail = Tail.ANY), ExclusionSet.Empty))
            val uSink = sink("retained root U", Pattern(root(u).copy(tail = Tail.ANY), ExclusionSet.Empty))
            f.exit(key, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(tSink, uSink)), false, emptySet()))
            val actual = compare(f, key, listOf(t, u))
            assertEquals(if (all) emptySet() else setOf(uSink.rule), actual.witnesses.map { it.rule }.toSet())
            assertTrue(actual.rawSummaries.none { it.node == null && it.conclusion.fact.mark == MarkSlot.Concrete(t) })
        }
    }

    @Test fun `derived root FLOW requests on its actual nonstatic original and recovers concrete supply`() {
        FiniteProgramFixtures().use { f ->
            val caller = f.method("root data caller", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val reader = f.method("root data reader", listOf(listOf(1), emptyList()), 1).key()
            val t = f.mark("T")
            f.entry(caller, RuleStatement(summary(ZERO_KEEP,
                gen(PathFact(arg, emptyList(), Tail.EXACT, MarkSlot.Concrete(t)))), StatementSummary.EMPTY, emptyList()))
            f.call(caller, 1, CallPlan(setOf(arg, STATIC), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(arg), ZERO_KEEP)),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(reader)),
                CallStage.Actions(RETURNED, REWRITTEN, emptyList()),
                CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(STATIC), ZERO_KEEP)),
            ), emptyList(), BEFORE, AFTER))
            f.statement(reader, 0, summary(starEdge(arg, emptyList(), STATIC, emptyList()), ZERO_KEEP))
            val sink = sink("derived root mark", Pattern(root(t).copy(tail = Tail.ANY), ExclusionSet.Empty))
            f.exit(reader, 1, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                listOf(sink)), false, emptySet()))
            val actual = compare(f, caller, listOf(t))
            assertEquals(setOf(sink.rule), actual.witnesses.map { it.rule }.toSet())
            val original = Pattern(PathFact(arg, emptyList(), Tail.STAR, MarkSlot.STAR), ExclusionSet.Empty)
            assertContains(actual.requests, ObservedRequest(reader, original, RequestKind.Mark(t)))
            assertTrue(actual.processed.any { it.method == reader && it.premise == setOf(original) &&
                it.conclusion.fact.base == STATIC && it.conclusion.fact.path.isEmpty() && it.conclusion.demand })
        }
    }
}
