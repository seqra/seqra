package org.opentaint.jvm.sast.project

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.taint.configuration.v2.TaintConfiguration
import org.opentaint.jvm.sast.dataflow.JIRCombinedTaintRulesProvider
import org.opentaint.jvm.sast.dataflow.JIRSourceFileResolver
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.dataflow.JIRTaintRulesProvider
import org.opentaint.dataflow.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.ap.ifds.access.ApMode
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.time.Duration

class ProjectAnalyzer(
    project: Project,
    projectPackage: String?,
    private val resultDir: Path,
    private val customConfig: Path?,
    private val cwe: List<Int>,
    private val useSymbolicExecution: Boolean,
    private val symbolicExecutionTimeout: Duration,
    ifdsAnalysisTimeout: Duration,
    ifdsApMode: ApMode,
    projectKind: ProjectKind,
    private val storeSummaries: Boolean
) : AbstractProjectAnalyzer(project, projectPackage, ifdsAnalysisTimeout, ifdsApMode, projectKind) {
    private fun loadTaintConfig(): TaintRulesProvider {
        val defaultConfig = TaintConfiguration().also { loadDefaultConfig(it) }
        val customConfig = customConfig?.let { cfg ->
            cfg.inputStream().use { cfgStream ->
                TaintConfiguration().apply { loadConfig(cfgStream) }
            }
        }

        val defaultRules = JIRTaintRulesProvider(defaultConfig)
        if (customConfig == null) return defaultRules

        val customRules = JIRTaintRulesProvider(customConfig)

        return JIRCombinedTaintRulesProvider(defaultRules, customRules)
    }

    override fun runAnalyzer(entryPoints: List<JIRMethod>) {
        val summarySerializationContext = JIRSummarySerializationContext(cp)

        val analyzer = JIRTaintAnalyzer(
            cp, loadTaintConfig(),
            projectLocations = projectClasses.projectLocations,
            ifdsTimeout = ifdsAnalysisTimeout,
            ifdsApMode = ifdsApMode,
            opentaintTimeout = symbolicExecutionTimeout,
            symbolicExecutionEnabled = useSymbolicExecution,
            analysisCwe = cwe.takeIf { it.isNotEmpty() }?.toSet(),
            summarySerializationContext = summarySerializationContext,
            storeSummaries = storeSummaries
        )

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
        analyzer.filterIfdsTracesWithOpentaint(entryPoints)
        logger.info { "Finish Opentaint for project: ${project.sourceRoot}" }

        (resultDir / "report-opentaint.sarif").outputStream().use {
            analyzer.generateSarifReportFromVerifiedIfdsTraces(it, sourcesResolver)
        }

        logger.info { "Finish Opentaint report for project: ${project.sourceRoot}" }
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}