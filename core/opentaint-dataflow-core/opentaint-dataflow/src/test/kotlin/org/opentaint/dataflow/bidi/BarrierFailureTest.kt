package org.opentaint.dataflow.bidi

import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.driver.*
import org.opentaint.dataflow.bidi.engine.*
import org.opentaint.dataflow.bidi.fixtures.DriverFixtures
import org.opentaint.dataflow.bidi.store.RecordStore
import org.opentaint.dataflow.bidi.store.RunSummaryStore
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.ir.api.common.CommonMethod
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BarrierFailureTest {
    private fun continuingPolicy(): IterationPolicy = object : IterationPolicy {
        override fun fieldLimit(runIndex: Int) = 1
        override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>) = true
    }

    private fun handlers(): RunMethodHandlers = RunMethodHandlers(
        MethodLocalServicesFactory(::RunMethodLocalServices), MethodCallServicesFactory(::RunMethodCallServices))

    private fun sharedCopy(source: SharedObjects, records: RecordStore = source.records,
                           resolver: UnitResolver<CommonMethod> = source.unitResolver): SharedObjects =
        SharedObjects(source.ap, source.ops, source.interpreter, source.language, source.contexts, records,
            source.vulnerabilities, resolver, source.refManager, source.cancellation, threads = 1,
            cancellationTimeout = source.cancellationTimeout, diagnostics = source.diagnostics)

    private enum class Fault(val status: RunStatus) {
        TIMEOUT(RunStatus.TIMEOUT), GUARD_OOM(RunStatus.OOM), RESOLVER(RunStatus.FAILED), HANDLER(RunStatus.FAILED)
    }

    /** Every status comes from a fresh actual manager, including its successful join of any launched jobs. */
    private fun incomplete(f: DriverFixtures, config: RunConfig, owner: SharedObjects,
                           timeout: Duration, fault: Fault): RunResult<RunMethodAnalyzer> {
        val result = when (fault) {
            Fault.TIMEOUT -> f.run(config, owner, Duration.ZERO)
            Fault.GUARD_OOM -> f.manager(config, owner).let { manager ->
                manager.fail(RunStatus.OOM)
                manager.run(timeout)
            }
            Fault.RESOLVER -> sharedCopy(owner, resolver = UnitResolver<CommonMethod> {
                throw AssertionError("real unit resolution failed")
            }).use { failing -> f.run(config, failing, timeout) }
            Fault.HANDLER -> {
                val actual = handlers()
                val factory = RunnerHandlerFactory<RunMethodAnalyzer> { port ->
                    val delegate = actual.create(port)
                    object : RunnerHandler<RunMethodAnalyzer> by delegate {
                        override fun handle(event: RunEvent) {
                            throw AssertionError("real counted handler failed")
                        }
                    }
                }
                RunManager(config, owner, factory, quantum = 1).run(timeout)
            }
        }
        assertEquals(fault.status, result.status)
        assertTrue(result.analyzers.isEmpty())
        assertTrue(result.witnessesByKey().isEmpty(), "Incomplete results expose no current-run evidence")
        return result
    }

    private fun states(report: Report): Map<String, ReportState> =
        report.entries.associate { it.key.rule.id to it.state }

    @Test fun `every actual incomplete first run ends without invoking a barrier or restarting cancelled work`(): Unit {
        for (fault in Fault.entries) DriverFixtures().use { f ->
            val program = f.persistentDemand()
            val calls = ArrayList<Int>()
            var barriers = 0
            var policyCalls = 0
            val policy = object : IterationPolicy {
                override fun fieldLimit(runIndex: Int) = 1
                override fun continueAfter(run: RunConfig, result: RunResult<RunMethodAnalyzer>, frontiers: List<Frontier>): Boolean {
                    policyCalls++
                    return true
                }
            }
            val driver = f.driver(policy, invokeRun = { config, owner, timeout ->
                calls += config.index
                incomplete(f, config, owner, timeout, fault)
            }, guardBarrier = { _, body -> barriers++; body() })
            val report = driver.analyze(listOf(program.root))
            assertEquals(AnalysisEnd(fault.status, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertTrue(report.entries.isEmpty())
            assertTrue(driver.frontiers.isEmpty())
            assertEquals(listOf(1), calls)
            assertEquals(0, barriers)
            assertEquals(0, policyCalls)
            assertFalse(f.cancellation.isActive(), "The incomplete actual manager cancelled this run; the driver must not reactivate it")
        }
    }

    @Test fun `every actual incomplete later run preserves the previous candidate report and completed frontier`(): Unit {
        for (fault in Fault.entries) DriverFixtures(threads = 2).use { f ->
            val demand = f.persistentDemand()
            val confirmed = f.confirmed()
            val calls = ArrayList<Int>()
            val completed = ArrayList<RunResult<RunMethodAnalyzer>>()
            var barriers = 0
            val driver = f.driver(continuingPolicy(), invokeRun = { config, owner, timeout ->
                calls += config.index
                if (config.index == 1) f.run(config, owner, timeout).also { completed += it }
                else incomplete(f, config, owner, timeout, fault)
            }, guardBarrier = { _, body -> barriers++; body() })
            val report = driver.analyze(listOf(demand.root, confirmed.root))
            assertEquals(AnalysisEnd(fault.status, 2, Direction.BACKWARD, EndReason.ABNORMAL), report.end)
            assertEquals(mapOf(demand.rule.id to ReportState.DEMAND, confirmed.rule.id to ReportState.DEMAND), states(report))
            assertTrue(report.entries.all { it.run == 1 && it.witnesses.all { witness -> witness.run == 1 } })
            assertTrue(report.entries.single { it.key.rule == confirmed.rule }.witnesses.none { it.confirmed })
            assertTrue(report.entries.single { it.key.rule == demand.rule }.witnesses.none { it.confirmed })
            assertEquals(listOf(1, 2), calls)
            assertEquals(listOf(RunStatus.COMPLETE), completed.map { it.status })
            assertEquals(1, barriers)
            assertEquals(listOf(1), driver.frontiers.map { it.run })
            assertEquals(2, driver.frontiers.single().demandVulnerabilities)
            assertEquals(0, driver.frontiers.single().confirmedVulnerabilities)
            assertFalse(f.cancellation.isActive())
        }
    }

    @Test fun `construction Errors are FAILED and preserve a completed earlier report without invoking the failed run barrier`(): Unit {
        for (later in listOf(false, true)) for (memoryError in listOf(false, true)) DriverFixtures().use { f ->
            val demand = f.persistentDemand()
            val calls = ArrayList<Int>()
            var barriers = 0
            val driver = f.driver(continuingPolicy(), invokeRun = { config, owner, timeout ->
                calls += config.index
                if ((!later && config.index == 1) || (later && config.index == 2)) {
                    if (memoryError) throw OutOfMemoryError("synthetic manager construction Error")
                    throw AssertionError("synthetic manager construction Error")
                }
                f.run(config, owner, timeout)
            }, guardBarrier = { _, body -> barriers++; body() })
            val report = driver.analyze(listOf(demand.root))
            assertEquals(AnalysisEnd(RunStatus.FAILED, if (later) 2 else 1,
                if (later) Direction.BACKWARD else Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertEquals(if (later) mapOf(demand.rule.id to ReportState.DEMAND) else emptyMap(), states(report))
            assertEquals(if (later) listOf(1, 2) else listOf(1), calls)
            assertEquals(if (later) 1 else 0, barriers)
            assertEquals(if (later) listOf(1) else emptyList(), driver.frontiers.map { it.run })
        }
    }

    private enum class GuardPhase { CONSTRUCTION, BODY, CLEANUP }

    @Test fun `barrier guard construction body and cleanup Errors are FAILED while committed report updates remain`(): Unit {
        for (phase in GuardPhase.entries) for (memoryError in listOf(false, true)) DriverFixtures(threads = 2).use { f ->
            val demand = f.persistentDemand()
            val confirmed = f.confirmed()
            fun explode(): Nothing = if (memoryError) throw OutOfMemoryError("synthetic guard $phase Error")
                else throw AssertionError("synthetic guard $phase Error")
            val records = object : RecordStore by f.records {
                override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) {
                    if (phase == GuardPhase.BODY) explode()
                    f.records.persist(direction, summaries)
                }
            }
            sharedCopy(f.shared, records = records).use { owner ->
                val calls = ArrayList<Int>()
                var barriers = 0
                var bodies = 0
                val driver = IterationDriver(continuingPolicy(), owner, 5.seconds,
                    invokeRun = { config, runOwner, timeout ->
                        calls += config.index
                        f.run(config, runOwner, timeout)
                    }, guardBarrier = { _, body ->
                        barriers++
                        if (phase == GuardPhase.CONSTRUCTION) explode()
                        bodies++
                        val next = body()
                        if (phase == GuardPhase.CLEANUP) explode()
                        next
                    })
                val report = driver.analyze(listOf(demand.root, confirmed.root))
                assertEquals(AnalysisEnd(RunStatus.FAILED, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end,
                    "A thrown OOME is an Error; it is not a memory guard status")
                assertEquals(listOf(1), calls)
                assertEquals(1, barriers)
                assertEquals(when (phase) { GuardPhase.CONSTRUCTION -> 0; GuardPhase.BODY -> 1; GuardPhase.CLEANUP -> 1 }, bodies)
                val expected = if (phase == GuardPhase.CONSTRUCTION) emptyMap() else
                    mapOf(demand.rule.id to ReportState.DEMAND, confirmed.rule.id to ReportState.DEMAND)
                assertEquals(expected, states(report))
                assertEquals(if (phase == GuardPhase.CLEANUP) listOf(1) else emptyList(), driver.frontiers.map { it.run })
                if (phase != GuardPhase.CONSTRUCTION)
                    assertTrue(report.entries.single { it.key.rule == confirmed.rule }.witnesses.none { it.confirmed })
            }
        }
    }

    @Test fun `actual barrier body cancellation maps to OOM after the already committed forward report update`(): Unit {
        DriverFixtures(threads = 2).use { f ->
            val demand = f.persistentDemand()
            val confirmed = f.confirmed()
            val records = object : RecordStore by f.records {
                override fun persist(direction: Direction, summaries: Sequence<Pair<MethodKey, RunSummaryStore>>) {
                    f.cancellation.cancel()
                    f.cancellation.checkpoint()
                }
            }
            sharedCopy(f.shared, records = records).use { owner ->
                val calls = ArrayList<Int>()
                var barriers = 0
                val driver = IterationDriver(continuingPolicy(), owner, 5.seconds,
                    invokeRun = { config, runOwner, timeout -> calls += config.index; f.run(config, runOwner, timeout) },
                    guardBarrier = { _, body -> barriers++; body() })
                val report = driver.analyze(listOf(demand.root, confirmed.root))
                assertEquals(AnalysisEnd(RunStatus.OOM, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
                assertEquals(mapOf(demand.rule.id to ReportState.DEMAND, confirmed.rule.id to ReportState.DEMAND), states(report))
                assertEquals(listOf(1), calls)
                assertEquals(1, barriers)
                assertTrue(driver.frontiers.isEmpty(), "Run-1 report committed; cancellation interrupts its persistence before the frontier")
                assertFalse(f.cancellation.isActive())
            }
        }
    }

    @Test fun `late guard cancellation overrides a completed stop or next run and preserves the completed barrier evidence`(): Unit {
        for (continuation in listOf(false, true)) DriverFixtures().use { f ->
            val program = if (continuation) f.persistentDemand() else f.confirmed()
            val calls = ArrayList<Int>()
            var barriers = 0
            var bodyReturned = false
            val driver = f.driver(if (continuation) continuingPolicy() else FixedLimits(listOf(1)), invokeRun = { config, owner, timeout ->
                calls += config.index
                f.run(config, owner, timeout)
            }, guardBarrier = { owner, body ->
                barriers++
                val next = body()
                if (continuation) {
                    val run = assertIs<Next.Run>(next)
                    assertEquals(2, run.config.index)
                } else {
                    val end = assertIs<Next.End>(next)
                    assertEquals(RunStatus.COMPLETE, end.status)
                    assertEquals(EndReason.POLICY, end.reason)
                }
                bodyReturned = true
                owner.cancellation.cancel() // After the last body checkpoint, before the guard returns.
                next
            })
            val report = driver.analyze(listOf(program.root))
            assertTrue(bodyReturned)
            assertEquals(AnalysisEnd(RunStatus.OOM, 1, Direction.FORWARD, EndReason.ABNORMAL), report.end)
            assertEquals(mapOf(program.rule.id to ReportState.DEMAND), states(report))
            assertEquals(listOf(1), calls, "An already constructed Next.Run cannot restart cancelled work")
            assertEquals(1, barriers)
            assertEquals(listOf(1), driver.frontiers.map { it.run })
            assertEquals(1, driver.frontiers.single().demandVulnerabilities)
            assertEquals(0, driver.frontiers.single().confirmedVulnerabilities)
            assertFalse(f.cancellation.isActive())
        }
    }
}
