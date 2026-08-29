package org.opentaint.go.sast.project

import mu.KLogging
import org.opentaint.common.sast.ProjectAnalysisResults
import org.opentaint.common.sast.ProjectAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.dataflow.ap.ifds.TaintAnalysisUnitRunnerManager
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ap.ifds.trace.VulnerabilityWithTrace
import org.opentaint.dataflow.configuration.go.serialized.GoConfigurationLoader
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedItem
import org.opentaint.dataflow.configuration.go.serialized.GoSerializedTaintConfig
import org.opentaint.go.config.GoDefaultConfigLoader
import org.opentaint.go.sast.dataflow.GoTaintAnalyzer
import org.opentaint.go.sast.dataflow.GoUnitResolver
import org.opentaint.go.sast.sarif.GoDebugFactReachabilitySarifGenerator
import org.opentaint.go.sast.sarif.GoSarifGenerator
import org.opentaint.ir.go.api.GoIRFunction
import org.opentaint.ir.go.client.GoIRClient
import org.opentaint.ir.go.inst.GoIRInst
import org.opentaint.project.GoProject
import org.opentaint.semgrep.go.pattern.conversion.GoLanguageStrategy

class GoProjectAnalyzer(
    project: GoProject,
    results: ProjectAnalysisResults,
    goOptions: GoProjectAnalysisOptions = GoProjectAnalysisOptions(),
) : ProjectAnalyzer<AnalysisCtx, GoProject, GoIRFunction, GoIRInst, GoSerializedItem, GoSerializedTaintConfig>(
    project,
    results,
    goOptions.common
) {
    private val modelPaths = goOptions.modelPaths
    private val useDefaultModels = goOptions.useDefaultModels

    override fun initializeProjectAnalysisContext(): AnalysisCtx {
        val client = GoIRClient()
        return AnalysisCtx(project, client, modelPaths, useDefaultModels)
    }

    override fun AnalysisCtx.selectProjectEntryPoints(): List<GoIRFunction> {
        val all = cp.packages.values
            .filter { it.isProject }
            .flatMap { it.functions }
            .filter { it.hasBody && !it.isSynthetic && it.parent == null }

        val selector = options.debugOptions?.debugRunAnalysisOnSelectedEntryPoints
        val filtered = filterEntryPoints(all, selector)
        if (selector != null && selector != "*" && filtered.isEmpty()) {
            logger.warn { "Entry-point selector matched no project function: '$selector'" }
        }
        return filtered
    }

    override fun ruleStrategy() = GoLanguageStrategy()
    override fun defaultConfigLoader() = GoDefaultConfigLoader
    override fun configLoader() = GoConfigurationLoader()

    override fun AnalysisCtx.createAnalyzer(
        externalMethodTracker: ExternalMethodTracker?,
        rules: PreloadedRules<GoSerializedItem, GoSerializedTaintConfig>
    ): TaintAnalyzer<GoIRFunction, GoIRInst> {
        val rulesProvider = rules.loadRules()
        return GoTaintAnalyzer(
            cp,
            rulesProvider,
            GoUnitResolver(),
            options.taintAnalyzerOptions(),
            externalMethodTracker
        )
    }

    override fun AnalysisCtx.sarifGenerator() =
        GoSarifGenerator(options.sarifGenerationOptions, project.sourceRoot())

    override fun AnalysisCtx.debugSarifGenerator() =
        GoDebugFactReachabilitySarifGenerator(options.sarifGenerationOptions, project.sourceRoot())

    override fun AnalysisCtx.runSeAnalyzer(
        engine: TaintAnalysisUnitRunnerManager,
        traces: List<VulnerabilityWithTrace>
    ): List<VulnerabilityWithTrace> {
        TODO("Not yet implemented")
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}

internal fun filterEntryPoints(
    entryPoints: List<GoIRFunction>,
    selector: String?,
): List<GoIRFunction> {
    if (selector == null || selector == "*") return entryPoints
    return entryPoints.filter { it.fullName == selector }
}
