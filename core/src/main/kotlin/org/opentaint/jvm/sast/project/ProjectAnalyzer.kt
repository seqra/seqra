package org.opentaint.jvm.sast.project

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.encodeToStream
import kotlinx.serialization.Serializable
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.taint.SkippedExternalMethods
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadSemgrepRules
import org.opentaint.jvm.sast.project.rules.semgrepRulesWithDefaultConfig
import org.opentaint.jvm.sast.sarif.DebugFactReachabilitySarifGenerator
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.jvm.sast.sarif.SarifGenerator
import org.opentaint.jvm.sast.se.api.SastSeAnalyzer
import org.opentaint.jvm.sast.util.asSequenceWithProgress
import org.opentaint.jvm.sast.util.loadDefaultConfig
import org.opentaint.jvm.sast.util.locationChecker
import org.opentaint.project.Project
import org.opentaint.semgrep.pattern.RuleMetadata
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.createTaintConfig
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.seconds

class ProjectAnalyzer(
    private val project: Project,
    private val resultDir: Path,
    private val options: ProjectAnalysisOptions,
) {
    private val ruleMetadatas = mutableListOf<RuleMetadata>()

    fun analyze() {
        val rules = preloadRules()
        val projectAnalysisContext = initializeProjectAnalysisContext(project, options)

        projectAnalysisContext.use {
            val entryPoints = it.selectProjectEntryPoints(options)
            it.runAnalyzer(entryPoints, rules)
        }
    }

    private sealed interface PreloadedRules {
        data class SemgrepRules(val rules: List<TaintRuleFromSemgrep>) : PreloadedRules
        data class Custom(val config: SerializedTaintConfig) : PreloadedRules
        data class SemgrepRulesWithCustomConfig(
            val rules: List<TaintRuleFromSemgrep>,
            val config: SerializedTaintConfig,
        ) : PreloadedRules
        data object DefaultRules : PreloadedRules
    }

    private fun preloadRules(): PreloadedRules {
        val customConfig = options.customConfig?.let { cfg ->
            cfg.inputStream().use { cfgStream ->
                loadSerializedTaintConfig(cfgStream)
            }
        }

        if (options.semgrepRuleSet.isNotEmpty()) {
            val loadedRules = options.loadSemgrepRules()
            ruleMetadatas += loadedRules.rulesWithMeta.map { it.second }
            val rules = loadedRules.rulesWithMeta.map { it.first }

            return if (customConfig != null) {
                PreloadedRules.SemgrepRulesWithCustomConfig(rules, customConfig)
            } else {
                PreloadedRules.SemgrepRules(rules)
            }
        }

        if (customConfig != null) {
            return PreloadedRules.Custom(customConfig)
        }

        return PreloadedRules.DefaultRules
    }

    private fun loadTaintConfig(cp: JIRClasspath, rules: PreloadedRules): TaintRulesProvider = when (rules) {
        is PreloadedRules.SemgrepRules -> {
            rules.rules.semgrepRulesWithDefaultConfig(cp)
        }

        is PreloadedRules.SemgrepRulesWithCustomConfig -> {
            // Load default pass-through rules, override with custom config, then layer semgrep rules
            val defaultPassRules = loadDefaultConfig()
            val config = TaintConfiguration(cp)
            config.loadConfig(defaultPassRules)
            // Custom config overrides default (OVERRIDE mode via JIRCombinedTaintRulesProvider)
            val customTaintConfig = TaintConfiguration(cp).apply { loadConfig(rules.config) }
            rules.rules.forEach { config.loadConfig(it.createTaintConfig()) }

            val baseRules = JIRTaintRulesProvider(config)
            val customRules = JIRTaintRulesProvider(customTaintConfig)
            JIRCombinedTaintRulesProvider(baseRules, customRules)
        }

        is PreloadedRules.Custom -> {
            val defaultConfig = TaintConfiguration(cp)
            defaultConfig.loadConfig(loadDefaultConfig())

            val customConfig = TaintConfiguration(cp).apply { loadConfig(rules.config) }

            val defaultRules = JIRTaintRulesProvider(defaultConfig)
            val customRules = JIRTaintRulesProvider(customConfig)
            JIRCombinedTaintRulesProvider(defaultRules, customRules)
        }

        is PreloadedRules.DefaultRules -> {
            val defaultConfig = TaintConfiguration(cp)
            defaultConfig.loadConfig(loadDefaultConfig())
            JIRTaintRulesProvider(defaultConfig)
        }
    }

    private fun ProjectAnalysisContext.runAnalyzer(entryPoints: List<JIRMethod>, rules: PreloadedRules) {
        val externalMethodTracker = if (options.externalMethodsOutput != null) ExternalMethodTracker() else null

        val analysisResult = runAnalyzerWithTraceResolver(entryPoints, rules, externalMethodTracker)
        generateReportFromAnalysisResult(analysisResult)

        // Write external methods YAML output if requested
        if (externalMethodTracker != null && options.externalMethodsOutput != null) {
            val skippedMethods = externalMethodTracker.getSkippedMethods()
            writeExternalMethodsYaml(options.externalMethodsOutput!!, skippedMethods)
        }
    }

    private data class AnalysisResult(
        val traces: List<VulnerabilityWithTrace>,
        val seVerifiedTraces: List<VulnerabilityWithTrace>? = null,
        val debugStatementsWithFact: Map<CommonInst, Set<FinalFactAp>>? = null
    )

    private fun ProjectAnalysisContext.runAnalyzerWithTraceResolver(
        entryPoints: List<JIRMethod>,
        rules: PreloadedRules,
        externalMethodTracker: ExternalMethodTracker? = null,
    ): AnalysisResult {
        val loadedConfig = loadTaintConfig(cp, rules)
        val config = analysisConfig(loadedConfig)

        JIRTaintAnalyzer(
            cp, config,
            projectClasses = projectClasses.locationChecker(),
            options = options.taintAnalyzerOptions(),
            externalMethodTracker = externalMethodTracker,
        ).use { analyzer ->
            logger.info { "Start IFDS analysis for project: ${project.sourceRoot}" }
            val traces = analyzer.analyzeWithIfds(entryPoints)
            logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

            var result = AnalysisResult(traces)

            if (options.debugOptions?.factReachabilitySarif == true) {
                val stmtsWithFact = analyzer.statementsWithFacts()
                result = result.copy(debugStatementsWithFact = stmtsWithFact)
            }

            if (!options.useSymbolicExecution) return result

            val seAnalyzer = SastSeAnalyzer.createSeEngine<TaintAnalysisUnitRunnerManager, VulnerabilityWithTrace>()
                ?: return result

            logger.info { "Start SE for project: ${project.sourceRoot}" }
            val seOptions = SastSeAnalyzer.SeOptions(options.symbolicExecutionTimeout, options.experimentalAAInterProcCallDepth)
            val verifiedTraces = seAnalyzer.analyzeTraces(
                cp, projectClasses.projectLocationsUnsafe, analyzer.ifdsEngine,
                traces, seOptions
            )
            logger.info { "Finish SE for project: ${project.sourceRoot}" }

            result = result.copy(seVerifiedTraces = verifiedTraces)

            return result
        }
    }

    private fun ProjectAnalysisContext.generateReportFromAnalysisResult(result: AnalysisResult) {
        logger.info { "Start SARIF report generation for project: ${project.sourceRoot}" }

        val sourcesResolver = project.sourceResolver(projectClasses)

        (resultDir / options.sarifGenerationOptions.sarifFileName).outputStream().use {
            generateSarifReportFromTraces(it, sourcesResolver, result.traces)
        }

        result.debugStatementsWithFact?.let { stmtsWithFact ->
            (resultDir / "debug-ifds-fact-reachability.sarif").outputStream().use {
                generateFactReachabilityReport(it, sourcesResolver, stmtsWithFact)
            }
        }

        result.seVerifiedTraces?.let { seVerifiedTraces ->
            val reportName = options.sarifGenerationOptions.sarifFileName + ".se-verified.sarif"
            (resultDir / reportName).outputStream().use {
                generateSarifReportFromTraces(it, sourcesResolver, seVerifiedTraces)
            }
        }

        logger.info { "Finish SARIF report for project: ${project.sourceRoot}" }
    }

    private fun ProjectAnalysisContext.generateSarifReportFromTraces(
        output: OutputStream,
        sourceFileResolver: JIRSourceFileResolver,
        traces: List<VulnerabilityWithTrace>
    ) {
        val generator = SarifGenerator(
            options.sarifGenerationOptions, project.sourceRoot,
            sourceFileResolver, JIRSarifTraits(cp)
        )

        val tracesWithProgress = traces.asSequenceWithProgress(10.seconds) { taken, overall ->
            logger.info { "Generated ${taken - 1}/${overall} sarif traces" }
        }

        generator.generateSarif(output, tracesWithProgress, ruleMetadatas)
        logger.info { "Sarif trace generation stats: ${generator.traceGenerationStats}" }
    }

    private fun ProjectAnalysisContext.generateFactReachabilityReport(
        output: OutputStream,
        sourceFileResolver: JIRSourceFileResolver,
        reachableFacts: Map<CommonInst, Set<FinalFactAp>>,
    ) {
        val generator = DebugFactReachabilitySarifGenerator(
            options.sarifGenerationOptions,
            sourceFileResolver, JIRSarifTraits(cp)
        )
        generator.generateSarif(output, reachableFacts)
    }

    /**
     * Writes external methods to two separate files derived from [outputPath]:
     *  - `<name>-without-rules.yaml` — methods with NO approximation rules (taint killed)
     *  - `<name>-with-rules.yaml` — methods with existing approximation rules
     *
     * For example, `external-methods.yaml` produces
     * `external-methods-without-rules.yaml` and `external-methods-with-rules.yaml`.
     */
    private fun writeExternalMethodsYaml(outputPath: Path, skippedMethods: SkippedExternalMethods) {
        val baseName = outputPath.fileName.toString().removeSuffix(".yaml").removeSuffix(".yml")
        val parent = outputPath.parent ?: outputPath.fileSystem.getPath(".")

        val withoutRulesPath = parent.resolve("$baseName-without-rules.yaml")
        val withRulesPath = parent.resolve("$baseName-with-rules.yaml")

        val withoutRulesSerialized = skippedMethods.withoutRules.map { it.toSerialized() }
        val withRulesSerialized = skippedMethods.withRules.map { it.toSerialized() }

        withoutRulesPath.outputStream().use { stream ->
            skippedMethodsYaml.encodeToStream(
                SerializedExternalMethodRecordList(methods = withoutRulesSerialized), stream,
            )
        }
        withRulesPath.outputStream().use { stream ->
            skippedMethodsYaml.encodeToStream(
                SerializedExternalMethodRecordList(methods = withRulesSerialized), stream,
            )
        }

        logger.info { "Wrote external methods without rules to $withoutRulesPath (${withoutRulesSerialized.size} entries)" }
        logger.info { "Wrote external methods with rules to $withRulesPath (${withRulesSerialized.size} entries)" }
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        private val skippedMethodsYaml = Yaml(
            configuration = YamlConfiguration(encodeDefaults = true)
        )
    }
}

@Serializable
private data class SerializedExternalMethodRecordList(
    val methods: List<SerializedExternalMethodRecord>,
)

@Serializable
private data class SerializedExternalMethodRecord(
    val method: String,
    val signature: String,
    val factPositions: List<String>,
    val callSites: Int,
)

private fun org.opentaint.dataflow.ap.ifds.taint.ExternalMethodRecord.toSerialized() =
    SerializedExternalMethodRecord(
        method = method,
        signature = signature,
        factPositions = factPositions.sorted(),
        callSites = callSites,
    )
