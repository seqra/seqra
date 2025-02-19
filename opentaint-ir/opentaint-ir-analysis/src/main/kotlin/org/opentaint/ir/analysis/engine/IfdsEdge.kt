package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod

data class IfdsEdge(val u: IfdsVertex, val v: IfdsVertex) {
    init {
        require(u.method == v.method)
    }

    val method: JIRMethod
        get() = u.method
}

sealed interface PredecessorKind {
    object NoPredecessor : PredecessorKind
    object Unknown : PredecessorKind
    object Sequent : PredecessorKind
    object CallToStart : PredecessorKind
    class ThroughSummary(val summaryEdge: IfdsEdge) : PredecessorKind
}

data class PathEdgePredecessor(
    val predEdge: IfdsEdge,
    val kind: PredecessorKind
)