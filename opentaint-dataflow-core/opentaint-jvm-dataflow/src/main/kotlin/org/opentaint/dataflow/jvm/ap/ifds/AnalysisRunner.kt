package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.dataflow.jvm.ifds.JIRUnitResolver

interface AnalysisRunner {
    val graph: JIRApplicationGraph
    val unitResolver: JIRUnitResolver
    val taintConfiguration: TaintRulesProvider
    val factTypeChecker: FactTypeChecker
    val sinkTracker: TaintSinkTracker

    fun submitNewUnprocessedEdge(edge: Edge)
    fun addNewSummaryEdges(initialStatement: JIRInst, edges: List<Edge>)
    fun addNewSinkRequirement(initialStatement: JIRInst, requirement: Fact.TaintedPath)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToZero, methodEntryPoint: JIRInst)
    fun subscribeOnMethodSummaries(edge: Edge.ZeroToFact, methodEntryPoint: JIRInst, methodFactBase: AccessPathBase)
    fun subscribeOnMethodSummaries(edge: Edge.FactToFact, methodEntryPoint: JIRInst, methodFactBase: AccessPathBase)
}
