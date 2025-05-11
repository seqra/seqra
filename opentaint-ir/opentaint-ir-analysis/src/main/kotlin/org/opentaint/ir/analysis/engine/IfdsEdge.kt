package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.JIRMethod

/**
 * Represents a directed (from [from] to [to]) edge between two ifds vertices
 */
data class IfdsEdge(
    val from: IfdsVertex,
    val to: IfdsVertex,
) {
    init {
        require(from.method == to.method)
    }

    var reason: IfdsEdge? = null

    val method: JIRMethod
        get() = from.method
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
    val kind: PredecessorKind,
)
