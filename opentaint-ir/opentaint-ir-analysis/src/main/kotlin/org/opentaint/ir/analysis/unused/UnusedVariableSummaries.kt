package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.analysis.ifds.SummaryEdge
import org.opentaint.ir.analysis.ifds.Vertex
import org.opentaint.ir.analysis.ifds.Vulnerability

data class UnusedVariableSummaryEdge(
    override val edge: Edge<UnusedVariableDomainFact>,
) : SummaryEdge<UnusedVariableDomainFact>

data class UnusedVariableVulnerability(
    override val message: String,
    override val sink: Vertex<UnusedVariableDomainFact>,
) : Vulnerability<UnusedVariableDomainFact>
