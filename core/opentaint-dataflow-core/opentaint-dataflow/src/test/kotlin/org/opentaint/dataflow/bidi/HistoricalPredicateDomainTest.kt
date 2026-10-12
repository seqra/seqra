package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.Seed
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.fixtures.HistoricalF71
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HistoricalPredicateDomainTest {
    @Test
    fun `historical representation domain rejects new source sink and compressed seed forms even with old opt ins`(): Unit {
        for (placement in listOf("statement source", "entry source", "exit source", "exit sink", "call sink", "seed")) {
            DriverFixtures().use { f ->
                val p = f.program
                val root = p.method("out of F71 $placement", listOf(listOf(1), emptyList()), 1).key()
                val builder = RulePredicate.Builder(p.cancellation)
                val predicate = builder.build(builder.leaf(p.pattern()))
                val form = FormKey(FormOwner(), root.method, root, p.inst(root, 1))
                val sourceId = RuleOccurrenceId(form, RuleKind.EXIT_SOURCE, 0)
                val source = SourceRuleOccurrence(sourceId, predicate, listOf(SourceAction(
                    ActionId(sourceId, ActionRole.SOURCE, 0), p.pattern(AccessPathBase.Return, tail = Tail.ANY_TAINT).fact)))
                val sinkId = RuleOccurrenceId(form, RuleKind.EXIT_SINK, 0)
                val sink = SinkRule(f.rule("new representation"), 0, predicate.leaves, emptyList(), sinkId, predicate)
                val sourceSummary = StatementSummary(emptySet(), emptyList(), emptyList(), emptyMap(), sources = listOf(source))
                when (placement) {
                    "statement source" -> p.statement(root, 1, sourceSummary)
                    "entry source" -> p.entry(root, RuleStatement(sourceSummary, StatementSummary.EMPTY, emptyList()))
                    "exit source" -> p.exit(root, 1, ExitRules(RuleStatement(sourceSummary, StatementSummary.EMPTY,
                        emptyList()), false, emptySet()))
                    "exit sink" -> p.exit(root, 1, ExitRules(RuleStatement(StatementSummary.EMPTY,
                        StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
                    "call sink" -> p.call(root, 1, CallPlan(emptySet(), emptyList(), listOf(sink),
                        CallPoint.BEFORE, CallPoint.AFTER))
                }
                val config = p.config(index = 2, roots = listOf(root),
                    seeds = if (placement == "seed") listOf(Seed.PredicateSink(sinkId, root, p.inst(root, 1))) else emptyList())
                val failure = assertFailsWith<IllegalStateException> {
                    HistoricalF71.validate(p, p.inputs(config), allowEndFacts = true, allowMustSources = true)
                }
                assertTrue(failure.message.orEmpty().contains("F71"), placement)
                assertTrue(failure.message.orEmpty().contains("compiled"), placement)
            }
        }
    }
}
