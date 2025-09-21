package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp
import org.opentaint.util.Maybe

interface MethodCallPrecondition {
    data class Precondition(val rule: TaintConfigurationItem, val action: Action, val fact: InitialFactAp)

    fun factSourceRulePrecondition(initialFact: InitialFactAp): Maybe<List<Pair<TaintConfigurationItem, AssignMark>>>
    fun factPassRulePrecondition(initialFact: InitialFactAp): Maybe<List<Precondition>>
}