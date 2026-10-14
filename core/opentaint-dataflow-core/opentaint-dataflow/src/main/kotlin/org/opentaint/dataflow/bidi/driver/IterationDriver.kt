package org.opentaint.dataflow.bidi.driver

import mu.KLogging
import org.opentaint.dataflow.bidi.ap.Direction
import org.opentaint.dataflow.bidi.ap.MethodKey
import org.opentaint.dataflow.bidi.engine.MethodCallServicesFactory
import org.opentaint.dataflow.bidi.engine.MethodLocalServicesFactory
import org.opentaint.dataflow.bidi.engine.RunConfig
import org.opentaint.dataflow.bidi.engine.RunManager
import org.opentaint.dataflow.bidi.engine.RunMethodAnalyzer
import org.opentaint.dataflow.bidi.engine.RunMethodCallServices
import org.opentaint.dataflow.bidi.engine.RunMethodHandlers
import org.opentaint.dataflow.bidi.engine.RunMethodLocalServices
import org.opentaint.dataflow.bidi.engine.RunResult
import org.opentaint.dataflow.bidi.engine.RunStatus
import org.opentaint.dataflow.bidi.engine.SharedObjects
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.MemoryManager
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.TimeSource

/** Runs fresh engines in sequence and reads their stores only after all runners join. */
class IterationDriver internal constructor(
    private val policy: IterationPolicy,
    private val shared: SharedObjects,
    private val budget: Duration,
    private val invokeRun: (RunConfig, SharedObjects, Duration) -> RunResult<RunMethodAnalyzer>,
    private val guardBarrier: (SharedObjects, () -> Next) -> Next,
) {
    constructor(policy: IterationPolicy, shared: SharedObjects, budget: Duration) :
        this(policy, shared, budget, ::runEngine, ::withBarrierGuard)

    private val frontierLog = ArrayList<Frontier>()
    val frontiers: List<Frontier> get() = Collections.unmodifiableList(ArrayList(frontierLog))

    fun analyze(roots: List<MethodKey>): Report {
        val start = TimeSource.Monotonic.markNow()
        val report = ReportBuilder()
        var index = 1
        fun end(status: RunStatus, reason: EndReason): Report {
            logger.info { "BIDI_ANALYSIS_END run=$index status=$status reason=$reason elapsed=${start.elapsedNow()}" }
            return report.build(AnalysisEnd(status, index,
                if (index % 2 == 1) Direction.FORWARD else Direction.BACKWARD, reason))
        }
        try {
            val firstLimit = policy.fieldLimit(1)
            require(firstLimit >= 1) { "ap.md S12 (d): run 1 needs a field limit of at least 1" }
            var config = RunConfig(1, firstLimit, demand = null, records = shared.records.view(),
                seeds = SeedIndex.EMPTY, roots = roots)
            while (true) {
                index = config.index
                val selectedTimeout = policy.timeout(config, budget - start.elapsedNow())
                // The callback consumes this same budget and cannot extend it.
                val timeout = minOf(selectedTimeout, budget - start.elapsedNow())
                when (val next = runOnce(config, report, timeout)) {
                    is Next.Run -> config = next.config
                    is Next.End -> return end(next.status, next.reason)
                }
            }
        } catch (error: Throwable) {
            logger.error(error) { "Run $index failed; the analysis ends with the report so far" }
            return end(RunStatus.FAILED, EndReason.ABNORMAL)
        }
    }

    /** This frame releases its result before the next engine is created. */
    private fun runOnce(config: RunConfig, report: ReportBuilder, timeout: Duration): Next {
        val start = TimeSource.Monotonic.markNow()
        logger.info { "BIDI_RUN_START run=${config.index} direction=${config.direction} limit=${config.fieldLimit} timeout=$timeout" }
        logOptimizationStatistics(config, "START")
        val result = try {
            invokeRun(config, shared, timeout)
        } catch (error: Throwable) {
            logger.info { "BIDI_RUN_END run=${config.index} status=FAILED engineTime=${start.elapsedNow()}" }
            logOptimizationStatistics(config, "END")
            throw error
        }
        logger.info { "BIDI_RUN_END run=${config.index} status=${result.status} engineTime=${start.elapsedNow()}" }
        logOptimizationStatistics(config, "END")
        if (result.status != RunStatus.COMPLETE) return Next.End(result.status)
        val barrierStart = TimeSource.Monotonic.markNow()
        val next = try {
            guardBarrier(shared) { barrier(config, result, report) }
        } catch (error: Cancellation.Cancelled) {
            Next.End(RunStatus.OOM)
        } finally {
            logger.info { "BIDI_BARRIER_END run=${config.index} barrierTime=${barrierStart.elapsedNow()} iterationTime=${start.elapsedNow()}" }
        }
        if (shared.cancellation.isActive()) return next
        logger.error { "Run ${config.index}: the barrier memory guard fired; the analysis ends with the report so far" }
        return Next.End(RunStatus.OOM)
    }

    private fun logOptimizationStatistics(config: RunConfig, phase: String) {
        shared.n1Statistics?.let { statistics ->
            logger.info { "BIDI_N1_STATISTICS run=${config.index} direction=${config.direction} limit=${config.fieldLimit} phase=$phase totals=${statistics.snapshot()}" }
        }
        shared.n2Statistics?.let { statistics ->
            logger.info { "BIDI_N2_STATISTICS run=${config.index} direction=${config.direction} limit=${config.fieldLimit} phase=$phase totals=${statistics.snapshot()}" }
        }
        shared.requestBatchStatistics?.let { statistics ->
            logger.info { "BIDI_REQUEST_BATCH_STATISTICS run=${config.index} direction=${config.direction} limit=${config.fieldLimit} phase=$phase totals=${statistics.snapshot()}" }
        }
    }

    private fun barrier(config: RunConfig, result: RunResult<RunMethodAnalyzer>, report: ReportBuilder): Next {
        val forward = config.direction == Direction.FORWARD
        if (forward) {
            Support(result, config.roots, config.mode, shared).confirm()
            report.add(config, result)
        }
        shared.records.persist(config.direction, result.analyzers.asSequence().map { it.key to it.summaries })
        val handOff = HandOff.of(config, result, report, shared)
        val frontier = frontierOf(config, result, handOff, report)
        frontierLog += frontier
        frontier.log()
        if (forward && !report.hasDemandVulnerability())
            return Next.End(RunStatus.COMPLETE, EndReason.STOP_RULE)
        if (forward && result.demandLayerEdges == 0L)
            return Next.End(RunStatus.COMPLETE, EndReason.NO_DEMAND_EDGE)
        if (forward && !policy.continueAfter(config, result, frontiers))
            return Next.End(RunStatus.COMPLETE, EndReason.POLICY)
        return Next.Run(nextConfig(config, handOff))
    }

    private fun nextConfig(config: RunConfig, handOff: HandOff): RunConfig {
        val index = config.index + 1
        val limit = policy.fieldLimit(index)
        require(limit >= config.fieldLimit) { "ap.md W3: the field limit must not decrease" }
        return RunConfig(index, limit, handOff.demand, shared.records.view(), handOff.seeds, config.roots)
    }

    companion object : KLogging() {
        const val BARRIER_MEMORY_THRESHOLD = 0.99
    }
}

private fun runEngine(config: RunConfig, shared: SharedObjects, timeout: Duration): RunResult<RunMethodAnalyzer> =
    RunManager(config, shared, RunMethodHandlers(MethodLocalServicesFactory(::RunMethodLocalServices),
        MethodCallServicesFactory(::RunMethodCallServices))).run(timeout)

private fun withBarrierGuard(shared: SharedObjects, body: () -> Next): Next =
    MemoryManager(shared.refManager, IterationDriver.BARRIER_MEMORY_THRESHOLD) { shared.cancellation.cancel() }
        .runWithMemoryManager(body)
