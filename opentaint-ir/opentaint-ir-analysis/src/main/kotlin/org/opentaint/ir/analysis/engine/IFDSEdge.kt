package org.opentaint.ir.analysis.engine

data class IFDSEdge<out T: DomainFact>(val u: IFDSVertex<T>, val v: IFDSVertex<T>)

enum class PathEdgePredecessorKind {
    NO_PREDECESSOR,
    UNKNOWN,
    SEQUENT,
    CALL_TO_START,
    THROUGH_SUMMARY
}

data class PathEdgePredecessor<out T: DomainFact>(
    val predEdge: IFDSEdge<T>,
    val kind: PathEdgePredecessorKind
)