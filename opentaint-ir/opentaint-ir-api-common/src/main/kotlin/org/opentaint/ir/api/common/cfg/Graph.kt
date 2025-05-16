package org.opentaint.ir.api.common.cfg

interface Graph<out Statement> : Iterable<Statement> {
    fun successors(node: @UnsafeVariance Statement): Set<Statement>
    fun predecessors(node: @UnsafeVariance Statement): Set<Statement>
}

interface ControlFlowGraph<out Statement> : Graph<Statement> {
    val entries: List<Statement>
    val exits: List<Statement>

    val instructions: List<Statement>
}
