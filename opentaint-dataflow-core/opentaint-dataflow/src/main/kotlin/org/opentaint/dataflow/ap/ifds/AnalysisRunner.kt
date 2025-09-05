package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface AnalysisRunner {
    val graph: ApplicationGraph<CommonMethod, CommonInst>
    val taintConfiguration: TaintRulesProvider
    val sinkTracker: TaintSinkTracker
    val apManager: ApManager
    val languageManager: LanguageManager
    val manager: TaintAnalysisUnitRunnerManager
    val methodCallResolver: MethodCallResolver

    fun enqueueMethodAnalyzer(analyzer: MethodAnalyzer)
    fun addNewSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>)
    fun addNewSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToZero, methodEntryPoint: MethodEntryPoint)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.FactToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
}
