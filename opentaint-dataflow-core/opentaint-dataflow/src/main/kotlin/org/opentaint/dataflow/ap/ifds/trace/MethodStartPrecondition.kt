package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

interface MethodStartPrecondition {
    fun factPrecondition(fact: InitialFactAp): List<TaintRulePrecondition.Source>
}
