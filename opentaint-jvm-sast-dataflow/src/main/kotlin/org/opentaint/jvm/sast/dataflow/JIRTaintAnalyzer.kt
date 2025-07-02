package org.opentaint.api.checkers

import io.github.detekt.sarif4k.SarifSchema210
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findClass
import org.opentaint.ir.api.jvm.ext.isSubClassOf
import org.opentaint.ir.api.jvm.ext.packageName
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
import org.opentaint.dataflow.ifds.TraceGraph
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.ifds.Vertex
import org.opentaint.dataflow.jvm.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.jvm.graph.JIRApplicationGraphImpl
import org.opentaint.dataflow.jvm.graph.defaultBannedPackagePrefixes
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.PackageUnit
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.dataflow.sarif.VulnerabilityDescription
import org.opentaint.dataflow.sarif.VulnerabilityInstance
import org.opentaint.dataflow.sarif.sarifReportFromVulnerabilities
import org.opentaint.dataflow.taint.TaintDomainFact
import org.opentaint.dataflow.taint.TaintVulnerability
import org.opentaint.machine.JIRMachine
import org.opentaint.machine.JIRMachineOptions
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val projectLocations: Set<RegisteredLocation>,
    val dependenciesLocations: Set<RegisteredLocation>,
    val ifdsTimeout: Duration,
    val opentaintTimeout: Duration,
    val symbolicExecutionEnabled: Boolean,
    val analysisCwe: Set<Int>?,
    val debugSummaryDump: Path? = null,
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(bannedLocations = dependenciesLocations)
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
        val vulnerability: TaintVulnerability<JIRInst>,
        val entryPoints: Set<JIRMethod>,
        val traceGraph: TraceGraph<TaintDomainFact, JIRInst>,
    )

    private val opentaintTargetMapping = IdentityHashMap<TaintAnalysis.TaintMethodSinkTarget, IfdsVulnerablity>()

    private lateinit var ifdsTraces: List<IfdsVulnerablity>
    private lateinit var verifiedIfdsTraces: List<IfdsVulnerablity>

    fun analyzeWithIfds(entryPoints: List<JIRMethod>) {
        ifdsTraces = analyzeTaintWithIfdsEngine(entryPoints)
    }

    fun ifdsReachedSinks(): List<ReachedSink> = ifdsReachedSinks(ifdsTraces)

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

        val taintAnalysis = TaintAnalysis(taintConfig, collectStates = false)

        if (entryPoints.isNotEmpty()) {
            JIRMachine(cp, options, jirOptions, interpreterObserver = taintAnalysis).use { machine ->
                machine.analyze(entryPoints)
            }
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

        val taintAnalysis = TaintAnalysis(taintConfig, collectStates = false)
        targets.values.forEach { tgts -> tgts.forEach { taintAnalysis.addTarget(it) } }

        if (entryPointsWithTargets.isNotEmpty()) {
            JIRMachine(cp, options, jirOptions, taintAnalysis).use { machine ->
                logger.debug { targets.printActiveTargets() }
                try {
                    machine.analyze(entryPointsWithTargets) { ep -> targets[ep] ?: emptyList() }
                } catch (ex: Throwable) {
                    logger.error("Machine error", ex)
                }
                logger.debug { targets.printActiveTargets() }
            }
        }

        verifiedIfdsTraces = taintAnalysis.verifiedTraces()

        return taintAnalysis.reachedSinks()
    }

    fun generateSarifReportFromIfdsTraces(sourceFileResolver: JIRSourceFileResolver): SarifSchema210 =
        generateSarifReportFromTraces(sourceFileResolver, ifdsTraces)

    fun generateSarifReportFromVerifiedIfdsTraces(sourceFileResolver: JIRSourceFileResolver): SarifSchema210 =
        generateSarifReportFromTraces(sourceFileResolver, verifiedIfdsTraces)

    private fun generateSarifReportFromTraces(
        sourceFileResolver: JIRSourceFileResolver,
        traces: List<IfdsVulnerablity>
    ): SarifSchema210 {
        val vulnerabilityInstances = mutableListOf<VulnerabilityInstance<TaintDomainFact, JIRInst>>()
        traces.forEach { vulnerability ->
            val rule = vulnerability.vulnerability.rule ?: return@forEach
            rule.cwe.mapTo(vulnerabilityInstances) { cwe ->
                VulnerabilityInstance(
                    vulnerability.traceGraph,
                    VulnerabilityDescription(ruleId = "CWE-$cwe", message = rule.ruleNote)
                )
            }
        }
        with(JIRTraits) {
            return sarifReportFromVulnerabilities(vulnerabilityInstances, sourceFileResolver = sourceFileResolver)
        }
    }

    private fun createIfdsEngine() = TaintAnalysisUnitRunnerManager(ifdsAnalysisGraph, analysisUnit) { rule ->
        when {
            // todo: remove
            rule is TaintPassThrough -> (rule.method as JIRMethod).enclosingClass.declaration.location !in projectLocations
            rule is TaintMethodSink -> analysisCwe == null || rule.cwe.any { it in analysisCwe }
            else -> true
        }
    }

    private fun analyzeTaintWithIfdsEngine(
        entryPoints: List<JIRMethod>,
    ): List<IfdsVulnerablity> = createIfdsEngine().use { ifdsEngine ->
        runCatching { ifdsEngine.runAnalysis(entryPoints, timeout = ifdsTimeout, cancellationTimeout = 30.seconds) }
            .onFailure { logger.error(it) { "Ifds engine failed" } }

        val allVulnerabilities = ifdsEngine.getOldVulnerabilities()
        val vulnerabilities = analysisCwe?.let { cwe ->
            allVulnerabilities.filter {
                val rule = it.rule ?: error("No rule")
                rule.cwe.intersect(cwe).isNotEmpty()
            }
        } ?: allVulnerabilities

        if (debugSummaryDump != null) {
            logger.debug { "Start summaries dump: $debugSummaryDump" }
            val json = Json { prettyPrint = true }
            debugSummaryDump.outputStream().use { out ->
                ifdsEngine.dumpSummariesJson(json, out)
            }
            logger.debug { "Finish summaries dump" }
        }

//        if (!symbolicExecutionEnabled) {
            // todo: fix trace generation
            val vulnerabilityStub = vulnerabilities.firstOrNull() ?: return@use emptyList()
            val emptyTraceGraph = TraceGraph(
                vulnerabilityStub.sink,
                sources = hashSetOf(),
                edges = hashMapOf(),
                unresolvedCrossUnitCalls = emptyMap()
            )
            return@use vulnerabilities.map {
                IfdsVulnerablity(
                    it,
                    entryPoints = emptySet(),
                    traceGraph = emptyTraceGraph.copy(sink = it.sink)
                )
            }
//        }

//        vulnerabilities.map {
//            val traceGraphWithEntryPoints = ifdsEngine.vulnerabilityTraceGraphWithEntryPoints(
//                it, resolveEntryPoints = !symbolicExecutionEnabled
//            )
//            IfdsVulnerablity(it, traceGraphWithEntryPoints.entryPoints, traceGraphWithEntryPoints.graph)
//        }
    }

    private fun buildTargetsFromIfdsTraces(
        instances: List<IfdsVulnerablity>,
        possibleEp: Set<JIRMethod>
    ): Map<JIRMethod, List<TaintAnalysis.TaintTarget>> {
        val result = hashMapOf<JIRMethod, MutableList<TaintAnalysis.TaintTarget>>()
        for (instance in instances) {
            val entryPoints = instance.entryPoints()
            val targets = resolveIfdsTraceTargets(instance)

            try {
                targets.forEach { verifyTarget(it, hashSetOf()) }
            } catch (ex: Exception) {
                logger.error("Bad target: $ex")
                continue
            }

            for (entrypoint in entryPoints) {
                check(entrypoint in possibleEp) { "Unexpected EP: $entrypoint" }
                result.getOrPut(entrypoint) { mutableListOf() } += targets
            }
        }
        return result
    }

    private fun verifyTarget(target: TaintAnalysis.TaintTarget, path: MutableSet<TaintAnalysis.TaintTarget>) {
        if (!path.add(target)) {
            error("Recursive target")
        }

        target.children.forEach { verifyTarget(it as TaintAnalysis.TaintTarget, path) }

        path.remove(target)
    }

    private fun resolveIfdsTraceTargets(instance: IfdsVulnerablity): Set<TaintAnalysis.TaintTarget> {
        val initialTargets = hashSetOf<TaintAnalysis.TaintTarget>()
        val resolvedTargets = hashMapOf<Vertex<TaintDomainFact, JIRInst>, TaintAnalysis.TaintTarget>()

        val vertexPredecessors = hashMapOf<Vertex<TaintDomainFact, JIRInst>, MutableSet<Vertex<TaintDomainFact, JIRInst>>>()
        for ((vertex, successors) in instance.traceGraph.edges) {
            successors.forEach { succ ->
                vertexPredecessors.getOrPut(succ) { hashSetOf() }.add(vertex)
            }
        }

        fun dfs(
            vertex: Vertex<TaintDomainFact, JIRInst>,
            prevTarget: TaintAnalysis.TaintTarget,
            path: MutableSet<Vertex<TaintDomainFact, JIRInst>>
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
        opentaintTargetMapping[sinkTarget] = instance
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
        vertex: Vertex<TaintDomainFact, JIRInst>,
        cache: MutableMap<Vertex<TaintDomainFact, JIRInst>, TaintAnalysis.TaintTarget>,
    ): TaintAnalysis.TaintTarget = cache.getOrPut(vertex) {
        TaintAnalysis.TaintIntermediateTarget(resolveLocationInst(vertex))
    }

    private fun IfdsVulnerablity.entryPoints(): Set<JIRMethod> {
        if (entryPoints.size > 1) {
            logger.warn { "Possibly wrong entry points: $entryPoints" }
        }

        return entryPoints
    }

    private fun resolveLocationInst(vertex: Vertex<TaintDomainFact, JIRInst>): JIRInst {
        val method = vertex.method as JIRMethod
        if (vertex.statement in method.instList) {
            return vertex.statement
        }

        return method.flowGraph().entry
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
        reachedSinks.map { ReachedSink(it.entrypoint, it.statement, it.rule) }


    private fun TaintAnalysis.verifiedTraces(): List<IfdsVulnerablity> =
        reachedSinks.mapNotNull { rs -> rs.target?.let { opentaintTargetMapping[it] } }

    private fun Map<JIRMethod, List<TaintAnalysis.TaintTarget>>.printActiveTargets(): String = buildString {
        values.flatten().filterNot { it.isRemoved }.forEach {
            appendLine("-".repeat(20))
            appendLine(it)
        }
    }

    companion object {
        val logger = object : KLogging() {}.logger

        class PackageUnitResolver(private val bannedLocations: Set<RegisteredLocation>) : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType {
                if (method.enclosingClass.declaration.location in bannedLocations) {
                    return UnknownUnit
                }

                return PackageUnit(method.enclosingClass.packageName)
            }
        }
    }
}
