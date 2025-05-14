package org.opentaint.ir.analysis.ifds2.taint

import org.opentaint.ir.analysis.engine.DomainFact
import org.opentaint.ir.analysis.engine.ZEROFact
import org.opentaint.ir.analysis.library.analyzers.NpeTaintNode
import org.opentaint.ir.analysis.library.analyzers.TaintAnalysisNode
import org.opentaint.ir.analysis.library.analyzers.TaintNode
import org.opentaint.ir.analysis.paths.AccessPath
import org.opentaint.ir.taint.configuration.TaintMark

sealed interface TaintFact

object Zero : TaintFact {
    override fun toString(): String = this.javaClass.simpleName
}

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : TaintFact {
    constructor(fact: TaintNode) : this(fact.variable, TaintMark(fact.nodeType))
}
