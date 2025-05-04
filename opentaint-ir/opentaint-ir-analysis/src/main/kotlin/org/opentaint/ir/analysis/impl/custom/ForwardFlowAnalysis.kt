package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.core.cfg.ControlFlowGraph
import org.opentaint.ir.api.core.cfg.Graph

abstract class ForwardFlowAnalysis<NODE, T>(graph: ControlFlowGraph<NODE>) : FlowAnalysisImpl<NODE, T>(graph) {

    override val isForward = true

    override fun run() {
        runAnalysis(FlowAnalysisDirection.FORWARD, ins, outs)
    }
}