package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
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
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintMark
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.jvm.ap.ifds.access.ApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.access.automata.AutomataApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.cactus.CactusApManager
import org.opentaint.dataflow.jvm.ap.ifds.access.tree.TreeApManager
import org.opentaint.dataflow.jvm.ap.ifds.trace.TraceResolver
import org.opentaint.dataflow.jvm.ap.ifds.trace.TraceResolverCancellation
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import java.lang.management.ManagementFactory
import java.lang.management.MemoryNotificationInfo
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import javax.management.Notification
import javax.management.NotificationEmitter
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TaintAnalysisUnitRunnerManager(
    val graph: JIRApplicationGraph,
    val unitResolver: JIRUnitResolver,
    apMode: ApMode = ApMode.Cactus,
    val taintRuleFilter: TaintRuleFilter? = null
): AutoCloseable {
    val lambdaTracker = JIRLambdaTracker()

    val apManager: ApManager = when (apMode) {
        ApMode.Tree -> TreeApManager
        ApMode.Cactus -> CactusApManager
        ApMode.Automata -> AutomataApManager
    }

    class UnitStorage(private val apManager: ApManager) {
        private val vulnerabilities = ConcurrentLinkedQueue<TaintVulnerability>()
        private val methodSummaries = ConcurrentHashMap<MethodEntryPoint, SummaryEdgeStorageWithSubscribers>()

        fun subscribeOnMethodEntryPointSummaries(
            methodEntryPoint: MethodEntryPoint,
            handler: SummaryEdgeStorageWithSubscribers.Subscriber
        ) {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            methodStorage.subscribeOnEdges(handler)
        }

        fun methodZeroSummaries(methodEntryPoint: MethodEntryPoint): Iterator<Edge.ZeroInitialEdge> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.zeroEdgesIterator()
        }

        fun methodZeroToFactSummaries(
            methodEntryPoint: MethodEntryPoint,
            factBase: AccessPathBase
        ): Iterator<Edge.ZeroToFact> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.zeroToFactEdgesIterator(factBase)
        }

        fun methodFactSummaries(
            methodEntryPoint: MethodEntryPoint,
            initialFactAp: FinalFactAp
        ): Iterator<Edge.FactToFact> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.factEdgesIterator(initialFactAp)
        }

        fun methodFactToFactSummaryEdges(
            methodEntryPoint: MethodEntryPoint,
            initialFactAp: FinalFactAp,
            finalFactBase: AccessPathBase
        ): Iterator<Edge.FactToFact> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.factToFactEdgesIterator(initialFactAp, finalFactBase)
        }

        fun addSummaryEdges(initialStatement: MethodEntryPoint, edges: List<Edge>) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addEdges(edges)
        }

        fun methodSinkRequirements(initialStatement: MethodEntryPoint, initialFactAp: FinalFactAp): Iterator<InitialFactAp> {
            val methodStorage = methodSummaryEdges(initialStatement)
            return methodStorage.sinkRequirementIterator(initialFactAp)
        }

        fun addSinkRequirement(initialStatement: MethodEntryPoint, requirement: InitialFactAp) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addSinkRequirement(requirement)
        }

        private fun methodSummaryEdges(methodEntryPoint: MethodEntryPoint) =
            methodSummaries.computeIfAbsent(methodEntryPoint) {
                SummaryEdgeStorageWithSubscribers(apManager, methodEntryPoint)
            }

        fun addVulnerability(vulnerability: TaintVulnerability) {
            vulnerabilities.add(vulnerability)
        }

        fun collectVulnerabilities(collector: MutableList<TaintVulnerability>) {
            collector.addAll(vulnerabilities)
        }

        fun collectMethodStats(stats: MethodStats) {
            methodSummaries.elements().iterator().forEach { it.collectStats(stats) }
        }
    }

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        graph.cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    private val taintConfig: TaintRulesProvider by lazy {
        val provider = object : TaintRulesProvider {
            override fun taintMarks(): Set<TaintMark> =
                taintConfigurationFeature?.getAllTaintMarks() ?: emptySet()

            override fun rulesForMethod(method: JIRMethod): Iterable<TaintConfigurationItem> {
                val config = taintConfigurationFeature ?: return emptyList()
                val rules = config.getConfigForMethod(method)

                if (taintRuleFilter == null) return rules
                return rules.filter { taintRuleFilter.ruleEnabled(it) }
            }
        }

        StringConcatRuleProvider(provider)
    }

    private class StringConcatRuleProvider(private val base: TaintRulesProvider) : TaintRulesProvider {
        private var stringConcatPassThrough: TaintPassThrough? = null

        private fun stringConcatPassThrough(method: JIRMethod): TaintPassThrough =
            stringConcatPassThrough ?: generateRule(method).also { stringConcatPassThrough = it }

        private fun generateRule(method: JIRMethod): TaintPassThrough {
            // todo: string concat hack
            val possibleArgs = (0..20).map { Argument(it) }

            return TaintPassThrough(
                method = method,
                condition = ConstantTrue,
                actionsAfter = possibleArgs.map { CopyAllMarks(from = it, to = Result) })
        }

        override fun taintMarks(): Set<TaintMark> = base.taintMarks()

        override fun rulesForMethod(method: JIRMethod): Iterable<TaintConfigurationItem> {
            val baseRules = base.rulesForMethod(method)

            if (method.name == "makeConcatWithConstants" && method.enclosingClass.name == "java.lang.invoke.StringConcatFactory") {
                return (sequenceOf(stringConcatPassThrough(method)) + baseRules).asIterable()
            }

            return baseRules
        }
    }

    private val runnerForUnit = ConcurrentHashMap<UnitType, TaintAnalysisUnitRunner>()
    private val unitStorage = ConcurrentHashMap<UnitType, UnitStorage>()
    private val methodDependencies = ConcurrentHashMap<JIRMethod, MutableSet<UnitType>>()

    private val runnerJobs = ConcurrentLinkedQueue<Job>()
    private val stopSignal = CompletableDeferred<Unit>()

    private val totalEventsProcessed = AtomicInteger()
    private val totalEventsEnqueued = AtomicInteger()

    @OptIn(DelicateCoroutinesApi::class)
    private val analyzerDispatcher = newFixedThreadPoolContext(
        nThreads = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1),
        name = "${this::class.java.name}-worker"
    )

    private val analyzerScope = CoroutineScope(analyzerDispatcher)
    private val traceResolverScope = CoroutineScope(analyzerDispatcher)

    @OptIn(DelicateCoroutinesApi::class)
    private val progressDispatcher = newSingleThreadContext(
        name = "${this::class.java.name}-progress"
    )

    private val progressScope = CoroutineScope(progressDispatcher)

    private val factTypeChecker = FactTypeCheckerImpl(graph.cp)

    override fun close() {
        analyzerDispatcher.close()
        progressDispatcher.close()

        (analyzerDispatcher.executor as? ExecutorService)?.shutdownNow()
        (progressDispatcher.executor as? ExecutorService)?.shutdownNow()
    }

    private fun MemoryPoolMXBean.updateThresholds() {
        val threshold = (OOM_DETECTION_THRESHOLD * usage.max).roundToLong()
        collectionUsageThreshold = threshold
        usageThreshold = threshold
    }

    private fun setupMemcheck() {
        val memoryPool = ManagementFactory.getMemoryPoolMXBeans()
            .singleOrNull {
                it.type == MemoryType.HEAP
                        && it.isUsageThresholdSupported
                        && it.isCollectionUsageThresholdSupported
            }
            ?: error("Expected exactly one memory pool that support threshold")

        memoryPool.updateThresholds()

        val notificationListener: (Notification, Any?) -> Unit = { notification, _ ->
            if (notification.type == MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED) {
                // The pool could have been resized => updating absolute thresholds
                memoryPool.updateThresholds()
            } else {
                check(notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED)
                logger.error { "Running low on memory, stopping analysis" }
                stopSignal.complete(Unit)
            }
        }

        val notificationFilter: (Notification) -> Boolean = { notification ->
            notification.type == MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED
                    || notification.type == MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED
        }

        val emitter = ManagementFactory.getMemoryMXBean() as NotificationEmitter
        emitter.addNotificationListener(notificationListener, notificationFilter, null)
    }

    fun runAnalysis(startMethods: List<JIRMethod>, timeout: Duration, cancellationTimeout: Duration) {
        val timeStart = TimeSource.Monotonic.markNow()

        val unitStartMethods = startMethods.groupBy { unitResolver.resolve(it) }.filterKeys { it != UnknownUnit }

        logger.info { "Starting analysis of ${startMethods.size} methods in ${unitStartMethods.size} units" }

        handleEventEnqueued()

        for ((unit, methods) in unitStartMethods) {
            val runner = getOrSpawnUnitRunner(unit)
            runner?.submitStartMethods(methods)
        }

        handleEventProcessed()

        setupMemcheck()

        // Spawn progress job:
        val progress = progressScope.launch {
            var previousEnqueuedEdges = -1
            while (isActive) {
                delay(10.seconds)
                val currentEnqueuedEdges = reportRunnerProgress()

                if (previousEnqueuedEdges == 0 && currentEnqueuedEdges == 0) {
                    logger.error { "No enqueued edges, but analyzer didn't finished" }
                    stopSignal.complete(Unit)
                }

                previousEnqueuedEdges = currentEnqueuedEdges
            }
        }

        runBlocking {
            try {
                val timeoutFailure = withTimeoutOrNull(timeout) {
                    stopSignal.await()
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

    data class VulnerabilityWithTrace(val vulnerability: TaintVulnerability, val trace: TraceResolver.Trace)

    fun resolveVulnerabilityTraces(
        entryPoints: Set<JIRMethod>,
        vulnerabilities: List<TaintVulnerability>,
        resolverParams: TraceResolver.Params,
        timeout: Duration,
        cancellationTimeout: Duration
    ): List<VulnerabilityWithTrace> {
        val traceResolverCancellation = TraceResolverCancellation()
        val traceResolver = TraceResolver(entryPoints, this, resolverParams, traceResolverCancellation)
        val result = ConcurrentLinkedQueue<VulnerabilityWithTrace>()

        val processed = AtomicInteger()
        val traceResolutionComplete = CompletableDeferred<Unit>()

        val traceResolverJobs = vulnerabilities.map { vulnerability ->
            traceResolverScope.launch {
                val trace = traceResolver.resolveTrace(vulnerability)
                result.add(VulnerabilityWithTrace(vulnerability, trace))

                if (processed.incrementAndGet() == vulnerabilities.size) {
                    traceResolutionComplete.complete(Unit)
                }
            }
        }

        val progress = progressScope.launch {
            while (isActive) {
                delay(10.seconds)
                logger.info { "Resolved ${processed.get()}/${vulnerabilities.size} traces" }
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
                traceResolverJobs.joinAll()
            }
        }

        logger.info { "Resolved ${processed.get()}/${vulnerabilities.size} traces" }
        return result.toList()
    }

    fun methodCallers(method: JIRMethod): Set<UnitType> =
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
        val storage = unitStorage.getOrPut(unit) { UnitStorage(apManager) }
        val sinkTracker = TaintSinkTracker(apManager, storage)
        val callResolver = JIRCallResolver(graph, unitResolver)
        val runner = TaintAnalysisUnitRunner(
            this, unit, graph, callResolver, unitResolver, taintConfig, factTypeChecker, sinkTracker
        )

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            logger.error { "Got exception from runner for unit $unit, stopping analysis" }
            stopSignal.completeExceptionally(exception)
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
            stopSignal.complete(Unit)
        }
    }

    fun handleCrossUnitZeroCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrSpawnUnitRunner(unit) ?: return

        registerMethodCallFromUnit(methodEntryPoint.method, callerUnit)

        runner.submitExternalInitialZeroFact(methodEntryPoint)
    }

    fun handleCrossUnitFactCall(callerUnit: UnitType, methodEntryPoint: MethodEntryPoint, methodFactAp: FinalFactAp) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrSpawnUnitRunner(unit) ?: return

        registerMethodCallFromUnit(methodEntryPoint.method, callerUnit)

        runner.submitExternalInitialFact(methodEntryPoint, methodFactAp)
    }

    private fun registerMethodCallFromUnit(method: JIRMethod, unit: UnitType) {
        val dependencies = methodDependencies.computeIfAbsent(method) {
            ConcurrentHashMap.newKeySet()
        }
        dependencies.add(unit)
    }

    fun newSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return

        val storage = unitStorage.getValue(unit)
        storage.addSummaryEdges(methodEntryPoint, edges)
    }

    fun newSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: InitialFactAp) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return

        val storage = unitStorage.getValue(unit)
        storage.addSinkRequirement(methodEntryPoint, requirement)
    }

    fun subscribeOnMethodEntryPointSummaries(
        methodEntryPoint: MethodEntryPoint,
        handler: SummaryEdgeStorageWithSubscribers.Subscriber
    ) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return

        val storage = unitStorage.getValue(unit)
        storage.subscribeOnMethodEntryPointSummaries(methodEntryPoint, handler)
    }

    fun findZeroSummaryEdges(methodEntryPoint: MethodEntryPoint): Iterator<Edge.ZeroInitialEdge> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return emptyList<Edge.ZeroInitialEdge>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodZeroSummaries(methodEntryPoint)
    }

    fun findZeroToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        factBase: AccessPathBase
    ): Iterator<Edge.ZeroToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        findUnitRunner(unit) ?: return emptyList<Edge.ZeroToFact>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodZeroToFactSummaries(methodEntryPoint, factBase)
    }

    fun findFactSummaryEdges(methodEntryPoint: MethodEntryPoint, initialFactAp: FinalFactAp): Iterator<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return emptyList<Edge.FactToFact>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodFactSummaries(methodEntryPoint, initialFactAp)
    }

    fun findFactToFactSummaryEdges(
        methodEntryPoint: MethodEntryPoint,
        initialFactAp: FinalFactAp,
        finalFactBase: AccessPathBase
    ): Iterator<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        findUnitRunner(unit) ?: return emptyList<Edge.FactToFact>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodFactToFactSummaryEdges(methodEntryPoint, initialFactAp, finalFactBase)
    }

    fun findSinkRequirements(methodEntryPoint: MethodEntryPoint, initialFactAp: FinalFactAp): Iterator<InitialFactAp> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return emptyList<InitialFactAp>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodSinkRequirements(methodEntryPoint, initialFactAp)
    }

    private fun reportRunnerProgress(): Int {
        val stats = runnerForUnit.mapValues { it.value.stats() }

        logger.info { "Progress: ${totalEventsProcessed.get()}/${totalEventsEnqueued.get()}" }
        logger.info {
            val maxMemory = Runtime.getRuntime().maxMemory()
            val usedMemory = maxMemory - Runtime.getRuntime().freeMemory()
            "Memory usage: ${usedMemory}/${maxMemory} (${percentToString(usedMemory, maxMemory)})"
        }

        logger.debug {
            val localTotal = factTypeChecker.localFactsTotal.sum()
            val localRejected = factTypeChecker.localFactsRejected.sum()
            val accessTotal = factTypeChecker.accessTotal.sum()
            val accessRejected = factTypeChecker.accessRejected.sum()
            buildString {
                append("Fact types: ")
                append("local $localRejected/$localTotal (${percentToString(localRejected, localTotal)})")
                append(" | ")
                append("access $accessRejected/$accessTotal (${percentToString(accessRejected, accessTotal)})")
            }
        }

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

        return totalEventsEnqueued.get()
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val OOM_DETECTION_THRESHOLD = 0.97
    }
}
