package org.opentaint.api.checkers

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.engine.PackageUnitResolver
import org.opentaint.ir.analysis.engine.UnitResolver
import org.opentaint.ir.analysis.graph.defaultBannedPackagePrefixes
import org.opentaint.ir.analysis.graph.newApplicationGraphForAnalysis
import org.opentaint.ir.analysis.ifds2.TraceGraph
import org.opentaint.ir.analysis.ifds2.Vertex
import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.TaintManager
import org.opentaint.ir.analysis.ifds2.taint.Vulnerability
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.isSubClassOf
import org.opentaint.ir.approximation.JIREnrichedVirtualMethod
import org.opentaint.ir.taint.configuration.ConstantTrue
import org.opentaint.ir.taint.configuration.TaintMethodSink
import org.opentaint.ir.taint.configuration.TaintPassThrough
import org.opentaint.ir.taint.configuration.taintConfigurationFeature
import org.opentaint.PathSelectionStrategy
import org.opentaint.PathSelectorFairnessStrategy
import org.opentaint.SolverType
import org.opentaint.StateCollectionStrategy
import org.opentaint.UMachineOptions
import org.opentaint.api.targets.TaintAnalysis
import org.opentaint.api.targets.TaintConfigurationFeatureProvider
import org.opentaint.api.targets.TaintConfigurationProvider
import org.opentaint.logger
import org.opentaint.machine.JIRMachine
import org.opentaint.machine.JIRMachineOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val projectLocations: Set<RegisteredLocation>,
    val dependenciesLocations: Set<RegisteredLocation>,
    val opentaintTimeout: Duration,
    val analysisCwe: Set<Int>?,
    val analysisUnit: UnitResolver = PackageUnitResolver
) {

    private val ifdsAnalysisGraph by lazy {
        runBlocking {
            // todo: ban dependencies?
            cp.newApplicationGraphForAnalysis(bannedPackagePrefixes = defaultBannedPackagePrefixes)
        }
    }

    private val taintConfig by lazy { taintConfig() }

    data class IfdsVulnerablity(
        val vulnerability: Vulnerability,
        val traceGraph: TraceGraph<TaintFact>,
    )

    private lateinit var ifdsTraces: List<IfdsVulnerablity>

    fun analyzeWithIfds(entrypoints: List<JIRMethod>): List<ReachedSink> {
        ifdsTraces = analyzeTaintWithIfdsEngine(entrypoints)
        return ifdsReachedSinks(ifdsTraces)
    }

    fun analyzeWithOpentaint(entrypoints: List<JIRMethod>): List<ReachedSink> {
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(
                PathSelectionStrategy.DFS,
            ),
            pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.CONSTANT_TIME,
            stateCollectionStrategy = StateCollectionStrategy.NONE,
            timeout = opentaintTimeout,
            useSoftConstraints = false,
            solverType = SolverType.YICES,
            loopIterativeDeepening = false,
            loopIterationLimit = 1,
            useMerging = false,
            exceptionsPropagation = false,
            dropExceptionalStates = true,
        )

        val jirOptions = JIRMachineOptions(
            mockNonConcreteVirtualCalls = true,
            useStaticAddressForConstantString = false,
            forkOnImplicitExceptions = false,
            useConcreteAddressForModelCompletion = true,
            skipIrrelevantClassInitializers = true,
            mockComplexMethods = true,
            projectLocations = projectLocations,
            dependenciesLocations = dependenciesLocations
        )

        val taintAnalysis = TaintAnalysis(taintConfig)

        JIRMachine(cp, options, jirOptions, interpreterObserver = taintAnalysis).use { machine ->
            machine.analyze(entrypoints)
        }

        return taintAnalysis.reachedSinks()
    }

    fun filterIfdsTracesWithOpentaint(entrypoints: List<JIRMethod>): List<ReachedSink> {
        check(this::ifdsTraces.isInitialized) { "No ifds traces" }

        val targets = buildTargetsFromIfdsTraces(ifdsTraces, entrypoints.toSet())
        val entrypointsWithTargets = targets.keys.toList()

        val options = UMachineOptions(
            pathSelectionStrategies = listOf(
                PathSelectionStrategy.TARGETED,
            ),
            pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.CONSTANT_TIME,
            stateCollectionStrategy = StateCollectionStrategy.NONE,
            timeout = opentaintTimeout,
            solverType = SolverType.YICES,
            useSoftConstraints = false,
            stopOnTargetsReached = true,
            loopIterativeDeepening = false,
            loopIterationLimit = 2,
            targetSearchDepth = 1u,
            exceptionsPropagation = false,
            dropExceptionalStates = true,
        )

        val jirOptions = JIRMachineOptions(
            mockNonConcreteVirtualCalls = true,
            useStaticAddressForConstantString = false,
            forkOnImplicitExceptions = false,
            useConcreteAddressForModelCompletion = true,
            forceRelevantClassInitializers = true,
            skipIrrelevantClassInitializers = true,
            mockComplexMethods = true,
            projectLocations = projectLocations,
            dependenciesLocations = dependenciesLocations
        )

        val taintAnalysis = TaintAnalysis(taintConfig)
        targets.values.forEach { tgts -> tgts.forEach { taintAnalysis.addTarget(it) } }

        logger.debug { targets.printActiveTargets() }

        JIRMachine(cp, options, jirOptions, taintAnalysis).use { machine ->
            machine.analyze(entrypointsWithTargets) { ep -> targets[ep] ?: emptyList() }
        }

        logger.debug { targets.printActiveTargets() }

        return taintAnalysis.reachedSinks()
    }

    private fun analyzeTaintWithIfdsEngine(
        entrypoints: List<JIRMethod>,
    ): List<IfdsVulnerablity> {
        val ifdsEngine = TaintManager(ifdsAnalysisGraph, analysisUnit, timeout = 10.minutes)
        val allVulnerabilities = ifdsEngine.analyze(entrypoints)

        // todo: IFDS cwe config
        val vulnerabilities = analysisCwe?.let { cwe ->
            allVulnerabilities.filter {
                val rule = it.first.rule ?: error("No rule")
                rule.cwe.intersect(cwe).isNotEmpty()
            }
        } ?: allVulnerabilities

        return vulnerabilities.map { IfdsVulnerablity(it.first, it.second) }
    }

    private fun buildTargetsFromIfdsTraces(
        instances: List<IfdsVulnerablity>,
        possibleEp: Set<JIRMethod>
    ): Map<JIRMethod, List<TaintAnalysis.TaintTarget>> {
        val result = hashMapOf<JIRMethod, MutableList<TaintAnalysis.TaintTarget>>()
        for (instance in instances) {
            val entrypoints = instance.entrypoints()
            val targets = resolveIfdsTraceTargets(instance)

            for (entrypoint in entrypoints) {
                check(entrypoint in possibleEp) { "Unexpected EP: $entrypoint" }
                result.getOrPut(entrypoint) { mutableListOf() } += targets
            }
        }
        return result
    }

    private fun resolveIfdsTraceTargets(instance: IfdsVulnerablity): Set<TaintAnalysis.TaintTarget> {
        val initialTargets = hashSetOf<TaintAnalysis.TaintTarget>()
        val resolvedTargets = hashMapOf<Vertex<TaintFact>, TaintAnalysis.TaintTarget>()

        val vertexPredecessors = hashMapOf<Vertex<TaintFact>, MutableSet<Vertex<TaintFact>>>()
        for ((vertex, successors) in instance.traceGraph.edges) {
            successors.forEach { succ ->
                vertexPredecessors.getOrPut(succ) { hashSetOf() }.add(vertex)
            }
        }

        fun dfs(vertex: Vertex<TaintFact>, prevTarget: TaintAnalysis.TaintTarget, path: MutableSet<Vertex<TaintFact>>) {
            val target = resolveVertexTarget(vertex, resolvedTargets)

            // todo: remove hack with locations
            val nextTarget = if (target.location == prevTarget.location) {
                prevTarget
            } else {
                target.addChild(prevTarget)
                target
            }

            val predecessors = vertexPredecessors[vertex]
            if (predecessors.isNullOrEmpty()) {
                initialTargets.add(nextTarget)
                return
            }

            path.add(vertex)
            for (pred in predecessors) {
                if (pred !in path) {
                    dfs(pred, nextTarget, path)
                }
            }
            path.remove(vertex)
        }

        val sinkVertex = instance.traceGraph.sink
        val sinkLocation = resolveLocationInst(sinkVertex)
        val sinkTarget = TaintAnalysis.TaintMethodSinkTarget(
            sinkLocation,
            ConstantTrue,
            instance.vulnerability.rule ?: error("No rule")
        )
        resolvedTargets[sinkVertex] = sinkTarget

        val dfsPath = hashSetOf(sinkVertex)
        vertexPredecessors[sinkVertex].orEmpty().forEach {
            if (it !in dfsPath) {
                dfs(it, sinkTarget, dfsPath)
            }
        }

        return initialTargets
    }

    private fun resolveVertexTarget(
        vertex: Vertex<TaintFact>,
        cache: MutableMap<Vertex<TaintFact>, TaintAnalysis.TaintTarget>,
    ): TaintAnalysis.TaintTarget = cache.getOrPut(vertex) {
        TaintAnalysis.TaintIntermediateTarget(resolveLocationInst(vertex))
    }

    private fun IfdsVulnerablity.entrypoints(): Set<JIRMethod> {
        val entrypoints = traceGraph.entryPoints.mapTo(hashSetOf()) { it.method }

        if (entrypoints.size > 1) {
            logger.warn { "Possibly wrong entrypoints: $entrypoints" }
        }

        return entrypoints
    }

    private fun resolveLocationInst(vertex: Vertex<TaintFact>): JIRInst {
        if (vertex.statement in vertex.method.instList) {
            return vertex.statement
        }

        return vertex.method.flowGraph().entry
    }

    class FixedConfig(val base: TaintConfigurationProvider) : TaintConfigurationProvider by base {
        override fun passThrough(method: JIRMethod): List<TaintPassThrough> {
            val cp = method.enclosingClass.classpath

            val collection = cp.findClass("java.util.Collection")
            if (method.enclosingClass.isSubClassOf(collection) && method.name == "add" && method is JIREnrichedVirtualMethod) {
                return emptyList()
            }

            val map = cp.findClass("java.util.Map")
            if (method.enclosingClass.isSubClassOf(map) && method.name == "put" && method is JIREnrichedVirtualMethod) {
                return emptyList()
            }

            return base.passThrough(method)
        }
    }

    private fun taintConfig(): TaintConfigurationProvider {
        val config = cp.taintConfigurationFeature()
        return FixedConfig(TaintConfigurationFeatureProvider(config))
    }

    data class ReachedSink(
        val entrypoint: JIRMethod,
        val sinkLocation: JIRInst,
        val sink: TaintMethodSink
    )

    private fun ifdsReachedSinks(instances: List<IfdsVulnerablity>): List<ReachedSink> =
        instances.flatMap {
            it.entrypoints().map { ep ->
                ReachedSink(
                    entrypoint = ep,
                    sinkLocation = it.vulnerability.sink.statement,
                    sink = it.vulnerability.rule ?: error("No rule")
                )
            }
        }

    private fun TaintAnalysis.reachedSinks(): List<ReachedSink> =
        reachedSinks.map { ReachedSink(it.first.entrypoint, it.first.currentStatement, it.second) }


    private fun Map<JIRMethod, List<TaintAnalysis.TaintTarget>>.printActiveTargets(): String = buildString {
        values.flatten().filterNot { it.isRemoved }.forEach {
            appendLine("-".repeat(20))
            appendLine(it)
        }
    }
}
