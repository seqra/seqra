package org.opentaint.jvm.sast.runner

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.jvm.sast.dataflow.DebugOptions
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.jvm.sast.project.ProjectAnalyzer
import org.opentaint.jvm.sast.project.TestProjectAnalyzer
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

    private val semgrepRuleSet: List<Path> by option(help = "Semgrep YAML rule file or directory containing YAML rules")
        .path()
        .multiple()

    private val semgrepRuleSeverity: List<Severity> by option(help = "Rule severity")
        .choice(Severity.entries.associateBy { it.name.lowercase() }).multiple()

    private val semgrepRuleLoadErrors: Path? by option(help = "Output file for errors encountered while loading Semgrep rules")
        .newFile()

    private val semgrepRuleLoadTrace: Path? by option(help = "Output file for Semgrep rules loader trace")
        .newFile()

    override fun analyzeProject(project: Project, analyzerOutputDir: Path, debugOptions: DebugOptions) {
        if (project.modules.isEmpty()) {
            return
        }

        val options = ProjectAnalysisOptions(
            customConfig = config,
            semgrepRuleSet = semgrepRuleSet,
            semgrepSeverity = semgrepRuleSeverity,
            semgrepRuleLoadErrors = semgrepRuleLoadErrors,
            semgrepRuleLoadTrace = semgrepRuleLoadTrace,
            cwe = cwe,
            useSymbolicExecution = useSymbolicExecution,
            symbolicExecutionTimeout = symbolicExecutionTimeout.seconds,
            ifdsAnalysisTimeout = ifdsAnalysisTimeout.seconds,
            ifdsApMode = ifdsApMode,
            projectKind = projectKind,
            storeSummaries = true,
            debugOptions = debugOptions
        )

        if (!debugOptions.runRuleTests) {
            val projectAnalyzer = ProjectAnalyzer(project, analyzerOutputDir, options)
            projectAnalyzer.analyze()
        } else {
            val testAnalyzer = TestProjectAnalyzer(project, analyzerOutputDir, options)
            testAnalyzer.analyze()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = ProjectAnalyzerRunner().main(args)
    }
}
