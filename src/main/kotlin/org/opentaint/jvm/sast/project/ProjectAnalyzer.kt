package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.util.JIRSarifTraits
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.rules.TaintConfiguration
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadSemgrepRules
import org.opentaint.jvm.sast.project.rules.semgrepRulesWithDefaultConfig
import org.opentaint.jvm.sast.sarif.DebugFactReachabilitySarifGenerator
import org.opentaint.jvm.sast.sarif.SarifGenerator
import org.opentaint.jvm.sast.se.api.SastSeAnalyzer
import org.opentaint.jvm.sast.util.loadDefaultConfig
import org.opentaint.project.Project
import org.opentaint.semgrep.pattern.RuleMetadata
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class ProjectAnalyzer(
    private val project: Project,
    private val resultDir: Path,
    private val options: ProjectAnalysisOptions,
) {
    private val ruleMetadatas = mutableListOf<RuleMetadata>()

    fun analyze() {
        val projectAnalysisContext = initializeProjectAnalysisContext(project, options)

        projectAnalysisContext.use {
            val entryPoints = it.selectProjectEntryPoints()
            it.runAnalyzer(entryPoints)
        }
    }

    private fun loadTaintConfig(cp: JIRClasspath): TaintRulesProvider {
        if (options.semgrepRuleSet.isNotEmpty()) {
            check(options.customConfig == null) { "Unsupported custom config" }
            return loadConfigFromSemgrepRules(cp)
        }

        val defaultConfig = TaintConfiguration(cp)
        defaultConfig.loadConfig(loadDefaultConfig())
        val customConfig = options.customConfig?.let { cfg ->
            cfg.inputStream().use { cfgStream ->
                TaintConfiguration(cp).apply { loadConfig(loadSerializedTaintConfig(cfgStream)) }
            }
        }

        val defaultRules = JIRTaintRulesProvider(defaultConfig)
        if (customConfig == null) return defaultRules

        val customRules = JIRTaintRulesProvider(customConfig)

        return JIRCombinedTaintRulesProvider(defaultRules, customRules)
    }

    private fun loadConfigFromSemgrepRules(cp: JIRClasspath): TaintRulesProvider {
        val loadedRules = options.loadSemgrepRules()
        ruleMetadatas += loadedRules.rulesWithMeta.map { it.second }
        return loadedRules.rulesWithMeta.map { it.first }.semgrepRulesWithDefaultConfig(cp)
    }

    private fun ProjectAnalysisContext.runAnalyzer(entryPoints: List<JIRMethod>) {
        val summarySerializationContext = JIRSummarySerializationContext(cp)

        val loadedConfig = loadTaintConfig(cp)
        val config = analysisConfig(loadedConfig)

        JIRTaintAnalyzer(
            cp, config,
            projectClasses = { projectClasses.isProjectClass(it) },
            options = options.taintAnalyzerOptions(),
            summarySerializationContext = summarySerializationContext,
        ).use { analyzer ->
            val sourcesResolver = project.sourceResolver(projectClasses)

            logger.info { "Start IFDS analysis for project: ${project.sourceRoot}" }
            val traces = analyzer.analyzeWithIfds(entryPoints)
            logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

            (resultDir / options.sarifGenerationOptions.sarifFileName).outputStream().use {
                generateSarifReportFromTraces(it, sourcesResolver, traces)
            }

            if (options.debugOptions?.factReachabilitySarif == true) {
                val stmtsWithFact = analyzer.statementsWithFacts()
                (resultDir / "debug-ifds-fact-reachability.sarif").outputStream().use {
                    generateFactReachabilityReport(it, sourcesResolver, stmtsWithFact)
                }
            }

            logger.info { "Finish IFDS analysis report for project: ${project.sourceRoot}" }

            if (!options.useSymbolicExecution) return

            val seAnalyzer = SastSeAnalyzer.createSeEngine<TaintAnalysisUnitRunnerManager, VulnerabilityWithTrace>()
                ?: return

            logger.info { "Start SE for project: ${project.sourceRoot}" }
            val verifiedTraces = seAnalyzer.analyzeTraces(
                cp, projectClasses.projectLocationsUnsafe, analyzer.ifdsEngine,
                traces, options.symbolicExecutionTimeout
            )
            logger.info { "Finish SE for project: ${project.sourceRoot}" }

            (resultDir / "report-se.sarif").outputStream().use {
                generateSarifReportFromTraces(it, sourcesResolver, verifiedTraces)
            }

            logger.info { "Finish SE report for project: ${project.sourceRoot}" }
        }
    }

    private fun ProjectAnalysisContext.generateSarifReportFromTraces(
        output: OutputStream,
        sourceFileResolver: SourceFileResolver<CommonInst>,
        traces: List<VulnerabilityWithTrace>
    ) {
        val generator = SarifGenerator(
            options.sarifGenerationOptions, project.sourceRoot,
            sourceFileResolver, JIRSarifTraits(cp)
        )
        generator.generateSarif(output, traces.asSequence(), ruleMetadatas)
        logger.info { "Sarif trace generation stats: ${generator.traceGenerationStats}" }
    }

    private fun ProjectAnalysisContext.generateFactReachabilityReport(
        output: OutputStream,
        sourceFileResolver: SourceFileResolver<CommonInst>,
        reachableFacts: Map<CommonInst, Set<FinalFactAp>>,
    ) {
        val generator = DebugFactReachabilitySarifGenerator(
            options.sarifGenerationOptions,
            sourceFileResolver, JIRSarifTraits(cp)
        )
        generator.generateSarif(output, reachableFacts)
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
