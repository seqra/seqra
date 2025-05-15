package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.api.cfg.JIRInst

sealed interface UnusedVariableDomainFact

object UnusedVariableZeroFact : UnusedVariableDomainFact {
    override fun toString(): String = "Zero"
}

data class UnusedVariable(
    val variable: AccessPath,
    val initStatement: JIRInst,
) : UnusedVariableDomainFact
