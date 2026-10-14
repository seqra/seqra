package org.opentaint.dataflow.jvm.bidi

import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.bidi.fixtures.JIRPrescanFixtures
import org.opentaint.dataflow.jvm.bidi.fixtures.NoTaintRules
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Entry setup must consume the same budget that the real driver receives. */
class JIRBidiSetupBudgetTest : BidiJIRTestUtils() {
    @Test
    fun `gather selection and catalog preparation consume the caller positive total budget before the first run`(): Unit {
        val budget = 25.milliseconds
        var selections = 0
        val rules = object : TaintRulesProvider by NoTaintRules {
            override fun selectRules(ruleIds: Set<String>) {
                selections++
                assertTrue(ruleIds.isEmpty())
                val start = TimeSource.Monotonic.markNow()
                while (start.elapsedNow() <= budget + 5.milliseconds) Thread.yield()
            }
        }
        val remaining = ArrayList<Duration>()
        var stopQueries = 0
        val policy = object : IterationPolicy {
            override fun fieldLimit(runIndex: Int): Int {
                assertEquals(1, runIndex)
                return 1
            }
            override fun timeout(run: RunConfig, rest: Duration): Duration {
                assertEquals(1, run.index)
                remaining += rest
                return rest
            }
            override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>,
                frontiers: List<Frontier>): Boolean {
                stopQueries++
                return false
            }
        }
        JIRPrescanFixtures(cp, graph, rules).use { f ->
            f.run()
            f.engine.requirePrescanJoined()
            val entry = JIRBidiAnalysis(cp, graph, f.units, rules, f.params, policy, f.refManager)
            val totalStart = TimeSource.Monotonic.markNow()
            entry.gather(f.manager, emptyList(), f.engine.prescanResolvedGraph())
            assertEquals(1, selections, "Selection occurs once before metadata collection")
            val afterGather = budget - totalStart.elapsedNow()
            assertTrue(afterGather <= Duration.ZERO, "Gather consumed the caller total budget")
            f.manager.releasePrescan()
            f.engine.releasePrescan()
            val report = entry.run(afterGather)
            assertEquals(1, selections)
            assertEquals(1, remaining.size)
            assertTrue(remaining.single() < afterGather,
                "Remapping and closure also consume the remaining budget: $afterGather -> ${remaining.single()}")
            assertEquals(0, stopQueries)
            assertEquals(AnalysisEnd(RunStatus.TIMEOUT, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertTrue(report.entries.isEmpty())
            assertTrue(entry.toVulnerabilities(report).isEmpty())
            assertEquals(TaintAnalysisUnitRunnerManager.Status.TIMEOUT, entry.status(report))
        }
    }
}
