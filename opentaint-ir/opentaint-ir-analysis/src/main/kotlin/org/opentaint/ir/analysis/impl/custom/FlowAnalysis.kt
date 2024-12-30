package org.opentaint.ir.analysis.impl.custom

import org.opentaint.ir.api.cfg.JIRGraph
import org.opentaint.ir.api.cfg.JIRInst

interface FlowAnalysis<T> {

    val ins: MutableMap<JIRInst, T>
    val outs: MutableMap<JIRInst, T>

    val graph: JIRGraph

    val isForward: Boolean

    fun newFlow(): T

    fun newEntryFlow(): T

    fun merge(in1: T, in2: T, out: T)

    fun copy(source: T?, dest: T)

    fun run()
}