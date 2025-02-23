package org.opentaint.ir.analysis.graph

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.impl.features.SyncUsagesExtension

/**
 * Possible we will need JIRRawInst instead of JIRInst
 */
class JIRApplicationGraphImpl(
    override val classpath: JIRClasspath,
    private val usages: SyncUsagesExtension
) : JIRApplicationGraph {
    private val methods = mutableSetOf<JIRMethod>()

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.flowGraph().predecessors(node).asSequence() +
                node.location.method.flowGraph().throwers(node).asSequence()
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.flowGraph().successors(node).asSequence() +
                node.location.method.flowGraph().catchers(node).asSequence()
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        return node.callExpr?.method?.method?.let {
            methods.add(it)
            sequenceOf(it)
        } ?: emptySequence()
    }

    override fun callers(method: JIRMethod): Sequence<JIRInst> {
        methods.add(method)
        return usages.findUsages(method).flatMap {
            it.flowGraph().instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.asSequence()
        }
    }

    override fun entryPoint(method: JIRMethod): Sequence<JIRInst> {
        methods.add(method)
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> {
        methods.add(method)
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: JIRInst): JIRMethod {
        return node.location.method.also { methods.add(it) }
    }
}