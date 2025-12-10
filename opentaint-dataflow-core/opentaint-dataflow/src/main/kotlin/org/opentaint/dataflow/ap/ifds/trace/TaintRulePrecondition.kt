package org.opentaint.dataflow.ap.ifds.trace

import org.opentaint.dataflow.configuration.CommonTaintAction
import org.opentaint.dataflow.configuration.CommonTaintAssignAction
import org.opentaint.dataflow.configuration.CommonTaintConfigurationItem
import org.opentaint.dataflow.configuration.CommonTaintConfigurationSource

sealed interface TaintRulePrecondition {
    interface PassRuleCondition

    data class Source(
        val rule: CommonTaintConfigurationSource,
        val action: Set<CommonTaintAssignAction>,
    ) : TaintRulePrecondition

    data class Pass(
        val rule: CommonTaintConfigurationItem,
        val action: Set<CommonTaintAction>,
        val condition: PassRuleCondition,
    ) : TaintRulePrecondition
}
