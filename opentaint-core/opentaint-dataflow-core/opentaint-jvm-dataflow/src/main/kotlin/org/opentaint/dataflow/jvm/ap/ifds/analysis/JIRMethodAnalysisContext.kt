package org.opentaint.dataflow.jvm.ap.ifds.analysis

import org.opentaint.dataflow.ap.ifds.analysis.MethodAnalysisContext
import org.opentaint.dataflow.ap.ifds.analysis.MethodCallFactMapper
import org.opentaint.dataflow.ap.ifds.MethodEntryPoint
import org.opentaint.dataflow.ap.ifds.TaintMarkAccessor
import org.opentaint.dataflow.ap.ifds.taint.TaintAnalysisContext
import org.opentaint.dataflow.jvm.ap.ifds.JIRFactTypeChecker
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalAliasAnalysis
import org.opentaint.dataflow.jvm.ap.ifds.JIRLocalVariableReachability
import org.opentaint.dataflow.jvm.ap.ifds.JIRMethodCallFactMapper

class JIRMethodAnalysisContext(
    override val methodEntryPoint: MethodEntryPoint,
    val factTypeChecker: JIRFactTypeChecker,
    val localVariableReachability: JIRLocalVariableReachability,
    val aliasAnalysis: JIRLocalAliasAnalysis?,
    val taint: TaintAnalysisContext,
) : MethodAnalysisContext {
    override val methodCallFactMapper: MethodCallFactMapper
        get() = JIRMethodCallFactMapper

    val taintMarksAssignedOnMethodEnter = hashSetOf<TaintMarkAccessor>()
}
