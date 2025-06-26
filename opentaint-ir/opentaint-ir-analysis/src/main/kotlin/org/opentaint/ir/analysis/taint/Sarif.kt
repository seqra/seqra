package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance
import org.opentaint.ir.api.common.cfg.CommonInst

fun <Statement : CommonInst> TaintVulnerability<Statement>.toSarif(
    graph: TraceGraph<TaintDomainFact, Statement>,
): VulnerabilityInstance<TaintDomainFact, Statement> =
    VulnerabilityInstance(
        graph,
        VulnerabilityDescription(
            ruleId = null,
            message = rule?.ruleNote
        )
    )
