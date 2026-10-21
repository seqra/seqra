package org.opentaint.python.sast.dataflow

import org.opentaint.common.sast.dataflow.TaintAnalyzer
import org.opentaint.common.sast.dataflow.TaintAnalyzerOptions
import org.opentaint.dataflow.ap.ifds.taint.ExternalMethodTracker
import org.opentaint.dataflow.ifds.UnitResolver
import org.opentaint.dataflow.python.analysis.PIRAnalysisManager
import org.opentaint.dataflow.python.graph.PIRApplicationGraph
import org.opentaint.dataflow.python.rules.PIRTaintRulesProvider
import org.opentaint.ir.api.python.PIRClasspath
import org.opentaint.ir.api.python.PIRFunction
import org.opentaint.ir.api.python.PIRInstruction

class PIRTaintAnalyzer(
    private val cp: PIRClasspath,
    private val taintConfig: PIRTaintRulesProvider,
    private val unitResolver: UnitResolver<PIRFunction>,
    options: TaintAnalyzerOptions,
    externalMethodTracker: ExternalMethodTracker? = null,
) : TaintAnalyzer<PIRFunction, PIRInstruction>(options, externalMethodTracker) {
    override fun analysisGraph() = PIRApplicationGraph(cp)
    override fun analysisManager() = PIRAnalysisManager(cp, taintConfig, externalMethodTracker = externalMethodTracker)
    override fun unitResolver(): UnitResolver<PIRFunction> = unitResolver
}
