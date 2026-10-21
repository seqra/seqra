package org.opentaint.dataflow.python.analysis

import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintAnalysisManager
import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.python.alias.PIRLocalAliasAnalysis
import org.opentaint.dataflow.python.rules.PIRTaintAnalysisContext
import org.opentaint.dataflow.python.rules.PIRTaintRulesProvider
import org.opentaint.ir.api.python.PIRFunction

class PIRMethodAnalysisContext(
    val analysisManager: PIRAnalysisManager,
    override val methodEntryPoint: MethodEntryPoint,
    val method: PIRFunction,
    val taint: PIRTaintAnalysisContext,
    val aliasAnalysis: PIRLocalAliasAnalysis?,
) : MethodAnalysisContext {

    val phase: TaintAnalysisManager.Phase get() = analysisManager.phase

    val methodCallFactMapper: PIRMethodCallFactMapper
        get() = PIRMethodCallFactMapper
}
