package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.cfg.JIRGraph

abstract class ForwardFlowAnalysis<T>(graph: JIRGraph) : FlowAnalysisImpl<T>(graph) {

    override val isForward = true

    override fun run() {
        runAnalysis(FlowAnalysisDirection.FORWARD, ins, outs)
    }
}