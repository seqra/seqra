package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.AccessPath
import org.opentaint.ir.taint.configuration.TaintMark

sealed interface TaintFact

object Zero : TaintFact {
    override fun toString(): String = javaClass.simpleName
}

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : TaintFact
