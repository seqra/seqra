package org.opentaint.jvm.sast.dataflow

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRCatchInst
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.cfg.JIRThrowInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.jvm.graph.JApplicationGraph
import org.opentaint.util.analysis.ApplicationGraph

class JExplicitExceptionsOnlyApplicationGraph(
    private val graph: JApplicationGraph
) : JApplicationGraph by graph {
    class CutMethodGraph(
        override val applicationGraph: JExplicitExceptionsOnlyApplicationGraph,
        private val graph: ApplicationGraph.MethodGraph<JIRMethod, JIRInst>
    ) : ApplicationGraph.MethodGraph<JIRMethod, JIRInst> by graph {
        override fun successors(node: JIRInst): Sequence<JIRInst> {
            val flowGraph = node.location.method.flowGraph()
            val successors = flowGraph.successors(node)
            val catchers = if (isThrower(node)) flowGraph.catchers(node) else emptySet()
            return successors.asSequence() + catchers.asSequence()
        }

        override fun predecessors(node: JIRInst): Sequence<JIRInst> {
            val graph = node.location.method.flowGraph()
            val predecessors = graph.predecessors(node)
            val throwers = if (node is JIRCatchInst) graph.throwers(node).filter(::isThrower) else emptyList()
            return predecessors.asSequence() + throwers.asSequence()
        }

        private fun isThrower(node: JIRInst) = node is JIRThrowInst || node.callExpr != null
    }

    override fun methodGraph(method: JIRMethod) = CutMethodGraph(this, graph.methodGraph(method))
}
