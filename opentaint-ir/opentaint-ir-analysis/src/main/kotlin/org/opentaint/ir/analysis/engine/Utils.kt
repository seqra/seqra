package org.opentaint.ir.analysis.engine

import org.opentaint.ir.analysis.ifds2.taint.TaintFact
import org.opentaint.ir.analysis.ifds2.taint.Tainted
import org.opentaint.ir.analysis.ifds2.taint.Zero
import org.opentaint.ir.analysis.library.analyzers.NpeTaintNode
import org.opentaint.ir.analysis.library.analyzers.TaintAnalysisNode

fun TaintFact.toDomainFact(): DomainFact = when (this) {
    Zero -> ZEROFact

    is Tainted -> {
        when (mark.name) {
            "NPE" -> NpeTaintNode(variable)
            else -> TaintAnalysisNode(variable, nodeType = mark.name)
        }
    }
}
