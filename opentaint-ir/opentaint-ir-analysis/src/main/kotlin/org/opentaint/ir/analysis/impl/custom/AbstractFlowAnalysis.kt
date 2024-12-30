package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.cfg.JIRBytecodeGraph

abstract class AbstractFlowAnalysis<NODE, T>(override val graph: JIRBytecodeGraph<NODE>) : FlowAnalysis<NODE, T> {

    override fun newEntryFlow(): T = newFlow()

    protected open fun merge(successor: NODE, income1: T, income2: T, outcome: T) {
        merge(income1, income2, outcome)
    }

    open fun ins(s: NODE): T? {
        return ins[s]
    }

    protected fun mergeInto(successor: NODE, input: T, incoming: T) {
        val tmp = newFlow()
        merge(successor, input, incoming, tmp)
        copy(tmp, input)
    }
}
