package org.opentaint.dataflow.ap.ifds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver
import org.opentaint.dataflow.ap.ifds.serialization.SummarySerializationContext
import org.opentaint.dataflow.ap.ifds.taint.CommonTaintAnalysisContext
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisUnitStorage
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker
import org.opentaint.dataflow.ap.ifds.taint.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.ap.ifds.trace.ParallelProcessingContext
import org.opentaint.dataflow.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker.VerifiedVulnerability
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityChecker.VulnerabilityVerificationStatus
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithInterproceduralTrace
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathGenerationResult
import org.opentaint.dataflow.ap.ifds.trace.path.TracePathResolveParams
import org.opentaint.dataflow.ap.ifds.trace.path.generateTracePath
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.util.Cancellation
import org.opentaint.dataflow.util.MemoryManager
import org.opentaint.dataflow.util.RefManager
import org.opentaint.dataflow.util.percentToString
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TaintAnalysisUnitRunnerManager(
    val refManager: RefManager,
    override val cancellation: Cancellation,
    private val analysisManager: TaintAnalysisManager,
    val graph: ApplicationGraph<CommonMethod, CommonInst>,
    override val unitResolver: UnitResolver<CommonMethod>,
    private val summarySerializationContext: SummarySerializationContext,
    private val taintRulesStatsSamplingPeriod: Int?,
): AnalysisUnitRunnerManager, AutoCloseable {
    enum class Status {
        OK, EXCEPTION, TIMEOUT, OOM
    }

    val status = AtomicReference<Status>(Status.OK)

    private var activeApManager: ApManager? = null
    val apManager: ApManager get() = checkNotNull(activeApManager) { "No active AP manager" }

    private fun updateFailureStatus(status: Status) {
        if (status == Status.OK) return
        this.status.compareAndSet(Status.OK, status)
    }

    private val runnerForUnit = ConcurrentHashMap<UnitType, TaintAnalysisUnitRunner>()
    private val unitStorage = ConcurrentHashMap<UnitType, TaintAnalysisUnitStorage>()
    private val methodDependencies = ConcurrentHashMap<CommonMethod, MutableSet<UnitType>>()

    private val runnerJobs = ConcurrentLinkedQueue<Job>()
    private val lifecycleLock = Any()
    private val analysisActive = AtomicBoolean(false)
    @Volatile private var hasRun = false
    @Volatile private var progressJob: Job? = null
    private var stopping = false
    private var analysisCompletion = CompletableDeferred<Unit>()

    private fun requireStoppedJobs() {
        check(!analysisActive.get()) { "Analysis is still active" }
        check(runnerJobs.all { it.isCompleted } && progressJob?.isCompleted != false) {
            "Prescan jobs have not joined"
        }
    }

    fun requirePrescanJoined(): Unit = synchronized(lifecycleLock) {
        check(hasRun) { "Prescan has not finished" }
        requireStoppedJobs()
    }

    /** Copy the completed prescan relation before releasing either prescan owner. */
    fun prescanResolvedGraph(exportCancellation: Cancellation = Cancellation()): ResolvedPrescanGraph = synchronized(lifecycleLock) {
        requirePrescanJoined()
        check(status.get() == Status.OK) { "A failed prescan cannot supply a complete static catalog" }
        val methods = LinkedHashSet<CommonMethod>()
        val calls = LinkedHashSet<ResolvedPrescanCall>()
        for (runner in runnerForUnit.values) {
            exportCancellation.checkpoint()
            runner.copyResolvedGraph(methods, calls, exportCancellation)
        }
        ResolvedPrescanGraph(methods, calls)
    }

    fun releasePrescan(): Unit = synchronized(lifecycleLock) {
        requirePrescanJoined()
        runnerForUnit.clear()
        unitStorage.clear()
        methodDependencies.clear()
        runnerJobs.clear()
        progressJob = null
        activeApManager = null
    }

    private val totalEventsProcessed = AtomicInteger()
    private val totalEventsEnqueued = AtomicInteger()
    private val totalDelayedUnits = AtomicInteger()

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

    private val analysisMemoryManager = MemoryManager(refManager, OOM_DETECTION_THRESHOLD) {
        logger.error { "Running low on memory, stopping analysis" }
        analysisCompletion.complete(Unit)
        cancellation.cancel()
        updateFailureStatus(Status.OOM)
    }

    fun storeSummaries() {
        unitStorage.values.forEach { storage ->
            storage.storeSummaries(summarySerializationContext)
        }
        summarySerializationContext.flush()
    }

    fun resetApManager(manager: ApManager): Unit = synchronized(lifecycleLock) {
        requireStoppedJobs()
        this.activeApManager = manager
        runnerForUnit.elements().iterator().forEach { it.resetApManager(manager) }
        unitStorage.elements().iterator().forEach { it.resetApManager(manager) }

        totalDelayedUnits.set(0)
        totalEventsEnqueued.set(0)
        totalEventsProcessed.set(0)
        analysisCompletion = CompletableDeferred()
    }

    fun runAnalysis(
        startMethods: List<MethodWithContext>,
        timeout: Duration,
        cancellationTimeout: Duration
    ) {
        synchronized(lifecycleLock) {
            requireStoppedJobs()
            check(analysisActive.compareAndSet(false, true)) { "Analysis is already active" }
            hasRun = true
            stopping = false
        }
        try {
            analysisMemoryManager.runWithMemoryManager {
                val timeStart = TimeSource.Monotonic.markNow()
                val currentProgress = AtomicInteger(0)
                try {
                    cancellation.activate()
                    handleEventEnqueued()
                    runnerForUnit.entries.forEach { (unit, runner) -> startRunner(unit, runner) }

                    val unitStartMethods = startMethods.groupBy { unitResolver.resolve(it.method) }
                        .filterKeys { it != UnknownUnit }
                    logger.info { "Starting analysis of ${startMethods.size} methods in ${unitStartMethods.size} units" }
                    for ((unit, methods) in unitStartMethods) {
                        getOrSpawnUnitRunner(unit)?.submitStartMethods(methods)
                    }
                    handleEventProcessed()

                    progressJob = progressScope.launch {
                        while (isActive) {
                            delay(10.seconds)
                            reportRunnerProgress(currentProgress)
                        }
                    }
                    runBlocking {
                        if (withTimeoutOrNull(timeout) { analysisCompletion.await() } == null) {
                            updateFailureStatus(Status.TIMEOUT)
                            logger.warn { "Ifds analysis timeout" }
                        }
                    }
                } catch (failure: Throwable) {
                    updateFailureStatus(if (failure is OutOfMemoryError) Status.OOM else Status.EXCEPTION)
                    throw failure
                } finally {
                    // Stop admission and request every cancellation before the bounded wait.
                    synchronized(lifecycleLock) {
                        stopping = true
                        cancellation.cancel()
                        runnerForUnit.values.forEach { it.cancel() }
                        progressJob?.cancel()
                        runnerJobs.forEach { it.cancel() }
                    }
                    val joined = runBlocking {
                        withTimeoutOrNull(cancellationTimeout) {
                            progressJob?.join()
                            runnerJobs.joinAll()
                            Unit
                        }
                    }
                    if (joined != null && runnerJobs.all { it.isCompleted } && progressJob?.isCompleted != false) {
                        runnerJobs.clear()
                    } else {
                        status.set(Status.EXCEPTION)
                        logger.error { "Analysis jobs did not join within the cancellation timeout" }
                    }
                    reportRunnerProgress(currentProgress)
                    logger.info { "Analysis done in ${timeStart.elapsedNow()}" }
                }
            }
        } finally {
            analysisActive.set(false)
        }
    }

    fun cleanup() {
        runnerForUnit.elements().asSequence().forEach { it.cleanup() }
        unitStorage.elements().asSequence().forEach { it.cleanup() }
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
        vulnerabilities: List<VulnerabilityWithInterproceduralTrace>,
        resolverParams: TracePathResolveParams,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<VulnerabilityWithTrace> {
        if (vulnerabilities.isEmpty()) return emptyList()
        cancellation.activate()

        val traceResolverMemoryManager = MemoryManager(refManager, TRACE_GENERATION_MEMORY_THRESHOLD) {
            cancellation.cancel()
            updateFailureStatus(Status.OOM)
            logger.error { "Running low on memory, stopping trace resolution" }
        }

        return traceResolverMemoryManager.runWithMemoryManager {
            resolveVulnerabilityTracesPathWithCancellation(
                vulnerabilities, resolverParams, timeout, cancellationTimeout
            )
        }
    }

    fun resolveVulnerabilityInterProceduralTraces(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        resolverParams: TraceResolver.Params,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<VulnerabilityWithInterproceduralTrace> {
        if (vulnerabilities.isEmpty()) return emptyList()
        cancellation.activate()

        val traceResolverMemoryManager = MemoryManager(refManager, TRACE_GENERATION_MEMORY_THRESHOLD) {
            cancellation.cancel()
            updateFailureStatus(Status.OOM)
            logger.error { "Running low on memory, stopping trace resolution" }
        }

        return traceResolverMemoryManager.runWithMemoryManager {
            resolveVulnerabilityTracesWithCancellation(
                entryPoints, vulnerabilities, resolverParams, timeout, cancellationTimeout
            )
        }
    }

    private fun resolveVulnerabilityTracesWithCancellation(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        resolverParams: TraceResolver.Params,
        timeout: Duration,
        cancellationTimeout: Duration,
    ): List<VulnerabilityWithInterproceduralTrace> {
        val traceResolver = TraceResolver(entryPoints, this, resolverParams, cancellation)

        val states = vulnerabilities.map { TraceResolver.State.Initial(it) }
        val traceResolutionContext = object : ParallelProcessingContext<TraceResolver.State, VulnerabilityWithInterproceduralTrace>(
            analyzerDispatcher, name = "Trace resolution", states
        ) {
            override fun processItem(item: TraceResolver.State): ProcessingResult<TraceResolver.State, VulnerabilityWithInterproceduralTrace> {
                val res = traceResolver.resolveTrace(item)
                return when (res) {
                    is TraceResolver.TraceResolutionResult.InProgress -> {
                        ProcessingResult.Running(res.state)
                    }

                    is TraceResolver.TraceResolutionResult.NoTrace -> {
                        ProcessingResult.Done(VulnerabilityWithInterproceduralTrace(res.vulnerability, trace = null))
                    }

                    is TraceResolver.TraceResolutionResult.Resolved -> {
                        ProcessingResult.Done(VulnerabilityWithInterproceduralTrace(res.vulnerability, res.trace))
                    }
                }
            }

            override fun createUnprocessed(item: TraceResolver.State): VulnerabilityWithInterproceduralTrace =
                VulnerabilityWithInterproceduralTrace(item.vulnerability, trace = null)

            private var prevStats: MethodStats? = null

            override fun reportStats() {
                logger.info { reportMemoryUsage() }

                logger.debug {
                    val methodStats = collectMethodStats()
                    val mostTRMethods = methodStats.stats.values.sortedByDescending { it.traceResolverSteps }

                    val delta = prevStats?.let { methodStats.subtract(it) }
                    val mostTrDelta = delta?.let { d -> d.stats.values.sortedByDescending { it.traceResolverSteps } }

                    prevStats = methodStats

                    buildString {
                        appendLine("Steps")
                        mostTRMethods.take(5).forEach { appendLine(it) }

                        if (mostTrDelta != null) {
                            appendLine("Delta")
                            mostTrDelta.take(5).forEach { appendLine(it) }
                        }
                    }
                }
            }
        }

        return traceResolutionContext.processAll(
            progressScope, timeout, cancellationTimeout, cancellation
        )
    }

    private fun resolveVulnerabilityTracesPathWithCancellation(
        vulnerabilities: List<VulnerabilityWithInterproceduralTrace>,
        resolverParams: TracePathResolveParams,
        timeout: Duration,
        cancellationTimeout: Duration,
    ): List<VulnerabilityWithTrace> {
        val traceResolutionContext = object : ParallelProcessingContext<VulnerabilityWithInterproceduralTrace, VulnerabilityWithTrace>(
            analyzerDispatcher, name = "Trace path resolution", vulnerabilities
        ) {
            override fun processItem(item: VulnerabilityWithInterproceduralTrace): ProcessingResult<VulnerabilityWithInterproceduralTrace, VulnerabilityWithTrace> {
                val resolved = generateTracePath(item.trace, resolverParams)
                return ProcessingResult.Done(VulnerabilityWithTrace(item.vulnerability, resolved))
            }

            override fun createUnprocessed(item: VulnerabilityWithInterproceduralTrace): VulnerabilityWithTrace =
                VulnerabilityWithTrace(item.vulnerability, TracePathGenerationResult.Failure)

            override fun reportStats() {
                logger.info { reportMemoryUsage() }
            }
        }

        return traceResolutionContext.processAll(
            progressScope, timeout, cancellationTimeout, cancellation
        )
    }

    fun confirmVulnerabilities(
        entryPoints: Set<CommonMethod>,
        vulnerabilities: List<TaintVulnerability>,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<TaintVulnerability> {
        cancellation.activate()

        val confirmed = mutableListOf<TaintVulnerability>()
        val unconfirmedVulnerabilities = mutableListOf<TaintVulnerability>()

        for (vulnerability in vulnerabilities) {
            if (VulnerabilityChecker.needVerification(vulnerability)) {
                unconfirmedVulnerabilities.add(vulnerability)
            } else {
                confirmed.add(vulnerability)
            }
        }

        if (unconfirmedVulnerabilities.isEmpty()) return confirmed

        val vulnConfirmMemoryManager = MemoryManager(refManager, TRACE_GENERATION_MEMORY_THRESHOLD) {
            cancellation.cancel()
            updateFailureStatus(Status.OOM)
            logger.error { "Running low on memory, stopping vulnerability confirmation" }
        }

        val checkedVulnerabilities = vulnConfirmMemoryManager.runWithMemoryManager {
            confirmVulnerabilitiesWithCancellation(
                entryPoints, unconfirmedVulnerabilities, timeout, cancellationTimeout,
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
        vulnerabilities: List<TaintVulnerability>,
        timeout: Duration,
        cancellationTimeout: Duration,
    ): List<VerifiedVulnerability> {
        val checker = VulnerabilityChecker(entryPoints, this, cancellation)
        val vulnConfirmationContext =
            object : ParallelProcessingContext<TaintVulnerability, VerifiedVulnerability>(
                analyzerDispatcher, name = "Vulnerability confirmation", vulnerabilities
            ) {
                override fun processItem(item: TaintVulnerability): ProcessingResult<TaintVulnerability, VerifiedVulnerability> {
                    val result = checker.verifyVulnerability(item)
                    return ProcessingResult.Done(result)
                }

                override fun createUnprocessed(item: TaintVulnerability): VerifiedVulnerability =
                    VerifiedVulnerability(item, status = VulnerabilityVerificationStatus.UNKNOWN)
            }

        return vulnConfirmationContext.processAll(
            progressScope, timeout, cancellationTimeout, cancellation
        )
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
    ) : TaintAnalysisManager by analysisManager {
        override fun getMethodAnalysisContext(
            methodEntryPoint: MethodEntryPoint,
            graph: ApplicationGraph<CommonMethod, CommonInst>,
            callResolver: MethodCallResolver,
            contextForEmptyMethod: MethodAnalysisContext?
        ): MethodAnalysisContext = analysisManager.getMethodAnalysisContext(
            methodEntryPoint, graph, callResolver,
            CommonTaintAnalysisContext(sinkTracker),
            contextForEmptyMethod
        )
    }

    private fun spawnNewRunner(unit: UnitType): TaintAnalysisUnitRunner {
        val storage = unitStorage.getOrPut(unit) {
            TaintAnalysisUnitStorage(apManager, analysisManager)
        }
        val sinkTracker = TaintSinkTracker(storage)
        val taintAnalyzer = TaintAnalysisManagerWithContext(analysisManager, sinkTracker)

        val runner = TaintAnalysisUnitRunner(
            manager = this,
            unit = unit,
            analysisManager = taintAnalyzer,
            graph = graph,
            unitResolver = unitResolver,
            summarySerializationContext = summarySerializationContext,
            taintRulesStatsSamplingPeriod = taintRulesStatsSamplingPeriod
        )

        startRunner(unit, runner)

        return runner
    }

    private fun startRunner(unit: UnitType, runner: TaintAnalysisUnitRunner) {
        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            if (exception is Cancellation.Cancelled) {
                logger.error { "Cancelled: $unit, stopping analysis" }
                return@CoroutineExceptionHandler
            }

            logger.error { "Got exception $exception from runner for unit $unit, stopping analysis" }
            analysisCompletion.completeExceptionally(exception)
            updateFailureStatus(Status.EXCEPTION)
        }

        synchronized(lifecycleLock) {
            if (stopping) {
                runner.cancel()
                return
            }
            val job = analyzerScope.launch(exceptionHandler, start = CoroutineStart.LAZY) { runner.runLoop() }
            runnerJobs.add(job)
            job.start()
        }
    }

    fun handleEventEnqueued() {
        totalEventsEnqueued.incrementAndGet()
    }

    fun handleEventProcessed() {
        totalEventsProcessed.incrementAndGet()

        val remainingEvents = totalEventsEnqueued.decrementAndGet()
        if (remainingEvents != 0) return

        val delayedUnits = totalDelayedUnits.getAndSet(0)
        if (delayedUnits != 0) {
            resumeDelayedUnits()
            return
        }

        logger.debug { "All runners are empty" }
        analysisCompletion.complete(Unit)
    }

    fun handleUnitDelayed() {
        totalDelayedUnits.incrementAndGet()
    }

    private fun resumeDelayedUnits() {
        handleEventEnqueued()

        logger.debug { "Resume delayed units" }
        runnerForUnit.values.forEach {
            it.resumeDelayedUnit()
        }

        handleEventProcessed()
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
        val usedMemory = MemoryManager.currentMemoryUsage()
        return "Memory usage: ${usedMemory}/${maxMemory} (${percentToString(usedMemory, maxMemory)})"
    }

    private fun reportRunnerProgress(prevProgress: AtomicInteger) {
        val stats = runnerForUnit.mapValues { it.value.stats() }

        val totalProcessed = totalEventsProcessed.get()
        val delta = totalProcessed - prevProgress.get()
        logger.info { "Progress: ${totalProcessed}/${totalEventsEnqueued.get()} (+$delta)" }
        logger.info { reportMemoryUsage() }

        prevProgress.set(totalProcessed)

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

            val mostTimeSpentMethods = methodStats.stats.values
                .filter { it.analysisTime > 0 }
                .sortedByDescending { it.analysisTime }

            val mostUnprocessedMethods = methodStats.stats.values.sortedByDescending { it.unprocessedEdges }
            val mostStepsMethods = methodStats.stats.values.sortedByDescending { it.steps }
            val mostHandledSummariesMethods = methodStats.stats.values.sortedByDescending { it.handledSummaries }
            val mostSourcesMethods = methodStats.stats.values.sortedByDescending { it.sourceSummaries }
            val mostPassMethods = methodStats.stats.values.sortedByDescending { it.passSummaries }

            buildString {
                if (mostTimeSpentMethods.isNotEmpty()) {
                    appendLine("Time spent")
                    mostTimeSpentMethods.take(10).forEach { appendLine(it) }
                }

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

        private const val OOM_DETECTION_THRESHOLD = 0.90
        private const val TRACE_GENERATION_MEMORY_THRESHOLD = 0.99
    }
}
