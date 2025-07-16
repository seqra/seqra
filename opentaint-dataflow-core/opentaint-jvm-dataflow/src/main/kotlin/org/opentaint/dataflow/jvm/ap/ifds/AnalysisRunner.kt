package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver

interface AnalysisRunner {
    val graph: JIRApplicationGraph
    val callResolver: JIRCallResolver
    val unitResolver: JIRUnitResolver
    val taintConfiguration: TaintRulesProvider
    val factTypeChecker: FactTypeChecker
    val sinkTracker: TaintSinkTracker

    fun submitNewUnprocessedEdge(edge: Edge)
    fun addNewSummaryEdges(methodEntryPoint: MethodEntryPoint, edges: List<Edge>)
    fun addNewSinkRequirement(methodEntryPoint: MethodEntryPoint, requirement: Fact.TaintedPath)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToZero, methodEntryPoint: MethodEntryPoint)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.FactToFact, methodEntryPoint: MethodEntryPoint, methodFactBase: AccessPathBase)
}
