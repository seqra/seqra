package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.interp.CallPoint.*
import org.opentaint.dataflow.bidi.interp.StageKind.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/** The discarded original must remain recoverable through actual FULL backward inputs. */
class RepresentativeRefinementTest {
    @Test fun `B2 recovers both FULL inputs after R1 keeps only the first argument`() {
        DriverFixtures().use { f ->
            val p = f.program
            p.enableGroupRuleViews()
            val main = p.method("representative main", listOf(listOf(1), listOf(2), emptyList()), 2).key()
            val callee = p.method("representative combine", listOf(listOf(1), emptyList()), 1).key()
            val x = AccessPathBase.LocalVar(0)
            val y = AccessPathBase.LocalVar(1)
            val a = AccessPathBase.Argument(0)
            val b = AccessPathBase.Argument(1)
            val ret = AccessPathBase.Return
            val t = MarkSlot.Concrete(p.mark("T"))
            val u = MarkSlot.Concrete(p.mark("U"))
            val v = MarkSlot.Concrete(p.mark("V"))
            val first = PathEdge(ZERO_FACT, p.pattern(x, mark = t).fact, ExclusionSet.Empty)
            val second = PathEdge(ZERO_FACT, p.pattern(y, mark = u).fact, ExclusionSet.Empty)
            p.entry(main, RuleStatement(f.summary(ZERO_KEEP, first, second), StatementSummary.EMPTY, emptyList()))
            p.call(main, 1, CallPlan(setOf(x, y, a, b, ret), listOf(
                CallStage.Edges(BEFORE, BOUND, BIND_IN, f.summary(ZERO_KEEP,
                    starEdge(x, emptyList(), a, emptyList()), starEdge(y, emptyList(), b, emptyList()))),
                CallStage.Actions(BOUND, ADDED, emptyList()),
                CallStage.Callees(ADDED, RETURNED, listOf(callee)),
                CallStage.Edges(RETURNED, AFTER, BIND_BACK, f.summary(ZERO_KEEP, keepEdge(ret))),
            ), emptyList(), BEFORE, AFTER))
            val builder = RulePredicate.Builder(p.cancellation, p.ap.marks)
            val predicate = builder.build(builder.and(listOf(
                builder.leaf(p.pattern(a, mark = t)), builder.leaf(p.pattern(b, mark = u)))))
            val id = RuleOccurrenceId(FormKey(FormOwner(), callee.method, callee, p.inst(callee, 0)), RuleKind.ENTRY_SOURCE, 0)
            val action = SourceAction(ActionId(id, ActionRole.SOURCE, 0), p.pattern(ret, tail = Tail.ANY_TAINT, mark = v).fact)
            val source = SourceRuleOccurrence(id, predicate, listOf(action))
            p.entry(callee, RuleStatement(StatementSummary(setOf(a, b), listOf(MicroEdge.of(ZERO_KEEP)),
                emptyList(), emptyMap(), sources = listOf(source)), StatementSummary.EMPTY, emptyList()))
            val sink = SinkRule(f.rule("representative result"), 0, listOf(p.pattern(ret, mark = v)), emptyList())
            p.exit(main, 2, ExitRules(RuleStatement(StatementSummary.EMPTY, StatementSummary.EMPTY, listOf(sink)), false, emptySet()))

            val report = f.driver(FixedLimits(listOf(1, 1, 2)), 20.seconds).analyze(listOf(main))
            assertTrue(f.results.all { it.status == RunStatus.COMPLETE })
            val run1 = f.results.single { it.runIndex == 1 }
            val outputs = run1.analyzers.single { it.key == callee }.summaries.all().toList()
                .filter { (_, result) -> result.base == ret && result is TaintTree }
            assertTrue(outputs.isNotEmpty(), "A complete GROUP binding emits the callee result")
            assertTrue(outputs.all { (initial, result) -> initial is InitialAp && initial.base == a && result.layer == Layer.DEMAND })
            val backward = f.results.single { it.runIndex == 2 }
            val recovered = backward.analyzers.flatMap { it.sourceHits?.entries()?.toList().orEmpty() }
            assertTrue(recovered.any { it.first == main && it.third == first }, "The chosen input has its real source")
            assertTrue(recovered.any { it.first == main && it.third == second }, "The omitted input also has its real source")
            assertTrue(report.entries.any { it.key.rule == sink.rule })
            val run3 = f.results.single { it.runIndex == 3 }
            assertTrue(run3.analyzers.single { it.key == callee }.summaries.all().any { (premise, result) ->
                premise is PremiseSet && premise.members.map { it.base }.toSet() == setOf(a, b) && result.base == ret
            }, "FULL forward analysis restores the complete conjunction")
        }
    }
}
