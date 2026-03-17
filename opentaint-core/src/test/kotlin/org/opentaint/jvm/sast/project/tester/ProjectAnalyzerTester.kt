package org.opentaint.jvm.sast.project.tester

import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.access.FactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta
import org.opentaint.dataflow.configuration.jvm.AssignMark
import org.opentaint.dataflow.configuration.jvm.ConstantTrue
import org.opentaint.dataflow.configuration.jvm.ContainsMark
import org.opentaint.dataflow.configuration.jvm.TaintCleaner
import org.opentaint.dataflow.configuration.jvm.TaintConfigurationItem
import org.opentaint.dataflow.configuration.jvm.TaintMark
import org.opentaint.dataflow.configuration.jvm.TaintMethodEntrySink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodExitSource
import org.opentaint.dataflow.configuration.jvm.TaintMethodSink
import org.opentaint.dataflow.configuration.jvm.TaintMethodSource
import org.opentaint.dataflow.configuration.jvm.TaintPassThrough
import org.opentaint.dataflow.configuration.jvm.TaintSinkMeta
import org.opentaint.dataflow.configuration.jvm.TaintStaticFieldSource
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRField
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.findMethodOrNull
import org.opentaint.jvm.sast.dataflow.DebugOptions
import org.opentaint.jvm.sast.dataflow.DummySerializationContext
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.ProjectAnalysisContext
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.jvm.sast.project.ProjectKind
import org.opentaint.jvm.sast.project.initializeProjectAnalysisContext
import org.opentaint.jvm.sast.project.selectProjectEntryPoints
import org.opentaint.jvm.sast.util.loadDefaultConfig
import org.opentaint.jvm.sast.util.locationChecker
import org.opentaint.project.Project
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration

private val logger = object : KLogging() {}.logger

@Suppress("unused")
fun testProjectAnalyzerOnTraces(
    project: Project,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    projectKind: ProjectKind,
    testDataJsonPath: Path,
    debugOptions: DebugOptions
) {
    val testDataTaintConfig: List<TracePair> = Json.decodeFromString(
        testDataJsonPath.readText()
    )

    val options = ProjectAnalysisOptions(projectKind = projectKind)
    val analysisContext = initializeProjectAnalysisContext(project, options)

    val mainConfig = JIRTaintRulesProvider(
        TaintConfiguration(analysisContext.cp).also { it.loadConfig(loadDefaultConfig()) }
    )

    val visitedAtSourceMarks = hashSetOf<TaintMark>()
    val stats = analysisContext.use {
        val testData = it.loadTestData(testDataTaintConfig)
        val config = createTestConfig(testData, mainConfig, visitedAtSourceMarks)
        val entryPoints = it.selectProjectEntryPoints(options)

        logger.info { "Start running tests" }
        val traces = it.analyze(config, entryPoints, ifdsAnalysisTimeout, ifdsApMode, debugOptions)
        getStats(traces, testData, visitedAtSourceMarks)
    }
    logger.info { "Total number of marks: ${stats.marksTotal}" }

    logger.info { "Sources not found: ${stats.sourcesNotFound.size} (recall=${String.format("%.2f", stats.sourcesRecall)})" }
    logger.debug { "Marks for missed sources: ${stats.sourcesNotFound}" }

    logger.info { "Sinks not found: ${stats.sinksNotFound.size} (recall=${String.format("%.2f", stats.sinksRecall)})" }
    logger.debug { "Marks for missed sinks: ${stats.sinksNotFound}" }
}

private data class ProjectTestData(
    val testDataByMark: Map<String, TracePair>,
    val testDataBySourceInst: Map<JIRInst, List<TracePair>>,
    val testDataBySinkInst: Map<JIRInst, List<TracePair>>
)

private fun ProjectAnalysisContext.loadTestData(testDataTaintConfig: List<TracePair>): ProjectTestData {
    fun TraceLocation.method(): JIRMethod? =
        cp.findClassOrNull(cls)?.findMethodOrNull(methodName, methodDesc)

    fun TraceLocation.inst(): JIRInst? =
        method()?.instList?.getOrNull(instIndex)
            .takeIf { it.toString() == instStr }
            .also {
                if (it == null) {
                    logger.warn { "Instruction $this not found" }
                }
            }

    val validTestData = testDataTaintConfig.filter {
        it.source.location.inst() != null && it.sink.location.inst() != null
    }

    val testDataByMark = validTestData
        .groupBy { it.source.mark }
        .mapValues { (_, trace) -> trace.single() }

    val testDataBySourceInst = validTestData.groupBy { it.source.location.inst()!! }
    val testDataBySinkInst = validTestData.groupBy { it.sink.location.inst()!! }

    return ProjectTestData(testDataByMark, testDataBySourceInst, testDataBySinkInst)
}

private fun createTestConfig(
    testData: ProjectTestData,
    mainConfig: TaintRulesProvider,
    visitedAtSourceMarks: MutableSet<TaintMark>
): TaintRulesProvider = object : TaintRulesProvider {
    override fun entryPointRulesForMethod(method: CommonMethod, fact: FactAp?, allRelevant: Boolean) =
        mainConfig.entryPointRulesForMethod(method, fact, allRelevant)

    override fun sourceRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        testData.testDataBySourceInst[statement].orEmpty().map { trace ->
            val source = trace.source
            val mark = TaintMark(source.mark)
            visitedAtSourceMarks.add(mark)

            TaintMethodSource(
                method = method,
                condition = ConstantTrue,
                actionsAfter = listOf(AssignMark(mark, specializePosition(it, source.position).single())),
                info = null
            )
        }
    }

    override fun sinkRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        testData.testDataBySinkInst[statement].orEmpty().map { trace ->
            val sink = trace.sink
            val meta = TaintSinkMeta(
                message = "Path generated by symbolic engine",
                severity = CommonTaintConfigurationSinkMeta.Severity.Error,
                cwe = null
            )
            TaintMethodSink(
                method = method,
                condition = ContainsMark(specializePosition(it, sink.position).single(), TaintMark(sink.mark)),
                trackFactsReachAnalysisEnd = emptyList(),
                id = sink.mark,
                meta = meta,
                info = null
            )
        }
    }

    override fun sinkRulesForMethodExit(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        initialFacts: Set<InitialFactAp>?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSink> = getRules(method) {
        emptyList<TaintMethodExitSink>()
    }

    override fun sinkRulesForMethodEntry(method: CommonMethod, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        emptyList<TaintMethodEntrySink>()
    }

    override fun passTroughRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ) = getRules(method) {
        if (it.enclosingClass.declaration.location.isRuntime) {
            return@getRules mainConfig.passTroughRulesForMethod(it, statement, fact, allRelevant)
        }

        emptyList<TaintPassThrough>()
    }

    override fun cleanerRulesForMethod(method: CommonMethod, statement: CommonInst, fact: FactAp?, allRelevant: Boolean) = getRules(method) {
        emptyList<TaintCleaner>()
    }

    override fun exitSourceRulesForMethod(
        method: CommonMethod,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintMethodExitSource> {
        return emptyList()
    }

    override fun sourceRulesForStaticField(
        field: JIRField,
        statement: CommonInst,
        fact: FactAp?,
        allRelevant: Boolean
    ): Iterable<TaintStaticFieldSource> = emptyList()

    private inline fun <T : TaintConfigurationItem> getRules(
        method: CommonMethod,
        body: (JIRMethod) -> Iterable<T>
    ): Iterable<T> {
        check(method is JIRMethod) { "Expected method to be JIRMethod" }
        return body(method)
    }
}

private fun ProjectAnalysisContext.analyze(
    config: TaintRulesProvider,
    entryPoints: List<JIRMethod>,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    debugOptions: DebugOptions
): List<VulnerabilityWithTrace> {
    val options = TaintAnalyzerOptions(
        ifdsTimeout = ifdsAnalysisTimeout,
        ifdsApMode = ifdsApMode,
        symbolicExecutionEnabled = false,
        analysisCwe = null,
        storeSummaries = false,
        debugOptions = debugOptions
    )
    JIRTaintAnalyzer(
        cp, config,
        projectClasses = projectClasses.locationChecker(),
        options = options,
        summarySerializationContext = DummySerializationContext,
    ).use { analyzer ->
        return analyzer.analyzeWithIfds(entryPoints)
    }
}

private fun getStats(
    ifdsTraces: List<VulnerabilityWithTrace>,
    testData: ProjectTestData,
    visitedAtSourceMarks: Set<TaintMark>
): EvaluationStats {
    val visitedAtSinkMarks = ifdsTraces.map {
        val rule = it.vulnerability.rule
        val condition = (rule as TaintMethodSink).condition

        check(condition is ContainsMark) { "Unexpected rule with non-trivial condition: $condition" }
        condition.mark
    }.toSet()

    val allMarks = testData.testDataByMark.keys.mapTo(hashSetOf()) { TaintMark(it) }
    return EvaluationStats(
        marksTotal = allMarks.size,
        sourcesNotFound = allMarks.filterNot { it in visitedAtSourceMarks },
        sinksNotFound = allMarks.filterNot { it in visitedAtSinkMarks }
    )
}

data class EvaluationStats(
    val marksTotal: Int,
    val sourcesNotFound: List<TaintMark>,
    val sinksNotFound: List<TaintMark>
) {
    val sinksRecall: Double
        get() = 1.0 * (marksTotal - sinksNotFound.size) / marksTotal

    val sourcesRecall: Double
        get() = 1.0 * (marksTotal - sourcesNotFound.size) / marksTotal
}
