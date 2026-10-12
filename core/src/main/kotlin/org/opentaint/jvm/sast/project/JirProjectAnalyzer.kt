package org.opentaint.jvm.sast.project

import org.opentaint.common.sast.ProjectAnalysisResults
import org.opentaint.common.sast.ProjectAnalysisStatus
import org.opentaint.common.sast.ProjectAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.sarif.DebugFactReachabilitySarifGenerator
import org.opentaint.common.sast.sarif.SarifGenerator
import org.opentaint.config.JavaDefaultConfigLoader
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.bidi.driver.IncreasingLimits
import org.opentaint.dataflow.configuration.jvm.serialized.JavaConfigurationLoader
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedItem
import org.opentaint.dataflow.configuration.jvm.serialized.SerializedTaintConfig
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.sast.dataflow.JIRTaintAnalyzer
import org.opentaint.jvm.sast.project.rules.analysisConfig
import org.opentaint.jvm.sast.project.rules.loadTaintConfig
import org.opentaint.jvm.sast.sarif.JIRSarifTraits
import org.opentaint.jvm.sast.sarif.JirDebugFactReachabilitySarifGenerator
import org.opentaint.jvm.sast.sarif.JirSarifGenerator
import org.opentaint.jvm.sast.se.api.SastSeAnalyzer
import org.opentaint.jvm.sast.util.locationChecker
import org.opentaint.project.JavaProject
import org.opentaint.semgrep.pattern.conversion.JavaLanguageStrategy

class JirProjectAnalyzer(
    project: JavaProject,
    results: ProjectAnalysisResults,
    private val jirOptions: ProjectAnalysisOptions,
): ProjectAnalyzer<ProjectAnalysisContext, JavaProject, JIRMethod, JIRInst, SerializedItem, SerializedTaintConfig>(
    project, results, jirOptions.common
) {
    override fun analyze(): ProjectAnalysisStatus {
        if (project.modules.isEmpty()) return ProjectAnalysisStatus.OK
        return super.analyze()
    }

    override fun initializeProjectAnalysisContext() =
        initializeProjectAnalysisContext(project, jirOptions)

    override fun ProjectAnalysisContext.selectProjectEntryPoints() =
        selectProjectEntryPoints(jirOptions)

    override fun ruleStrategy() = JavaLanguageStrategy()
    override fun defaultConfigLoader() = JavaDefaultConfigLoader
    override fun configLoader() = JavaConfigurationLoader()

    override fun ProjectAnalysisContext.createAnalyzer(
        externalMethodTracker: ExternalMethodTracker?,
        rules: PreloadedRules<SerializedItem, SerializedTaintConfig>
    ): TaintAnalyzer<JIRMethod, JIRInst> {
        val loadedConfig = loadTaintConfig(cp, rules)
        val config = analysisConfig(loadedConfig)
        return JIRTaintAnalyzer(
            cp, config,
            projectClasses = projectClasses.locationChecker(),
            options = options.taintAnalyzerOptions(),
            jirOptions = JIRTaintAnalyzer.JIRAnalysisOptions(jirOptions.disableDefaultGetModel),
            externalMethodTracker = externalMethodTracker,
            bidiPolicy = if (options.ifdsBidirectional) IncreasingLimits else null,
        )
    }

    override fun ProjectAnalysisContext.runSeAnalyzer(
        engine: TaintAnalysisUnitRunnerManager,
        traces: List<VulnerabilityWithTrace>
    ): List<VulnerabilityWithTrace> {
        val seAnalyzer = SastSeAnalyzer.createSeEngine<TaintAnalysisUnitRunnerManager, VulnerabilityWithTrace>()
            ?: return traces

        val seOptions = SastSeAnalyzer.SeOptions(
            options.symbolicExecutionTimeout, options.experimentalAAInterProcCallDepth
        )
        return seAnalyzer.analyzeTraces(
            cp, projectClasses.projectLocationsUnsafe, engine,
            traces, seOptions
        )
    }

    override fun ProjectAnalysisContext.sarifGenerator(): SarifGenerator<*> {
        val sourcesResolver = project.sourceResolver(projectClasses)
        return JirSarifGenerator(
            options.sarifGenerationOptions, project.sourceRoot,
            sourcesResolver, JIRSarifTraits(cp)
        )
    }

    override fun ProjectAnalysisContext.debugSarifGenerator(): DebugFactReachabilitySarifGenerator<*> {
        val sourcesResolver = project.sourceResolver(projectClasses)
        return JirDebugFactReachabilitySarifGenerator(
            options.sarifGenerationOptions,
            sourcesResolver, JIRSarifTraits(cp)
        )
    }
}
