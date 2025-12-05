package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource
import org.opentaint.dataflow.ap.ifds.access.InitialFactAp

sealed interface TaintRulePrecondition {
    data class Source(val rule: CommonTaintConfigurationSource, val action: CommonTaintAssignAction) : TaintRulePrecondition
    data class Pass(val rule: CommonTaintConfigurationItem, val action: CommonTaintAction, val fact: InitialFactAp) : TaintRulePrecondition
}
