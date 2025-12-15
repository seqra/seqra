package org.opentaint.dataflow.ap.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.FinalFactAp
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.analysis.AnalysisManager
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallResolver

interface AnalysisRunner {
    val graph: ApplicationGraph<CommonMethod, CommonInst>
    val apManager: ApManager
    val analysisManager: AnalysisManager
    val manager: AnalysisUnitRunnerManager
    val methodCallResolver: MethodCallResolver

    fun enqueueMethodAnalyzer(analyzer: MethodAnalyzer)
    fun addNewSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>)
    fun getPrecalculatedSummaries(methodEntryPoint: MethodEntryPoint): Pair<List<Edge>, List<InitialFactAp>>?
    fun addNewSideEffectRequirement(methodEntryPoint: MethodEntryPoint, requirements: List<InitialFactAp>)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToZero, methodEntryPoint: MethodEntryPoint)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.FactToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.NDFactToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun submitExternalInitialZeroFact(methodEntryPoint: MethodEntryPoint)
    fun submitExternalInitialFact(methodEntryPoint: MethodEntryPoint, factAp: FinalFactAp)
}
