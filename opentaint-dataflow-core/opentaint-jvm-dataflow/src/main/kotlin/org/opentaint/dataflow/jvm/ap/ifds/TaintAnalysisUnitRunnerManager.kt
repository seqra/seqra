package org.opentaint.dataflow.jvm.ap.ifds

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
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
import org.opentaint.dataflow.taint.TaintVulnerability as OldTaintVulnerability
import org.opentaint.dataflow.ifds.AccessPath as OldAccessPath
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.taint.TaintVertex
import org.opentaint.dataflow.taint.TaintZeroFact
import org.opentaint.dataflow.taint.Tainted
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class TaintAnalysisUnitRunnerManager(
    val graph: JIRApplicationGraph,
    val unitResolver: JIRUnitResolver,
    val taintRuleFilter: TaintRuleFilter? = null
): AutoCloseable {
    data class WorkListEmptyEvent(val unit: UnitType, val isEmpty: Boolean)

    class UnitStorage {
        private val vulnerabilities = ConcurrentLinkedQueue<TaintVulnerability>()
        private val methodSummaries = ConcurrentHashMap<JIRInst, SummaryEdgeStorageWithSubscribers>()

        fun subscribeOnMethodEntryPointSummaries(
            methodEntryPoint: JIRInst,
            handler: SummaryEdgeStorageWithSubscribers.Subscriber
        ) {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            methodStorage.subscribeOnEdges(handler)
        }

        fun methodZeroSummaries(methodEntryPoint: JIRInst): Iterator<Edge.ZeroInitialEdge> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.zeroEdgesIterator()
        }

        fun methodFactSummaries(methodEntryPoint: JIRInst, initialFact: Fact.TaintedTree): Iterator<Edge.FactToFact> {
            val methodStorage = methodSummaryEdges(methodEntryPoint)
            return methodStorage.factEdgesIterator(initialFact)
        }

        fun addSummaryEdges(initialStatement: JIRInst, edges: List<Edge>) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addEdges(edges)
        }

        fun methodSinkRequirements(initialStatement: JIRInst, initialFact: Fact.TaintedTree): Iterator<Fact.TaintedPath> {
            val methodStorage = methodSummaryEdges(initialStatement)
            return methodStorage.sinkRequirementIterator(initialFact)
        }

        fun addSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath) {
            val methodStorage = methodSummaryEdges(initialStatement)
            methodStorage.addSinkRequirement(requirement)
        }

        private fun methodSummaryEdges(methodEntryPoint: JIRInst) =
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
            val classSummaries = methodSummaries.entries.groupBy { it.key.location.method.enclosingClass }
            return classSummaries.mapValues { (_, summaries) ->
                summaries.groupBy(
                    { it.key.location.method },
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
        graph.project.features
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

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JIRMethod>> = hashMapOf()
    private val runnerForUnit: MutableMap<UnitType, TaintAnalysisUnitRunner> = hashMapOf()
    private val queueIsEmpty = ConcurrentHashMap<UnitType, Boolean>()

    private val unitStorage: MutableMap<UnitType, UnitStorage> = hashMapOf()

    private val stopSignal = CompletableDeferred<Unit>()

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

    private val factTypeChecker = FactTypeChecker(graph.project)

    override fun close() {
        analyzerDispatcher.close()
        progressDispatcher.close()

        (analyzerDispatcher.executor as? ExecutorService)?.shutdownNow()
        (progressDispatcher.executor as? ExecutorService)?.shutdownNow()
    }

    fun addStart(method: JIRMethod) {
        registerEntryPoints(listOf(method))
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
        val stubAp = OldAccessPath(JIRThis(graph.project.objectType), emptyList())
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

        // Add start methods:
        registerEntryPoints(startMethods)

        // Determine all units:
        val allUnits = methodsForUnit.keys.toList()
        logger.info {
            val numMethods = methodsForUnit.values.sumOf { it.size }
            "Starting analysis of $numMethods methods in ${allUnits.size} units"
        }

        // Spawn runner jobs:
        val allJobs = allUnits.map { unit ->
            // Create the runner:
            val runner = newRunner(unit)

            // Start the runner:
            analyzerScope.launch(start = CoroutineStart.LAZY) {
                val methods = methodsForUnit[unit].orEmpty().toList()
                runner.run(methods)
            }
        }

        // Spawn progress job:
        val progress = progressScope.launch {
            var previousEnqueuedEdges = -1L
            while (isActive) {
                delay(10.seconds)
                val currentEnqueuedEdges = reportRunnerProgress()

                if (previousEnqueuedEdges == 0L && currentEnqueuedEdges == 0L) {
                    logger.error { "No enqueued edges, but analyzer didn't finished" }
                    stopSignal.complete(Unit)
                }

                previousEnqueuedEdges = currentEnqueuedEdges
            }
        }

        // Spawn stopper job:
        val stopper = progressScope.launch {
            stopSignal.await()
            logger.info { "Stopping all runners..." }
            allJobs.forEach { it.cancel() }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()
        allJobs.forEach { it.start() }

        // Await all runners:
        runBlocking {
            withTimeoutOrNull(timeout) {
                allJobs.joinAll()
            } ?: run {
                logger.warn { "Ifds analysis timeout" }
                runnerForUnit.values.forEach { it.cancel() }

                withTimeoutOrNull(cancellationTimeout) {
                    allJobs.forEach { it.cancel() }
                    allJobs.joinAll()
                }
            }

            withTimeoutOrNull(cancellationTimeout) {
                progress.cancelAndJoin()
                stopper.cancelAndJoin()
            }
        }

        reportRunnerProgress()
        logger.info { "All ${allJobs.size} jobs completed in ${timeStartJobs.elapsedNow()}" }
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


    private fun registerEntryPoints(methods: List<JIRMethod>) {
        val methodsWithUnresolvedDependencies = mutableListOf<JIRMethod>()
        for (method in methods) {
            val unit = unitResolver.resolve(method)
            if (unit == UnknownUnit) continue

            val unitMethods = methodsForUnit.getOrPut(unit) { hashSetOf() }
            if (unitMethods.add(method)) {
                methodsWithUnresolvedDependencies.add(method)
            }
        }

        val resolvedMethods = hashSetOf<JIRMethod>()
        while (methodsWithUnresolvedDependencies.isNotEmpty()) {
            val method = methodsWithUnresolvedDependencies.removeLast()
            if (!resolvedMethods.add(method)) continue

            val unit = unitResolver.resolve(method)
            if (unit == UnknownUnit) continue

            methodsForUnit.getOrPut(unit) { hashSetOf() }

            for (inst in method.instList) {
                graph.callees(inst).filterTo(methodsWithUnresolvedDependencies) { it !in resolvedMethods }
            }
        }
    }

    private fun newRunner(unit: UnitType): TaintAnalysisUnitRunner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }
        val storage = unitStorage.getOrPut(unit) { UnitStorage() }

        val sinkTracker = TaintSinkTracker(storage)
        val runner = TaintAnalysisUnitRunner(this, unit, graph, unitResolver, taintConfig, factTypeChecker, sinkTracker)
        runnerForUnit[unit] = runner
        return runner
    }

    fun handleControlEvent(event: WorkListEmptyEvent) {
        queueIsEmpty[event.unit] = event.isEmpty
        if (event.isEmpty) {
            if (runnerForUnit.keys.all { queueIsEmpty[it] == true }) {
                logger.debug { "All runners are empty" }
                stopSignal.complete(Unit)
            }
        }
    }

    fun handleCrossUnitZeroCall(methodEntryPoint: JIRInst) {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
        val runner = runnerForUnit[unit] ?: return
        runner.submitExternalInitialZeroFact(methodEntryPoint)
    }

    fun handleCrossUnitFactCall(methodEntryPoint: JIRInst, methodFact: Fact.TaintedTree) {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
        val runner = runnerForUnit[unit] ?: return
        runner.submitExternalInitialFact(methodEntryPoint, methodFact)
    }

    fun newSummaryEdges(initialStatement: JIRInst, edges: List<Edge>) {
        val unit = unitResolver.resolve(initialStatement.location.method)
        val storage = unitStorage.getValue(unit)
        storage.addSummaryEdges(initialStatement, edges)
    }

    fun newSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath) {
        val unit = unitResolver.resolve(initialStatement.location.method)
        val storage = unitStorage.getValue(unit)
        storage.addSinkRequirement(initialStatement, requirement)
    }

    fun subscribeOnMethodEntryPointSummaries(
        methodEntryPoint: JIRInst,
        handler: SummaryEdgeStorageWithSubscribers.Subscriber
    ) {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
        val storage = unitStorage.getValue(unit)
        storage.subscribeOnMethodEntryPointSummaries(methodEntryPoint, handler)
    }

    fun findZeroSummaryEdges(methodEntryPoint: JIRInst): Iterator<Edge.ZeroInitialEdge> {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
        val storage = unitStorage.getValue(unit)
        return storage.methodZeroSummaries(methodEntryPoint)
    }

    fun findFactSummaryEdges(methodEntryPoint: JIRInst, initialFact: Fact.TaintedTree): Iterator<Edge.FactToFact> {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
        val storage = unitStorage.getValue(unit)
        return storage.methodFactSummaries(methodEntryPoint, initialFact)
    }

    fun findSinkRequirements(methodEntryPoint: JIRInst, initialFact: Fact.TaintedTree): Iterator<Fact.TaintedPath> {
        val unit = unitResolver.resolve(methodEntryPoint.location.method)
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

    private fun reportRunnerProgress(): Long {
        val stats = runnerForUnit.mapValues { it.value.stats() }
        val totalProcessed = stats.values.sumOf { it.processed }
        val totalEnqueued = stats.values.sumOf { it.enqueued }

        logger.info { "Progress: ${totalProcessed}/${totalEnqueued}" }
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

        return totalEnqueued
    }

    private fun percentToString(current: Long, total: Long): String {
        val percentValue = current.toDouble() / total
        return String.format("%.2f", percentValue * 100) + "%"
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
