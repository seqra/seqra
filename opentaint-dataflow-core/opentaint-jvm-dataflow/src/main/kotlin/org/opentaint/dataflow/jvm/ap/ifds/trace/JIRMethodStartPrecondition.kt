package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ap.ifds.trace.TaintRulePrecondition
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.taint.TaintRulesProvider

class JIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
) : MethodStartPrecondition {
    override fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source> =
        JIRMethodCallPrecondition.getEntryPointPrecondition(
            apManager,
            context.taint.taintConfig as TaintRulesProvider,
            context.factTypeChecker,
            context.methodEntryPoint.method as JIRMethod,
            fact
        )
}
