package org.opentaint.jvm.sast.project

import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzerTester
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.TracePair
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.configuration.jvm.serialized.TaintConfiguration
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration

class ProjectAnalyzerTester(
    project: Project,
    projectPackage: String?,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    projectKind: ProjectKind,
    testDataJsonPath: Path,
    debugOptions: DebugOptions
) : AbstractProjectAnalyzer(
    project,
    projectPackage,
    ifdsAnalysisTimeout,
    ifdsApMode,
    projectKind,
    debugOptions
) {
    private val testDataTaintConfig: List<TracePair> = Json.decodeFromString(
        testDataJsonPath.readText()
    )

    private fun loadMainConfig(): TaintRulesProvider =
        JIRTaintRulesProvider(TaintConfiguration().also { it.loadConfig(loadDefaultConfig()) })

    override fun runAnalyzer(entryPoints: List<JIRMethod>) {
        JIRTaintAnalyzerTester(
            cp, loadMainConfig(), testDataTaintConfig,
            projectLocations = projectClasses.projectLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
            debugOptions = debugOptions
        ).use { analyzer ->
            logger.info { "Start running tests" }
            val stats = analyzer.runTests(entryPoints)
            logger.info { "Total number of marks: ${stats.marksTotal}" }

            logger.info { "Sources not found: ${stats.sourcesNotFound.size} (recall=${String.format("%.2f", stats.sourcesRecall)})" }
            logger.debug { "Marks for missed sources: ${stats.sourcesNotFound}" }

            logger.info { "Sinks not found: ${stats.sinksNotFound.size} (recall=${String.format("%.2f", stats.sinksRecall)})" }
            logger.debug { "Marks for missed sinks: ${stats.sinksNotFound}" }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}