package org.opentaint.api.checkers

import kotlinx.coroutines.runBlocking
import org.opentaint.ir.analysis.graph.JIRApplicationGraphImpl
import org.opentaint.ir.analysis.graph.defaultBannedPackagePrefixes
import org.opentaint.ir.analysis.ifds.PackageUnitResolver
import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.ifds.UnitResolver
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.taint.TaintDomainFact
import org.opentaint.ir.analysis.taint.TaintVulnerability
import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.RegisteredLocation
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.findClass
import org.opentaint.ir.api.ext.isSubClassOf
import org.opentaint.ir.approximation.JIREnrichedVirtualMethod
import org.opentaint.ir.impl.features.hierarchyExt
import org.opentaint.ir.impl.features.usagesExt
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
        val usages = runBlocking { cp.usagesExt() }
        val hierarchy = runBlocking { cp.hierarchyExt() }

        val mainGraph = JIRApplicationGraphImpl(cp, usages)
        JIRSimplifiedApplicationGraph(
            mainGraph, hierarchy,
            bannedLocations = dependenciesLocations,
            bannedPackagePrefixes = defaultBannedPackagePrefixes
        )
    }

    private val taintConfig by lazy { taintConfig() }

    data class IfdsVulnerablity(
        val vulnerability: TaintVulnerability,
        val entryPoints: Set<JIRMethod>,
        val traceGraph: TraceGraph<TaintDomainFact>,
    )

    private lateinit var ifdsTraces: List<IfdsVulnerablity>

    fun analyzeWithIfds(entryPoints: List<JIRMethod>): List<ReachedSink> {
        ifdsTraces = analyzeTaintWithIfdsEngine(entryPoints)
        return ifdsReachedSinks(ifdsTraces)
    }

    fun analyzeWithOpentaint(entryPoints: List<JIRMethod>): List<ReachedSink> {
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
            machine.analyze(entryPoints)
        }

        return taintAnalysis.reachedSinks()
    }

    fun filterIfdsTracesWithOpentaint(entryPoints: List<JIRMethod>): List<ReachedSink> {
        check(this::ifdsTraces.isInitialized) { "No ifds traces" }

        val targets = buildTargetsFromIfdsTraces(ifdsTraces, entryPoints.toSet())
        val entryPointsWithTargets = targets.keys.toList()

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
            machine.analyze(entryPointsWithTargets) { ep -> targets[ep] ?: emptyList() }
        }

        logger.debug { targets.printActiveTargets() }

        return taintAnalysis.reachedSinks()
    }

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<IfdsVulnerablity> {
        val ifdsEngine = JIRIfdsTaintAnalyzer(ifdsAnalysisGraph, analysisUnit)
        val allVulnerabilities = ifdsEngine.analyze(entryPoints, timeout = 10.minutes)

        // todo: IFDS cwe config
        val vulnerabilities = analysisCwe?.let { cwe ->
            allVulnerabilities.filter {
                val rule = it.rule ?: error("No rule")
                rule.cwe.intersect(cwe).isNotEmpty()
            }
        } ?: allVulnerabilities

        return vulnerabilities.map {
            val traceGraphWithEntryPoints = ifdsEngine.vulnerabilityTraceGraphWithEntryPoints(it)
            IfdsVulnerablity(it, traceGraphWithEntryPoints.entryPoints, traceGraphWithEntryPoints.graph)
        }
    }

    private fun buildTargetsFromIfdsTraces(
        instances: List<IfdsVulnerablity>,
        possibleEp: Set<JIRMethod>
    ): Map<JIRMethod, List<TaintAnalysis.TaintTarget>> {
        val result = hashMapOf<JIRMethod, MutableList<TaintAnalysis.TaintTarget>>()
        for (instance in instances) {
            val entryPoints = instance.entryPoints()
            val targets = resolveIfdsTraceTargets(instance)

            for (entrypoint in entryPoints) {
                check(entrypoint in possibleEp) { "Unexpected EP: $entrypoint" }
                result.getOrPut(entrypoint) { mutableListOf() } += targets
            }
        }
        return result
    }

    private fun resolveIfdsTraceTargets(instance: IfdsVulnerablity): Set<TaintAnalysis.TaintTarget> {
        val initialTargets = hashSetOf<TaintAnalysis.TaintTarget>()
        val resolvedTargets = hashMapOf<Vertex<TaintDomainFact>, TaintAnalysis.TaintTarget>()

        val vertexPredecessors = hashMapOf<Vertex<TaintDomainFact>, MutableSet<Vertex<TaintDomainFact>>>()
        for ((vertex, successors) in instance.traceGraph.edges) {
            successors.forEach { succ ->
                vertexPredecessors.getOrPut(succ) { hashSetOf() }.add(vertex)
            }
        }

        fun dfs(
            vertex: Vertex<TaintDomainFact>,
            prevTarget: TaintAnalysis.TaintTarget,
            path: MutableSet<Vertex<TaintDomainFact>>
        ) {
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
        vertex: Vertex<TaintDomainFact>,
        cache: MutableMap<Vertex<TaintDomainFact>, TaintAnalysis.TaintTarget>,
    ): TaintAnalysis.TaintTarget = cache.getOrPut(vertex) {
        TaintAnalysis.TaintIntermediateTarget(resolveLocationInst(vertex))
    }

    private fun IfdsVulnerablity.entryPoints(): Set<JIRMethod> {
        if (entryPoints.size > 1) {
            logger.warn { "Possibly wrong entry points: $entryPoints" }
        }

        return entryPoints
    }

    private fun resolveLocationInst(vertex: Vertex<TaintDomainFact>): JIRInst {
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
            it.entryPoints().map { ep ->
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
