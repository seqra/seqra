package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.Reason
import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface TaintEvent<Statement : CommonInst>

data class NewSummaryEdge<Statement : CommonInst>(
    val edge: TaintEdge<Statement>,
) : TaintEvent<Statement>

data class NewVulnerability<Statement : CommonInst>(
    val vulnerability: TaintVulnerability<Statement>,
) : TaintEvent<Statement>

data class EdgeForOtherRunner<Statement : CommonInst>(
    val edge: TaintEdge<Statement>,
    val reason: Reason<TaintDomainFact, Statement>,
) : TaintEvent<Statement> {
    init {
        // TODO: remove this check
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}
