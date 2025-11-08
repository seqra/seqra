package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.ir.taint.configuration.Action
import org.opentaint.ir.taint.configuration.AssignMark
import org.opentaint.ir.taint.configuration.TaintConfigurationItem
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

sealed interface TaintRulePrecondition {
    data class Source(val rule: TaintConfigurationItem, val action: AssignMark) : TaintRulePrecondition
    data class Pass(val rule: TaintConfigurationItem, val action: Action, val fact: InitialFactAp) : TaintRulePrecondition
}
