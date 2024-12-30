package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst

abstract class AbstractFlowAnalysis<T>(override val graph: JIRGraph) : FlowAnalysis<T> {

    override fun newEntryFlow(): T = newFlow()

    protected open fun merge(successor: JIRInst, income1: T, income2: T, outcome: T) {
        merge(income1, income2, outcome)
    }

    open fun ins(s: JIRInst): T? {
        return ins[s]
    }

    protected fun mergeInto(successor: JIRInst, input: T, incoming: T) {
        val tmp = newFlow()
        merge(successor, input, incoming, tmp)
        copy(tmp, input)
    }
}
