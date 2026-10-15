package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import java.util.concurrent.locks.LockSupport
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class DriverTest {
    private fun policy(
        limit: (Int) -> Int = { 1 },
        next: (RunConfig, RunResult<RunMethodAnalyzer>, List<Frontier>) -> Boolean = { _, _, _ -> false },
        timeoutOf: (RunConfig, Duration) -> Duration = { _, remaining -> remaining },
    ): IterationPolicy = object : IterationPolicy {
        override fun fieldLimit(runIndex: Int): Int = limit(runIndex)
        override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>): Boolean =
            next(run, result, frontiers)
        override fun timeout(run: RunConfig, remaining: Duration): Duration = timeoutOf(run, remaining)
    }

    @Test
    fun `zero and negative first limits fail before constructing a run or invoking a barrier`(): Unit {
        for (limit in listOf(0, -1)) DriverFixtures().use { f ->
            val c = f.confirmed()
            var invoked = 0
            var barriers = 0
            val driver = f.driver(policy(limit = { limit }), invokeRun = { _, _, _ ->
                invoked++
                error("An invalid first limit cannot create a manager")
            }, guardBarrier = { _, _ ->
                barriers++
                error("An invalid first limit cannot reach a barrier")
            })
            val report = driver.analyze(listOf(c.root))
            assertEquals(AnalysisEnd(RunStatus.FAILED, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertTrue(report.entries.isEmpty() && driver.frontiers.isEmpty())
            assertEquals(0, invoked)
            assertEquals(0, barriers)
        }
    }

    @Test
    fun `the first limit is read once and the public constructor executes the actual normal candidate barrier`(): Unit {
        DriverFixtures().use { f ->
            val c = f.confirmed()
            val reads = ArrayList<Int>()
            val p = policy(limit = { index ->
                reads += index
                check(reads.count { it == 1 } == 1) { "The first limit was sampled twice" }
                1
            }, next = { _, _, _ -> error("NO_DEMAND_EDGE precedes policy") })
            val driver = IterationDriver(p, f.shared, 5.seconds)
            val report = driver.analyze(listOf(c.root))
            assertEquals(listOf(1), reads)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.NO_DEMAND_EDGE), report.end)
            val entry = report.entries.single()
            assertEquals(c.rule, entry.key.rule)
            assertEquals(ReportState.DEMAND, entry.state)
            assertTrue(entry.witnesses.all { !it.confirmed })
            assertEquals(listOf(1), driver.frontiers.map { it.run })
            assertEquals(0, driver.frontiers.single().confirmedVulnerabilities)
            assertEquals(1, driver.frontiers.single().demandVulnerabilities)
        }
    }

    @Test
    fun `FixedLimits owns its list before caller mutation`(): Unit {
        val limits = arrayListOf(1, 2, 3, 4, 5)
        val p = FixedLimits(limits)
        limits[0] = 0
        limits[2] = -1
        limits.clear()
        assertEquals(listOf(1, 2, 3, 4, 5), (1..5).map(p::fieldLimit))
    }

    @Test
    fun `a decreasing third limit fails after preserving both completed barriers and the forward report`(): Unit {
        DriverFixtures().use { f ->
            val c = f.persistentDemand()
            val reads = ArrayList<Int>()
            val driver = f.driver(policy(limit = { index -> reads += index; if (index == 2) 2 else 1 },
                next = { _, _, _ -> true }))
            val report = driver.analyze(listOf(c.root))
            assertEquals(listOf(1, 2, 3), reads)
            assertEquals(listOf(1, 2), f.configs.map { it.index })
            assertEquals(listOf(1, 2), f.configs.map { it.fieldLimit })
            assertEquals(AnalysisEnd(RunStatus.FAILED, 2, Direction.BACKWARD, EndReason.ABNORMAL), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(c.rule, report.entries.single().key.rule)
            assertEquals(listOf(1, 2), driver.frontiers.map { it.run })
            assertEquals(listOf(1, 0), driver.frontiers.map { it.demandVulnerabilities })
        }
    }

    @Test
    fun `policy is asked only after a genuine demand barrier and can stop with its complete finding`(): Unit {
        DriverFixtures().use { f ->
            val c = f.persistentDemand()
            val policyCalls = ArrayList<Int>()
            val driver = f.driver(policy(next = { run, result, frontiers ->
                policyCalls += run.index
                assertEquals(RunStatus.COMPLETE, result.status)
                assertEquals(Direction.FORWARD, result.direction)
                assertTrue(result.demandLayerEdges > 0)
                assertEquals(listOf(run.index), frontiers.map { it.run })
                assertEquals(1, frontiers.last().demandVulnerabilities)
                assertTrue(frontiers.last().seeds > 0)
                false
            }))
            val report = driver.analyze(listOf(c.root))
            assertEquals(listOf(1), policyCalls)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(c.rule, report.entries.single().key.rule)
            assertEquals(listOf(1), driver.frontiers.map { it.run })
        }
    }

    @Test
    fun `field limit and timeout policy throwables are inside the guarded region`(): Unit {
        for (failure in listOf(IllegalStateException("policy"), AssertionError("policy"), OutOfMemoryError("policy"))) {
            for (duringLimit in listOf(false, true)) DriverFixtures().use { f ->
                val c = f.confirmed()
                val p = if (duringLimit) policy(limit = { throw failure }) else policy(timeoutOf = { _, _ -> throw failure })
                val driver = f.driver(p)
                val report = driver.analyze(listOf(c.root))
                assertEquals(AnalysisEnd(RunStatus.FAILED, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
                assertTrue(report.entries.isEmpty() && driver.frontiers.isEmpty())
                assertTrue(f.configs.isEmpty(), "No manager starts after a policy failure")
            }
        }
    }

    @Test
    fun `normal unsupported joint support stops before policy despite demand report and outgoing sink seeds`(): Unit {
        DriverFixtures().use { f ->
            val c = f.unsupportedJoint()
            val driver = f.driver(policy(next = { _, _, _ -> error("NO_DEMAND_EDGE precedes policy") }))
            val report = driver.analyze(listOf(c.root))
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 1, Direction.FORWARD, EndReason.NO_DEMAND_EDGE), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(c.rule, report.entries.single().key.rule)
            assertTrue(report.entries.single().witnesses.all { !it.confirmed })
            assertEquals(0L, f.results.single().demandLayerEdges)
            assertEquals(1, driver.frontiers.single().demandVulnerabilities)
            assertTrue(driver.frontiers.single().seeds >= 2)
            assertEquals(listOf(1), f.configs.map { it.index })
        }
    }

    @Test
    fun `equal positive limits are accepted and complete backward runs never call the stop policy`(): Unit {
        DriverFixtures().use { f ->
            val c = f.persistentDemand()
            val calls = ArrayList<Int>()
            val driver = f.driver(policy(next = { config, result, frontiers ->
                calls += config.index
                assertEquals(Direction.FORWARD, result.direction)
                assertEquals((1..config.index).toList(), frontiers.map { it.run })
                config.index < 3
            }))
            val report = driver.analyze(listOf(c.root))
            assertEquals(listOf(1, 2, 3), f.configs.map { it.index })
            assertEquals(listOf(1, 1, 1), f.configs.map { it.fieldLimit })
            assertEquals(listOf(Direction.FORWARD, Direction.BACKWARD, Direction.FORWARD), f.configs.map { it.direction })
            assertEquals(listOf(1, 3), calls)
            assertEquals(AnalysisEnd(RunStatus.COMPLETE, 3, Direction.FORWARD, EndReason.POLICY), report.end)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
        }
    }

    @Test
    fun `a continue policy Throwable or Error retains the already completed finding and frontier`(): Unit {
        for (failure in listOf(IllegalStateException("continue"), AssertionError("continue"), OutOfMemoryError("continue"))) {
            DriverFixtures().use { f ->
                val c = f.persistentDemand()
                val driver = f.driver(policy(next = { _, _, _ -> throw failure }))
                val report = driver.analyze(listOf(c.root))
                assertEquals(AnalysisEnd(RunStatus.FAILED, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
                assertEquals(ReportState.DEMAND, report.entries.single().state)
                assertEquals(c.rule, report.entries.single().key.rule)
                assertEquals(listOf(1), driver.frontiers.map { it.run })
                assertEquals(listOf(1), f.configs.map { it.index })
                assertEquals(RunStatus.COMPLETE, f.results.single().status)
            }
        }
    }

    @Test
    fun `frontier getter and policy histories own stable unmodifiable snapshots`(): Unit {
        DriverFixtures().use { f ->
            val c = f.persistentDemand()
            val policySnapshots = ArrayList<List<Frontier>>()
            lateinit var driver: IterationDriver
            driver = f.driver(policy(next = { config, _, frontiers ->
                policySnapshots += frontiers
                assertFails { (frontiers as MutableList<Frontier>).clear() }
                val publicSnapshot = driver.frontiers
                assertEquals((1..config.index).toList(), publicSnapshot.map { it.run })
                assertFails { (publicSnapshot as MutableList<Frontier>).clear() }
                config.index < 3
            }))
            val before = driver.frontiers
            assertTrue(before.isEmpty())
            val report = driver.analyze(listOf(c.root))
            assertEquals(EndReason.POLICY, report.end.reason)
            assertTrue(before.isEmpty(), "A getter snapshot from before the run cannot become a live view")
            assertEquals(listOf(1), policySnapshots[0].map { it.run })
            assertEquals(listOf(1, 2, 3), policySnapshots[1].map { it.run })
            assertEquals(listOf(1, 2, 3), driver.frontiers.map { it.run })
            assertFails { (driver.frontiers as MutableList<Frontier>).clear() }
            assertEquals(3, driver.frontiers.size)
        }
    }

    @Test
    fun `an oversized positive policy timeout is capped to the freshly remaining analysis budget`(): Unit {
        DriverFixtures().use { f ->
            val c = f.confirmed()
            val remainingAtPolicy = ArrayList<Duration>()
            val driver = f.driver(policy(timeoutOf = { _, remaining ->
                remainingAtPolicy += remaining
                30.seconds
            }), budget = 5.seconds)
            val report = driver.analyze(listOf(c.root))
            assertEquals(EndReason.NO_DEMAND_EDGE, report.end.reason)
            assertEquals(1, remainingAtPolicy.size)
            assertEquals(1, f.timeouts.size)
            assertTrue(f.timeouts.single() > Duration.ZERO)
            assertTrue(f.timeouts.single() <= remainingAtPolicy.single())
            assertTrue(f.timeouts.single() <= 5.seconds)
        }
    }

    @Test
    fun `timeout policy elapsed time cannot renew an expired budget by returning a positive timeout`(): Unit {
        DriverFixtures().use { f ->
            val c = f.confirmed()
            val budget = 120.milliseconds
            var callbacks = 0
            val driver = f.driver(policy(timeoutOf = { _, remaining ->
                callbacks++
                assertTrue(remaining <= budget)
                expire(budget)
                30.seconds
            }), budget = budget)
            val report = driver.analyze(listOf(c.root))
            assertEquals(AnalysisEnd(RunStatus.TIMEOUT, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertEquals(1, callbacks)
            assertTrue(report.entries.isEmpty() && driver.frontiers.isEmpty())
            assertTrue(f.timeouts.all { it <= Duration.ZERO })
            assertTrue(f.results.all { it.status == RunStatus.TIMEOUT && it.analyzers.isEmpty() })
            assertTrue(f.configs.size <= 1)
        }
    }

    @Test
    fun `barrier elapsed time consumes the next run budget without undoing the completed forward report`(): Unit {
        DriverFixtures().use { f ->
            val c = f.persistentDemand()
            val budget = 300.milliseconds
            val remaining = ArrayList<Pair<Int, Duration>>()
            var barrierCount = 0
            val driver = f.driver(policy(next = { _, _, _ -> true }, timeoutOf = { config, rest ->
                remaining += config.index to rest
                30.seconds
            }), budget = budget, guardBarrier = { _, body ->
                val next = body()
                barrierCount++
                if (barrierCount == 1) expire(budget)
                next
            })
            val report = driver.analyze(listOf(c.root))
            assertEquals(AnalysisEnd(RunStatus.TIMEOUT, 2, Direction.BACKWARD, EndReason.ABNORMAL), report.end)
            assertEquals(1, barrierCount)
            assertEquals(listOf(1, 2), remaining.map { it.first })
            assertTrue(remaining[1].second < Duration.ZERO)
            assertTrue(remaining[1].second < remaining[0].second)
            assertEquals(ReportState.DEMAND, report.entries.single().state)
            assertEquals(c.rule, report.entries.single().key.rule)
            assertEquals(listOf(1), driver.frontiers.map { it.run })
            assertTrue(f.timeouts.size <= 2)
            if (f.timeouts.size == 2) assertTrue(f.timeouts[1] <= Duration.ZERO)
        }
    }

    private fun expire(duration: Duration) {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() <= duration) LockSupport.parkNanos(1_000_000)
        assertTrue(start.elapsedNow() > duration)
    }
}
