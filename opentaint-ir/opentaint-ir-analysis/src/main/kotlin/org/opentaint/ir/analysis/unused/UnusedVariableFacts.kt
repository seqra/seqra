package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.CommonAccessPath
import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface UnusedVariableDomainFact

object UnusedVariableZeroFact : UnusedVariableDomainFact {
    override fun toString(): String = "Zero"
}

data class UnusedVariable(
    val variable: CommonAccessPath,
    val initStatement: CommonInst<*, *>,
) : UnusedVariableDomainFact
