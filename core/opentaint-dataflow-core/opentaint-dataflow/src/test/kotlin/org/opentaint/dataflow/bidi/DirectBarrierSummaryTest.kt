package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.ap.ifds.AccessPathBase
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.driver.EndReason
import org.opentaint.dataflow.bidi.driver.FixedLimits
import org.opentaint.dataflow.bidi.driver.ReportState
import org.opentaint.dataflow.bidi.fixtures.*
import org.opentaint.dataflow.bidi.interp.*
import kotlin.test.*

class DirectBarrierSummaryTest {
    @Test
    fun `an exact forward exit demand keeps refinement live without processed demand or links`(): Unit = DriverFixtures().use { f ->
        val base = AccessPathBase.LocalVar(0)
        val ret = AccessPathBase.Return
        val t = MarkSlot.Concrete(f.program.mark("summary-only T"))
        val root = f.program.method("summary-only demand", listOf(listOf(1), emptyList()), 1).key()
        val source = PathEdge(ZERO_FACT, PathFact(base, emptyList(), Tail.EXACT, t), ExclusionSet.Empty)
        val may = PathEdge(source.to, PathFact(ret, emptyList(), Tail.ANY, t), ExclusionSet.Empty)
        val sink = SinkRule(f.rule("summary-only sink"), 0,
            listOf(f.program.pattern(ret, tail = Tail.ANY, mark = t)), emptyList())
        f.program.entry(root, RuleStatement(f.summary(source, ZERO_KEEP), StatementSummary.EMPTY, emptyList()))
        f.program.exit(root, 1, ExitRules(RuleStatement(f.summary(may, ZERO_KEEP),
            StatementSummary.EMPTY, listOf(sink)), false, emptySet()))
        for (reference in listOf(
            HistoricalConcreteIteration(f.program, listOf(root), listOf(1, 1, 2)).run(),
            DirectCurrentIteration(f.program, listOf(root), listOf(1, 1, 2)).run())) {
            val exact = reference.runs.last().observation
            assertTrue(exact.processed.all { !it.conclusion.demand })
            assertTrue(exact.links.isEmpty())
            assertContains(exact.rawSummaries, ObservedEdge(root, setOf(ZERO_PATTERN),
                Conclusion(PathFact(ret, emptyList(), Tail.ANY, t), ExclusionSet.Empty, true)))
            assertEquals(listOf(1, 2, 3), reference.runs.map { it.index }, "Raw summaries count as demand objects at the barrier")
            assertEquals(mapOf(HistoricalFinding(sink.rule, f.program.inst(root, 1)) to HistoricalState.DEMAND), reference.findings)
        }
        val report = f.driver(FixedLimits(listOf(1, 1, 2)), run1MarkMode = MarkMode.GROUP).analyze(listOf(root))
        assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
        assertTrue(f.results.first().demandLayerEdges > 0)
        assertEquals(EndReason.POLICY, report.end.reason)
        assertEquals(ReportState.DEMAND, report.entries.single().state)
    }
}
