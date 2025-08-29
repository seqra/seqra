package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst

class JIRSafeApplicationGraph(
    private val graph: JIRApplicationGraph
) : JIRApplicationGraph by graph {
    override fun entryPoints(method: JIRMethod): Sequence<JIRInst> = try {
        graph.entryPoints(method)
    } catch (e: Throwable) {
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> = try {
        graph.exitPoints(method)
    } catch (e: Throwable) {
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }
}
