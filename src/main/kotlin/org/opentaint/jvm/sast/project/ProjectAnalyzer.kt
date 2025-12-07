package org.opentaint.jvm.sast.project

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.TaintConfiguration
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyAnalysisEndSinksForEntryPoints
import org.opentaint.dataflow.jvm.util.JIRSarifTraits
import org.opentaint.dataflow.sarif.SourceFileResolver
import org.opentaint.jvm.sast.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer.DebugOptions
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.jvm.sast.sarif.SarifGenerator
import org.opentaint.jvm.sast.se.api.SastSeAnalyzer
import org.opentaint.jvm.sast.util.loadDefaultConfig
import org.opentaint.project.Project
import org.opentaint.semgrep.pattern.AbstractSemgrepError
import org.opentaint.semgrep.pattern.SemgrepFileErrors
import org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.semgrep.pattern.loadSemgrepRule
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.time.Duration

class ProjectAnalyzer(
    private val project: Project,
    private val projectPackage: String?,
    private val resultDir: Path,
    private val customConfig: Path?,
    private val semgrepRuleSet: Path?,
    private val semgrepRuleLoadErrors: Path?,
    private val cwe: List<Int>,
    private val useSymbolicExecution: Boolean,
    private val symbolicExecutionTimeout: Duration,
    private val ifdsAnalysisTimeout: Duration,
    private val ifdsApMode: ApMode,
    private val projectKind: ProjectKind,
    private val storeSummaries: Boolean,
    private val debugOptions: DebugOptions
) {
    fun analyze() {
        val projectAnalysisContext = initializeProjectAnalysisContext(
            project, projectPackage, projectKind,
            summariesApMode = ifdsApMode.takeIf { storeSummaries }
        )

        projectAnalysisContext.use {
            val entryPoints = it.selectProjectEntryPoints()
            it.runAnalyzer(entryPoints)
        }
    }

    private fun loadTaintConfig(): TaintRulesProvider {
        if (semgrepRuleSet != null) {
            check(customConfig == null) { "Unsupported custom config" }
            return loadSemgrepRules(semgrepRuleSet, semgrepRuleLoadErrors)
        }

        val defaultConfig = TaintConfiguration()
        defaultConfig.loadConfig(loadDefaultConfig())
        val customConfig = customConfig?.let { cfg ->
            cfg.inputStream().use { cfgStream ->
                TaintConfiguration().apply { loadConfig(loadSerializedTaintConfig(cfgStream)) }
            }
        }

        val defaultRules = JIRTaintRulesProvider(defaultConfig)
        if (customConfig == null) return defaultRules

        val customRules = JIRTaintRulesProvider(customConfig)

        return JIRCombinedTaintRulesProvider(defaultRules, customRules)
    }

    private fun loadSemgrepRules(semgrepRulesPath: Path, semgrepRuleLoadErrors: Path?): TaintRulesProvider {
        val semgrepFilesErrors: ArrayList<AbstractSemgrepError> = arrayListOf()
        val semgrepRules = parseSemgrepRules(semgrepRulesPath, semgrepFilesErrors)
        if (semgrepRuleLoadErrors != null) {
            runCatching {
                val prettyJson = Json {
                    prettyPrint = true
                }
                semgrepRuleLoadErrors.outputStream().bufferedWriter().use { writer ->
                    writer.write(prettyJson.encodeToString(semgrepFilesErrors))
                }
                logger.info { "Wrote semgrep load errors to $semgrepRuleLoadErrors" }
            }.onFailure { ex ->
                logger.error(ex) { "Failed to write semgrep load errors to $semgrepRuleLoadErrors: ${ex.message}" }
            }

        }

        val defaultRules = loadDefaultConfig()
        val defaultPassRules = SerializedTaintConfig(passThrough = defaultRules.passThrough)

        val config = TaintConfiguration()
        config.loadConfig(defaultPassRules)
        semgrepRules.forEach { config.loadSemgrepRule(it) }

        return JIRTaintRulesProvider(config)
    }

    private fun parseSemgrepRules(
        semgrepRulesPath: Path,
        semgrepFilesError: ArrayList<AbstractSemgrepError>
    ): List<TaintRuleFromSemgrep> {
        val rules = mutableListOf<TaintRuleFromSemgrep>()
        val loader = SemgrepRuleLoader()
        val ruleExtensions = arrayOf("yaml", "yml")
        semgrepRulesPath.walk().filter { it.extension in ruleExtensions }.forEach { rulePath ->
            val ruleName =
                if (semgrepRulesPath.isAbsolute)
                    semgrepRulesPath.resolve(rulePath).relativeTo(semgrepRulesPath.root)
                else
                    rulePath.relativeTo(semgrepRulesPath)

            val ruleText = rulePath.readText()

            val semgrepFileErrors = SemgrepFileErrors(ruleName.toString())
            semgrepFilesError += semgrepFileErrors
            rules += loader.loadRuleSet(
                ruleText,
                ruleName.toString(),
                semgrepFileErrors
            )
        }
        return rules
    }

    private fun ProjectAnalysisContext.runAnalyzer(entryPoints: List<JIRMethod>) {
        val summarySerializationContext = JIRSummarySerializationContext(cp)

        JIRTaintAnalyzer(
            cp, loadTaintConfig().applyAnalysisEndSinksForEntryPoints(entryPoints.toHashSet()),
            projectLocations = projectClasses.projectLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
            symbolicExecutionEnabled = useSymbolicExecution,
            analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
            summarySerializationContext = summarySerializationContext,
            storeSummaries = storeSummaries,
            debugOptions = debugOptions
        ).use { analyzer ->
            val sourcesResolver = JIRSourceFileResolver(
                project.sourceRoot,
                projectClasses.locationProjectModules.mapValues { (_, module) -> module.moduleSourceRoot }
            )

            logger.info { "Start IFDS analysis for project: ${project.sourceRoot}" }
            val traces = analyzer.analyzeWithIfds(entryPoints)
            logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

            (resultDir / "report-ifds.sarif").outputStream().use {
                generateSarifReportFromTraces(it, sourcesResolver, traces)
            }

            logger.info { "Finish IFDS analysis report for project: ${project.sourceRoot}" }

            if (!useSymbolicExecution) return

            val seAnalyzer = SastSeAnalyzer.createSeEngine<TaintAnalysisUnitRunnerManager, VulnerabilityWithTrace>()
                ?: return

            logger.info { "Start SE for project: ${project.sourceRoot}" }
            val verifiedTraces = seAnalyzer.analyzeTraces(
                cp, projectClasses.projectLocations, analyzer.ifdsEngine,
                traces, symbolicExecutionTimeout
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
        sourceFileResolver: org.opentaint.dataflow.sarif.SourceFileResolver<CommonInst>,
        traces: List<VulnerabilityWithTrace>
    ) {
        val generator = SarifGenerator(sourceFileResolver, JIRSarifTraits(cp))
        generator.generateSarif(output, traces.asSequence())
        logger.info { "Sarif trace generation stats: ${generator.traceGenerationStats}" }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
