package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vulnerability
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst
import org.opentaint.ir.taint.configuration.TaintMethodSink

data class TaintSummaryEdge<Method, Statement>(
    override val edge: TaintEdge<Method, Statement>,
) : SummaryEdge<TaintDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>

data class TaintVulnerability<Method, Statement>(
    override val message: String,
    override val sink: TaintVertex<Method, Statement>,
    val rule: TaintMethodSink? = null,
) : Vulnerability<TaintDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>
