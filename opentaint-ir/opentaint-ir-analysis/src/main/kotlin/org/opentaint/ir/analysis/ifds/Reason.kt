package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface Reason<out Fact, out Statement : CommonInst> {

    object Initial : Reason<Nothing, Nothing>

    object External : Reason<Nothing, Nothing>

    data class CrossUnitCall<Fact, Statement : CommonInst>(
        val caller: Vertex<Fact, Statement>,
    ) : Reason<Fact, Statement>

    data class Sequent<Fact, Statement : CommonInst>(
        val edge: Edge<Fact, Statement>,
    ) : Reason<Fact, Statement>

    data class CallToStart<Fact, Statement : CommonInst>(
        val edge: Edge<Fact, Statement>,
    ) : Reason<Fact, Statement>

    data class ThroughSummary<Fact, Statement : CommonInst>(
        val edge: Edge<Fact, Statement>,
        val summaryEdge: Edge<Fact, Statement>,
    ) : Reason<Fact, Statement>
}
