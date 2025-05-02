package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.core.cfg.Graph

abstract class BackwardFlowAnalysis<NODE, T>(graph: Graph<NODE>) : FlowAnalysisImpl<NODE, T>(graph) {

    override val isForward: Boolean = false

    override fun run() {
        runAnalysis(FlowAnalysisDirection.BACKWARD, outs, ins)
    }
}