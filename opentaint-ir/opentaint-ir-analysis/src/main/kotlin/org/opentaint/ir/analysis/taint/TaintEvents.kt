package org.opentaint.ir.analysis.taint

sealed interface TaintEvent

data class NewSummaryEdge(
    val edge: TaintEdge,
) : TaintEvent

data class NewVulnerability(
    val vulnerability: Vulnerability,
) : TaintEvent

data class EdgeForOtherRunner(
    val edge: TaintEdge,
) : TaintEvent {
    init {
        // TODO: remove this check
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}
