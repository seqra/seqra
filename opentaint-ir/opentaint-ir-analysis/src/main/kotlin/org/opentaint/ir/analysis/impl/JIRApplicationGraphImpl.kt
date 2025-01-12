package org.opentaint.ir.analysis.impl

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst
import org.opentaint.ir.api.ext.cfg.callExpr
import org.opentaint.ir.impl.features.SyncUsagesExtension

/**
 * Possible we will need JIRRawInst instead of JIRInst
 */
open class JIRApplicationGraphImpl(
    val classpath: JIRClasspath,
    protected val usages: SyncUsagesExtension
) : ApplicationGraph<JIRMethod, JIRInst> {

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.flowGraph().predecessors(node).asSequence()
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.flowGraph().successors(node).asSequence()
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        return node.callExpr?.method?.method?.let {
            sequenceOf(it)
        } ?: emptySequence()
    }

    override fun callers(method: JIRMethod): Sequence<JIRInst> {
        return usages.findUsages(method).flatMap {
            it.flowGraph().instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }
        }
    }

    override fun entryPoint(method: JIRMethod): Sequence<JIRInst> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: JIRInst): JIRMethod {
        return node.location.method
    }
}