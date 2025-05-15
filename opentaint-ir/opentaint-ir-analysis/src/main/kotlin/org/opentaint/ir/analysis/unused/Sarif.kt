package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance

fun UnusedVariableVulnerability.toSarif(): VulnerabilityInstance<UnusedVariableDomainFact> {
    return VulnerabilityInstance(
        TraceGraph(sink, mutableSetOf(sink), mutableMapOf(), emptyMap()),
        VulnerabilityDescription(ruleId = null, message = message)
    )
}
