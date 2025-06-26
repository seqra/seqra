package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vulnerability
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.taint.configuration.TaintMethodSink

data class TaintSummaryEdge<Statement : CommonInst>(
    override val edge: TaintEdge<Statement>,
) : SummaryEdge<TaintDomainFact, Statement>

data class TaintVulnerability<Statement : CommonInst>(
    override val message: String,
    override val sink: TaintVertex<Statement>,
    val rule: TaintMethodSink? = null,
) : Vulnerability<TaintDomainFact, Statement>
