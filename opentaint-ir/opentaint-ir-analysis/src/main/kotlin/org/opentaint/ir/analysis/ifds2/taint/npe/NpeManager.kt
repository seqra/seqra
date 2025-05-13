package org.opentaint.ir.analysis.ifds2.taint.npe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.opentaint.ir.analysis.engine.SummaryStorageImpl
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.engine.UnitType
import org.opentaint.ir.analysis.ifds2.ControlEvent
import org.opentaint.ir.analysis.ifds2.Manager
import org.opentaint.ir.analysis.ifds2.QueueEmptinessChanged
import org.opentaint.ir.analysis.ifds2.UniRunner
import org.opentaint.ir.analysis.ifds2.pathEdges
import org.opentaint.ir.analysis.ifds2.taint.EdgeForOtherRunner
import org.opentaint.ir.analysis.ifds2.taint.NewSummaryEdge
import org.opentaint.ir.analysis.ifds2.taint.NewVulnerability
import org.opentaint.ir.analysis.ifds2.taint.SummaryEdge
import org.opentaint.ir.analysis.ifds2.taint.TaintEdge
import org.opentaint.ir.analysis.ifds2.taint.TaintEvent
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.TaintRunner
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.Vulnerability
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.taint.configuration.TaintMark
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

class NpeManager(
    private val graph: JIRApplicationGraph,
    private val unitResolver: UnitResolver,
) : Manager<TaintFact, TaintEvent> {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JIRMethod>> = hashMapOf()
    private val runnerForUnit: MutableMap<UnitType, TaintRunner> = hashMapOf()
    private val queueIsEmpty: MutableMap<UnitType, Boolean> = ConcurrentHashMap()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<Vulnerability>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun newRunner(
        unit: UnitType,
    ): TaintRunner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph)
        val runner = UniRunner(graph, analyzer, this@NpeManager, unitResolver, unit)

        runnerForUnit[unit] = runner
        return runner
    }

    private fun addStart(method: JIRMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        methodsForUnit.getOrPut(unit) { mutableSetOf() }.add(method)
        // TODO: val isNew = (...).add(); if (isNew) { deps.forEach { addStart(it) } }
    }

    @OptIn(ExperimentalTime::class)
    fun analyze(
        startMethods: List<JIRMethod>,
    ): List<Vulnerability> = runBlocking(Dispatchers.Default) {
        val timeStart = TimeSource.Monotonic.markNow()

        // Add start methods:
        for (method in startMethods) {
            addStart(method)
        }

        // Determine all units:
        val allUnits = methodsForUnit.keys.toList()
        logger.info {
            "Starting analysis of ${
                methodsForUnit.values.sumOf { it.size }
            } methods in ${allUnits.size} units"
        }

        // Spawn runner jobs:
        val allJobs = allUnits.map { unit ->
            // Create the runner:
            val runner = newRunner(unit)

            // Start the runner:
            launch(start = CoroutineStart.LAZY) {
                val methods = methodsForUnit[unit]!!.toList()
                runner.run(methods)
            }
        }

        // Spawn progress job:
        val progress = launch(Dispatchers.IO) {
            logger.info { "Progress job started" }
            while (isActive) {
                delay(1.seconds)
                logger.info {
                    "Progress: total propagated ${
                        runnerForUnit.values.sumOf { it.pathEdges.size }
                    } path edges"
                }
            }
            logger.info { "Progress job finished" }
        }

        // Spawn stopper job:
        val stopper = launch(Dispatchers.IO) {
            logger.info { "Stopper job started" }
            stopRendezvous.receive()
            // delay(100)
            // @OptIn(ExperimentalCoroutinesApi::class)
            // if (runnerForUnit.values.any { !it.workList.isEmpty }) {
            //     logger.warn { "NOT all runners have empty work list" }
            //     error("?")
            // }
            logger.info { "Stopping all runners..." }
            allJobs.forEach { it.cancel() }
            logger.info { "Stopper job finished" }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()
        allJobs.forEach { it.start() }

        // Await all runners:
        withTimeoutOrNull(3600.seconds) {
            allJobs.joinAll()
        } ?: run {
            allJobs.forEach { it.cancel() }
            allJobs.joinAll()
        }
        progress.cancelAndJoin()
        stopper.cancelAndJoin()
        logger.info {
            "All ${allJobs.size} jobs completed in %.1f s".format(
                timeStartJobs.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }

        // Extract found vulnerabilities (sinks):
        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods
            .flatMap { method ->
                vulnerabilitiesStorage.getCurrentFacts(method)
            }
        logger.debug { "Total found ${foundVulnerabilities.size} vulnerabilities" }
        for (vulnerability in foundVulnerabilities) {
            logger.debug { "$vulnerability in ${vulnerability.method}" }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }

        logger.info { "Total propagated ${runnerForUnit.values.sumOf { it.pathEdges.size }} path edges" }

        if (logger.isDebugEnabled()) {
            val statsFileName = "stats.csv"
            logger.debug { "Writing stats in '$statsFileName'..." }
            File(statsFileName).outputStream().bufferedWriter().use { writer ->
                val sep = ";"
                writer.write(listOf("classname", "cwe", "method", "sink", "fact").joinToString(sep) + "\n")
                for (vulnerability in foundVulnerabilities) {
                    val m = vulnerability.method
                    if (vulnerability.rule != null) {
                        for (cwe in vulnerability.rule.cwe) {
                            writer.write(
                                listOf(
                                    m.enclosingClass.simpleName,
                                    cwe,
                                    m.name,
                                    vulnerability.sink.statement,
                                    vulnerability.sink.fact
                                ).joinToString(sep) { "\"$it\"" } + "\n")
                        }
                    } else if (
                        vulnerability.sink.fact is Tainted
                        && vulnerability.sink.fact.mark == TaintMark.NULLNESS
                    ) {
                        val cwe = 476
                        writer.write(
                            listOf(
                                m.enclosingClass.simpleName,
                                cwe,
                                m.name,
                                vulnerability.sink.statement,
                                vulnerability.sink.fact
                            ).joinToString(sep) { "\"$it\"" } + "\n")
                    } else {
                        logger.warn { "Bad vulnerability without rule: $vulnerability" }
                    }
                }
            }
        }

        logger.info {
            "Analysis done in %.1f s".format(
                timeStart.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }
        foundVulnerabilities
    }

    override fun handleEvent(event: TaintEvent) {
        when (event) {
            is NewSummaryEdge -> {
                summaryEdgesStorage.add(SummaryEdge(event.edge))
            }

            is NewVulnerability -> {
                vulnerabilitiesStorage.add(event.vulnerability)
            }

            is EdgeForOtherRunner -> {
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = runnerForUnit[unit] ?: run {
                    logger.trace { "Ignoring event=$event for non-existing runner for unit=$unit" }
                    return
                }
                otherRunner.submitNewEdge(event.edge)
            }
        }
    }

    override fun handleControlEvent(event: ControlEvent) {
        when (event) {
            is QueueEmptinessChanged -> {
                queueIsEmpty[event.runner.unit] = event.isEmpty
                if (event.isEmpty) {
                    if (runnerForUnit.keys.all { queueIsEmpty[it] == true }) {
                        stopRendezvous.trySend(Unit).getOrNull()
                    }
                }
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: JIRMethod,
        scope: CoroutineScope,
        handler: (TaintEdge) -> Unit,
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .onEach { handler(it.edge) }
            .launchIn(scope)
    }
}

fun runNpeAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver,
    startMethods: List<JIRMethod>,
): List<Vulnerability> {
    val manager = NpeManager(graph, unitResolver)
    return manager.analyze(startMethods)
}
