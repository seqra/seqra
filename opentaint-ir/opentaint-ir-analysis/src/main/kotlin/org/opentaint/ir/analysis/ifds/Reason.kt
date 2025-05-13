package org.opentaint.ir.analysis.ifds

sealed class Reason {
    object Initial : Reason()

    object External : Reason()

    data class Sequent<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class CallToStart<Fact>(
        val edge: Edge<Fact>,
    ) : Reason()

    data class ThroughSummary<Fact>(
        val edge: Edge<Fact>,
        val summaryEdge: Edge<Fact>,
    ) : Reason()
}
