package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.ifds.Vulnerability
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

data class UnusedVariableSummaryEdge<Method, Statement>(
    override val edge: Edge<UnusedVariableDomainFact, Method, Statement>,
) : SummaryEdge<UnusedVariableDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>

data class UnusedVariableVulnerability<Method, Statement>(
    override val message: String,
    override val sink: Vertex<UnusedVariableDomainFact, Method, Statement>,
) : Vulnerability<UnusedVariableDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement>
