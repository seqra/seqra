package org.opentaint.jvm.sast.project

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.encodeToStream
import mu.KLogging
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.jvm.ap.ifds.taint.SkippedExternalMethods
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider.CombinationMode
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider.CombinationOptions
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadSemgrepRules
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

    fun analyze(): ProjectAnalysisStatus {
        val rules = preloadRules()
        val projectAnalysisContext = initializeProjectAnalysisContext(project, options)

        return projectAnalysisContext.use {
            val entryPoints = it.selectProjectEntryPoints(options)
            it.runAnalyzer(entryPoints, rules)
        }
    }

    private data class PreloadedRules(
        val rules: List<TaintRuleFromSemgrep>,
        val customApproximationConfig: List<SerializedTaintConfig>,
    )

    private fun preloadRules(): PreloadedRules {
        val loadedRules = options.loadSemgrepRules()
        ruleMetadatas += loadedRules.rulesWithMeta.map { it.second }
        val rules = loadedRules.rulesWithMeta.map { it.first }

        val approximations = options.customApproximationConfig.map { cfg ->
            cfg.inputStream().use { cfgStream ->
                loadSerializedTaintConfig(cfgStream)
            }
        }

        return PreloadedRules(rules, approximations)
    }

    private fun loadTaintConfig(cp: JIRClasspath, rules: PreloadedRules): TaintRulesProvider {
        val config = TaintConfiguration(cp)
        rules.rules.forEach { config.loadConfig(it.createTaintConfig()) }

        val defaultPassRules = loadDefaultConfig()
        config.loadConfig(defaultPassRules)

        val provider = JIRTaintRulesProvider(config)
        if (rules.customApproximationConfig.isEmpty()) return provider

        val approximationsConfig = TaintConfiguration(cp)
        rules.customApproximationConfig.forEach {
            approximationsConfig.loadConfig(it)
        }

        return JIRCombinedTaintRulesProvider(
            provider, JIRTaintRulesProvider(approximationsConfig),
            approximationConfigCombinationOptions,
        )
    }

    private fun ProjectAnalysisContext.runAnalyzer(entryPoints: List<JIRMethod>, rules: PreloadedRules): ProjectAnalysisStatus {
        val externalMethodTracker = if (options.trackExternalMethods) ExternalMethodTracker() else null

        val analysisResult = runAnalyzerWithTraceResolver(entryPoints, rules, externalMethodTracker)
        generateReportFromAnalysisResult(analysisResult)

        if (externalMethodTracker != null) {
            val externalMethods = externalMethodTracker.getExternalMethods()
            writeExternalMethodsYaml(externalMethods)
        }

        return analysisResult.status.toProjectStatus()
    }

    private data class AnalysisResult(
        val status: JIRTaintAnalyzer.Status,
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
            val (traces, status) = analyzer.analyzeWithIfds(entryPoints)
            logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

            var result = AnalysisResult(status, traces)

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

    private fun writeExternalMethodsYaml(extMethods: SkippedExternalMethods) {
        val withoutRulesPath = resultDir / "external-methods-without-rules.yaml"
        val withRulesPath = resultDir / "external-methods-with-rules.yaml"

        withoutRulesPath.outputStream().use { stream ->
            skippedMethodsYaml.encodeToStream(extMethods.withoutRules, stream)
        }

        logger.info { "Wrote external methods without rules to $withoutRulesPath (${extMethods.withoutRules.size} entries)" }

        withRulesPath.outputStream().use { stream ->
            skippedMethodsYaml.encodeToStream(extMethods.withRules, stream)
        }

        logger.info { "Wrote external methods with rules to $withRulesPath (${extMethods.withRules.size} entries)" }
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        private val skippedMethodsYaml = Yaml(
            configuration = YamlConfiguration(encodeDefaults = true)
        )

        private val approximationConfigCombinationOptions = CombinationOptions(
            entryPoint = CombinationMode.IGNORE,
            source = CombinationMode.IGNORE,
            sink = CombinationMode.IGNORE,
            cleaner = CombinationMode.IGNORE,
            passThrough = CombinationMode.OVERRIDE,
        )
    }
}
