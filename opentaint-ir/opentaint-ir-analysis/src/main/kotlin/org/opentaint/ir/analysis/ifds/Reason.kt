package org.opentaint.ir.analysis.ifds

import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface Reason<out Fact, out Method, out Statement> {
    object Initial : Reason<Nothing, Nothing, Nothing>

    object External : Reason<Nothing, Nothing, Nothing>

    data class CrossUnitCall<Fact, Method, Statement>(
        val caller: Vertex<Fact, Method, Statement>,
    ) : Reason<Fact, Method, Statement>
        where Method : CommonMethod<Method, Statement>,
              Statement : CommonInst<Method, Statement>

    data class Sequent<Fact, Method, Statement>(
        val edge: Edge<Fact, Method, Statement>,
    ) : Reason<Fact, Method, Statement>
        where Method : CommonMethod<Method, Statement>,
              Statement : CommonInst<Method, Statement>

    data class CallToStart<Fact, Method, Statement>(
        val edge: Edge<Fact, Method, Statement>,
    ) : Reason<Fact, Method, Statement>
        where Method : CommonMethod<Method, Statement>,
              Statement : CommonInst<Method, Statement>

    data class ThroughSummary<Fact, Method, Statement>(
        val edge: Edge<Fact, Method, Statement>,
        val summaryEdge: Edge<Fact, Method, Statement>,
    ) : Reason<Fact, Method, Statement>
        where Method : CommonMethod<Method, Statement>,
              Statement : CommonInst<Method, Statement>
}
