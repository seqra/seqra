package org.opentaint.ir.analysis.engine

import mu.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.opentaint.ir.analysis.library.analyzers.NpeTaintNode
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * This manager launches and manages [IfdsUnitRunner]s for all units, reachable from [startMethods].
 * It also merges [TraceGraph]s from different units giving a complete [TraceGraph] for each vulnerability.
 * See [runAnalysis] for more info.
 */
class MainIfdsUnitManager(
    private val graph: JIRApplicationGraph,
    private val unitResolver: UnitResolver,
    private val ifdsUnitRunnerFactory: IfdsUnitRunnerFactory,
    private val startMethods: List<JIRMethod>,
    private val timeoutMillis: Long,
) : IfdsUnitManager {

    private val foundMethods: MutableMap<UnitType, MutableSet<JIRMethod>> = mutableMapOf()
    private val crossUnitCallers: MutableMap<JIRMethod, MutableSet<CrossUnitCallFact>> = mutableMapOf()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdgeFact>()
    private val tracesStorage = SummaryStorageImpl<TraceGraphFact>()
    private val crossUnitCallsStorage = SummaryStorageImpl<CrossUnitCallFact>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<VulnerabilityLocation>()

    private val aliveRunners: MutableMap<UnitType, IfdsUnitRunner> = ConcurrentHashMap()
    private val queueEmptiness: MutableMap<UnitType, Boolean> = mutableMapOf()
    private val dependencies: MutableMap<UnitType, MutableSet<UnitType>> = mutableMapOf()
    private val dependenciesRev: MutableMap<UnitType, MutableSet<UnitType>> = mutableMapOf()

    // private val deleteJobs: MutableMap<UnitType, Job> = ConcurrentHashMap()
    // private val pathEdgesStorage: MutableMap<UnitType, Collection<IfdsEdge>> = ConcurrentHashMap()

    private fun getAllCallees(method: JIRMethod): Set<JIRMethod> {
        val result = mutableSetOf<JIRMethod>()
        for (inst in method.flowGraph().instructions) {
            graph.callees(inst).forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun addStart(method: JIRMethod) {
        val unit = unitResolver.resolve(method)
        // TODO: remove this unnecessary if-condition (superseded by '.add()' below):
        if (method in foundMethods[unit].orEmpty()) {
            return
        }

        foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
        // val dependencies = getAllCallees(method)
        // dependencies.forEach { addStart(it) }
    }

    private val IfdsVertex.traceGraph: TraceGraph
        get() = tracesStorage
            .getCurrentFacts(method)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    /**
     * Launches [IfdsUnitRunner] for each observed unit, handles respective jobs,
     * and gathers results into list of vulnerabilities, restoring full traces
     */
    fun analyze(): List<VulnerabilityInstance> = runBlocking(Dispatchers.Default) {
        withTimeoutOrNull(timeoutMillis) {
            logger.info { "Searching for units to analyze..." }
            startMethods.forEach {
                ensureActive()
                addStart(it)
            }

            val allUnits = foundMethods.keys.toList()
            logger.info { "Starting analysis. Number of found units: ${allUnits.size}" }
            for ((i, entry) in foundMethods.entries.withIndex()) {
                val (unit, methods) = entry
                logger.info { "Unit [${i + 1}/${foundMethods.size}] :: $unit :: total ${methods.size} methods" }
                for ((j, method) in methods.withIndex()) {
                    logger.info { "- Method [${j + 1}/${methods.size}] $method" }
                }
            }

            val progressLoggerJob = launch {
                while (isActive) {
                    delay(1000)
                    val totalCount = allUnits.size
                    val aliveCount = aliveRunners.size

                    logger.info {
                        "Current progress: ${totalCount - aliveCount} / $totalCount units completed"
                    }
                }
            }

            launch {
                dispatchDependencies()
            }

            // TODO: do smth smarter here
            val allJobs = allUnits.map { unit ->
                val runner = ifdsUnitRunnerFactory.newRunner(
                    graph,
                    this@MainIfdsUnitManager,
                    unitResolver,
                    unit,
                    foundMethods[unit]!!.toList()
                )
                aliveRunners[unit] = runner
                // pathEdgesStorage[unit] = when (runner) {
                //     is BaseIfdsUnitRunner -> runner.pathEdges
                //     is BidiIfdsUnitRunnerFactory.BidiIfdsUnitRunner -> (runner.forwardRunner as BaseIfdsUnitRunner).pathEdges
                //     else -> error("Bad runner: $runner")
                // }
                runner.launchIn(this)
            }

            allJobs.joinAll()
            eventChannel.close()
            progressLoggerJob.cancel()
        }

        logger.info { "All jobs completed, gathering results..." }

        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods.flatMap { method ->
            vulnerabilitiesStorage.getCurrentFacts(method)
        }

        logger.debug { "Total found ${foundVulnerabilities.size} sinks" }
        for (vulnerability in foundVulnerabilities) {
            logger.debug { "$vulnerability in ${vulnerability.method}" }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }

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
                                    vulnerability.sink.domainFact
                                ).joinToString(sep) { "\"$it\"" } + "\n")
                        }
                    } else if (vulnerability.sink.domainFact is NpeTaintNode) {
                        val cwe = 476
                        writer.write(
                            listOf(
                                m.enclosingClass.simpleName,
                                cwe,
                                m.name,
                                vulnerability.sink.statement,
                                vulnerability.sink.domainFact
                            ).joinToString(sep) { "\"$it\"" } + "\n")
                    } else {
                        logger.warn { "Bad vulnerability without rule: $vulnerability" }
                    }
                }
            }
        }

        foundMethods.values.flatten().forEach { method ->
            for (crossUnitCall in crossUnitCallsStorage.getCurrentFacts(method)) {
                val calledMethod = graph.methodOf(crossUnitCall.calleeVertex.statement)
                crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(crossUnitCall)
            }
        }

        logger.info { "Restoring traces..." }

        foundVulnerabilities
            .map { VulnerabilityInstance(it, extendTraceGraph(it.sink.traceGraph)) }
            .filter {
                it.traceGraph.sources.any { source ->
                    graph.methodOf(source.statement) in startMethods || source.domainFact == ZEROFact
                }
            }
    }

    private val TraceGraph.methods: List<JIRMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                listOf(graph.methodOf(sink.statement))).distinct()
        }

    /**
     * Given a [traceGraph], searches for other traceGraphs (from different units)
     * and merges them into given if they extend any path leading to sink.
     *
     * This method allows to restore traces that pass through several units.
     */
    private fun extendTraceGraph(traceGraph: TraceGraph): TraceGraph {
        var result = traceGraph
        val methodQueue: MutableSet<JIRMethod> = traceGraph.methods.toMutableSet()
        val addedMethods: MutableSet<JIRMethod> = methodQueue.toMutableSet()
        while (methodQueue.isNotEmpty()) {
            val method = methodQueue.first()
            methodQueue.remove(method)
            for (callFact in crossUnitCallers[method].orEmpty()) {
                // TODO: merge calleeVertices here
                val sFacts = setOf(callFact.calleeVertex)
                val upGraph = callFact.callerVertex.traceGraph
                val newValue = result.mergeWithUpGraph(upGraph, sFacts)
                if (result != newValue) {
                    result = newValue
                    for (nMethod in upGraph.methods) {
                        if (nMethod !in addedMethods) {
                            addedMethods.add(nMethod)
                            methodQueue.add(nMethod)
                        }
                    }
                }
            }
        }
        return result
    }

    override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner) {
        when (event) {
            is EdgeForOtherRunnerQuery -> {
                check(event.edge.from == event.edge.to) { "Edge for other runner must be a loop-edge" }
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = aliveRunners[unit] ?: return
                if (otherRunner.job?.isActive == true) {
                    otherRunner.submitNewEdge(event.edge)
                }
            }

            is NewSummaryFact -> {
                when (val fact = event.fact) {
                    is CrossUnitCallFact -> crossUnitCallsStorage.add(fact)
                    is SummaryEdgeFact -> summaryEdgesStorage.add(fact)
                    is TraceGraphFact -> tracesStorage.add(fact)
                    is VulnerabilityLocation -> vulnerabilitiesStorage.add(fact)
                    else -> error("Unexpected $fact")
                }
            }

            is QueueEmptinessChanged -> {
                eventChannel.send(Pair(event, runner))
            }

            is SubscriptionForSummaryEdges -> {
                eventChannel.send(Pair(event, runner))
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .collect(event.collector)
            }
        }
    }

    // Used to linearize all events that change dependencies or queue emptiness of runners
    private val eventChannel: Channel<Pair<IfdsUnitRunnerEvent, IfdsUnitRunner>> =
        Channel(capacity = Int.MAX_VALUE)

    // TODO: replace async dispatcher with a synchronous one
    private suspend fun dispatchDependencies() = coroutineScope {
        eventChannel.consumeEach { (event, runner) ->
            when (event) {
                is SubscriptionForSummaryEdges -> {
                    dependencies.getOrPut(runner.unit) { mutableSetOf() }
                        .add(unitResolver.resolve(event.method))
                    dependenciesRev.getOrPut(unitResolver.resolve(event.method)) { mutableSetOf() }
                        .add(runner.unit)
                }

                is QueueEmptinessChanged -> {
                    if (runner.unit !in aliveRunners) {
                        return@consumeEach
                    }
                    queueEmptiness[runner.unit] = event.isEmpty
                    // deleteJobs[runner.unit]?.run {
                    //     logger.debug { "Cancelling the stopping of the runner for ${runner.unit}" }
                    //     cancel()
                    // }
                    if (event.isEmpty) {
                        // deleteJobs[runner.unit] = launch {
                        //     logger.debug { "Going to stop the runner for ${runner.unit} in 5 seconds..." }
                        //     delay(5.seconds)
                        logger.info { "Stopping the runner for ${runner.unit}..." }
                        val toDelete = mutableListOf(runner.unit)
                        while (toDelete.isNotEmpty()) {
                            val current = toDelete.removeLast()
                            if (current in aliveRunners &&
                                dependencies[runner.unit].orEmpty().all { queueEmptiness[it] != false }
                            ) {
                                if (aliveRunners[current] == null) continue
                                aliveRunners[current]!!.job?.cancel() ?: error("Runner's job is not instantiated")
                                aliveRunners.remove(current)
                                for (next in dependenciesRev[current].orEmpty()) {
                                    if (queueEmptiness[next] == true) {
                                        toDelete.add(next)
                                    }
                                }
                            }
                        }
                        // }
                    }
                }

                else -> error("Unexpected event for dependencies dispatcher")
            }
        }
    }
}
