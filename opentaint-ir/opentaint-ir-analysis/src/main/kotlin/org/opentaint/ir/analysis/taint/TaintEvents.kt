package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Reason

sealed interface TaintEvent

data class NewSummaryEdge(
    val edge: TaintEdge,
) : TaintEvent

data class NewVulnerability(
    val vulnerability: TaintVulnerability,
) : TaintEvent

data class EdgeForOtherRunner(
    val edge: TaintEdge,
    val reason: Reason<TaintDomainFact>
) : TaintEvent {
    init {
        // TODO: remove this check
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}
