@file:JvmName("BackwardApplicationGraphs")

package org.opentaint.dataflow.graph

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph

private class BackwardApplicationGraphImpl<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>,
) : ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun predecessors(node: Statement) = forward.successors(node)
    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)
    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoints(method: Method) = forward.exitPoints(method)
    override fun exitPoints(method: Method) = forward.entryPoints(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)
    override fun statementsOf(method: Method) = forward.statementsOf(method)
}

val <Method, Statement> ApplicationGraph<Method, Statement>.reversed: ApplicationGraph<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst
    get() = when (this) {
        is BackwardApplicationGraphImpl -> this.forward
        else -> BackwardApplicationGraphImpl(this)
    }
