package org.opentaint.jvm.sast.project

import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzerTester
import org.opentaint.jvm.sast.dataflow.TracePair
import org.opentaint.dataflow.ap.ifds.access.ApMode
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.time.Duration

class ProjectAnalyzerTester(
    project: Project,
    projectPackage: String?,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    projectKind: ProjectKind,
    testDataJsonPath: Path
) : AbstractProjectAnalyzer(
    project,
    projectPackage,
    ifdsAnalysisTimeout,
    ifdsApMode,
    projectKind
) {
    private val testDataTaintConfig: List<TracePair> = Json.decodeFromString(
        testDataJsonPath.readText()
    )

    override fun runAnalyzer(entryPoints: List<JIRMethod>) {
        val analyzer = JIRTaintAnalyzerTester(
            cp, taintConfig, testDataTaintConfig,
            projectLocations = projectClasses.projectLocations,
            dependenciesLocations = projectClasses.dependenciesLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
        )

        logger.info { "Start running tests" }
        val stats = analyzer.runTests(entryPoints)
        logger.info { "Total number of marks: ${stats.marksTotal}" }

        logger.info { "Sources not found: ${stats.sourcesNotFound.size} (recall=${String.format("%.2f", stats.sourcesRecall)})" }
        logger.debug { "Marks for missed sources: ${stats.sourcesNotFound}" }

        logger.info { "Sinks not found: ${stats.sinksNotFound.size} (recall=${String.format("%.2f", stats.sinksRecall)})" }
        logger.debug { "Marks for missed sinks: ${stats.sinksNotFound}" }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}