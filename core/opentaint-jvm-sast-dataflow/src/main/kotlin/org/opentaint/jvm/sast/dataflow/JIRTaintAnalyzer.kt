package org.opentaint.jvm.sast.dataflow

import org.opentaint.dataflow.jvm.ap.ifds.taint.forBidirectionalCore
import kotlinx.coroutines.runBlocking
import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.bidi.driver.BidiEntry
import org.opentaint.dataflow.bidi.driver.IterationPolicy
import org.opentaint.dataflow.ifds.UnitType
import org.opentaint.dataflow.ifds.UnknownUnit
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRSafeApplicationGraph
import org.opentaint.dataflow.jvm.ap.ifds.JIRSummarySerializationContext
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaMethod
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRAnalysisManager
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodGetDefault
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver
import org.opentaint.dataflow.jvm.ifds.PackageUnit
import org.opentaint.dataflow.jvm.bidi.JIRBidiAnalysis
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.RegisteredLocation
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.packageName
import org.opentaint.ir.impl.features.usagesExt
import org.opentaint.jvm.graph.JApplicationGraphImpl
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.jvm.graph.JApplicationSingleExitGraph
import org.opentaint.jvm.sast.dataflow.DataFlowApproximationLoader.isApproximation
import org.opentaint.util.analysis.ApplicationGraph

class JIRTaintAnalyzer(
    val cp: JIRClasspath,
    val taintConfiguration: TaintRulesProvider,
    val projectClasses: ClassLocationChecker,
    options: TaintAnalyzerOptions,
    val jirOptions: JIRAnalysisOptions = JIRAnalysisOptions(),
    val analysisUnit: JIRUnitResolver = PackageUnitResolver(projectClasses),
    externalMethodTracker: ExternalMethodTracker? = null,
    val bidiPolicy: IterationPolicy? = null,
): TaintAnalyzer<JIRMethod, JIRInst>(options, externalMethodTracker) {
    override fun analysisGraph(): ApplicationGraph<JIRMethod, JIRInst> {
        val usages = runBlocking { cp.usagesExt() }
        val mainGraph = JApplicationGraphImpl(cp, usages)
        val tryBoundaryExceptionsGraph = JTryBoundaryExceptionsApplicationGraph(mainGraph)
        val singleExitGraph = JApplicationSingleExitGraph(tryBoundaryExceptionsGraph)
        return JIRSafeApplicationGraph(singleExitGraph)
    }

    data class JIRAnalysisOptions(
        val disableDefaultGetModel: Boolean = false,
    )

    private val analysisParams get() = JIRAnalysisManager.Params(
        defaultGetModel = configureDefaultModel(),
        aliasAnalysisParams = JIRLocalAliasAnalysis.Params(
            aliasAnalysisInterProcCallDepth = options.experimentalAAInterProcCallDepth
        )
    )

    private fun configureDefaultModel(): JIRMethodGetDefault? {
        if (jirOptions.disableDefaultGetModel) return null

        val config = object : JIRMethodGetDefault.Configuration {
            override fun enableDefaultPropagationForClass(cls: JIRClassOrInterface): Boolean =
                !projectClasses.isProjectClass(cls)
        }

        return JIRMethodGetDefault(config)
    }

    private val taintConfig: TaintRulesProvider by lazy {
        StringConcatRuleProvider(taintConfiguration).let { if (bidiPolicy != null) it.forBidirectionalCore() else it }
    }

    override fun analysisManager() =
        JIRAnalysisManager(cp, refManager, taintConfig, externalMethodTracker, analysisParams)

    override fun bidiEntry(): BidiEntry? = bidiPolicy?.let { policy ->
        JIRBidiAnalysis(cp, ifdsAnalysisGraph as JApplicationGraph, analysisUnit, taintConfig,
            analysisParams, policy, refManager)
    }

    override fun unitResolver() = analysisUnit

    override fun summarySerializationContext() = JIRSummarySerializationContext(cp)

    override fun coverageReportTool() = object : AnalyzerCoverageReportTool<JIRMethod, JIRClassOrInterface> {
        override fun includeInReport(method: JIRMethod): Boolean {
            val cls = method.enclosingClass
            if (cls is LambdaAnonymousClassFeature.JIRLambdaClass) return false
            return projectClasses.isProjectClass(cls)
        }

        override fun methodInstructionCount(method: JIRMethod): Int = method.instList.size
        override fun groupingUnit(method: JIRMethod): JIRClassOrInterface = method.enclosingClass
        override fun printUnit(key: JIRClassOrInterface): String = key.name
        override fun unitMethods(unit: JIRClassOrInterface): List<JIRMethod> = unit.declaredMethods
    }

    companion object {
        class PackageUnitResolver(private val projectLocations: ClassLocationChecker) : JIRUnitResolver {
            override fun resolve(method: JIRMethod): UnitType {
                if (!projectLocations.isProjectClass(method.enclosingClass) && !isApproximation(method) && method !is JIRLambdaMethod) {
                    return UnknownUnit
                }

                return PackageUnit(method.enclosingClass.packageName)
            }

            override fun locationIsUnknown(loc: RegisteredLocation): Boolean =
                !projectLocations.isProjectLocation(loc)
        }
    }
}
