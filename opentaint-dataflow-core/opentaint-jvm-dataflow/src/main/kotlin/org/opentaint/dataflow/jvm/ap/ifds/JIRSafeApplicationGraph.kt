package org.opentaint.dataflow.jvm.ap.ifds

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph

class JIRSafeApplicationGraph(
    private val graph: JApplicationGraph
) : JApplicationGraph by graph {
    override fun entryPoints(method: JIRMethod): Sequence<JIRInst> = try {
        graph.entryPoints(method)
    } catch (e: Throwable) {
        logger.error(e) { "Method inst list failure $method" }
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> = try {
        graph.exitPoints(method)
    } catch (e: Throwable) {
        logger.error(e) { "Method inst list failure $method" }
        // we couldn't find instructions list
        // TODO: maybe fix flowGraph()
        emptySequence()
    }

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
