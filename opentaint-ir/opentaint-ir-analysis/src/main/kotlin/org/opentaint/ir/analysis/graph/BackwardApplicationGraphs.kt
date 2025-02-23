@file:JvmName("BackwardApplicationGraphs")
package org.opentaint.ir.analysis.graph

import org.opentaint.ir.api.JIRClasspath
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.ApplicationGraph
import org.opentaint.ir.api.analysis.JIRApplicationGraph
import org.opentaint.ir.api.cfg.JIRInst

private class BackwardApplicationGraph<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>
) : ApplicationGraph<Method, Statement> {
    override fun predecessors(node: Statement) = forward.successors(node)

    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)

    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoint(method: Method) = forward.exitPoints(method)

    override fun exitPoints(method: Method) = forward.entryPoint(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)
}

val <Method, Statement> ApplicationGraph<Method, Statement>.reversed
    get() = if (this is BackwardApplicationGraph) {
        this.forward
    } else {
        BackwardApplicationGraph(this)
    }

private class BackwardJIRApplicationGraph(val forward: JIRApplicationGraph) :
    JIRApplicationGraph, ApplicationGraph<JIRMethod, JIRInst> by BackwardApplicationGraph(forward) {
    override val classpath: JIRClasspath
        get() = forward.classpath
}

val JIRApplicationGraph.reversed: JIRApplicationGraph
    get() = if (this is BackwardJIRApplicationGraph) {
        this.forward
    } else {
        BackwardJIRApplicationGraph(this)
    }