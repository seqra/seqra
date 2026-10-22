package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import org.opentaint.dataflow.bidi.store.DemandStore
import kotlin.test.*

class StaticProducerGrammarTest {
    private fun demand(f: MethodAnalysisFixtures) =
        DemandStore.Builder(f.manager).apply { completeRun1StaticFields() }.build()

    /** The backward source result is created inside this method, without an
     * added-link query or a static sink seed which could validate it first. */
    private fun backwardSource(f: MethodAnalysisFixtures, condition: Pattern): RunMethodAnalyzer {
        val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
        val predicate = builder.build(builder.leaf(condition))
        val occurrence = RuleOccurrenceId(FormKey(FormOwner(), f.key.method, f.key, f.inst(0)),
            RuleKind.ENTRY_SOURCE, 0)
        val target = f.pattern(AccessPathBase.Argument(0), mark = MarkSlot.Concrete(f.mark("U")))
        val action = SourceAction(ActionId(occurrence, ActionRole.SOURCE, 0), target.fact)
        val summary = StatementSummary(setOf(STATIC), emptyList(), emptyList(), emptyMap(),
            sources = listOf(SourceRuleOccurrence(occurrence, predicate, listOf(action))))
        f.entryRules(RuleStatement(summary, StatementSummary.EMPTY, emptyList()))
        return f.core(f.config(index = 2, demand = demand(f)),
            localFactory = MethodLocalServicesFactory(::RunMethodLocalServices)).also {
            it.addInitial(f.manager.initial(target))
        }
    }

    @Test fun `a backward conditional source cannot execute a broad static result inside its method`() {
        for (root in listOf(true, false)) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val condition = f.pattern(STATIC, if (root) emptyList() else listOf(cls), Tail.ANY)
            val actor = backwardSource(f, condition)
            val failure = assertFailsWith<IllegalStateException> { f.drain(actor) }
            assertContains(checkNotNull(failure.message), "Unsupported run-2 concrete static input")
            assertTrue(f.processed.none { it.facts.base == STATIC },
                "The unsupported result is diagnosed before it enters method work")
            assertTrue(actor.summaries.all().none { it.second.base == STATIC })
        }
    }

    @Test fun `backward source results keep exact class markers and bounded named fields`() {
        for (shape in 0..3) MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val field = f.field("f")
            val path = when (shape) {
                0 -> listOf(cls)
                3 -> listOf(cls, field, f.field("nested"))
                else -> listOf(cls, field)
            }
            val condition = f.pattern(STATIC, path, if (shape == 2) Tail.ANY else Tail.EXACT)
            val actor = backwardSource(f, condition)
            f.drain(actor)
            val leaves = actor.summaries.all().filter { it.second.base == STATIC }
                .flatMap { f.ops.leaves(it.second) }.toList()
            val expected = if (shape == 3) f.pattern(STATIC, listOf(cls, field), Tail.ANY,
                condition.fact.mark) else condition
            assertContains(leaves, expected)
        }
    }

    @Test fun `current named field results preserve MUST and field exclusion headers`() {
        MethodAnalysisFixtures().use { f ->
            val cls = f.manager.accessors.index(ClassStaticAccessor("C"))
            val path = listOf(cls, f.field("f"))
            val mark = MarkSlot.Concrete(f.mark("T"))
            val input = f.fact(STATIC, path, Tail.ANY_TAINT, mark,
                exclusion = ExclusionSet.of(f.field("excluded")))
            val actor = f.core(f.config(index = 2, demand = demand(f)))
            val premise = f.manager.initial(f.pattern(mark = mark))
            val bounded = ArrayList<Facts>()
            actor.cut(Cut.CALL, premise, input) { _, value -> bounded += value }
            assertEquals(listOf(input), bounded, "Current facts use anchor checks, not strict initial-header checks")
            actor.summaryDelta(premise, input)
            assertEquals(listOf(input), actor.summaries.all().map { it.second }.toList(),
                "Direct native/summary results keep their existing header too")
        }
    }
}
