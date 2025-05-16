package org.opentaint.ir.analysis.taint

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

fun <Method, Statement> TaintVulnerability<Method, Statement>.toSarif(
    graph: TraceGraph<TaintDomainFact, Method, Statement>,
): VulnerabilityInstance<TaintDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> =
    VulnerabilityInstance(
        graph,
        VulnerabilityDescription(
            ruleId = null,
            message = rule?.ruleNote
        )
    )
