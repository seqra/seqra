package org.opentaint.dataflow.jvm.bidi.interp

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.jvm.graph.JMethodExitExceptionalInst
import org.opentaint.util.analysis.ApplicationGraph
import java.util.BitSet

/** The backward zero traversal can enter code that reaches no original forward exit. */
class JIRExitWiredGraph(private val forward: ApplicationGraph<CommonMethod, CommonInst>) :
    ApplicationGraph<CommonMethod, CommonInst> by forward {
    override fun methodGraph(method: CommonMethod): ApplicationGraph.MethodGraph<CommonMethod, CommonInst> {
        val graph = forward.methodGraph(method)
        val exceptionalExit = graph.exitPoints().firstOrNull { it is JMethodExitExceptionalInst } ?: return graph
        val exiting = BitSet()
        val queue = ArrayDeque<CommonInst>()
        for (exit in graph.exitPoints()) if (exiting.mark(exit)) queue.addLast(exit)
        while (queue.isNotEmpty()) {
            for (previous in graph.predecessors(queue.removeFirst())) {
                if (exiting.mark(previous)) queue.addLast(previous)
            }
        }
        val wired = graph.statements().filter { !exiting[it.index] }.toList()
        if (wired.isEmpty()) return graph
        return Wired(this, graph, exceptionalExit, exiting, wired)
    }

    private class Wired(
        override val applicationGraph: ApplicationGraph<CommonMethod, CommonInst>,
        private val graph: ApplicationGraph.MethodGraph<CommonMethod, CommonInst>,
        private val exceptionalExit: CommonInst,
        private val exiting: BitSet,
        private val wired: List<CommonInst>,
    ) : ApplicationGraph.MethodGraph<CommonMethod, CommonInst> by graph {
        override fun successors(node: CommonInst): Sequence<CommonInst> =
            if (exiting[node.index]) graph.successors(node) else graph.successors(node) + exceptionalExit

        override fun predecessors(node: CommonInst): Sequence<CommonInst> =
            if (node != exceptionalExit) graph.predecessors(node) else graph.predecessors(node) + wired.asSequence()
    }

    private companion object {
        val CommonInst.index: Int get() = (this as JIRInst).location.index
        fun BitSet.mark(node: CommonInst): Boolean {
            if (get(node.index)) return false
            set(node.index)
            return true
        }
    }
}
