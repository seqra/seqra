package org.opentaint.jvm.sast.runner

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
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
import org.opentaint.jvm.sast.project.SarifGenerationOptions
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

    private val semgrepRuleLoadTrace: Path? by option(help = "Output file for Semgrep rules loader trace")
        .newFile()

    private val sarifFileName: String by option(help = "Sarif file name")
        .default(SarifGenerationOptions.DEFAULT_FILE_NAME)

    private val sarifCodeFlowLimit: Int? by option(help = "Sarif code flow limit").int()

    private val sarifSemgrepStyleId: Boolean by option(help = "Use semgrep style ids").flag()

    private val sarifToolVersion: String by option(help = "Tool version")
        .default(SarifGenerationOptions.DEFAULT_VERSION)

    private val sarifToolSemanticVersion: String by option(help = "Tool semantic version")
        .default(SarifGenerationOptions.DEFAULT_SEMANTIC_VERSION)

    private val sarifGenerateFingerprint: Boolean by option(help = "Generate partial fingerprints")
        .flag()

    private val sarifUriBase: String? by option(help = "Sarif sources root uri")

    private val experimentalAAInterProcCallDepth: Int by option(help = "Experimental options: inter-proc alias analysis call depth")
        .int().default(1)

    override fun analyzeProject(project: Project, analyzerOutputDir: Path, debugOptions: DebugOptions) {
        if (project.modules.isEmpty()) {
            return
        }

        val sarifOptions = SarifGenerationOptions(
            sarifFileName = sarifFileName,
            sarifCodeFlowLimit = sarifCodeFlowLimit,
            useSemgrepStyleId = sarifSemgrepStyleId,
            toolVersion = sarifToolVersion,
            toolSemanticVersion = sarifToolSemanticVersion,
            uriBase = sarifUriBase,
            generateFingerprint = sarifGenerateFingerprint,
        )

        val options = ProjectAnalysisOptions(
            customConfig = config,
            semgrepRuleSet = semgrepRuleSet,
            semgrepSeverity = semgrepRuleSeverity,
            semgrepRuleLoadTrace = semgrepRuleLoadTrace,
            cwe = cwe,
            useSymbolicExecution = useSymbolicExecution,
            symbolicExecutionTimeout = symbolicExecutionTimeout.seconds,
            ifdsAnalysisTimeout = ifdsAnalysisTimeout.seconds,
            ifdsApMode = ifdsApMode,
            projectKind = projectKind,
            storeSummaries = true,
            experimentalAAInterProcCallDepth = experimentalAAInterProcCallDepth,
            debugOptions = debugOptions,
            sarifGenerationOptions = sarifOptions,
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
