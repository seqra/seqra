package org.opentaint.jvm.sast.runner

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer.DebugOptions
import org.opentaint.jvm.sast.project.ProjectKind
import org.opentaint.jvm.sast.util.file
import org.opentaint.jvm.sast.util.newDirectory
import org.opentaint.project.Project
import org.opentaint.util.CliWithLogger
import java.nio.file.Path
import kotlin.io.path.createDirectories

abstract class AbstractAnalyzerRunner : CliWithLogger() {
    protected val ifdsAnalysisTimeout: Int by option(help = "IFDS analysis timeout in seconds")
        .int().default(10000)

    protected val ifdsApMode: ApMode by option(help = "IFDS Ap mode")
        .choice(ApMode.entries.associateBy { it.name })
        .default(ApMode.Tree)

    private val debugTaintRulesStats: Boolean by
        option(help = "Enable reporting stats about analyzer steps per taint rule")
        .flag(default = false)

    private val debugTaintRulesStatsSamplingPeriod: Int by
        option(help = "Number of analyzer steps per one taint rule stats sample")
        .int()
        .default(100)

    private val debugIfdsCoverage: Boolean by option(help = "Enable coverage report by ifds engine")
        .flag(default = false)

    private val debugFactReachabilitySarif: Boolean by option(help = "Generate SARIF with fact reachability info")
        .flag(default = false)

    private val project: Path by option(help = "Project configuration (yaml)")
        .file()
        .required()

    protected val projectKind: ProjectKind by option(help = "Project kind")
        .choice(ProjectKind.entries.associateBy { it.name.lowercase().replace('_', '-') })
        .default(ProjectKind.UNKNOWN)

    private val outputDir by option(help = "Analyzer output directory")
        .newDirectory()
        .required()

    private val debugOptions by lazy {
        DebugOptions(
            taintRulesStatsSamplingPeriod = debugTaintRulesStatsSamplingPeriod.takeIf { debugTaintRulesStats },
            enableIfdsCoverage = debugIfdsCoverage,
            factReachabilitySarif = debugFactReachabilitySarif,
        )
    }

    override fun main() {
        val project = runCatching { Project.load(project) }
            .onFailure {
                logger.error(it) { "Incorrect project configuration" }
                return
            }
            .getOrThrow()

        val resolvedProject = project.resolve(this.project.parent)

        outputDir.createDirectories()

        runProjectAnalysisRecursively(resolvedProject)
    }

    private fun runProjectAnalysisRecursively(project: Project) {
        try {
            logger.info { "Start analysis for project: ${project.sourceRoot}" }
            analyzeProject(project, outputDir, debugOptions)
            logger.info { "Finish analysis for project: ${project.sourceRoot}" }
        } catch (ex: Throwable) {
            logger.error(ex) { "Fail analysis for project: ${project.sourceRoot}" }
        }

        project.subProjects.forEach {
            runProjectAnalysisRecursively(it)
        }
    }

    protected abstract fun analyzeProject(project: Project, analyzerOutputDir: Path, debugOptions: DebugOptions)

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}