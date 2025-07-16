package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.impl.cfg.JIRInstLocationImpl
import org.opentaint.dataflow.jvm.graph.JIRNoopInst
import java.util.concurrent.ConcurrentHashMap

class JIRSingleEntryPointApplicationGraph(
    private val graph: JIRApplicationGraph
) : JIRApplicationGraph by graph {
    private val startInstCache = ConcurrentHashMap<JIRMethod, JIRNoopInst>()

    // For backward analysis we may want for method to start with "neutral" operation =>
    //  we add noop to the beginning of every method
    private fun getStartInst(method: JIRMethod): JIRNoopInst = startInstCache.computeIfAbsent(method) {
        val lineNumber = method.flowGraph().entries.firstOrNull()?.lineNumber?.let { it - 1 } ?: -1
        JIRNoopInst(JIRInstLocationImpl(method, -1, lineNumber))
    }

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return when (node) {
            getStartInst(method) -> {
                emptySequence()
            }

            in graph.entryPoints(method) -> {
                sequenceOf(getStartInst(method))
            }

            else -> {
                graph.predecessors(node)
            }
        }
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        val method = methodOf(node)
        return when (node) {
            getStartInst(method) -> {
                graph.entryPoints(method)
            }

            else -> {
                graph.successors(node)
            }
        }
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        error("Graph is not suitable for call resolution")
    }

    override fun callers(method: JIRMethod): Sequence<JIRInst> {
        error("Graph is not suitable for call resolution")
    }

    override fun entryPoints(method: JIRMethod): Sequence<JIRInst> = try {
        sequenceOf(getStartInst(method))
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
