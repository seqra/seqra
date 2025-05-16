package org.opentaint.ir.analysis.unused

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
import org.opentaint.ir.analysis.ifds.ControlEvent
import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.Manager
import org.opentaint.ir.analysis.ifds.QueueEmptinessChanged
import org.opentaint.ir.analysis.ifds.Runner
import org.opentaint.ir.analysis.ifds.SummaryStorageImpl
import org.opentaint.ir.analysis.ifds.UniRunner
import org.opentaint.ir.analysis.ifds.UnitResolver
import org.opentaint.ir.analysis.ifds.UnitType
import org.opentaint.ir.analysis.ifds.UnknownUnit
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.util.getPathEdges
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = mu.KotlinLogging.logger {}

class UnusedVariableManager(
    private val graph: JIRApplicationGraph,
    private val unitResolver: UnitResolver,
) : Manager<UnusedVariableDomainFact, Event> {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JIRMethod>> = hashMapOf()
    private val runnerForUnit: MutableMap<UnitType, Runner<UnusedVariableDomainFact>> = hashMapOf()
    private val queueIsEmpty = ConcurrentHashMap<UnitType, Boolean>()

    private val summaryEdgesStorage = SummaryStorageImpl<UnusedVariableSummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<UnusedVariableVulnerability>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun newRunner(
        unit: UnitType,
    ): Runner<UnusedVariableDomainFact> {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        logger.debug { "Creating a new runner for $unit" }
        val analyzer = UnusedVariableAnalyzer(graph)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@UnusedVariableManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = UnusedVariableZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    private fun getAllCallees(method: JIRMethod): Set<JIRMethod> {
        val result: MutableSet<JIRMethod> = hashSetOf()
        for (inst in method.flowGraph().instructions) {
            result += graph.callees(inst)
        }
        return result
    }

    private fun addStart(method: JIRMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        val isNew = methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        if (isNew) {
            for (dep in getAllCallees(method)) {
                addStart(dep)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    fun analyze(
        startMethods: List<JIRMethod>,
        timeout: Duration = 3600.seconds,
    ): List<UnusedVariableVulnerability> = runBlocking {
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
            while (isActive) {
                delay(1.seconds)
                logger.info {
                    "Progress: propagated ${
                        runnerForUnit.values.sumOf { it.getPathEdges().size }
                    } path edges"
                }
            }
        }

        // Spawn stopper job:
        val stopper = launch(Dispatchers.IO) {
            stopRendezvous.receive()
            logger.info { "Stopping all runners..." }
            allJobs.forEach { it.cancel() }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()
        allJobs.forEach { it.start() }

        // Await all runners:
        withTimeoutOrNull(timeout) {
            allJobs.joinAll()
        } ?: run {
            logger.info { "Timeout!" }
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
        val foundVulnerabilities = allUnits.flatMap { unit ->
            val runner = runnerForUnit[unit] ?: error("No runner for $unit")
            val result = runner.getIfdsResult()
            val allFacts = result.facts

            val used = hashMapOf<JIRInst, Boolean>()
            for ((inst, facts) in allFacts) {
                for (fact in facts) {
                    if (fact is UnusedVariable) {
                        used.putIfAbsent(fact.initStatement, false)
                        if (fact.variable.isUsedAt(inst)) {
                            used[fact.initStatement] = true
                        }
                    }

                }
            }
            used.filterValues { !it }.keys.map {
                UnusedVariableVulnerability(
                    message = "Assigned value is unused",
                    sink = Vertex(it, UnusedVariableZeroFact)
                )
            }
        }

        if (logger.isDebugEnabled) {
            logger.debug { "Total found ${foundVulnerabilities.size} vulnerabilities" }
            for (vulnerability in foundVulnerabilities) {
                logger.debug { "$vulnerability in ${vulnerability.method}" }
            }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }
        logger.info {
            "Total propagated ${
                runnerForUnit.values.sumOf { it.getPathEdges().size }
            } path edges"
        }
        logger.info {
            "Analysis done in %.1f s".format(
                timeStart.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }
        foundVulnerabilities
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is NewSummaryEdge -> {
                summaryEdgesStorage.add(UnusedVariableSummaryEdge(event.edge))
            }
        }
    }

    override fun handleControlEvent(event: ControlEvent) {
        when (event) {
            is QueueEmptinessChanged -> {
                queueIsEmpty[event.runner.unit] = event.isEmpty
                if (event.isEmpty) {
                    if (runnerForUnit.keys.all { queueIsEmpty[it] == true }) {
                        logger.debug { "All runners are empty" }
                        stopRendezvous.trySend(Unit).getOrNull()
                    }
                }
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: JIRMethod,
        scope: CoroutineScope,
        handler: (Edge<UnusedVariableDomainFact>) -> Unit,
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .onEach { handler(it.edge) }
            .launchIn(scope)
    }
}

fun runUnusedVariableAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver,
    startMethods: List<JIRMethod>,
): List<UnusedVariableVulnerability> {
    val manager = UnusedVariableManager(graph, unitResolver)
    return manager.analyze(startMethods)
}
