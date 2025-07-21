package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThis
import org.opentaint.ir.api.jvm.ext.objectType
import org.opentaint.ir.taint.configuration.Argument
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.CopyAllMarks
import org.opentaint.ir.taint.configuration.Result
import org.opentaint.ir.taint.configuration.TaintConfigurationFeature
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.TaintSinkTracker.TaintVulnerability
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.taint.TaintVertex
import org.opentaint.dataflow.taint.TaintZeroFact
import org.opentaint.dataflow.taint.Tainted
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.opentaint.dataflow.ifds.AccessPath as OldAccessPath
import org.opentaint.dataflow.taint.TaintVulnerability as OldTaintVulnerability

class TaintAnalysisUnitRunnerManager(
    val graph: JIRApplicationGraph,
    val unitResolver: JIRUnitResolver,
    val taintRuleFilter: TaintRuleFilter? = null
): AutoCloseable {
    class UnitStorage {
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

        fun methodFactSummaries(methodEntryPoint: MethodEntryPoint, initialFact: Fact.TaintedTree): Iterator<Edge.FactToFact> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.factEdgesIterator(initialFact)
        }

        fun addSummaryEdges(initialStatement: MethodEntryPoint, edges: List<Edge>) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addEdges(edges)
        }

        fun methodSinkRequirements(initialStatement: MethodEntryPoint, initialFact: Fact.TaintedTree): Iterator<Fact.TaintedPath> {
            val methodStorage = methodSummaryEdges(initialStatement)
            return methodStorage.sinkRequirementIterator(initialFact)
        }

        fun addSinkRequirement(initialStatement: MethodEntryPoint, requirement: Fact.TaintedPath) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addSinkRequirement(requirement)
        }

        private fun methodSummaryEdges(methodEntryPoint: MethodEntryPoint) =
            methodSummaries.computeIfAbsent(methodEntryPoint) { SummaryEdgeStorageWithSubscribers(methodEntryPoint) }

        fun addVulnerability(vulnerability: TaintVulnerability) {
            vulnerabilities.add(vulnerability)
        }

        fun collectVulnerabilities(collector: MutableList<TaintVulnerability>) {
            collector.addAll(vulnerabilities)
        }

        fun collectMethodStats(stats: MethodStats) {
            methodSummaries.elements().iterator().forEach { it.collectStats(stats) }
        }

        fun allSummaries(): Map<JIRClassOrInterface, Map<JIRMethod, Sequence<Edge>>> {
            val classSummaries = methodSummaries.entries.groupBy { it.key.method.enclosingClass }
            return classSummaries.mapValues { (_, summaries) ->
                summaries.groupBy(
                    { it.key.method },
                    {
                        val zeroEdges = it.value.zeroEdgesIterator().asSequence()
                        val factEdges = it.value.factEdgesIterator().asSequence()
                        zeroEdges + factEdges
                    }
                ).mapValues { (_, summaries) -> summaries.asSequence().flatten() }
            }
        }
    }

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        graph.cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    private val taintConfig: TaintRulesProvider by lazy {
        val provider = object : TaintRulesProvider {
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

    @OptIn(DelicateCoroutinesApi::class)
    private val progressDispatcher = newSingleThreadContext(
        name = "${this::class.java.name}-progress"
    )

    private val progressScope = CoroutineScope(progressDispatcher)

    private val factTypeChecker = FactTypeChecker(graph.cp)

    override fun close() {
        analyzerDispatcher.close()
        progressDispatcher.close()

        (analyzerDispatcher.executor as? ExecutorService)?.shutdownNow()
        (progressDispatcher.executor as? ExecutorService)?.shutdownNow()
    }

    // Analyze with compatibility
    fun analyze(startMethods: List<JIRMethod>, timeout: Duration): List<OldTaintVulnerability<JIRInst>> {
        runAnalysis(startMethods, timeout, cancellationTimeout = 10.seconds)
        return getOldVulnerabilities()
    }

    fun getOldVulnerabilities() = getVulnerabilities().map { convertToOldVulnerability(it) }

    private fun convertToOldVulnerability(
        vulnerability: TaintVulnerability
    ): OldTaintVulnerability<JIRInst> = with(vulnerability) {
        val stubAp = OldAccessPath(JIRThis(graph.cp.objectType), emptyList())
        val convertedFact = when(val edgeFact = fact){
            Fact.Zero -> TaintZeroFact
            is Fact.TaintedPath -> Tainted(stubAp, edgeFact.mark)
            is Fact.TaintedTree -> Tainted(stubAp, edgeFact.mark)
        }

        return OldTaintVulnerability(
            message = rule.ruleNote,
            rule = rule,
            sink = TaintVertex(statement, convertedFact)
        )
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
            val timeoutFailure = withTimeoutOrNull(timeout) { stopSignal.await() }
            if (timeoutFailure == null) {
                logger.warn { "Ifds analysis timeout" }
            }

            runnerForUnit.elements().asSequence().forEach { it.cancel() }

            withTimeoutOrNull(cancellationTimeout) {
                progress.cancelAndJoin()
                runnerJobs.forEach { it.cancel() }
                runnerJobs.joinAll()
            }
        }

        reportRunnerProgress()
        logger.info { "Analysis done in ${timeStart.elapsedNow()}" }
    }

    fun getVulnerabilities(): List<TaintVulnerability> {
        // Extract found vulnerabilities (sinks):
        val vulnerabilities = mutableListOf<TaintVulnerability>()
        unitStorage.values.forEach { storage ->
            storage.collectVulnerabilities(vulnerabilities)
        }

        return vulnerabilities
            .also { logger.info { "Total vulnerabilities: ${it.size}" } }
    }

    private fun getOrSpawnUnitRunner(unit: UnitType): TaintAnalysisUnitRunner? {
        if (unit == UnknownUnit) return null
        return runnerForUnit.computeIfAbsent(unit) {
            spawnNewRunner(unit)
        }
    }

    private fun spawnNewRunner(unit: UnitType): TaintAnalysisUnitRunner {
        val storage = unitStorage.getOrPut(unit) { UnitStorage() }
        val sinkTracker = TaintSinkTracker(storage)
        val callResolver = JIRCallResolver(graph, unitResolver)
        val runner = TaintAnalysisUnitRunner(
            this, unit, graph, callResolver, unitResolver, taintConfig, factTypeChecker, sinkTracker
        )

        val job = analyzerScope.launch { runner.runLoop() }
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

    fun handleCrossUnitZeroCall(methodEntryPoint: MethodEntryPoint) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrSpawnUnitRunner(unit) ?: return
        runner.submitExternalInitialZeroFact(methodEntryPoint)
    }

    fun handleCrossUnitFactCall(methodEntryPoint: MethodEntryPoint, methodFact: Fact.TaintedTree) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        val runner = getOrSpawnUnitRunner(unit) ?: return
        runner.submitExternalInitialFact(methodEntryPoint, methodFact)
    }

    fun newSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>) {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return

        val storage = unitStorage.getValue(unit)
        storage.addSummaryEdges(methodEntryPoint, edges)
    }

    fun newSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: Fact.TaintedPath) {
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

    fun findFactSummaryEdges(methodEntryPoint: MethodEntryPoint, initialFact: Fact.TaintedTree): Iterator<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return emptyList<Edge.FactToFact>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodFactSummaries(methodEntryPoint, initialFact)
    }

    fun findSinkRequirements(methodEntryPoint: MethodEntryPoint, initialFact: Fact.TaintedTree): Iterator<Fact.TaintedPath> {
        val unit = unitResolver.resolve(methodEntryPoint.method)
        getOrSpawnUnitRunner(unit) ?: return emptyList<Fact.TaintedPath>().iterator()

        val storage = unitStorage.getValue(unit)
        return storage.methodSinkRequirements(methodEntryPoint, initialFact)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun dumpSummariesJson(json: Json, outputStream: OutputStream) {
        val classSummaries = mutableListOf<ClassSummaries>()
        unitStorage.forEach { (_, storage) ->
            storage.allSummaries().mapTo(classSummaries) { (cls, methods) ->
                val methodSummaries = methods.map { (method, summaries) ->
                    MethodSummaries("$method", summaries)
                }
                ClassSummaries(cls.name, methodSummaries)
            }
        }

        json.encodeToStream(classSummaries, outputStream)
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
    }
}
