package org.opentaint.ir.analysis.impl

import org.opentaint.opentaint-ir.api.JIRClasspath
import org.opentaint.opentaint-ir.api.JIRMethod
import org.opentaint.opentaint-ir.api.analysis.ApplicationGraph
import org.opentaint.opentaint-ir.api.cfg.JIRGraph
import org.opentaint.opentaint-ir.api.cfg.JIRInst
import org.opentaint.opentaint-ir.api.ext.cfg.callExpr
import org.opentaint.opentaint-ir.impl.analysis.JIRAnalysisPlatformImpl
import org.opentaint.opentaint-ir.impl.analysis.features.JIRCacheGraphFeature
import org.opentaint.opentaint-ir.impl.features.SyncUsagesExtension

/**
 * Possible we will need JIRRawInst instead of JIRInst
 */
class JIRApplicationGraphImpl(
    override val classpath: JIRClasspath,
    private val usages: SyncUsagesExtension,
    cacheSize: Long = 10_000,
) : JIRAnalysisPlatformImpl(classpath, listOf(JIRCacheGraphFeature(cacheSize))), ApplicationGraph<JIRMethod, JIRInst> {

    private val JIRMethod.actualFlowGraph: JIRGraph
        get() {
            return flowGraph(this)
        }

    override fun predecessors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.actualFlowGraph.predecessors(node).asSequence()
    }

    override fun successors(node: JIRInst): Sequence<JIRInst> {
        return node.location.method.actualFlowGraph.successors(node).asSequence()
    }

    override fun callees(node: JIRInst): Sequence<JIRMethod> {
        return node.callExpr?.method?.method?.let {
            sequenceOf(it)
        } ?: emptySequence()
    }

    override fun callers(method: JIRMethod): Sequence<JIRInst> {
        return usages.findUsages(method).flatMap {
            it.actualFlowGraph.instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.asSequence()
        }
    }

    override fun entryPoint(method: JIRMethod): Sequence<JIRInst> {
        return method.actualFlowGraph.entries.asSequence()
    }

    override fun exitPoints(method: JIRMethod): Sequence<JIRInst> {
        return method.actualFlowGraph.exits.asSequence()
    }

    override fun methodOf(node: JIRInst): JIRMethod {
        return node.location.method
    }
}