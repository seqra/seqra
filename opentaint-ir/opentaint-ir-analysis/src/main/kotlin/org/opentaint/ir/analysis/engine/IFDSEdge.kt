package org.opentaint.ir.analysis.engine

data class IFDSEdge(val u: IFDSVertex, val v: IFDSVertex)

enum class PathEdgePredecessorKind {
    NO_PREDECESSOR,
    UNKNOWN,
    SEQUENT,
    CALL_TO_START,
    THROUGH_SUMMARY
}

data class PathEdgePredecessor(
    val predEdge: IFDSEdge,
    val kind: PathEdgePredecessorKind
)