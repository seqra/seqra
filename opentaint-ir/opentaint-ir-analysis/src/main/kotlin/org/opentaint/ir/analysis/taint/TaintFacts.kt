package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.taint.configuration.TaintMark

sealed interface TaintDomainFact

object TaintZeroFact : TaintDomainFact {
    override fun toString(): String = "Zero"
}

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : TaintDomainFact
