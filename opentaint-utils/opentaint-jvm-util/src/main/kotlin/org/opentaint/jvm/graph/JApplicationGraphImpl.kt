package org.opentaint.jvm.graph

import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.cfg.JIRInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.impl.features.SyncUsagesExtension

open class JApplicationGraphImpl(
    override val cp: JIRClasspath,
    private val usages: SyncUsagesExtension,
) : JApplicationGraph {
    open class JMethodGraphImpl(
        override val applicationGraph: JApplicationGraph,
        override val method: JIRMethod
    ) : JApplicationGraph.JMethodGraph {
        private val flowGraph by lazy { method.flowGraph() }

        override fun predecessors(node: JIRInst): Sequence<JIRInst> {
            val predecessors = flowGraph.predecessors(node)
            val throwers = flowGraph.throwers(node)
            return predecessors.asSequence() + throwers.asSequence()
        }

        override fun successors(node: JIRInst): Sequence<JIRInst> {
            val successors = flowGraph.successors(node)
            val catchers = flowGraph.catchers(node)
            return successors.asSequence() + catchers.asSequence()
        }

        override fun entryPoints(): Sequence<JIRInst> = flowGraph.entries.asSequence()

        override fun exitPoints(): Sequence<JIRInst> = flowGraph.exits.asSequence()

        override fun statements(): Sequence<JIRInst> = method.instList.asSequence()
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        val callExpr = node.callExpr ?: return emptySequence()
        return sequenceOf(callExpr.method.method)
    }

    override fun callers(method: JIRMethod): Sequence<JIRInst> {
        return usages.findUsages(method).flatMap {
            it.flowGraph().instructions.asSequence().filter { inst ->
                val callExpr = inst.callExpr ?: return@filter false
                callExpr.method.method == method
            }
        }
    }

    override fun methodOf(node: JIRInst): JIRMethod {
        return node.location.method
    }

    override fun methodGraph(method: JIRMethod): JApplicationGraph.JMethodGraph =
        JMethodGraphImpl(this, method)
}
