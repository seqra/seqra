package org.opentaint.ir.analysis.ifds

sealed interface Reason<out Fact> {
    object Initial : Reason<Nothing>

    object External : Reason<Nothing>

    data class CrossUnitCall<Fact>(
        val caller: Vertex<Fact>,
    ) : Reason<Fact>

    data class Sequent<Fact>(
        val edge: Edge<Fact>,
    ) : Reason<Fact>

    data class CallToStart<Fact>(
        val edge: Edge<Fact>,
    ) : Reason<Fact>

    data class ThroughSummary<Fact>(
        val edge: Edge<Fact>,
        val summaryEdge: Edge<Fact>,
    ) : Reason<Fact>
}
