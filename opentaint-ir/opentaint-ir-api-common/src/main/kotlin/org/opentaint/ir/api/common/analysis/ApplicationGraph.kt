package org.opentaint.ir.api.common.analysis

import org.opentaint.ir.api.common.CommonProject

/**
 * Provides both CFG and call graph (i.e., the supergraph in terms of RHS95 paper).
 */
interface ApplicationGraph<Method, Statement> {
    val project: CommonProject

    fun predecessors(node: Statement): Sequence<Statement>
    fun successors(node: Statement): Sequence<Statement>

    fun callees(node: Statement): Sequence<Method>
    fun callers(method: Method): Sequence<Statement>

    fun entryPoints(method: Method): Sequence<Statement>
    fun exitPoints(method: Method): Sequence<Statement>

    fun methodOf(node: Statement): Method
}
