package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.MethodAnalysisFixtures
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

/** Production actor regressions for the former unchanged-fact feedback loop. */
class Run1LoopTerminationInvestigationTest {
    private val x = AccessPathBase.Argument(0)
    private val y = AccessPathBase.LocalVar(0)

    private fun source(f: MethodAnalysisFixtures, owner: FormOwner, statement: Int,
                       from: AccessPathBase, to: AccessPathBase): SourceRuleOccurrence {
        val id = RuleOccurrenceId(FormKey(owner, f.key.method, f.key, f.inst(statement)), RuleKind.CALL_SOURCE, 0)
        val builder = RulePredicate.Builder(f.cancellation, f.manager.marks)
        val predicate = builder.build(builder.leaf(f.pattern(from)))
        val grouped = RulePredicate.Builder(f.cancellation, f.manager.marks)
        val groupedPredicate = grouped.build(grouped.leaf(f.run1Pattern(from)))
        return SourceRuleOccurrence(id, predicate,
            listOf(SourceAction(ActionId(id, ActionRole.SOURCE, 0), f.pattern(to).fact)),
            groupTransfers = true, unaryTransfers = listOf(SourceUnaryTransfer(groupedPredicate, f.pattern(from))))
    }

    private fun graph(loopSource: Boolean, plainCopy: Boolean = false, nonZero: Boolean = false,
                      duplicateInitial: Boolean = false,
                      observe: (MethodAnalysisFixtures, RunMethodAnalyzer) -> Unit) {
        MethodAnalysisFixtures(listOf(listOf(1), listOf(2), listOf(2, 3), emptyList()), normalExit = 3).use { f ->
            val owner = FormOwner()
            val entry = listOf(ZERO_KEEP, PathEdge(ZERO_FACT, f.pattern(x).fact, ExclusionSet.Empty))
            f.entryRules(RuleStatement(StatementSummary(setOf(AccessPathBase.Zero), entry.map { MicroEdge.of(it) },
                emptyList(), emptyMap()), StatementSummary.EMPTY, emptyList()))
            if (plainCopy) {
                f.statement(1, StatementSummary(setOf(x), listOf(MicroEdge.of(
                    PathEdge(f.pattern(x).fact, f.pattern(y).fact, ExclusionSet.Empty))), emptyList(), emptyMap()))
                if (loopSource) f.statement(2, StatementSummary(setOf(y), listOf(MicroEdge.of(
                    PathEdge(f.pattern(y).fact, f.pattern(y).fact, ExclusionSet.Empty))), emptyList(), emptyMap()))
            } else {
                f.statement(1, StatementSummary(emptySet(), emptyList(), emptyList(), emptyMap(),
                    sources = listOf(source(f, owner, 1, x, y))))
                if (loopSource) f.statement(2, StatementSummary(setOf(y), emptyList(), emptyList(), emptyMap(),
                    sources = listOf(source(f, owner, 2, y, y))))
            }
            val initial = f.manager.initial(f.run1Pattern(x))
            val actor = f.core(localFactory = MethodLocalServicesFactory(::RunMethodLocalServices))
            if (nonZero) {
                actor.addInitial(initial)
                if (duplicateInitial) actor.addInitial(initial)
            } else actor.addRootZero()
            observe(f, actor)
        }
    }

    @Test fun `conditional source self loop reaches an ordinary AP fixed point`() = checkLoop(plainCopy = false)
    @Test fun `plain marked copy self loop reaches an ordinary AP fixed point`() = checkLoop(plainCopy = true)
    @Test fun `a legal nonzero exact initial closes the same marked copy cycle`() =
        checkLoop(plainCopy = true, nonZero = true)
    @Test fun `a repeated actual initial does not reopen the closed cycle`() =
        checkLoop(plainCopy = true, nonZero = true, duplicateInitial = true)

    private fun checkLoop(plainCopy: Boolean, nonZero: Boolean = false, duplicateInitial: Boolean = false) {
        graph(loopSource = true, plainCopy = plainCopy, nonZero = nonZero,
            duplicateInitial = duplicateInitial) { f, actor ->
            closeWithinBound(f, actor)
            val seen = f.processed.filter { it.node.location.index == 2 && it.facts.base == y }
            assertTrue(seen.isNotEmpty(), "The actual loop body must run")
            assertEquals(1, seen.map { it.premise }.distinct().size)
            assertEquals(1, seen.map { f.ops.leaves(it.facts).toList() }.distinct().size)
            assertTrue(seen.all { it.facts.layer == Layer.DEMAND })
            assertEquals(1, actor.edges.edgesAt(f.inst(2), seen.first().premise).count { it.second.base == y })
            val processed = f.processed.size
            repeat(4) { assertTrue(actor.work(1)); f.pumpQuestions() }
            assertEquals(processed, f.processed.size, "An unchanged closed relation must remain quiescent")
        }
    }

    @Test fun `the acyclic source control still reaches the exit`() {
        graph(loopSource = false) { f, actor ->
            closeWithinBound(f, actor)
            assertTrue(f.processed.any { it.node.location.index == 3 && it.facts.base == y })
        }
    }

    private fun closeWithinBound(f: MethodAnalysisFixtures, actor: RunMethodAnalyzer) {
        var complete = false
        repeat(32) {
            if (!complete) complete = actor.work(1)
            f.pumpQuestions()
        }
        assertTrue(complete, "The finite one-group CFG must close in 32 bounded work quanta")
        assertTrue(actor.work(1))
    }
}
