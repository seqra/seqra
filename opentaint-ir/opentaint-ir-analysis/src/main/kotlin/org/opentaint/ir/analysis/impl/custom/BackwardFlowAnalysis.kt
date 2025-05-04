package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.core.cfg.ControlFlowGraph

abstract class BackwardFlowAnalysis<NODE, T>(graph: ControlFlowGraph<NODE>) : FlowAnalysisImpl<NODE, T>(graph) {

    override val isForward: Boolean = false

    override fun run() {
        runAnalysis(FlowAnalysisDirection.BACKWARD, outs, ins)
    }
}