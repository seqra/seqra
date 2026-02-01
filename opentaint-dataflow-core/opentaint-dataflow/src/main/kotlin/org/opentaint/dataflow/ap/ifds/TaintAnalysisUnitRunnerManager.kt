package org.opentaint.dataflow.ap.ifds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerabilityWithEndFactRequirement
import org.opentaint.dataflow.ap.ifds.trace.ParallelProcessingContext
import org.opentaint.dataflow.ap.ifds.trace.ProcessingCancellation
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker.VerifiedVulnerability
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker.VulnerabilityVerificationStatus
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonTaintRulesProvider
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.MemoryManager
import org.opentaint.dataflow.util.percentToString
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TaintAnalysisUnitRunnerManager(
    private val analysisManager: TaintAnalysisManager,
    val graph: ApplicationGraph<CommonMethod, CommonInst>,
    override val unitResolver: UnitResolver<CommonMethod>,
    private val taintConfig: CommonTaintRulesProvider,
    private val summarySerializationContext: SummarySerializationContext,
    val apManager: ApManager,
    private val taintRulesStatsSamplingPeriod: Int?
): AnalysisUnitRunnerManager, AutoCloseable {
    private val runnerForUnit = ConcurrentHashMap<UnitType, TaintAnalysisUnitRunner>()
    private val unitStorage = ConcurrentHashMap<UnitType, TaintAnalysisUnitStorage>()
    private val methodDependencies = ConcurrentHashMap<CommonMethod, MutableSet<UnitType>>()

    private val runnerJobs = ConcurrentLinkedQueue<Job>()
    private val analysisCompletion = CompletableDeferred<Unit>()

    private val totalEventsProcessed = AtomicInteger()
    private val totalEventsEnqueued = AtomicInteger()

    @OptIn(DelicateCoroutinesApi::class)
    private val analyzerDispatcher = newFixedThreadPoolContext(
        nThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
        name = "${this::class.java.name}-worker"
    )

    private val analyzerScope = CoroutineScope(analyzerDispatcher)

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val progressDispatcher = newSingleThreadContext(
        name = "${this::class.java.name}-progress"
    )

    private val progressScope = CoroutineScope(progressDispatcher)

    override fun close() {
        analyzerDispatcher.close()
        progressDispatcher.close()

        (analyzerDispatcher.executor as? ExecutorService)?.shutdownNow()
        (progressDispatcher.executor as? ExecutorService)?.shutdownNow()
    }

    private val analysisMemoryManager = MemoryManager(OOM_DETECTION_THRESHOLD) {
        logger.error { "Running low on memory, stopping analysis" }
        analysisCompletion.complete(Unit)
    }

    fun storeSummaries() {
        unitStorage.values.forEach { storage ->
            storage.storeSummaries(summarySerializationContext)
        }
        summarySerializationContext.flush()
    }

    fun runAnalysis(
        startMethods: List<CommonMethod>,
        timeout: Duration,
        cancellationTimeout: Duration
    ) = analysisMemoryManager.runWithMemoryManager {
        val timeStart = TimeSource.Monotonic.markNow()

        val unitStartMethods = startMethods.groupBy { unitResolver.resolve(it) }.filterKeys { it != UnknownUnit }

        logger.info { "Starting analysis of ${startMethods.size} methods in ${unitStartMethods.size} units" }

        handleEventEnqueued()

        for ((unit, methods) in unitStartMethods) {
            val runner = getOrSpawnUnitRunner(unit)
            runner?.submitStartMethods(methods)
        }

        handleEventProcessed()

        // Spawn progress job:
        val progress = progressScope.launch {
            while (isActive) {
                delay(10.seconds)
                reportRunnerProgress()
            }
        }

        runBlocking {
            try {
                val timeoutFailure = withTimeoutOrNull(timeout) {
                    analysisCompletion.await()
                }

                if (timeoutFailure == null) {
                    logger.warn { "Ifds analysis timeout" }
                }
            } finally {
                runnerForUnit.elements().asSequence().forEach { it.cancel() }

                withTimeoutOrNull(cancellationTimeout) {
                    progress.cancelAndJoin()
                    runnerJobs.forEach { it.cancel() }
                    runnerJobs.joinAll()
                }

                reportRunnerProgress()
                logger.info { "Analysis done in ${timeStart.elapsedNow()}" }
            }
        }
    }

    fun getVulnerabilities(): List<TaintVulnerability> {
        // Extract found vulnerabilities (sinks):
        val vulnerabilities = mutableListOf<TaintVulnerability>()
        unitStorage.values.forEach { storage ->
            storage.collectVulnerabilities(vulnerabilities)
        }

        return vulnerabilities
    }

    fun resolveVulnerabilityTraces(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        resolverParams: TraceResolver.Params,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<VulnerabilityWithTrace> {
        if (vulnerabilities.isEmpty()) return emptyList()

        val traceResolverCancellation = ProcessingCancellation()
        val traceResolverMemoryManager = MemoryManager(TRACE_GENERATION_MEMORY_THRESHOLD) {
            traceResolverCancellation.cancel()
            logger.error { "Running low on memory, stopping trace resolution" }
        }

        return traceResolverMemoryManager.runWithMemoryManager {
            resolveVulnerabilityTracesWithCancellation(
                entryPoints, vulnerabilities, resolverParams, timeout, cancellationTimeout,
                traceResolverCancellation
            )
        }
    }

    private fun resolveVulnerabilityTracesWithCancellation(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        resolverParams: TraceResolver.Params,
        timeout: Duration,
        cancellationTimeout: Duration,
        traceResolverCancellation: ProcessingCancellation
    ): List<VulnerabilityWithTrace> {
        val traceResolver = TraceResolver(entryPoints, this, resolverParams, traceResolverCancellation)
        val traceResolutionContext = object : ParallelProcessingContext<TaintVulnerability, VulnerabilityWithTrace>(
            analyzerDispatcher, name = "Trace resolution", vulnerabilities
        ) {
            override fun createUnprocessed(item: TaintVulnerability) =
                VulnerabilityWithTrace(item, trace = null)

            override fun reportStats() {
                logger.info { reportMemoryUsage() }

                logger.debug {
                    val methodStats = collectMethodStats()
                    val mostTRMethods = methodStats.stats.values.sortedByDescending { it.traceResolverSteps }

                    buildString {
                        appendLine("Steps")
                        mostTRMethods.take(5).forEach { appendLine(it) }
                    }
                }
            }
        }

        return traceResolutionContext.processAll(
            progressScope, timeout, cancellationTimeout, traceResolverCancellation
        ) { vulnerability ->
            val trace = traceResolver.resolveTrace(vulnerability)
            VulnerabilityWithTrace(vulnerability, trace)
        }
    }

    fun confirmVulnerabilities(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<TaintVulnerability> {
        val confirmed = mutableListOf<TaintVulnerability>()
        val unconfirmedVulnerabilities = mutableListOf<TaintVulnerabilityWithEndFactRequirement>()

        for (vulnerability in vulnerabilities) {
            when (vulnerability) {
                is TaintSinkTracker.TaintVulnerabilityUnconditional -> confirmed.add(vulnerability)
                is TaintSinkTracker.TaintVulnerabilityWithFact -> confirmed.add(vulnerability)
                is TaintVulnerabilityWithEndFactRequirement -> unconfirmedVulnerabilities.add(vulnerability)
            }
        }

        if (unconfirmedVulnerabilities.isEmpty()) return confirmed

        val vulnConfirmCancellation = ProcessingCancellation()
        val vulnConfirmMemoryManager = MemoryManager(TRACE_GENERATION_MEMORY_THRESHOLD) {
            vulnConfirmCancellation.cancel()
            logger.error { "Running low on memory, stopping vulnerability confirmation" }
        }

        val checkedVulnerabilities = vulnConfirmMemoryManager.runWithMemoryManager {
            confirmVulnerabilitiesWithCancellation(
                entryPoints, unconfirmedVulnerabilities, timeout, cancellationTimeout,
                vulnConfirmCancellation
            )
        }

        for (vulnerability in checkedVulnerabilities) {
            when (vulnerability.status) {
                VulnerabilityVerificationStatus.UNCONFIRMED -> {
                    // skip
                }

                VulnerabilityVerificationStatus.CONFIRMED,
                VulnerabilityVerificationStatus.UNKNOWN -> {
                    confirmed.add(vulnerability.vuln)
                }
            }
        }

        return confirmed
    }

    private fun confirmVulnerabilitiesWithCancellation(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerabilityWithEndFactRequirement>,
        timeout: Duration,
        cancellationTimeout: Duration,
        vulnConfirmCancellation: ProcessingCancellation,
    ): List<VerifiedVulnerability> {
        val checker = VulnerabilityChecker(entryPoints, this, vulnConfirmCancellation)
        val vulnConfirmationContext =
            object : ParallelProcessingContext<TaintVulnerabilityWithEndFactRequirement, VerifiedVulnerability>(
                analyzerDispatcher, name = "Vulnerability confirmation", vulnerabilities
            ) {
                override fun createUnprocessed(item: TaintVulnerabilityWithEndFactRequirement): VerifiedVulnerability =
                    VerifiedVulnerability(
                        item.vulnerability,
                        status = VulnerabilityVerificationStatus.UNKNOWN
                    )
            }

        return vulnConfirmationContext.processAll(
            progressScope, timeout, cancellationTimeout, vulnConfirmCancellation
        ) { checker.verifyVulnerability(it) }
    }

    fun methodCallers(method: CommonMethod): Set<UnitType> =
        methodDependencies[method].orEmpty()

    fun findUnitRunner(unit: UnitType): TaintAnalysisUnitRunner? {
        if (unit == UnknownUnit) return null
        return runnerForUnit[unit]
    }

    private fun getOrSpawnUnitRunner(unit: UnitType): TaintAnalysisUnitRunner? {
        if (unit == UnknownUnit) return null
        return runnerForUnit.computeIfAbsent(unit) {
            spawnNewRunner(unit)
        }
    }

    private class TaintAnalysisManagerWithContext(
        private val analysisManager: TaintAnalysisManager,
        private val sinkTracker: TaintSinkTracker,
        private val taintConfig: CommonTaintRulesProvider,
    ) : TaintAnalysisManager by analysisManager {
        override fun getMethodAnalysisContext(
            methodEntryPoint: MethodEntryPoint,
            graph: ApplicationGraph<CommonMethod, CommonInst>
        ): MethodAnalysisContext = analysisManager.getMethodAnalysisContext(
            methodEntryPoint, graph,
            TaintAnalysisContext(taintConfig, sinkTracker)
        )
    }

    private fun spawnNewRunner(unit: UnitType): TaintAnalysisUnitRunner {
        val storage = unitStorage.getOrPut(unit) {
            TaintAnalysisUnitStorage(apManager, analysisManager)
        }
        val sinkTracker = TaintSinkTracker(storage)
        val taintAnalyzer = TaintAnalysisManagerWithContext(analysisManager, sinkTracker, taintConfig)

        val runner = TaintAnalysisUnitRunner(
            manager = this,
            unit = unit,
            analysisManager = taintAnalyzer,
            graph = graph,
            unitResolver = unitResolver,
            summarySerializationContext = summarySerializationContext,
            taintRulesStatsSamplingPeriod = taintRulesStatsSamplingPeriod
        )

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            logger.error { "Got exception from runner for unit $unit, stopping analysis" }
            analysisCompletion.completeExceptionally(exception)
        }

        val job = analyzerScope.launch(exceptionHandler) { runner.runLoop() }
        runnerJobs.add(job)

        return runner
    }

    fun handleEventEnqueued() {
        totalEventsEnqueued.incrementAndGet()
    }

    fun handleEventProcessed() {
        totalEventsProcessed.incrementAndGet()

        val remainingEvents = totalEventsEnqueued.decrementAndGet()
        if (remainingEvents == 0) {
            logger.debug { "All runners are empty" }
            analysisCompletion.complete(Unit)
        }
    }

    override fun registerMethodCallFromUnit(method: CommonMethod, unit: UnitType) {
        val dependencies = methodDependencies.computeIfAbsent(method) {
            ConcurrentHashMap.newKeySet()
        }
        dependencies.add(unit)
    }

    override fun getOrCreateUnitRunner(unit: UnitType): AnalysisRunner? {
        return getOrSpawnUnitRunner(unit)
    }

    override fun getOrCreateUnitStorage(unit: UnitType): MethodSummariesUnitStorage? {
        getOrSpawnUnitRunner(unit) ?: return null
        return unitStorage.getValue(unit)
    }

    fun collectMethodStats(): MethodStats {
        val methodStats = MethodStats()
        runnerForUnit.values.forEach { it.collectMethodStats(methodStats) }
        unitStorage.values.forEach { it.collectMethodStats(methodStats) }
        return methodStats
    }

    fun allUnits(): Set<UnitType> = runnerForUnit.keys

    private fun reportMemoryUsage(): String {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val usedMemory = maxMemory - Runtime.getRuntime().freeMemory()
        return "Memory usage: ${usedMemory}/${maxMemory} (${percentToString(usedMemory, maxMemory)})"
    }

    private fun reportRunnerProgress() {
        val stats = runnerForUnit.mapValues { it.value.stats() }

        logger.info { "Progress: ${totalEventsProcessed.get()}/${totalEventsEnqueued.get()}" }
        logger.info { reportMemoryUsage() }

        analysisManager.reportLanguageSpecificRunnerProgress(logger)

        logger.debug {
            val runnerStats = stats.entries
                .sortedByDescending { it.value.enqueued }
                .filter { it.value.enqueued > 0 }

            buildString {
                appendLine()
                runnerStats.take(10).forEach { appendLine(it) }
            }
        }

        logger.debug {
            val methodStats = collectMethodStats()

            val mostUnprocessedMethods = methodStats.stats.values.sortedByDescending { it.unprocessedEdges }
            val mostStepsMethods = methodStats.stats.values.sortedByDescending { it.steps }
            val mostHandledSummariesMethods = methodStats.stats.values.sortedByDescending { it.handledSummaries }
            val mostSourcesMethods = methodStats.stats.values.sortedByDescending { it.sourceSummaries }
            val mostPassMethods = methodStats.stats.values.sortedByDescending { it.passSummaries }

            buildString {
                appendLine("Unprocessed")
                mostUnprocessedMethods.take(5).forEach { appendLine(it) }

                appendLine("Steps")
                mostStepsMethods.take(5).forEach { appendLine(it) }

                appendLine("Handled summaries")
                mostHandledSummariesMethods.take(5).forEach { appendLine(it) }

                appendLine("Source summary")
                mostSourcesMethods.take(5).forEach { appendLine(it) }

                appendLine("Pass summary")
                mostPassMethods.take(5).forEach { appendLine(it) }

                if (taintRulesStatsSamplingPeriod != null) {
                    printTaintRuleStats(methodStats)
                }
            }
        }
    }

    private fun StringBuilder.printTaintRuleStats(methodStats: MethodStats) {
        val stepsForTaintRule = mutableMapOf<String, Long>()
        methodStats.stats.values.forEach { stats ->
            stats.stepsForTaintMark.forEach { (mark, count) ->
                val rule = mark.split("#").first()
                stepsForTaintRule.compute(rule) { _, prev -> prev?.let { it + count } ?: count }
            }
        }

        val mostStepsForTaintRule = stepsForTaintRule.entries.sortedByDescending { it.value }

        appendLine("Steps for taint rules (sampled)")
        mostStepsForTaintRule.take(5).forEach { appendLine("${it.key} -> ${it.value}") }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val OOM_DETECTION_THRESHOLD = 0.97
        private const val TRACE_GENERATION_MEMORY_THRESHOLD = 0.99
    }
}
