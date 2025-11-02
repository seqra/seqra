package org.opentaint.dataflow.jvm.ap.ifds.trace

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.trace.MethodStartPrecondition
import org.opentaint.dataflow.ap.ifds.access.ApManager
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.dataflow.jvm.ap.ifds.analysis.JIRMethodAnalysisContext
import org.opentaint.dataflow.jvm.util.JIRTraits
import org.opentaint.util.Maybe

class JIRMethodStartPrecondition(
    private val apManager: ApManager,
    private val context: JIRMethodAnalysisContext,
    private val traits: JIRTraits,
) : MethodStartPrecondition {
    override fun factPrecondition(
        fact: InitialFactAp
    ): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>> =
        JIRMethodCallPrecondition.getEntryPointPrecondition(
            apManager, context.taint.taintConfig, context.methodEntryPoint.method as JIRMethod, traits, fact
        )
}
