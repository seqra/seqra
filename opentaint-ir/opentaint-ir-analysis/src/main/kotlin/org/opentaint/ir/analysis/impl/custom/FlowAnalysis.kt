package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.jvm.cfg.JIRBytecodeGraph

interface FlowAnalysis<NODE, T> {

    val ins: MutableMap<NODE, T>
    val outs: MutableMap<NODE, T>

    val graph: JIRBytecodeGraph<NODE>

    val isForward: Boolean

    fun newFlow(): T

    fun newEntryFlow(): T

    fun merge(in1: T, in2: T, out: T)

    fun copy(source: T?, dest: T)

    fun run()
}
