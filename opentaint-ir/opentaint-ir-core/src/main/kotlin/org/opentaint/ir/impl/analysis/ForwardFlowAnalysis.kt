package org.opentaint.opentaint-ir.impl.analysis

import org.opentaint.opentaint-ir.api.cfg.JIRGraph

abstract class ForwardFlowAnalysis<T>(graph: JIRGraph) : FlowAnalysisImpl<T>(graph) {

    override val isForward = true

    override fun run() {
        runAnalysis(FlowAnalysisDirection.FORWARD, ins, outs)
    }
}