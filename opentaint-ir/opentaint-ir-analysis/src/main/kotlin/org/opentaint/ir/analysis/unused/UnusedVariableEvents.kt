package org.opentaint.ir.analysis.unused

import org.opentaint.ir.analysis.ifds.Edge
import org.opentaint.ir.api.common.CommonMethod
import org.opentaint.ir.api.common.cfg.CommonInst

sealed interface UnusedVariableEvent<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst

data class NewSummaryEdge<Method, Statement>(
    val edge: Edge<UnusedVariableDomainFact, Statement>,
) : UnusedVariableEvent<Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst
