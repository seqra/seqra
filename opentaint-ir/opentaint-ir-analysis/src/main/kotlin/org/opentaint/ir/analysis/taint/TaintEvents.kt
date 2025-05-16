package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Reason
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface TaintEvent<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>

data class NewSummaryEdge<Method, Statement>(
    val edge: TaintEdge<Method, Statement>,
) : TaintEvent<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>

data class NewVulnerability<Method, Statement>(
    val vulnerability: TaintVulnerability<Method, Statement>,
) : TaintEvent<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>

data class EdgeForOtherRunner<Method, Statement>(
    val edge: TaintEdge<Method, Statement>,
    val reason: Reason<TaintDomainFact, Method, Statement>,
) : TaintEvent<Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    init {
        // TODO: remove this check
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}
