package org.opentaint.opentaint-ir.impl.analysis

import org.opentaint.opentaint-ir.api.cfg.JIRGraph

abstract class BackwardFlowAnalysis<T> (graph: JIRGraph) : FlowAnalysisImpl<T>(graph) {

    override val isForward: Boolean = false

    override fun run() {
        runAnalysis(FlowAnalysisDirection.BACKWARD, outs, ins)
    }
}