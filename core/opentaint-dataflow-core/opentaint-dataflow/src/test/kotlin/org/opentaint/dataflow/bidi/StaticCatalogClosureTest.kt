package org.opentaint.dataflow.bidi

import org.junit.jupiter.api.Test
import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import org.opentaint.dataflow.bidi.ap.ReferenceRequestKind as RequestKind
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSink
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import kotlin.test.*

/** New catalog acceptance is separate from the unmodified scalar closure and transport assertions. */
class StaticCatalogClosureTest {
    private val returned = AccessPathBase.Return
    private val result = AccessPathBase.Argument(1)
    private val x = AccessPathBase.Argument(0)
    private val zero = setOf(ZERO_PATTERN)

    private fun summary(vararg edges: PathEdge): StatementSummary =
        StatementSummary(edges.map { it.from.base }.toSet(), edges.map(MicroEdge::of), emptyList(), emptyMap())

    private fun call(targets: List<MethodKey>, input: AccessPathBase = STATIC,
                     steps: List<CleanStep> = emptyList()): CallPlan = CallPlan(setOf(input, STATIC, returned, result), listOf(
        CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(input), keepEdge(STATIC), ZERO_KEEP)),
        CallStage.Actions(BOUND, ADDED, emptyList()), CallStage.Callees(ADDED, RETURNED, targets),
        CallStage.Actions(RETURNED, REWRITTEN, steps),
        CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(STATIC), keepEdge(input),
            starEdge(returned, emptyList(), result, emptyList()), ZERO_KEEP)),
    ), emptyList(), BEFORE, AFTER)

    private fun rule(name: String): CommonTaintConfigurationSink = object : CommonTaintConfigurationSink {
        override val id = name
        override val meta = object : CommonTaintConfigurationSinkMeta {
            override val message = name
            override val severity = CommonTaintConfigurationSinkMeta.Severity.Warning
        }
    }

    private fun compare(f: FiniteProgramFixtures, config: RunConfig, fields: List<Int>, marks: List<TaintMark>,
                        sameUnit: Boolean, policy: SchedulePolicy, seed: Long = 0,
                        expectedRoot: ObservedEdge? = null): RunObservation {
        val expected = NaiveClosure(f, config, semantics = NaiveClosure.Semantics.SELECTED).run() // Independent selected scalar request/field/default semantics.
        expectedRoot?.let { assertContains(expected.rawSummaries, it, "The scalar program must return the wrapper value") }
        val scheduler = ProductionSchedule(f, config, sameUnit, policy, seed, quantum = 1,
            duplicateDeliveries = true, useStaticCatalog = true)
        val actual = scheduler.run()
        val universe = BoundedDenotation(listOf(AccessPathBase.Zero, STATIC, x, result, returned, AccessPathBase.Argument(7)),
            (fields + f.field("fresh")).distinct(), (marks + f.mark("fresh") + TaintMark.ZERO).distinct(), 2)
        try {
            expected.assertCatalogRefinement(actual, universe, config.roots)
        } catch (error: AssertionError) {
            throw AssertionError("Catalog refinement failed: sameUnit=$sameUnit policy=$policy seed=$seed " +
                "limit=${config.fieldLimit} program=${f.describe()}\nscalar=$expected\ncatalog=$actual\ntrace=${scheduler.trace}", error)
        }
        return actual
    }

    @Test
    fun `catalog supplies deepest static reads through chains diamonds and recursion in both unit layouts`(): Unit {
        for (shape in listOf("chain", "diamond", "recursive")) for (limit in 1..1)
            for (sameUnit in listOf(false, true)) for (policy in listOf(SchedulePolicy.FIFO,
                SchedulePolicy.REPLAY_FIRST, SchedulePolicy.NOTIFY_FIRST)) FiniteProgramFixtures().use { f ->
                val caller = f.method("$shape caller", listOf(listOf(1), listOf(2), emptyList()), 2).key("root")
                val wrapperGraph = listOf(listOf(1), listOf(2), listOf(3), emptyList())
                val middle = f.method("$shape wrapper", wrapperGraph, 3).key("middle")
                val getter = f.method("$shape reader", listOf(listOf(1), emptyList()), 1).key("reader")
                val cls = f.ap.accessors.index(ClassStaticAccessor("Box"))
                val field = f.field("g"); val path = listOf(cls, field)
                val t = MarkSlot.Concrete(f.mark("T")); val u = MarkSlot.Concrete(f.mark("U"))
                val source = PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, t), ExclusionSet.Empty)
                f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
                val first = if (shape == "diamond") {
                    val other = f.method("diamond other wrapper", wrapperGraph, 3).key("other")
                    f.call(other, 1, call(listOf(getter)))
                    f.statement(other, 2, summary(starEdge(result, emptyList(), returned, emptyList()), ZERO_KEEP))
                    listOf(middle, other)
                } else listOf(middle)
                f.call(caller, 1, call(first))
                f.call(middle, 1, call(if (shape == "recursive") listOf(getter, middle) else listOf(getter)))
                // A call binds the reader Return into wrapper Arg1; the wrapper must explicitly return it.
                f.statement(middle, 2, summary(starEdge(result, emptyList(), returned, emptyList()), ZERO_KEEP))
                f.statement(getter, 0, summary(PathEdge(PathFact(STATIC, path, Tail.EXACT, t),
                    PathFact(returned, emptyList(), Tail.EXACT, u), ExclusionSet.Empty)))
                val sink = SinkRule(rule("$shape static finding"), 0,
                    listOf(f.pattern(returned, mark = u)), emptyList())
                f.exit(getter, 1, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY,
                    listOf(sink)), false, emptySet()))
                val expectedReturn = ObservedEdge(caller, zero,
                    Conclusion(PathFact(result, emptyList(), Tail.EXACT, u), ExclusionSet.Empty, true))
                val actual = compare(f, f.config(L = limit, roots = listOf(caller)), path, listOf(t.mark, u.mark),
                    sameUnit, policy, expectedRoot = expectedReturn)
                assertContains(actual.rawSummaries, expectedReturn)
                assertTrue(actual.witnesses.isNotEmpty())
                assertTrue(actual.witnesses.all { it.rule === sink.rule && it.methodKey == getter &&
                    it.edges.all { edge -> edge.layer == Layer.DEMAND && edge.premise == setOf(f.pattern(STATIC, path, mark = t)) } })
                assertTrue(actual.sourceHits.isEmpty())
            }
    }

    @Test
    fun `ordinary static default demotes possible mutations without emitting a static root initial`(): Unit {
        for (effect in listOf("empty", "kill other field", "clean other field", "kill supplied field", "clean supplied field"))
            for (limit in 1..1) for (sameUnit in listOf(false, true)) FiniteProgramFixtures().use { f ->
                val caller = f.method("caller $effect", listOf(listOf(1), listOf(2), emptyList()), 2).key("caller")
                val callee = f.method("callee $effect", listOf(listOf(1), emptyList()), 1).key("callee")
                val cls = f.ap.accessors.index(ClassStaticAccessor("Box"))
                val field = f.field("g"); val other = f.field("f"); val path = listOf(cls, field)
                val t = MarkSlot.Concrete(f.mark("T"))
                val source = PathEdge(ZERO_FACT, PathFact(STATIC, path, Tail.EXACT, t), ExclusionSet.Empty)
                f.entry(caller, RuleStatement(summary(ZERO_KEEP, source), StatementSummary.EMPTY, emptyList()))
                val changed = listOf(cls, if (effect.endsWith("supplied field")) field else other)
                val steps = when {
                    effect.startsWith("kill") -> listOf(CleanStep.Kill(MicroEdgeBuilder().apply {
                        write(STATIC, changed, weak = false, values = emptyList(), aliasPaths = emptyList())
                    }.build()))
                    effect.startsWith("clean") -> listOf(CleanStep.Clean(Cleaner(STATIC, f.ap.path(changed), CleanReach.EXACT, t.mark)))
                    else -> emptyList()
                }
                f.call(caller, 1, call(listOf(callee)))
                if (steps.isNotEmpty()) f.call(callee, 0, CallPlan(setOf(STATIC), listOf(
                    CallStage.Edges(BEFORE, BOUND, BIND_IN, summary(keepEdge(STATIC), ZERO_KEEP)),
                    CallStage.Actions(BOUND, ADDED, emptyList()),
                    CallStage.Edges(ADDED, RETURNED, UNRESOLVED, summary(keepEdge(STATIC), ZERO_KEEP)),
                    CallStage.Actions(RETURNED, REWRITTEN, steps),
                    CallStage.Edges(REWRITTEN, AFTER, BIND_BACK, summary(keepEdge(STATIC), ZERO_KEEP)),
                ), emptyList(), BEFORE, AFTER))
                val actual = compare(f, f.config(L = limit, roots = listOf(caller)), listOf(cls, field, other),
                    listOf(t.mark), sameUnit, SchedulePolicy.NOTIFY_FIRST)
                val expected = ObservedEdge(caller, zero, Conclusion(source.to, ExclusionSet.Empty, true))
                assertContains(actual.rawSummaries, expected, effect)
                assertTrue(actual.initials.none { it.method == callee && it.initial.fact.base == STATIC &&
                    it.initial.fact.path.isEmpty() }, "Defaults are stored summaries, never root initials")
                val root = f.pattern(STATIC, tail = Tail.STAR, mark = MarkSlot.STAR)
                assertContains(actual.rawSummaries, ObservedEdge(callee, setOf(root),
                    Conclusion(root.fact, ExclusionSet.Empty, true)))
                assertTrue(actual.witnesses.isEmpty())
            }
    }

    @Test
    fun `static copies local copies and argument writes keep demands on their original chains with late suppliers`(): Unit {
        for (origin in listOf("static copy", "static local", "argument")) for (limit in 1..1)
            for (sameUnit in listOf(false, true)) for (seed in 0L..3L) FiniteProgramFixtures().use { f ->
                val caller = f.method("$origin caller", listOf(listOf(1, 2), listOf(3), listOf(3), listOf(4), emptyList()), 4).key("caller")
                val callee = f.method("$origin callee", listOf(listOf(1), listOf(2), emptyList()), 2).key("callee")
                val cls = f.ap.accessors.index(ClassStaticAccessor("Box"))
                val g = f.field("g"); val field = f.field("f")
                val originalPath = if (origin == "argument") emptyList() else listOf(cls, g)
                val originalBase = if (origin == "argument") x else STATIC
                val targetBase = if (origin == "static local") returned else STATIC
                val targetPath = if (targetBase == STATIC) listOf(cls, field) else emptyList()
                val t = MarkSlot.Concrete(f.mark("T"))
                val source = PathEdge(ZERO_FACT, PathFact(originalBase, originalPath, Tail.EXACT, t), ExclusionSet.Empty)
                // One branch reaches the call before the other branch publishes this source.
                f.statement(caller, 1, summary(ZERO_KEEP, source))
                f.call(caller, 3, call(listOf(callee), input = originalBase))
                f.statement(callee, 0, summary(starEdge(originalBase, originalPath, targetBase, targetPath)))
                val sink = SinkRule(rule("$origin sink"), 0, listOf(f.pattern(targetBase, targetPath, mark = t)), emptyList())
                f.exit(callee, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
                val actual = compare(f, f.config(L = limit, roots = listOf(caller)), listOf(cls, g, field),
                    listOf(t.mark), sameUnit, SchedulePolicy.SEEDED, seed)
                assertTrue(actual.witnesses.isNotEmpty(), origin)
                val original = f.pattern(originalBase, originalPath, Tail.STAR, MarkSlot.STAR)
                assertContains(actual.requests, ObservedRequest(callee, original, RequestKind.Mark(t.mark)),
                    "The changed checked fact must request on the original $origin chain")
                assertTrue(actual.witnesses.all { w -> w.edges.all { edge -> edge.layer == Layer.DEMAND &&
                    edge.premise == setOf(f.pattern(originalBase, originalPath, mark = t)) } })
                val inputLayer = if (originalBase == STATIC) Layer.DEMAND else Layer.NORMAL
                assertTrue(actual.links.any { it.callee == callee && it.caller == caller && it.call == f.inst(caller, 3) &&
                    it.premise == zero && it.callerLayer == inputLayer && it.linkLayer == inputLayer &&
                    it.added == Pattern(source.to, ExclusionSet.Empty) })
            }
    }
}
