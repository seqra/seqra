package org.opentaint.jvm.sast.runner

import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.restrictTo
import mu.KLogging
import org.opentaint.common.sast.CommonAnalysisOptions
import org.opentaint.common.sast.CommonProjectAnalyzer
import org.opentaint.common.sast.ProjectAnalysisStatus
import org.opentaint.common.sast.dataflow.DebugOptions
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.go.sast.project.GoProjectAnalysisOptions
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.jvm.sast.project.ProjectKind
import org.opentaint.jvm.sast.util.file
import org.opentaint.jvm.sast.util.newDirectory
import org.opentaint.project.Project
import org.opentaint.util.CliWithLogger
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.system.exitProcess

abstract class AbstractAnalyzerRunner : CliWithLogger() {
    protected val ifdsAnalysisTimeout: Int by option(help = "IFDS analysis timeout in seconds")
        .int().default(10000)

    protected val ifdsApMode: ApMode by option(help = "IFDS Ap mode")
        .choice(ApMode.entries.associateBy { it.name })
        .default(ApMode.Tree)

    protected val iterationLimit: Int? by option(help = "Cap the iterative-deepening fact depth at N field accessors and drop facts over it (unlimited by default)")
        .int().restrictTo(min = 0)

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

    private val debugRunAnalysisOnSelectedEntryPoints: String? by option(
        help = """
            Run analysis on selected entry points.
            Use '*' to run on all methods
            Use method fqn (e.g.  com.example.Class#method) to run on specific method
            """.trimIndent()
    )

    private val debugRunRuleTests: Boolean by option(help = "Run rule tests instead of project analysis")
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

    val debugOptions by lazy {
        DebugOptions(
            taintRulesStatsSamplingPeriod = debugTaintRulesStatsSamplingPeriod.takeIf { debugTaintRulesStats },
            enableIfdsCoverage = debugIfdsCoverage,
            factReachabilitySarif = debugFactReachabilitySarif,
            enableVulnSummary = false,
            runRuleTests = debugRunRuleTests,
            debugRunAnalysisOnSelectedEntryPoints = debugRunAnalysisOnSelectedEntryPoints
        )
    }

    abstract fun commonOptions(): CommonAnalysisOptions
    abstract fun javaOptions(): ProjectAnalysisOptions
    abstract fun goOptions(): GoProjectAnalysisOptions

    private val analysisOptions = object : CommonProjectAnalyzer.OptionProvider {
        override fun commonOptions(): CommonAnalysisOptions = this@AbstractAnalyzerRunner.commonOptions()
        override fun javaOptions(): ProjectAnalysisOptions = this@AbstractAnalyzerRunner.javaOptions()
        override fun goOptions(): GoProjectAnalysisOptions = this@AbstractAnalyzerRunner.goOptions()
    }

    override fun main() {
        val project = runCatching { Project.load(this.project) }
            .onFailure {
                logger.error(it) { "Incorrect project configuration" }
                exitProcess(-1)
            }
            .getOrThrow()

        val resolvedProject = project.resolve(this.project.parent)

        outputDir.createDirectories()

        val analyzer = CommonProjectAnalyzer(
            resolvedProject,
            outputDir,
            runInTestMode = debugOptions.runRuleTests,
            analysisOptions
        )
        val status = analyzer.run()
        exitProcessIfNotOk(status)
    }

    private fun exitProcessIfNotOk(status: ProjectAnalysisStatus) {
        val exitCode = when (status) {
            ProjectAnalysisStatus.OK -> return
            ProjectAnalysisStatus.TIMEOUT -> -2
            ProjectAnalysisStatus.OOM -> -3
            ProjectAnalysisStatus.EXCEPTION -> -4
        }
        exitProcess(exitCode)
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}