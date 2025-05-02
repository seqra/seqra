package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.opentaint.ir.analysis.logger
import org.opentaint.ir.analysis.runAnalysis
import org.opentaint.ir.api.core.CoreMethod
import org.opentaint.ir.api.core.analysis.ApplicationGraph
import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * This manager launches and manages [IfdsUnitRunner]s for all units, reachable from [startMethods].
 * It also merges [TraceGraph]s from different units giving a complete [TraceGraph] for each vulnerability.
 * See [runAnalysis] for more info.
 */
class MainIfdsUnitManager<UnitType, Method, Location, Statement> (
    private val graph: ApplicationGraph<Method, Statement>,
    private val unitResolver: UnitResolver<UnitType, Method>,
    private val ifdsUnitRunnerFactory: IfdsUnitRunnerFactory<Method, Location, Statement>,
    private val startMethods: List<Method>,
    private val timeoutMillis: Long
) : IfdsUnitManager<UnitType, Method, Location, Statement>
        where Method : CoreMethod<Statement>,
              Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {

    private val foundMethods: MutableMap<UnitType, MutableSet<Method>> = mutableMapOf()
    private val crossUnitCallers: MutableMap<Method, MutableSet<CrossUnitCallFact<Method, Location, Statement>>> = mutableMapOf()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdgeFact<Method, Location, Statement>, Method>()
    private val tracesStorage = SummaryStorageImpl<TraceGraphFact<Method, Location, Statement>, Method>()
    private val crossUnitCallsStorage = SummaryStorageImpl<CrossUnitCallFact<Method, Location, Statement>, Method>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<VulnerabilityLocation<Method, Location, Statement>, Method>()

    private val aliveRunners: MutableMap<UnitType, IfdsUnitRunner<UnitType, Method, Location, Statement>> = ConcurrentHashMap()
    private val queueEmptiness: MutableMap<UnitType, Boolean> = mutableMapOf()
    private val dependencies: MutableMap<UnitType, MutableSet<UnitType>> = mutableMapOf()
    private val dependenciesRev: MutableMap<UnitType, MutableSet<UnitType>> = mutableMapOf()

    private fun getAllCallees(method: Method): Set<Method> {
        val result = mutableSetOf<Method>()
        for (inst in method.flowGraph().instructions) {
            graph.callees(inst).forEach {
                result.add(it)
            }
        }
        return result
    }

    private fun addStart(method: Method) {
        val unit = unitResolver.resolve(method)
        if (method in foundMethods[unit].orEmpty()) {
            return
        }

        foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
        val dependencies = getAllCallees(method)
        dependencies.forEach { addStart(it) }
    }

    private val IfdsVertex<Method, Location, Statement>.traceGraph: TraceGraph<Method, Location, Statement>
        get() = tracesStorage
            .getCurrentFacts(method)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    /**
     * Launches [IfdsUnitRunner] for each observed unit, handles respective jobs,
     * and gathers results into list of vulnerabilities, restoring full traces
     */
    fun analyze(): List<VulnerabilityInstance<Method, Location, Statement>> =
        runBlocking(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMillis) {
                logger.info { "Searching for units to analyze..." }
                startMethods.forEach {
                    ensureActive()
                    addStart(it)
                }

                val allUnits = foundMethods.keys.toList()
                logger.info { "Starting analysis. Number of found units: ${allUnits.size}" }

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
                    runner.launchIn(this)
                }

                allJobs.joinAll()
                eventChannel.close()
                progressLoggerJob.cancel()
            }

            logger.info { "All jobs completed, gathering results..." }

            val foundVulnerabilities = foundMethods.values.flatten().flatMap { method ->
                vulnerabilitiesStorage.getCurrentFacts(method)
            }

            foundMethods.values.flatten().forEach { method ->
                for (crossUnitCall in crossUnitCallsStorage.getCurrentFacts(method)) {
                    val calledMethod = graph.methodOf(crossUnitCall.calleeVertex.statement)
                    crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(crossUnitCall)
                }
            }

            logger.info { "Restoring traces..." }

            foundVulnerabilities
                .map { VulnerabilityInstance(it.vulnerabilityDescription, extendTraceGraph(it.sink.traceGraph)) }
                .filter {
                    it.traceGraph.sources.any { source ->
                        graph.methodOf(source.statement) in startMethods || source.domainFact == ZEROFact
                    }
                }
        }

    private val TraceGraph<Method, Location, Statement>.methods: List<Method>
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
    private fun extendTraceGraph(traceGraph: TraceGraph<Method, Location, Statement>): TraceGraph<Method, Location, Statement> {
        var result = traceGraph
        val methodQueue: MutableSet<Method> = traceGraph.methods.toMutableSet()
        val addedMethods: MutableSet<Method> = methodQueue.toMutableSet()
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

    override suspend fun handleEvent(
        event: IfdsUnitRunnerEvent,
        runner: IfdsUnitRunner<UnitType, Method, Location, Statement>
    ) {
        when (event) {
            is EdgeForOtherRunnerQuery<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                event as EdgeForOtherRunnerQuery<Method, Location, Statement>
                val otherRunner = aliveRunners[unitResolver.resolve(event.edge.method)] ?: return
                if (otherRunner.job?.isActive == true) {
                    otherRunner.submitNewEdge(event.edge)
                }
            }
            is NewSummaryFact<*> -> {
                @Suppress("UNCHECKED_CAST")
                when (val fact = event.fact) {
                    is CrossUnitCallFact<*, *, *> -> crossUnitCallsStorage.send(fact as CrossUnitCallFact<Method, Location, Statement>)
                    is SummaryEdgeFact<*, *, *> -> summaryEdgesStorage.send(fact as SummaryEdgeFact<Method, Location, Statement>)
                    is TraceGraphFact<*, *, *> -> tracesStorage.send(fact as TraceGraphFact<Method, Location, Statement>)
                    is VulnerabilityLocation<*, *, *> -> vulnerabilitiesStorage.send(fact as VulnerabilityLocation<Method, Location, Statement>)
                }
            }
            is QueueEmptinessChanged -> {
                eventChannel.send(Pair(event, runner))
            }
            is SubscriptionForSummaryEdges<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                event as SubscriptionForSummaryEdges<Method, Location, Statement>
                eventChannel.send(Pair(event, runner))
                summaryEdgesStorage.getFacts(event.method).map {
                    it.edge
                }.collect(event.collector)
            }
        }
    }

    // Used to linearize all events that change dependencies or queue emptiness of runners
    private val eventChannel: Channel<Pair<IfdsUnitRunnerEvent, IfdsUnitRunner<UnitType, Method, Location, Statement>>> =
        Channel(capacity = Int.MAX_VALUE)

    private suspend fun dispatchDependencies() = eventChannel.consumeEach { (event, runner) ->
        when (event) {
            is SubscriptionForSummaryEdges<*, *, *> -> {
                @Suppress("UNCHECKED_CAST")
                event as SubscriptionForSummaryEdges<Method, Location, Statement>
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
                if (event.isEmpty) {
                    val toDelete = mutableListOf(runner.unit)
                    while (toDelete.isNotEmpty()) {
                        val current = toDelete.removeLast()
                        if (current in aliveRunners &&
                            dependencies[runner.unit].orEmpty().all { queueEmptiness[it] != false }
                        ) {
                            aliveRunners[current]!!.job?.cancel() ?: error("Runner's job is not instantiated")
                            aliveRunners.remove(current)
                            for (next in dependenciesRev[current].orEmpty()) {
                                if (queueEmptiness[next] == true) {
                                    toDelete.add(next)
                                }
                            }
                        }
                    }
                }
            }
            else -> error("Unexpected event for dependencies dispatcher")
        }
    }
}