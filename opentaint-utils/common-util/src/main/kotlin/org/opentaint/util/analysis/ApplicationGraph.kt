package org.opentaint.util.analysis

interface ApplicationGraph<Method, Statement> {
    interface MethodGraph<Method, Statement> {
        val method: Method
        val applicationGraph: ApplicationGraph<Method, Statement>

        fun predecessors(node: Statement): Sequence<Statement>
        fun successors(node: Statement): Sequence<Statement>

        fun entryPoints(): Sequence<Statement>
        fun exitPoints(): Sequence<Statement>

        fun statements(): Sequence<Statement>
    }

    fun callees(node: Statement): Sequence<Method>
    fun callers(method: Method): Sequence<Statement>
    fun methodOf(node: Statement): Method

    fun methodGraph(method: Method): MethodGraph<Method, Statement>
}
