package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.ifds.Vulnerability
import org.opentaint.ir.api.common.cfg.CommonInst

data class UnusedVariableSummaryEdge<Statement : CommonInst>(
    override val edge: Edge<UnusedVariableDomainFact, Statement>,
) : SummaryEdge<UnusedVariableDomainFact, Statement>

data class UnusedVariableVulnerability<Statement : CommonInst>(
    override val message: String,
    override val sink: Vertex<UnusedVariableDomainFact, Statement>,
) : Vulnerability<UnusedVariableDomainFact, Statement>
