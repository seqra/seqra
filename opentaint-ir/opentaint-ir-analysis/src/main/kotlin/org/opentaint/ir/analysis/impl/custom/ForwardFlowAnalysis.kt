package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.core.cfg.JIRBytecodeGraph

abstract class ForwardFlowAnalysis<NODE, T>(graph: JIRBytecodeGraph<NODE>) : FlowAnalysisImpl<NODE, T>(graph) {

    override val isForward = true

    override fun run() {
        runAnalysis(FlowAnalysisDirection.FORWARD, ins, outs)
    }
}