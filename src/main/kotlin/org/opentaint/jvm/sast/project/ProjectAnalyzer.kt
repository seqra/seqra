package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.dataflow.configuration.jvm.serialized.TaintConfiguration
import org.opentaint.dataflow.configuration.jvm.serialized.loadSerializedTaintConfig
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ap.ifds.taint.applyAnalysisEndSinksForEntryPoints
import org.opentaint.org.opentaint.semgrep.pattern.SemgrepRuleLoader
import org.opentaint.org.opentaint.semgrep.pattern.TaintRuleFromSemgrep
import org.opentaint.org.opentaint.semgrep.pattern.loadSemgrepRule
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
    project: Project,
    projectPackage: String?,
    private val resultDir: Path,
    private val customConfig: Path?,
    private val semgrepRuleSet: Path?,
    private val cwe: List<Int>,
    private val useSymbolicExecution: Boolean,
    private val symbolicExecutionTimeout: Duration,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    projectKind: ProjectKind,
    private val storeSummaries: Boolean,
    debugOptions: DebugOptions
) : AbstractProjectAnalyzer(project, projectPackage, ifdsAnalysisTimeout, ifdsApMode, projectKind, debugOptions) {
    private fun loadTaintConfig(): TaintRulesProvider {
        if (semgrepRuleSet != null) {
            check(customConfig == null) { "Unsupported custom config" }
            return loadSemgrepRules(semgrepRuleSet)
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

    private fun loadSemgrepRules(semgrepRulesPath: Path): TaintRulesProvider {
        val semgrepRules = parseSemgrepRules(semgrepRulesPath)

        val defaultRules = loadDefaultConfig()
        val defaultPassRules = SerializedTaintConfig(passThrough = defaultRules.passThrough)

        val config = TaintConfiguration()
        config.loadConfig(defaultPassRules)
        semgrepRules.forEach { config.loadSemgrepRule(it) }

        return JIRTaintRulesProvider(config)
    }

    private fun parseSemgrepRules(semgrepRulesPath: Path): List<TaintRuleFromSemgrep> {
        val rules = mutableListOf<TaintRuleFromSemgrep>()
        val loader = SemgrepRuleLoader()
        val ruleExtensions = arrayOf("yaml", "yml")
        semgrepRulesPath.walk().filter { it.extension in ruleExtensions }.forEach { rulePath ->
            val ruleName = rulePath.relativeTo(semgrepRulesPath)
            val ruleText = rulePath.readText()
            rules += loader.loadRuleSet(ruleText, ruleName.toString())
        }
        return rules
    }

    override fun runAnalyzer(entryPoints: List<JIRMethod>) {
        val summarySerializationContext = JIRSummarySerializationContext(cp)

        JIRTaintAnalyzer(
            cp, loadTaintConfig().applyAnalysisEndSinksForEntryPoints(entryPoints.toHashSet()),
            projectLocations = projectClasses.projectLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
            opentaintTimeout = symbolicExecutionTimeout,
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
            analyzer.analyzeWithIfds(entryPoints)
            logger.info { "Finish IFDS analysis for project: ${project.sourceRoot}" }

            (resultDir / "report-ifds.sarif").outputStream().use {
                analyzer.generateSarifReportFromIfdsTraces(it, sourcesResolver)
            }

            logger.info { "Finish IFDS analysis report for project: ${project.sourceRoot}" }

            if (!useSymbolicExecution) return

            logger.info { "Start Opentaint for project: ${project.sourceRoot}" }
            analyzer.filterIfdsTracesWithOpentaint()
            logger.info { "Finish Opentaint for project: ${project.sourceRoot}" }

            (resultDir / "report-opentaint.sarif").outputStream().use {
                analyzer.generateSarifReportFromVerifiedIfdsTraces(it, sourcesResolver)
            }

            logger.info { "Finish Opentaint report for project: ${project.sourceRoot}" }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}