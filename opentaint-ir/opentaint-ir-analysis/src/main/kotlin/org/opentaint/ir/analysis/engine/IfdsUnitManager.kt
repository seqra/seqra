package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.opentaint.ir.analysis.VulnerabilityInstance
import org.opentaint.ir.analysis.logger
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import java.util.concurrent.ConcurrentHashMap

class IfdsUnitManager<UnitType>(
    private val graph: JIRApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
    private val ifdsUnitRunner: IfdsUnitRunner,
    private val timeoutMillis: Long,
    private val initMethods: List<JIRMethod>
) {

    private val unitsQueue: MutableSet<UnitType> = mutableSetOf()
    private val foundMethods: MutableMap<UnitType, MutableSet<JIRMethod>> = mutableMapOf()
    private val dependsOn: MutableMap<UnitType, Int> = mutableMapOf()
    private val crossUnitCallers: MutableMap<JIRMethod, MutableSet<CrossUnitCallFact>> = mutableMapOf()
    private val summary: Summary = SummaryImpl()

    init {
        initMethods.forEach { addStart(it) }
    }

    private val IfdsVertex.traceGraph: TraceGraph
        get() = summary
            .getCurrentFactsFiltered<TraceGraphFact>(method, this)
            .map { it.graph }
            .singleOrNull { it.sink == this }
            ?: TraceGraph.bySink(this)

    fun analyze(): List<VulnerabilityInstance> = runBlocking(Dispatchers.Default) {
        val unitJobs: MutableMap<UnitType, Job> = ConcurrentHashMap()
        val unitsCount = unitsQueue.size

        logger.info { "Starting analysis. Number of units to analyze: $unitsCount" }

        val progressLoggerJob = launch {
            while (isActive) {
                delay(1000)
                logger.info {
                    "Current progress: ${unitJobs.values.count { it.isCompleted }} / $unitsCount units completed"
                }
            }
        }

        // TODO: do smth smarter here
        unitsQueue.toList().forEach { unit ->
            unitJobs[unit] = launch {
                withTimeout(timeoutMillis) {
                    ifdsUnitRunner.run(graph, summary, unitResolver, unit, foundMethods[unit]!!.toList())
                }
            }
        }

        unitJobs.values.joinAll()
        progressLoggerJob.cancelAndJoin()
        logger.info { "All jobs completed, gathering results..." }

        val foundVulnerabilities = foundMethods.values.flatten().flatMap { method ->
            summary.getCurrentFactsFiltered<VulnerabilityLocation>(method, null)
        }

        foundMethods.values.flatten().forEach { method ->
            for (crossUnitCall in summary.getCurrentFactsFiltered<CrossUnitCallFact>(method, null)) {
                val calledMethod = graph.methodOf(crossUnitCall.calleeVertex.statement)
                crossUnitCallers.getOrPut(calledMethod) { mutableSetOf() }.add(crossUnitCall)
            }
        }

        logger.info { "Restoring traces..." }

        foundVulnerabilities
            .map { VulnerabilityInstance(it.vulnerabilityType, extendTraceGraph(it.sink.traceGraph)) }
            .filter {
                it.traceGraph.sources.any { source ->
                    graph.methodOf(source.statement) in initMethods || source.domainFact == ZEROFact
                }
            }
    }

    private val TraceGraph.methods: List<JIRMethod>
        get() {
            return (edges.keys.map { graph.methodOf(it.statement) } +
                    listOf(graph.methodOf(sink.statement))).distinct()
        }

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

    private fun getAllDependencies(method: JIRMethod): Set<JIRMethod> {
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
        if (method in foundMethods[unit].orEmpty()) {
            return
        }

        unitsQueue.add(unit)
        foundMethods.getOrPut(unit) { mutableSetOf() }.add(method)
        val dependencies = getAllDependencies(method)
        dependencies.forEach { addStart(it) }
        dependsOn[unit] = dependsOn.getOrDefault(unit, 0) + dependencies.size
    }
}

fun runAnalysis(
    graph: JIRApplicationGraph,
    unitResolver: UnitResolver<*>,
    ifdsUnitRunner: IfdsUnitRunner,
    methods: List<JIRMethod>,
    timeoutMillis: Long = Long.MAX_VALUE
): List<VulnerabilityInstance> {
    return IfdsUnitManager(graph, unitResolver, ifdsUnitRunner, timeoutMillis, methods).analyze()
}