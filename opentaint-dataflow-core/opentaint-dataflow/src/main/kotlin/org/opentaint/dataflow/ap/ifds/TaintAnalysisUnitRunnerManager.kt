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
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.ap.ifds.access.cactus.CactusApManager
import org.opentaint.dataflow.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.ap.ifds.trace.TraceResolutionContext
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.TraceResolverCancellation
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.MemoryManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TaintAnalysisUnitRunnerManager(
    val languageManager: LanguageManager,
    val graph: ApplicationGraph<CommonMethod, CommonInst>,
    override val unitResolver: UnitResolver<CommonMethod>,
    apMode: ApMode = ApMode.Cactus,
    private val taintConfig: TaintRulesProvider
): AnalysisUnitRunnerManager, AutoCloseable {
    val apManager: ApManager = when (apMode) {
        ApMode.Tree -> TreeApManager
        ApMode.Cactus -> CactusApManager
        ApMode.Automata -> AutomataApManager
    }

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

        val traceResolverCancellation = TraceResolverCancellation()
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
        traceResolverCancellation: TraceResolverCancellation
    ): List<VulnerabilityWithTrace> {
        val traceResolver = TraceResolver(entryPoints, this, resolverParams, traceResolverCancellation)

        val traceResolutionContext = TraceResolutionContext(analyzerDispatcher, vulnerabilities)
        val traceResolutionComplete = traceResolutionContext.resolveAll { vulnerability ->
            val trace = traceResolver.resolveTrace(vulnerability)
            VulnerabilityWithTrace(vulnerability, trace)
        }

        val progress = progressScope.launch {
            while (isActive) {
                delay(10.seconds)
                logger.info { "Resolved ${traceResolutionContext.processed}/${vulnerabilities.size} traces" }
            }
        }

        runBlocking {
            val traceResolutionStatus = withTimeoutOrNull(timeout) { traceResolutionComplete.await() }
            if (traceResolutionStatus == null) {
                logger.warn { "Ifds trace resolution timeout" }
            }

            withTimeoutOrNull(cancellationTimeout) {
                traceResolverCancellation.cancel()

                progress.cancelAndJoin()
                traceResolutionContext.join()
            }
        }

        return traceResolutionContext.resolvedTraces().also { result ->
            logger.info { "Resolved ${result.size}/${vulnerabilities.size} traces" }
        }
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

    private fun spawnNewRunner(unit: UnitType): TaintAnalysisUnitRunner {
        val storage = unitStorage.getOrPut(unit) { TaintAnalysisUnitStorage(apManager) }
        val sinkTracker = TaintSinkTracker(apManager, storage)
        val runner = TaintAnalysisUnitRunner(
            this, unit, graph, unitResolver, taintConfig, sinkTracker
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

    private fun reportRunnerProgress() {
        val stats = runnerForUnit.mapValues { it.value.stats() }

        logger.info { "Progress: ${totalEventsProcessed.get()}/${totalEventsEnqueued.get()}" }
        logger.info {
            val maxMemory = Runtime.getRuntime().maxMemory()
            val usedMemory = maxMemory - Runtime.getRuntime().freeMemory()
            "Memory usage: ${usedMemory}/${maxMemory} (${percentToString(usedMemory, maxMemory)})"
        }

        languageManager.reportLanguageSpecificRunnerProgress(logger)

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
            val methodStats = MethodStats()
            runnerForUnit.values.forEach { it.collectMethodStats(methodStats) }
            unitStorage.values.forEach { it.collectMethodStats(methodStats) }

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
            }
        }
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val OOM_DETECTION_THRESHOLD = 0.97
        private const val TRACE_GENERATION_MEMORY_THRESHOLD = 0.99
    }
}
