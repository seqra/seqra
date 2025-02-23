package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod

/**
 * Represents a directed (from [u] to [v]) edge between two ifds vertices
 */
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

/**
 * Contains info about predecessor of path edge.
 * Used mainly to restore traces.
 */
data class PathEdgePredecessor(
    val predEdge: IfdsEdge,
    val kind: PredecessorKind
)