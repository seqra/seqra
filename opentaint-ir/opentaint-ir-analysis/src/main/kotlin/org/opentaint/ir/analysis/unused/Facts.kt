package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.api.cfg.JIRInst

sealed interface Fact

object Zero : Fact {
    override fun toString(): String = javaClass.simpleName
}

data class UnusedVariable(
    val variable: AccessPath,
    val initStatement: JIRInst,
) : Fact
