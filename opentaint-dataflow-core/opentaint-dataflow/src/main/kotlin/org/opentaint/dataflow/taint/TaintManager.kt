/*
 *  Copyright 2022 Opentaint contributors (opentaint.dev)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opentaint.dataflow.taint

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
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.graph.ApplicationGraph
import org.opentaint.dataflow.graph.reversed
import org.opentaint.dataflow.ifds.ControlEvent
import org.opentaint.dataflow.ifds.IfdsResult
import org.opentaint.dataflow.ifds.Manager
import org.opentaint.dataflow.ifds.QueueEmptinessChanged
import org.opentaint.dataflow.ifds.SummaryStorageImpl
import org.opentaint.dataflow.ifds.TraceGraph
import org.opentaint.dataflow.ifds.UniRunner
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.ifds.Vertex
import org.opentaint.dataflow.util.Traits
import org.opentaint.dataflow.util.getPathEdges
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private val logger = mu.KotlinLogging.logger {}

open class TaintManager<Method, Statement>(
    private val traits: Traits<Method, Statement>,
    protected val graph: ApplicationGraph<Method, Statement>,
    protected val unitResolver: UnitResolver<Method>,
    private val useBidiRunner: Boolean = false,
    private val getConfigForMethod: (Method) -> List<TaintConfigurationItem>?,
) : Manager<TaintDomainFact, TaintEvent<Statement>, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    protected val methodsForUnit: MutableMap<UnitType, MutableSet<Method>> = hashMapOf()
    val runnerForUnit: MutableMap<UnitType, TaintRunner<Method, Statement>> = hashMapOf()
    private val queueIsEmpty = ConcurrentHashMap<UnitType, Boolean>()

    private val summaryEdgesStorage = SummaryStorageImpl<TaintSummaryEdge<Statement>>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<TaintVulnerability<Statement>>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    protected open fun newRunner(
        unit: UnitType,
    ): TaintRunner<Method, Statement> {
        // check(unit !in runnerForUnit) { "Runner for $unit already exists" }
        if (unit in runnerForUnit) {
            return runnerForUnit[unit]!!
        }

        logger.debug { "Creating a new runner for $unit" }
        val runner = if (useBidiRunner) {
            TaintBidiRunner(
                manager = this@TaintManager,
                graph = graph,
                unitResolver = unitResolver,
                unit = unit,
                { manager ->
                    val analyzer = TaintAnalyzer(traits, graph, getConfigForMethod)
                    UniRunner(
                        traits = traits,
                        manager = manager,
                        graph = graph,
                        analyzer = analyzer,
                        unitResolver = unitResolver,
                        unit = unit,
                        zeroFact = TaintZeroFact
                    )
                },
                { manager ->
                    val analyzer = BackwardTaintAnalyzer(traits, graph)
                    UniRunner(
                        traits = traits,
                        manager = manager,
                        graph = graph.reversed,
                        analyzer = analyzer,
                        unitResolver = unitResolver,
                        unit = unit,
                        zeroFact = TaintZeroFact
                    )
                }
            )
        } else {
            val analyzer = TaintAnalyzer(traits, graph, getConfigForMethod)
            UniRunner(
                traits = traits,
                manager = this@TaintManager,
                graph = graph,
                analyzer = analyzer,
                unitResolver = unitResolver,
                unit = unit,
                zeroFact = TaintZeroFact
            )
        }

        runnerForUnit[unit] = runner
        return runner
    }

    private fun getAllCallees(method: Method): Set<Method> {
        val result: MutableSet<Method> = hashSetOf()
        for (inst in method.flowGraph().instructions) {
            @Suppress("UNCHECKED_CAST")
            result += graph.callees(inst as Statement)
        }
        return result
    }

    protected open fun addStart(method: Method) {
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

    @JvmName("analyze") // needed for Java interop because of inline class (Duration)
    fun analyze(
        startMethods: List<Method>,
        timeout: Duration = 3600.seconds,
    ): List<TaintVulnerability<Statement>> = runBlocking(Dispatchers.Default) {
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
        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods
            .flatMap { method ->
                vulnerabilitiesStorage.getCurrentFacts(method)
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

    override fun handleEvent(event: TaintEvent<Statement>) {
        when (event) {
            is NewSummaryEdge -> {
                summaryEdgesStorage.add(TaintSummaryEdge(event.edge))
            }

            is NewVulnerability -> {
                vulnerabilitiesStorage.add(event.vulnerability)
            }

            is EdgeForOtherRunner -> {
                val method = graph.methodOf(event.edge.from.statement)
                val unit = unitResolver.resolve(method)
                val otherRunner = runnerForUnit[unit] ?: run {
                    // error("No runner for $unit")
                    logger.trace { "Ignoring event=$event for non-existing runner for unit=$unit" }
                    return
                }
                otherRunner.submitNewEdge(event.edge, event.reason)
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
        method: Method,
        scope: CoroutineScope,
        handler: (TaintEdge<Statement>) -> Unit,
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .onEach { handler(it.edge) }
            .launchIn(scope)
    }

    fun vulnerabilityTraceGraph(
        vulnerability: TaintVulnerability<Statement>,
    ): TraceGraph<TaintDomainFact, Statement> {
        @Suppress("UNCHECKED_CAST")
        val result = getIfdsResultForMethod(vulnerability.method as Method)
        val initialGraph = result.buildTraceGraph(vulnerability.sink)
        val resultGraph = initialGraph.copy(unresolvedCrossUnitCalls = emptyMap())

        val resolvedCrossUnitEdges =
            hashSetOf<Pair<Vertex<TaintDomainFact, Statement>, Vertex<TaintDomainFact, Statement>>>()
        val unresolvedCrossUnitCalls = initialGraph.unresolvedCrossUnitCalls.entries.toMutableList()
        while (unresolvedCrossUnitCalls.isNotEmpty()) {
            val (caller, callees) = unresolvedCrossUnitCalls.removeLast()

            val unresolvedCallees = hashSetOf<Vertex<TaintDomainFact, Statement>>()
            for (callee in callees) {
                if (resolvedCrossUnitEdges.add(caller to callee)) {
                    unresolvedCallees.add(callee)
                }
            }

            if (unresolvedCallees.isEmpty()) continue

            @Suppress("UNCHECKED_CAST")
            val callerResult = getIfdsResultForMethod(caller.method as Method)
            val callerGraph = callerResult.buildTraceGraph(caller)
            resultGraph.mergeWithUpGraph(callerGraph, unresolvedCallees)
            unresolvedCrossUnitCalls += callerGraph.unresolvedCrossUnitCalls.entries
        }

        return resultGraph
    }

    private fun getIfdsResultForMethod(method: Method): IfdsResult<TaintDomainFact, Statement> {
        val unit = unitResolver.resolve(method)
        val runner = runnerForUnit[unit] ?: error("No runner for $unit")
        return runner.getIfdsResult()
    }
}
