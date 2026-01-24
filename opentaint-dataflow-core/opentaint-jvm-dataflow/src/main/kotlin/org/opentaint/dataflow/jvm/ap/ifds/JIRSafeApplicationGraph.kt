package org.opentaint.dataflow.jvm.ap.ifds

import mu.KLogging
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JIRSafeApplicationGraph(
    private val graph: JApplicationGraph
) : JApplicationGraph by graph {
    class SafeMethodGraph(
        private val graph: ApplicationGraph.MethodGraph<JIRMethod, JIRInst>
    ) : ApplicationGraph.MethodGraph<JIRMethod, JIRInst> by graph {
        override fun entryPoints(): Sequence<JIRInst> = try {
            graph.entryPoints()
        } catch (e: Throwable) {
            logger.error(e) { "Method inst list failure $method" }
            // we couldn't find instructions list
            // TODO: maybe fix flowGraph()
            emptySequence()
        }

        override fun exitPoints(): Sequence<JIRInst> = try {
            graph.exitPoints()
        } catch (e: Throwable) {
            logger.error(e) { "Method inst list failure $method" }
            // we couldn't find instructions list
            // TODO: maybe fix flowGraph()
            emptySequence()
        }
    }

    override fun methodGraph(method: JIRMethod) = SafeMethodGraph(graph.methodGraph(method))

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
