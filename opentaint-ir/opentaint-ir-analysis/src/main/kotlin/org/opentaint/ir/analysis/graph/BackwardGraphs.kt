@file:JvmName("BackwardApplicationGraphs")

package org.opentaint.ir.analysis.graph

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.CommonProject
import org.opentaint.ir.api.common.analysis.ApplicationGraph
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.api.jvm.JIRClasspath
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.analysis.JIRApplicationGraph
import org.opentaint.ir.api.jvm.cfg.JIRInst

private class BackwardApplicationGraphImpl<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>,
) : ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override val project: CommonProject
        get() = forward.project

    override fun predecessors(node: Statement) = forward.successors(node)
    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)
    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoints(method: Method) = forward.exitPoints(method)
    override fun exitPoints(method: Method) = forward.entryPoints(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)
}

@Suppress("UNCHECKED_CAST")
val <Method, Statement> ApplicationGraph<Method, Statement>.reversed: ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst
    get() = when (this) {
        is JIRApplicationGraph -> this.reversed as ApplicationGraph<Method, Statement>
        is BackwardApplicationGraphImpl -> this.forward
        else -> BackwardApplicationGraphImpl(this)
    }

private class BackwardJIRApplicationGraphImpl(
    val forward: JIRApplicationGraph,
) : JIRApplicationGraph,
    ApplicationGraph<JIRMethod, JIRInst> by BackwardApplicationGraphImpl(forward) {

    override val project: JIRClasspath
        get() = forward.project
}

val JIRApplicationGraph.reversed: JIRApplicationGraph
    get() = if (this is BackwardJIRApplicationGraphImpl) {
        this.forward
    } else {
        BackwardJIRApplicationGraphImpl(this)
    }
