package org.opentaint.jvm.graph

import org.opentaint.ir.api.jvm.JcClasspath
import org.opentaint.ir.api.jvm.JcMethod
import org.opentaint.ir.api.jvm.cfg.JcInst
import org.opentaint.ir.api.jvm.ext.cfg.callExpr
import org.opentaint.ir.impl.features.SyncUsagesExtension

open class JApplicationGraphImpl(
    override val cp: JcClasspath,
    private val usages: SyncUsagesExtension,
) : JApplicationGraph {
    override fun predecessors(node: JcInst): Sequence<JcInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        val catchers = graph.catchers(node)
        return successors.asSequence() + catchers.asSequence()
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        val callExpr = node.callExpr ?: return emptySequence()
        return sequenceOf(callExpr.method.method)
    }

    override fun callers(method: JcMethod): Sequence<JcInst> {
        return usages.findUsages(method).flatMap {
            it.flowGraph().instructions.asSequence().filter { inst ->
                val callExpr = inst.callExpr ?: return@filter false
                callExpr.method.method == method
            }
        }
    }

    override fun entryPoints(method: JcMethod): Sequence<JcInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JcMethod): Sequence<JcInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: JcInst): JcMethod {
        return node.location.method
    }

    override fun statementsOf(method: JcMethod): Sequence<JcInst> {
        return method.instList.asSequence()
    }
}
