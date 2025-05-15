package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vulnerability
import org.opentaint.ir.taint.configuration.TaintMethodSink

data class TaintSummaryEdge(
    override val edge: TaintEdge,
) : SummaryEdge<TaintDomainFact>

data class TaintVulnerability(
    override val message: String,
    override val sink: TaintVertex,
    val rule: TaintMethodSink? = null,
) : Vulnerability<TaintDomainFact>
