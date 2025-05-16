package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.TraceGraph
import org.opentaint.ir.analysis.sarif.VulnerabilityDescription
import org.opentaint.ir.analysis.sarif.VulnerabilityInstance
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

fun <Method, Statement> UnusedVariableVulnerability<Method, Statement>.toSarif():
    VulnerabilityInstance<UnusedVariableDomainFact, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> =
    VulnerabilityInstance(
        TraceGraph<UnusedVariableDomainFact, Method, Statement>(sink, mutableSetOf(sink), mutableMapOf(), emptyMap()),
        VulnerabilityDescription(ruleId = null, message = message)
    )
