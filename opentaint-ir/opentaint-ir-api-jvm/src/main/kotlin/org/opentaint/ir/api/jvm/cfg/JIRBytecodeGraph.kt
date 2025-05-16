package org.opentaint.ir.api.jvm.cfg

import org.opentaint.ir.api.common.cfg.ControlFlowGraph

interface JIRBytecodeGraph<out Statement> : ControlFlowGraph<Statement> {
    fun throwers(node: @UnsafeVariance Statement): Set<Statement>
    fun catchers(node: @UnsafeVariance Statement): Set<Statement>
}
