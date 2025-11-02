package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.util.Maybe

interface MethodStartPrecondition {
    fun factPrecondition(fact: InitialFactAp): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>>
}
