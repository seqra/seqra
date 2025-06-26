package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance
import org.opentaint.ir.api.common.cfg.CommonInst

fun <Statement : CommonInst> UnusedVariableVulnerability<Statement>.toSarif():
    VulnerabilityInstance<UnusedVariableDomainFact, Statement> =
    VulnerabilityInstance(
        TraceGraph(sink, mutableSetOf(sink), mutableMapOf(), emptyMap()),
        VulnerabilityDescription(ruleId = null, message = message)
    )
