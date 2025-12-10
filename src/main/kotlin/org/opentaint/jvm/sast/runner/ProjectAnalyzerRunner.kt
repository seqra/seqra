package org.opentaint.jvm.sast.runner

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer.DebugOptions
import org.opentaint.jvm.sast.project.ProjectAnalyzer
import org.opentaint.jvm.sast.util.file
import org.opentaint.project.Project
import org.opentaint.util.newFile
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class ProjectAnalyzerRunner : AbstractAnalyzerRunner() {
    private val cwe: List<Int> by option(help = "Analyzer CWE")
        .int().multiple()

    private val useSymbolicExecution: Boolean by option(help = "Use symbolic execution engine")
        .boolean().default(false)

    private val symbolicExecutionTimeout: Int by option(help = "Symbolic execution timeout in seconds")
        .int().default(60)

    private val config: Path? by option(help = "User defined analysis configuration")
        .file()

    private val semgrepRuleSet: Path? by option(help = "Semgrep YAML rule file or directory containing YAML rules")
        .path()

    private val semgrepRuleLoadErrors: Path? by option(help = "Output file for errors encountered while loading Semgrep rules")
        .newFile()

    override fun analyzeProject(project: Project, analyzerOutputDir: Path, debugOptions: DebugOptions) {
        val projectAnalyzer = ProjectAnalyzer(
            project = project,
            projectPackage = null,
            resultDir = analyzerOutputDir,
            cwe = cwe,
            useSymbolicExecution = useSymbolicExecution,
            symbolicExecutionTimeout = symbolicExecutionTimeout.seconds,
            ifdsAnalysisTimeout = ifdsAnalysisTimeout.seconds,
            ifdsApMode = ifdsApMode,
            storeSummaries = true,
            projectKind = projectKind,
            customConfig = config,
            semgrepRuleSet = semgrepRuleSet,
            semgrepRuleLoadErrors = semgrepRuleLoadErrors,
            debugOptions = debugOptions
        )

        projectAnalyzer.analyze()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = ProjectAnalyzerRunner().main(args)
    }
}
