@file:JvmName("BackwardApplicationGraphs")

package org.opentaint.dataflow.graph

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.util.analysis.ApplicationGraph

private class BackwardApplicationGraphImpl<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>,
) : ApplicationGraph<Method, Statement>
    where Method : CommonMethod, Statement : CommonInst {

    private class BackwardMethodGraphImpl<Method, Statement>(
        override val method: Method,
        override val applicationGraph: BackwardApplicationGraphImpl<Method, Statement>,
        private val forward: ApplicationGraph.MethodGraph<Method, Statement>
    ) : ApplicationGraph.MethodGraph<Method, Statement>
            where Method : CommonMethod, Statement : CommonInst {

        override fun predecessors(node: Statement) = forward.successors(node)
        override fun successors(node: Statement) = forward.predecessors(node)

        override fun entryPoints() = forward.exitPoints()
        override fun exitPoints() = forward.entryPoints()

        override fun statements() = forward.statements()
    }

    override fun callees(node: Statement) = forward.callees(node)
    override fun callers(method: Method) = forward.callers(method)


    override fun methodOf(node: Statement) = forward.methodOf(node)

    override fun methodGraph(method: Method): ApplicationGraph.MethodGraph<Method, Statement> =
        BackwardMethodGraphImpl(method, this, forward.methodGraph(method))
}

val <Method, Statement> ApplicationGraph<Method, Statement>.reversed: ApplicationGraph<Method, Statement>
        where Method : CommonMethod, Statement : CommonInst
    get() = when (this) {
        is BackwardApplicationGraphImpl -> this.forward
        else -> BackwardApplicationGraphImpl(this)
    }
