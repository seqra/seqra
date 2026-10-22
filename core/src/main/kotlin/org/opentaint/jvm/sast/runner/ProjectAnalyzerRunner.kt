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
import org.opentaint.common.sast.CommonAnalysisOptions
import org.opentaint.common.sast.sarif.SarifGenerationOptions
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSinkMeta.Severity
import org.opentaint.go.sast.project.GoProjectAnalysisOptions
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader
import org.opentaint.jvm.sast.project.ProjectAnalysisOptions
import org.opentaint.jvm.sast.util.directory
import org.opentaint.util.newFile
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.walk
import kotlin.time.Duration.Companion.seconds

class ProjectAnalyzerRunner : AbstractAnalyzerRunner() {
    private val cwe: List<Int> by option(help = "Analyzer CWE")
        .int().multiple()

    private val useSymbolicExecution: Boolean by option(help = "Use symbolic execution engine")
        .boolean().default(false)

    private val symbolicExecutionTimeout: Int by option(help = "Symbolic execution timeout in seconds")
        .int().default(60)

    private val passthroughApproximations: List<Path> by option(
        help = "passThrough approximation YAML file or directory (walked recursively, OVERRIDE mode, repeatable)"
    )
        .path()
        .multiple()

    private val semgrepRuleSet: List<Path> by option(help = "Semgrep YAML rule file or directory containing YAML rules")
        .path()
        .multiple()

    private val semgrepRuleSeverity: List<Severity> by option(help = "Rule severity")
        .choice(Severity.entries.associateBy { it.name.lowercase() }).multiple()

    private val semgrepRuleId: List<String> by option(help = "Filter active rules by ID")
        .multiple()

    private val semgrepRuleIdExclude: List<String> by option(help = "Exclude rules by ID (applied after any --semgrep-rule-id filter)")
        .multiple()

    private val trackExternalMethods: Boolean by option(help = "Track external methods, produce external methods YAML lists")
        .flag()

    private val disableDefaultGetModel: Boolean by option(help = "Disable default model for java getter")
        .flag(default = false)

    private val javaDataflowApproximations: List<Path> by option(help = "Directory of compiled approximation class files")
        .directory()
        .multiple()

    private val goModels: List<Path> by option(help = "Directory containing opentaint/<target-package> Go model sources")
        .directory()
        .multiple()

    private val disableDefaultGoModels: Boolean by option(help = "Disable bundled Go models")
        .flag(default = false)

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

    private val sarifOptions get() = SarifGenerationOptions(
        sarifFileName = sarifFileName,
        sarifCodeFlowLimit = sarifCodeFlowLimit,
        useSemgrepStyleId = sarifSemgrepStyleId,
        toolVersion = sarifToolVersion,
        toolSemanticVersion = sarifToolSemanticVersion,
        uriBase = sarifUriBase,
        generateFingerprint = sarifGenerateFingerprint,
    )

    private val commonOptions get() = CommonAnalysisOptions(
        customApproximationConfig = passthroughApproximations.flatMap { collectYamlConfigs(it) },
        semgrepRuleSet = semgrepRuleSet,
        semgrepRuleLoadTrace = semgrepRuleLoadTrace,
        semgrepSeverity = semgrepRuleSeverity,
        semgrepRuleId = semgrepRuleId,
        semgrepRuleIdExclude = semgrepRuleIdExclude,
        trackExternalMethods = trackExternalMethods,
        ifdsAnalysisTimeout = ifdsAnalysisTimeout.seconds,
        ifdsApMode = ifdsApMode,
        debugOptions = debugOptions,
        sarifGenerationOptions = sarifOptions,
        cwe = cwe,
        useSymbolicExecution = useSymbolicExecution,
        symbolicExecutionTimeout = symbolicExecutionTimeout.seconds,
        storeSummaries = false,
        experimentalAAInterProcCallDepth = experimentalAAInterProcCallDepth,
    )

    override fun commonOptions(): CommonAnalysisOptions = commonOptions

    override fun javaOptions() = ProjectAnalysisOptions(
        common = commonOptions,
        projectKind = projectKind,
        disableDefaultGetModel = disableDefaultGetModel,
        approximationOptions = DataFlowApproximationLoader.Options(
            customApproximationPaths = javaDataflowApproximations,
        ),
    )

    override fun goOptions() = GoProjectAnalysisOptions(
        common = commonOptions,
        modelPaths = goModels,
        useDefaultModels = !disableDefaultGoModels,
    )

    private fun collectYamlConfigs(path: Path): List<Path> = path.walk()
        .filter { it.extension in arrayOf("yaml", "yml") }
        .sortedBy { it.toString() }
        .toList()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = ProjectAnalyzerRunner().main(args)
    }
}
