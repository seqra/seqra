package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance

fun TaintVulnerability.toSarif(
    graph: TraceGraph<TaintDomainFact>,
): VulnerabilityInstance<TaintDomainFact> {
    return VulnerabilityInstance(
        graph,
        VulnerabilityDescription(
            ruleId = null,
            message = rule?.ruleNote
        )
    )
}
