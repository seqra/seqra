package org.opentaint.ir.analysis.engine

import org.opentaint.ir.api.core.cfg.CoreInst
import org.opentaint.ir.api.core.cfg.CoreInstLocation

/**
 * Represents a directed (from [u] to [v]) edge between two ifds vertices
 */
data class IfdsEdge<Method, Location, Statement>(
    val u: IfdsVertex<Method, Location, Statement>,
    val v: IfdsVertex<Method, Location, Statement>
) where Location : CoreInstLocation<Method>,
        Statement : CoreInst<Location, Method, *> {
    init {
        require(u.method == v.method)
    }

    val method: Method
        get() = u.method
}

sealed interface PredecessorKind {
    object NoPredecessor : PredecessorKind
    object Unknown : PredecessorKind
    object Sequent : PredecessorKind
    object CallToStart : PredecessorKind
    class ThroughSummary(val summaryEdge: IfdsEdge<*, *, *>) : PredecessorKind
}

/**
 * Contains info about predecessor of path edge.
 * Used mainly to restore traces.
 */
data class PathEdgePredecessor(
    val predEdge: IfdsEdge<*, *, *>,
    val kind: PredecessorKind
)